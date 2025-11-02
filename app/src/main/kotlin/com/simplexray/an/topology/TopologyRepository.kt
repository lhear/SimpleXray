package com.simplexray.an.topology

import android.content.Context as AndroidContext
import com.simplexray.an.config.ApiConfig
import com.xray.app.stats.command.GetStatsRequest
import com.google.gson.Gson
import com.xray.app.stats.command.StatsServiceGrpcKt
import io.grpc.Context as GrpcContext
import io.grpc.Deadline
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TopologyRepository(
    private val context: AndroidContext,
    private val stub: StatsServiceGrpcKt.StatsServiceCoroutineStub,
    externalScope: CoroutineScope? = null
) {
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _graph = MutableStateFlow(Pair(emptyList<Node>(), emptyList<Edge>()))
    private val prevWeights = mutableMapOf<String, Float>()
    private val ipDomainCache = mutableMapOf<String, String>()
    private val resolver = com.simplexray.an.domain.DomainResolver(scope)
    val graph: Flow<Pair<List<Node>, List<Edge>>> = _graph.asStateFlow()

    fun start() {
        val useMock = ApiConfig.isMock(context)
        if (useMock) startMock() else startLive()
    }

    private fun startMock() {
        scope.launch {
            // Static sample graph for now
            val (nodes, edges) = mockGraph()
            _graph.emit(nodes to edges)
        }
    }

    private fun startLive() {
        scope.launch {
            while (isActive) {
                try {
                    val name = ApiConfig.getOnlineKey(context)
                    if (name.isBlank()) {
                        // If online key is not configured, wait before retrying
                        delay(5000)
                        continue
                    } else {
                        val deadlineMs = com.simplexray.an.config.ApiConfig.getGrpcDeadlineMs(context)
                        val deadline = Deadline.after(deadlineMs, TimeUnit.MILLISECONDS)
                        val deadlineCtx = GrpcContext.current().withDeadline(deadline, Executors.newSingleThreadScheduledExecutor())
                        val previous = deadlineCtx.attach()
                        val resp = try {
                            stub.getStatsOnlineIpList(GetStatsRequest.newBuilder().setName(name).build())
                        } finally {
                            deadlineCtx.detach(previous)
                            deadlineCtx.cancel(null)
                        }
                        val bytesKey = ApiConfig.getOnlineBytesKey(context)
                        val bytesMap = if (bytesKey.isNotBlank()) try {
                            val bytesDeadline = Deadline.after(deadlineMs, TimeUnit.MILLISECONDS)
                            val bytesDeadlineCtx = GrpcContext.current().withDeadline(bytesDeadline, Executors.newSingleThreadScheduledExecutor())
                            val prevBytes = bytesDeadlineCtx.attach()
                            try {
                                stub.getStatsOnlineIpList(GetStatsRequest.newBuilder().setName(bytesKey).build()).ipsMap
                            } finally {
                                bytesDeadlineCtx.detach(prevBytes)
                                bytesDeadlineCtx.cancel(null)
                            }
                        } catch (_: Throwable) { null } else null
                        val central = Node(id = "local", label = "Local", type = Node.Type.Domain)
                        val ipNodes = mutableMapOf<String, Node>()
                        val domainNodes = mutableMapOf<String, Node>()
                        val edges = mutableListOf<Edge>()

                        val sourceMap = bytesMap ?: resp.ipsMap
                        val maxVal = if (sourceMap.isNotEmpty()) {
                            sourceMap.values.maxOrNull()?.toDouble()?.toFloat()?.coerceAtLeast(1f) ?: 1f
                        } else 1f
                        val ipDomain = parseIpDomainMap(ApiConfig.getIpDomainJson(context)).toMutableMap()

                        // First pass: create IP nodes and edges from central
                        val autoRdns = ApiConfig.isAutoReverseDns(context)
                        resp.ipsMap.forEach { (ip, v0) ->
                            val ipId = "ip:$ip"
                            val ipNode = ipNodes.getOrPut(ipId) { Node(id = ipId, label = ip, type = Node.Type.IP) }
                            val baseVal = (sourceMap[ip] ?: v0).toDouble().toFloat()
                            val weight = (baseVal / maxVal).coerceIn(0.05f, 1f)
                            var domain = ipDomain[ip]
                            if (domain.isNullOrBlank() && autoRdns) {
                                val cached = ipDomainCache[ip]
                                if (cached != null) {
                                    domain = cached
                                    ipDomain[ip] = cached
                                } else {
                                    resolver.resolveAsync(ip) { k, v -> 
                                        if (!v.isNullOrBlank()) {
                                            ipDomainCache[k] = v
                                        }
                                    }
                                }
                            }
                            if (domain != null && domain.isNotBlank()) {
                                val domId = "dom:$domain"
                                val domNode = domainNodes.getOrPut(domId) { Node(id = domId, label = domain, type = Node.Type.Domain) }
                                edges += Edge(from = domId, to = ipId, weight = weight)
                            } else {
                                edges += Edge(from = central.id, to = ipId, weight = weight)
                            }
                        }

                        // If we have domain nodes, connect central -> domain with aggregated weights
                        if (domainNodes.isNotEmpty()) {
                            val agg = mutableMapOf<String, Float>()
                            edges.filter { it.from.startsWith("dom:") }.forEach { e ->
                                agg[e.from] = (agg[e.from] ?: 0f) + e.weight
                            }
                            val maxAgg = agg.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                            for ((domId, sumW) in agg) {
                                val w = (sumW / maxAgg).coerceIn(0.05f, 1f)
                                edges += Edge(from = central.id, to = domId, weight = w)
                            }
                        }

                        val nodes = listOf(central) + domainNodes.values + ipNodes.values

                        // Apply EMA smoothing to edge weights
                        val alpha = com.simplexray.an.config.ApiConfig.getTopologyAlpha(context)
                        val smoothed = edges.map { e ->
                            val key = e.from + "->" + e.to
                            val prev = prevWeights[key]
                            val w = if (prev == null) e.weight else (alpha * e.weight + (1 - alpha) * prev)
                            prevWeights[key] = w
                            e.copy(weight = w)
                        }
                        _graph.emit(nodes to smoothed)
                    }
                } catch (e: Throwable) {
                    // Log error for debugging but don't clear the graph
                    // Keep previous state if available, only clear on first error
                    android.util.Log.e("TopologyRepository", "Error fetching topology data", e)
                    // Only emit empty if this is the first error (no previous data)
                    if (_graph.value.first.isEmpty()) {
                        _graph.emit(emptyList<Node>() to emptyList())
                    }
                }
                delay(3000)
            }
        }
    }

    private fun parseIpDomainMap(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            val mapType = Map::class.java
            @Suppress("UNCHECKED_CAST")
            val raw = Gson().fromJson(json, Map::class.java) as Map<*, *>
            raw.mapNotNull { (k, v) ->
                val key = k?.toString()?.trim()
                val value = v?.toString()?.trim()
                if (!key.isNullOrBlank() && !value.isNullOrBlank()) key to value else null
            }.toMap()
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}

private fun mockGraph(): Pair<List<Node>, List<Edge>> {
    val central = Node(id = "local", label = "Local", type = Node.Type.Domain)
    val domains = listOf(
        Node("dom:youtube.com", "youtube.com", Node.Type.Domain),
        Node("dom:twitter.com", "twitter.com", Node.Type.Domain),
        Node("dom:cloudflare.com", "cloudflare.com", Node.Type.Domain),
        Node("dom:steamcommunity.com", "steamcommunity.com", Node.Type.Domain),
    )
    val ips = listOf(
        Node("ip:142.250.0.1", "142.250.0.1", Node.Type.IP),
        Node("ip:104.16.0.1", "104.16.0.1", Node.Type.IP),
        Node("ip:151.101.1.1", "151.101.1.1", Node.Type.IP),
        Node("ip:13.107.246.1", "13.107.246.1", Node.Type.IP),
    )
    val edges = listOf(
        Edge("dom:youtube.com", "ip:142.250.0.1", 0.8f),
        Edge("dom:twitter.com", "ip:104.16.0.1", 0.6f),
        Edge("dom:cloudflare.com", "ip:104.16.0.1", 0.7f),
        Edge("dom:steamcommunity.com", "ip:151.101.1.1", 0.5f),
        Edge("dom:youtube.com", "ip:151.101.1.1", 0.4f),
        Edge("local", "dom:youtube.com", 0.9f),
        Edge("local", "dom:twitter.com", 0.7f),
        Edge("local", "dom:cloudflare.com", 0.8f),
        Edge("local", "dom:steamcommunity.com", 0.6f)
    )
    val nodes = listOf(central) + domains + ips
    return nodes to edges
}
