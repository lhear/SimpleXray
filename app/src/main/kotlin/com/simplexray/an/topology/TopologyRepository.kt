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
import com.simplexray.an.data.source.LogFileManager
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.service.XrayProcessManager
import io.grpc.Status
import io.grpc.StatusException

class TopologyRepository(
    private val context: AndroidContext,
    private var stub: StatsServiceGrpcKt.StatsServiceCoroutineStub,
    externalScope: CoroutineScope? = null
) {
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _graph = MutableStateFlow(Pair(emptyList<Node>(), emptyList<Edge>()))
    private val prevWeights = mutableMapOf<String, Float>()
    private val ipDomainCache = mutableMapOf<String, String>()
    private val resolver = com.simplexray.an.domain.DomainResolver(scope)
    private val logFileManager = LogFileManager(context)
    private val prefs = Preferences(context)
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 3
    val graph: Flow<Pair<List<Node>, List<Edge>>> = _graph.asStateFlow()
    
    /**
     * Refresh gRPC stub with current port from preferences
     */
    private fun refreshStubIfNeeded() {
        val currentPort = prefs.apiPort.takeIf { it > 0 } ?: XrayProcessManager.statsPort
        if (currentPort <= 0) return // Port not available yet
        
        val (currentHost, currentGrpcPort) = com.simplexray.an.grpc.GrpcChannelFactory.currentEndpoint()
        
        // Update endpoint if port changed or host is different
        if (currentGrpcPort != currentPort || currentHost != "127.0.0.1") {
            // Port may have changed, update endpoint
            com.simplexray.an.grpc.GrpcChannelFactory.setEndpoint("127.0.0.1", currentPort)
            stub = com.simplexray.an.grpc.GrpcChannelFactory.statsStub("127.0.0.1", currentPort)
            android.util.Log.d("TopologyRepository", "Refreshed gRPC stub with port $currentPort (was $currentGrpcPort)")
        }
    }

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
                    // Refresh stub in case port changed
                    refreshStubIfNeeded()
                    
                    val name = ApiConfig.getOnlineKey(context)
                    if (name.isBlank()) {
                        // If online key is not configured, wait before retrying
                        // Emit empty state so UI can show appropriate message
                        if (_graph.value.first.isEmpty()) {
                            _graph.emit(emptyList<Node>() to emptyList())
                        }
                        delay(5000)
                        continue
                    } else {
                        // Wait for Xray service to be ready (check if port is available)
                        val apiPort = prefs.apiPort.takeIf { it > 0 } ?: XrayProcessManager.statsPort
                        if (apiPort <= 0) {
                            // Port not available yet, wait and try again
                            if (_graph.value.first.isEmpty()) {
                                _graph.emit(emptyList<Node>() to emptyList())
                            }
                            delay(2000)
                            continue
                        }
                        
                        val deadlineMs = com.simplexray.an.config.ApiConfig.getGrpcDeadlineMs(context)
                        val deadline = Deadline.after(deadlineMs, TimeUnit.MILLISECONDS)
                        val deadlineCtx = GrpcContext.current().withDeadline(deadline, Executors.newSingleThreadScheduledExecutor())
                        val previous = deadlineCtx.attach()
                        val resp = try {
                            stub.getStatsOnlineIpList(GetStatsRequest.newBuilder().setName(name).build())
                        } catch (e: StatusException) {
                            // Handle gRPC errors
                            when (e.status.code) {
                                Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> {
                                    consecutiveErrors++
                                    if (consecutiveErrors >= maxConsecutiveErrors) {
                                        android.util.Log.w("TopologyRepository", "gRPC unavailable after $consecutiveErrors attempts, trying log fallback")
                                        // Try log fallback
                                        val logBasedGraph = tryExtractTopologyFromLogs()
                                        if (logBasedGraph.first.isNotEmpty()) {
                                            _graph.emit(logBasedGraph)
                                        } else if (_graph.value.first.isEmpty()) {
                                            _graph.emit(emptyList<Node>() to emptyList())
                                        }
                                        delay(3000)
                                    } else {
                                        // Retry with refreshed stub
                                        refreshStubIfNeeded()
                                        delay(1000)
                                    }
                                    deadlineCtx.detach(previous)
                                    deadlineCtx.cancel(null)
                                    continue
                                }
                                else -> throw e
                            }
                        } finally {
                            deadlineCtx.detach(previous)
                            deadlineCtx.cancel(null)
                        }
                        
                        // Reset error count on success
                        consecutiveErrors = 0
                        
                        // Check if response is empty - if so, try to use logs as fallback
                        if (resp.ipsMap.isEmpty()) {
                            // No data available yet, but connection is working
                            // Try to extract topology data from service logs
                            val logBasedGraph = tryExtractTopologyFromLogs()
                            if (logBasedGraph.first.isNotEmpty()) {
                                _graph.emit(logBasedGraph)
                                android.util.Log.d("TopologyRepository", "Using log-based topology data: ${logBasedGraph.first.size} nodes")
                            } else {
                                // Keep previous graph if exists, otherwise emit empty
                                if (_graph.value.first.isEmpty()) {
                                    _graph.emit(emptyList<Node>() to emptyList())
                                }
                            }
                            delay(3000)
                            continue
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
                    consecutiveErrors++
                    // Log error for debugging but don't clear the graph
                    android.util.Log.e("TopologyRepository", "Error fetching topology data (attempt $consecutiveErrors)", e)
                    
                    // After multiple failures, try log fallback
                    if (consecutiveErrors >= maxConsecutiveErrors && _graph.value.first.isEmpty()) {
                        android.util.Log.d("TopologyRepository", "Trying log fallback after $consecutiveErrors errors")
                        val logBasedGraph = tryExtractTopologyFromLogs()
                        if (logBasedGraph.first.isNotEmpty()) {
                            _graph.emit(logBasedGraph)
                            consecutiveErrors = 0 // Reset on successful fallback
                        } else {
                            _graph.emit(emptyList<Node>() to emptyList())
                        }
                    } else if (_graph.value.first.isEmpty()) {
                        _graph.emit(emptyList<Node>() to emptyList())
                    }
                    
                    // Try refreshing stub on error
                    refreshStubIfNeeded()
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

    /**
     * Extract topology data from service logs as fallback when gRPC stats are unavailable
     */
    private fun tryExtractTopologyFromLogs(): Pair<List<Node>, List<Edge>> {
        return try {
            val logContent = logFileManager.readLogs() ?: return emptyList<Node>() to emptyList()
            if (logContent.isBlank()) return emptyList<Node>() to emptyList()
            
            val ipPattern = Regex("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")
            val domainPattern = Regex("""\b([a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\b""")
            
            val ipNodes = mutableMapOf<String, Node>()
            val domainNodes = mutableMapOf<String, Node>()
            val ipCounts = mutableMapOf<String, Int>()
            val domainCounts = mutableMapOf<String, Int>()
            val ipDomainMap = mutableMapOf<String, String>()
            
            // Parse logs line by line
            logContent.split("\n").forEach { line ->
                // Extract IP addresses
                ipPattern.findAll(line).forEach { match ->
                    val ip = match.value
                    if (ip != "127.0.0.1" && ip != "0.0.0.0" && !ip.startsWith("192.168.") && !ip.startsWith("10.")) {
                        ipCounts[ip] = (ipCounts[ip] ?: 0) + 1
                        val ipId = "ip:$ip"
                        ipNodes.getOrPut(ipId) { 
                            Node(id = ipId, label = ip, type = Node.Type.IP) 
                        }
                    }
                }
                
                // Extract domain names (excluding common log prefixes)
                domainPattern.findAll(line).forEach { match ->
                    val domain = match.value.lowercase()
                    // Filter out common false positives
                    if (domain.length > 3 && 
                        !domain.contains("localhost") && 
                        !domain.startsWith("x.") &&
                        domain.count { it == '.' } >= 1 &&
                        !domain.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) {
                        domainCounts[domain] = (domainCounts[domain] ?: 0) + 1
                        val domId = "dom:$domain"
                        domainNodes.getOrPut(domId) {
                            Node(id = domId, label = domain, type = Node.Type.Domain)
                        }
                    }
                }
            }
            
            // Create edges based on frequency (higher count = higher weight)
            val edges = mutableListOf<Edge>()
            val central = Node(id = "local", label = "Local", type = Node.Type.Domain)
            val maxIpCount = ipCounts.values.maxOrNull() ?: 1
            val maxDomainCount = domainCounts.values.maxOrNull() ?: 1
            
            // Connect IPs to central
            ipNodes.values.forEach { ipNode ->
                val ip = ipNode.label
                val count = ipCounts[ip] ?: 1
                val weight = (count.toFloat() / maxIpCount.toFloat()).coerceIn(0.1f, 1f)
                edges += Edge(from = central.id, to = ipNode.id, weight = weight)
            }
            
            // Connect domains to central
            domainNodes.values.forEach { domainNode ->
                val domain = domainNode.label
                val count = domainCounts[domain] ?: 1
                val weight = (count.toFloat() / maxDomainCount.toFloat()).coerceIn(0.1f, 1f)
                edges += Edge(from = central.id, to = domainNode.id, weight = weight)
            }
            
            val nodes = listOf(central) + domainNodes.values + ipNodes.values
            
            // Apply EMA smoothing
            val alpha = com.simplexray.an.config.ApiConfig.getTopologyAlpha(context)
            val smoothed = edges.map { e ->
                val key = e.from + "->" + e.to
                val prev = prevWeights[key]
                val w = if (prev == null) e.weight else (alpha * e.weight + (1 - alpha) * prev)
                prevWeights[key] = w
                e.copy(weight = w)
            }
            
            android.util.Log.d("TopologyRepository", "Extracted ${nodes.size} nodes and ${smoothed.size} edges from logs")
            nodes to smoothed
        } catch (e: Exception) {
            android.util.Log.w("TopologyRepository", "Failed to extract topology from logs", e)
            emptyList<Node>() to emptyList()
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
