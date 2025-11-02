package com.simplexray.an.topology

import android.content.Context
import com.simplexray.an.config.ApiConfig
import com.xray.app.stats.command.GetStatsRequest
import com.google.gson.Gson
import com.xray.app.stats.command.StatsServiceGrpcKt
import io.grpc.Context
import kotlinx.coroutines.CoroutineScope
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
    private val context: Context,
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
                        _graph.emit(emptyList<Node>() to emptyList())
                    } else {
                        val deadlineMs = com.simplexray.an.config.ApiConfig.getGrpcDeadlineMs(context)
                        val deadlineCtx = Context.current().withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                        val resp = try {
                            deadlineCtx.attach()
                            stub.getStatsOnlineIpList(GetStatsRequest.newBuilder().setName(name).build())
                        } finally {
                            deadlineCtx.detach(deadlineCtx.cancellationCause)
                            deadlineCtx.cancel(null)
                        }
                        val bytesKey = ApiConfig.getOnlineBytesKey(context)
                        val bytesMap = if (bytesKey.isNotBlank()) try {
                            val bytesDeadlineCtx = Context.current().withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                            try {
                                bytesDeadlineCtx.attach()
                                stub.getStatsOnlineIpList(GetStatsRequest.newBuilder().setName(bytesKey).build()).ipsMap
                            } finally {
                                bytesDeadlineCtx.detach(bytesDeadlineCtx.cancellationCause)
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
                } catch (_: Throwable) {
                    _graph.emit(emptyList<Node>() to emptyList())
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
    val domains = listOf(
        Node("d1", "youtube.com", Node.Type.Domain),
        Node("d2", "twitter.com", Node.Type.Domain),
        Node("d3", "cloudflare.com", Node.Type.Domain),
        Node("d4", "steamcommunity.com", Node.Type.Domain),
    )
    val ips = listOf(
        Node("i1", "142.250.0.1", Node.Type.IP),
        Node("i2", "104.16.0.1", Node.Type.IP),
        Node("i3", "151.101.1.1", Node.Type.IP),
        Node("i4", "13.107.246.1", Node.Type.IP),
    )
    val edges = listOf(
        Edge("d1", "i1", 2f),
        Edge("d2", "i2", 1f),
        Edge("d3", "i2", 1.5f),
        Edge("d4", "i3", 1f),
        Edge("d1", "i3", 0.5f)
    )
    return domains + ips to edges
}
