package com.simplexray.an.topology

import android.content.Context as AndroidContext
import android.os.IBinder
import android.os.RemoteException
import com.simplexray.an.config.ApiConfig
import com.simplexray.an.common.AppLogger
import com.xray.app.stats.command.GetStatsRequest
import com.google.gson.Gson
import com.xray.app.stats.command.StatsServiceGrpcKt
import com.simplexray.an.service.IVpnServiceBinder
import com.simplexray.an.service.IVpnStateCallback
import io.grpc.Context as GrpcContext
import io.grpc.Deadline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import com.simplexray.an.data.source.LogFileManager
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.service.XrayProcessManager
import io.grpc.Status
import io.grpc.StatusException

/**
 * Application-scoped singleton repository for network topology graph state.
 * 
 * Architecture fixes:
 * - HOT SharedFlow with replay (survives background/resume)
 * - Application scope (not Activity scope)
 * - Binder reconnect logic with topology callback re-registration
 * - Node/Edge delta merging with adjacency maps
 * - Sliding window edge weight normalization
 * - Last 50 snapshots for replay
 */
class TopologyRepository private constructor(
    private val context: AndroidContext,
    private var stub: StatsServiceGrpcKt.StatsServiceCoroutineStub,
    private val repositoryScope: CoroutineScope
) {
    companion object {
        private const val TAG = "TopologyRepository"
        
        // SharedFlow configuration - HOT flow with replay buffer
        private const val REPLAY_SIZE = 10
        private const val EXTRA_BUFFER_CAPACITY = 200
        private const val MAX_SNAPSHOTS = 50
        
        // Polling interval
        private const val POLLING_INTERVAL_MS = 3000L
        
        @Volatile
        private var INSTANCE: TopologyRepository? = null
        
        /**
         * Get singleton instance. Must be called from Application.onCreate()
         */
        fun getInstance(
            application: AndroidContext,
            stub: StatsServiceGrpcKt.StatsServiceCoroutineStub,
            scope: CoroutineScope
        ): TopologyRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TopologyRepository(application, stub, scope).also { INSTANCE = it }
            }
        }
        
        /**
         * Get instance if already initialized, null otherwise
         */
        fun getInstanceOrNull(): TopologyRepository? = INSTANCE
    }
    
    // HOT SharedFlow with replay buffer - survives background/resume
    private val _topologyFlow = MutableSharedFlow<TopologyGraph>(
        replay = REPLAY_SIZE,
        extraBufferCapacity = EXTRA_BUFFER_CAPACITY,
        onBufferOverflow = kotlinx.coroutines.flow.BufferOverflow.DROP_OLDEST
    )
    
    val topologyFlow: SharedFlow<TopologyGraph> = _topologyFlow
    
    // Current graph state (immutable snapshot)
    @Volatile
    private var currentGraph = TopologyGraph(emptyList(), emptyList())
    
    // Snapshot history for replay (last 50)
    private val snapshotHistory = ArrayDeque<TopologyGraph>(MAX_SNAPSHOTS)
    
    // Adjacency maps for fast merging
    private val nodeMap = mutableMapOf<String, Node>()
    private val edgeMap = mutableMapOf<String, MutableSet<Edge>>() // NodeId -> Set of edges
    private val edgeByPair = mutableMapOf<String, Edge>() // "from->to" -> Edge
    
    // Weight tracking for sliding window
    private val edgeWeightHistory = mutableMapOf<String, ArrayDeque<Float>>() // Edge key -> sliding window
    
    // Binder state
    @Volatile
    private var binder: IVpnServiceBinder? = null
    @Volatile
    private var serviceBinder: IBinder? = null
    private var serviceConnection: android.content.ServiceConnection? = null
    @Volatile
    private var isBinding = false
    
    // Binder callback for topology updates
    private val topologyCallback = object : IVpnStateCallback.Stub() {
        override fun onConnected() {
            AppLogger.d("$TAG: Service connected, requesting topology snapshot")
            repositoryScope.launch {
                // Request immediate full snapshot on reconnect
                requestFullSnapshot()
            }
        }
        
        override fun onDisconnected() {
            AppLogger.d("$TAG: Service disconnected")
            // Keep last graph, don't clear on disconnect
        }
        
        override fun onError(error: String?) {
            AppLogger.w("$TAG: Service error: $error")
        }
        
        override fun onTrafficUpdate(uplink: Long, downlink: Long) {
            // Traffic updates don't directly update topology, but trigger refresh
            // This ensures we stay in sync with tunnel state
            repositoryScope.launch {
                // Trigger refresh if we haven't updated recently
                refreshIfNeeded()
            }
        }
    }
    
    // Death recipient for binder death detection
    private val deathRecipient = object : IBinder.DeathRecipient {
        override fun binderDied() {
            AppLogger.w("$TAG: Binder died, reconnecting...")
            serviceBinder?.unlinkToDeath(this, 0)
            binder = null
            serviceBinder = null
            
            // Reconnect and re-register callback
            repositoryScope.launch {
                delay(1000L)
                bindToService()
            }
        }
    }
    
    private val prevWeights = mutableMapOf<String, Float>()
    private val ipDomainCache = mutableMapOf<String, String>()
    private val resolver = com.simplexray.an.domain.DomainResolver(repositoryScope)
    private val logFileManager = LogFileManager(context)
    private val prefs = Preferences(context)
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 3
    private var lastUpdateTime = 0L
    private val refreshIntervalMs = 5000L
    
    init {
        // Start polling loop
        repositoryScope.launch {
            startPolling()
        }
        
        // Bind to service for topology callbacks
        bindToService()
    }
    
    /**
     * Refresh gRPC stub with current port from preferences
     */
    private fun refreshStubIfNeeded() {
        val currentPort = prefs.apiPort.takeIf { it > 0 } ?: XrayProcessManager.statsPort
        if (currentPort <= 0) return
        
        val (currentHost, currentGrpcPort) = com.simplexray.an.grpc.GrpcChannelFactory.currentEndpoint()
        
        if (currentGrpcPort != currentPort || currentHost != "127.0.0.1") {
            com.simplexray.an.grpc.GrpcChannelFactory.setEndpoint("127.0.0.1", currentPort)
            stub = com.simplexray.an.grpc.GrpcChannelFactory.statsStub("127.0.0.1", currentPort)
            AppLogger.d("$TAG: Refreshed gRPC stub with port $currentPort")
        }
    }
    
    /**
     * Bind to VPN service for topology state callbacks
     */
    private fun bindToService() {
        if (isBinding || binder != null) {
            AppLogger.d("$TAG: Already binding or bound")
            return
        }
        
        isBinding = true
        val intent = android.content.Intent(context, com.simplexray.an.service.TProxyService::class.java)
        serviceConnection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
                AppLogger.d("$TAG: Service connected")
                isBinding = false
                
                try {
                    binder = IVpnServiceBinder.Stub.asInterface(service)
                    serviceBinder = service
                    if (binder == null) {
                        AppLogger.w("$TAG: Failed to get binder interface")
                        return
                    }
                    
                    // Link to death recipient
                    service?.linkToDeath(deathRecipient, 0)
                    
                    // Register topology callback - CRITICAL: Re-register on reconnect
                    val registered = binder!!.registerCallback(topologyCallback)
                    if (registered) {
                        AppLogger.d("$TAG: Topology callback registered successfully")
                        
                        // Request immediate full snapshot
                        repositoryScope.launch {
                            requestFullSnapshot()
                        }
                    } else {
                        AppLogger.w("$TAG: Failed to register topology callback")
                    }
                } catch (e: Exception) {
                    AppLogger.e("$TAG: Error in onServiceConnected", e)
                    isBinding = false
                    binder = null
                    serviceBinder = null
                }
            }
            
            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                AppLogger.w("$TAG: Service disconnected")
                isBinding = false
                binder?.unregisterCallback(topologyCallback)
                serviceBinder?.unlinkToDeath(deathRecipient, 0)
                binder = null
                serviceBinder = null
            }
        }
        
        try {
            val bound = (context.applicationContext as? android.app.Application)?.bindService(
                intent,
                serviceConnection!!,
                android.content.Context.BIND_AUTO_CREATE or android.content.Context.BIND_IMPORTANT
            ) ?: false
            
            if (!bound) {
                AppLogger.w("$TAG: Failed to bind to service")
                isBinding = false
                serviceConnection = null
            }
        } catch (e: Exception) {
            AppLogger.e("$TAG: Error binding to service", e)
            isBinding = false
            serviceConnection = null
        }
    }
    
    /**
     * Request full topology snapshot immediately
     */
    private suspend fun requestFullSnapshot() {
        withContext(Dispatchers.IO) {
            try {
                fetchAndMergeTopology()
            } catch (e: Exception) {
                AppLogger.w("$TAG: Error requesting full snapshot", e)
            }
        }
    }
    
    /**
     * Refresh if needed (throttled)
     */
    private suspend fun refreshIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < refreshIntervalMs) {
            return // Throttle updates
        }
        requestFullSnapshot()
    }
    
    /**
     * Start polling loop for topology updates
     */
    private suspend fun startPolling() {
        val useMock = ApiConfig.isMock(context)
        if (useMock) {
            startMock()
            return
        }
        
        while (isActive) {
            try {
                refreshStubIfNeeded()
                
                val name = ApiConfig.getOnlineKey(context)
                if (name.isBlank()) {
                    if (currentGraph.nodes.isEmpty()) {
                        emitGraph(TopologyGraph(emptyList(), emptyList()))
                    }
                    delay(5000)
                    continue
                }
                
                val apiPort = prefs.apiPort.takeIf { it > 0 } ?: XrayProcessManager.statsPort
                if (apiPort <= 0) {
                    if (currentGraph.nodes.isEmpty()) {
                        emitGraph(TopologyGraph(emptyList(), emptyList()))
                    }
                    delay(2000)
                    continue
                }
                
                fetchAndMergeTopology()
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                consecutiveErrors++
                AppLogger.e("$TAG: Error fetching topology (attempt $consecutiveErrors)", e)
                
                if (consecutiveErrors >= maxConsecutiveErrors && currentGraph.nodes.isEmpty()) {
                    val logBasedGraph = tryExtractTopologyFromLogs()
                    if (logBasedGraph.nodes.isNotEmpty()) {
                        applyDelta(logBasedGraph)
                        consecutiveErrors = 0
                    } else {
                        emitGraph(TopologyGraph(emptyList(), emptyList()))
                    }
                } else if (currentGraph.nodes.isEmpty()) {
                    emitGraph(TopologyGraph(emptyList(), emptyList()))
                }
                
                refreshStubIfNeeded()
            }
            
            delay(POLLING_INTERVAL_MS)
        }
    }
    
    /**
     * Fetch topology from gRPC and merge with current state
     */
    private suspend fun fetchAndMergeTopology() = withContext(Dispatchers.Default) {
        val deadlineMs = ApiConfig.getGrpcDeadlineMs(context)
        val deadline = Deadline.after(deadlineMs, TimeUnit.MILLISECONDS)
        val deadlineCtx = GrpcContext.current().withDeadline(deadline, Executors.newSingleThreadScheduledExecutor())
        val previous = deadlineCtx.attach()
        
        val resp = try {
            stub.getStatsOnlineIpList(GetStatsRequest.newBuilder().setName(ApiConfig.getOnlineKey(context)).build())
        } catch (e: StatusException) {
            deadlineCtx.detach(previous)
            deadlineCtx.cancel(null)
            when (e.status.code) {
                Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> {
                    consecutiveErrors++
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        val logBasedGraph = tryExtractTopologyFromLogs()
                        if (logBasedGraph.nodes.isNotEmpty()) {
                            applyDelta(logBasedGraph)
                        }
                    }
                    return@withContext
                }
                else -> throw e
            }
        } finally {
            deadlineCtx.detach(previous)
            deadlineCtx.cancel(null)
        }
        
        consecutiveErrors = 0
        lastUpdateTime = System.currentTimeMillis()
        
        if (resp.ipsMap.isEmpty()) {
            val logBasedGraph = tryExtractTopologyFromLogs()
            if (logBasedGraph.nodes.isNotEmpty()) {
                applyDelta(logBasedGraph)
            }
            return@withContext
        }
        
        val bytesKey = ApiConfig.getOnlineBytesKey(context)
        val bytesMap = if (bytesKey.isNotBlank()) {
            try {
                val bytesDeadline = Deadline.after(deadlineMs, TimeUnit.MILLISECONDS)
                val bytesDeadlineCtx = GrpcContext.current().withDeadline(bytesDeadline, Executors.newSingleThreadScheduledExecutor())
                val prevBytes = bytesDeadlineCtx.attach()
                try {
                    stub.getStatsOnlineIpList(GetStatsRequest.newBuilder().setName(bytesKey).build()).ipsMap
                } finally {
                    bytesDeadlineCtx.detach(prevBytes)
                    bytesDeadlineCtx.cancel(null)
                }
            } catch (_: Throwable) {
                null
            }
        } else null
        
        // Build new graph from gRPC response
        val newGraph = buildGraphFromStats(resp.ipsMap, bytesMap)
        
        // Apply delta merge
        applyDelta(newGraph)
    }
    
    /**
     * Build graph from stats response
     */
    private fun buildGraphFromStats(
        ipsMap: Map<String, Long>,
        bytesMap: Map<String, Long>?
    ): TopologyGraph {
        val central = Node(id = "local", label = "Local", type = Node.Type.Domain)
        val ipNodes = mutableMapOf<String, Node>()
        val domainNodes = mutableMapOf<String, Node>()
        val edges = mutableListOf<Edge>()
        
        val sourceMap = bytesMap ?: ipsMap
        val maxVal = if (sourceMap.isNotEmpty()) {
            sourceMap.values.maxOrNull()?.toDouble()?.toFloat()?.coerceAtLeast(1f) ?: 1f
        } else 1f
        
        val ipDomain = parseIpDomainMap(ApiConfig.getIpDomainJson(context)).toMutableMap()
        val autoRdns = ApiConfig.isAutoReverseDns(context)
        
        // First pass: create IP nodes and edges
        ipsMap.forEach { (ip, v0) ->
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
        
        // Connect central -> domain with aggregated weights
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
        
        // Apply sliding window normalization to edge weights
        val normalizedEdges = edges.map { edge ->
            normalizeEdgeWeight(edge)
        }
        
        return TopologyGraph(nodes, normalizedEdges)
    }
    
    /**
     * Normalize edge weight using sliding window average
     */
    private fun normalizeEdgeWeight(edge: Edge): Edge {
        val key = "${edge.from}->${edge.to}"
        val history = edgeWeightHistory.getOrPut(key) { ArrayDeque() }
        
        // Add new weight to window (keep last 10 samples)
        history.addLast(edge.weight)
        while (history.size > 10) {
            history.removeFirst()
        }
        
        // Calculate average
        val avgWeight = history.average().toFloat()
        
        // Apply EMA smoothing
        val alpha = ApiConfig.getTopologyAlpha(context)
        val prev = prevWeights[key]
        val smoothed = if (prev == null) {
            avgWeight
        } else {
            alpha * avgWeight + (1 - alpha) * prev
        }
        prevWeights[key] = smoothed
        
        return edge.copy(weight = smoothed.coerceIn(0.05f, 1f))
    }
    
    /**
     * Apply delta merge: add missing nodes, remove stale nodes, update edge weights
     */
    private fun applyDelta(newGraph: TopologyGraph) {
        // Build new maps
        val newNodes = newGraph.nodes.associateBy { it.id }
        val newEdges = newGraph.edges.associateBy { "${it.from}->${it.to}" }
        
        // Merge nodes: add new, update existing, track removed
        val existingNodeIds = nodeMap.keys.toSet()
        val newNodeIds = newNodes.keys
        
        // Add/update nodes
        newNodes.forEach { (id, node) ->
            nodeMap[id] = node
        }
        
        // Remove stale nodes (not in new graph and not central)
        val nodesToRemove = existingNodeIds - newNodeIds - setOf("local")
        nodesToRemove.forEach { id ->
            nodeMap.remove(id)
            edgeMap.remove(id)
        }
        
        // Merge edges: add new, update existing, remove stale
        val existingEdgeKeys = edgeByPair.keys.toSet()
        val newEdgeKeys = newEdges.keys
        
        // Add/update edges
        newEdges.forEach { (key, edge) ->
            edgeByPair[key] = edge
            edgeMap.getOrPut(edge.from) { mutableSetOf() }.add(edge)
            edgeMap.getOrPut(edge.to) { mutableSetOf() }.add(edge)
        }
        
        // Remove stale edges
        val edgesToRemove = existingEdgeKeys - newEdgeKeys
        edgesToRemove.forEach { key ->
            val edge = edgeByPair.remove(key)
            edge?.let { e ->
                edgeMap[e.from]?.remove(e)
                edgeMap[e.to]?.remove(e)
            }
        }
        
        // Build final graph
        val finalNodes = nodeMap.values.toList()
        val finalEdges = edgeByPair.values.toList()
        
        val finalGraph = TopologyGraph(finalNodes, finalEdges)
        emitGraph(finalGraph)
    }
    
    /**
     * Emit new graph snapshot (thread-safe)
     */
    private fun emitGraph(graph: TopologyGraph) {
        currentGraph = graph
        
        // Add to history
        snapshotHistory.addLast(graph)
        while (snapshotHistory.size > MAX_SNAPSHOTS) {
            snapshotHistory.removeFirst()
        }
        
        // Emit to flow
        repositoryScope.launch {
            _topologyFlow.emit(graph)
        }
    }
    
    private fun startMock() {
        repositoryScope.launch {
            val (nodes, edges) = mockGraph()
            emitGraph(TopologyGraph(nodes, edges))
        }
    }
    
    private fun parseIpDomainMap(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
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
    
    private fun tryExtractTopologyFromLogs(): TopologyGraph {
        return try {
            val logContent = logFileManager.readLogs() ?: return TopologyGraph(emptyList(), emptyList())
            if (logContent.isBlank()) return TopologyGraph(emptyList(), emptyList())
            
            val ipPattern = Regex("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")
            val domainPattern = Regex("""\b([a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\b""")
            
            val ipNodes = mutableMapOf<String, Node>()
            val domainNodes = mutableMapOf<String, Node>()
            val ipCounts = mutableMapOf<String, Int>()
            val domainCounts = mutableMapOf<String, Int>()
            
            logContent.split("\n").forEach { line ->
                ipPattern.findAll(line).forEach { match ->
                    val ip = match.value
                    if (ip != "127.0.0.1" && ip != "0.0.0.0" && !ip.startsWith("192.168.") && !ip.startsWith("10.")) {
                        ipCounts[ip] = (ipCounts[ip] ?: 0) + 1
                        val ipId = "ip:$ip"
                        ipNodes.getOrPut(ipId) { Node(id = ipId, label = ip, type = Node.Type.IP) }
                    }
                }
                
                domainPattern.findAll(line).forEach { match ->
                    val domain = match.value.lowercase()
                    if (domain.length > 3 && !domain.contains("localhost") && !domain.startsWith("x.") &&
                        domain.count { it == '.' } >= 1 &&
                        !domain.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) {
                        domainCounts[domain] = (domainCounts[domain] ?: 0) + 1
                        val domId = "dom:$domain"
                        domainNodes.getOrPut(domId) { Node(id = domId, label = domain, type = Node.Type.Domain) }
                    }
                }
            }
            
            val edges = mutableListOf<Edge>()
            val central = Node(id = "local", label = "Local", type = Node.Type.Domain)
            val maxIpCount = ipCounts.values.maxOrNull() ?: 1
            val maxDomainCount = domainCounts.values.maxOrNull() ?: 1
            
            ipNodes.values.forEach { ipNode ->
                val ip = ipNode.label
                val count = ipCounts[ip] ?: 1
                val weight = (count.toFloat() / maxIpCount.toFloat()).coerceIn(0.1f, 1f)
                edges += Edge(from = central.id, to = ipNode.id, weight = weight)
            }
            
            domainNodes.values.forEach { domainNode ->
                val domain = domainNode.label
                val count = domainCounts[domain] ?: 1
                val weight = (count.toFloat() / maxDomainCount.toFloat()).coerceIn(0.1f, 1f)
                edges += Edge(from = central.id, to = domainNode.id, weight = weight)
            }
            
            val nodes = listOf(central) + domainNodes.values + ipNodes.values
            
            val alpha = ApiConfig.getTopologyAlpha(context)
            val smoothed = edges.map { e ->
                val key = e.from + "->" + e.to
                val prev = prevWeights[key]
                val w = if (prev == null) e.weight else (alpha * e.weight + (1 - alpha) * prev)
                prevWeights[key] = w
                e.copy(weight = w)
            }
            
            TopologyGraph(nodes, smoothed)
        } catch (e: Exception) {
            AppLogger.w("$TAG: Failed to extract topology from logs", e)
            TopologyGraph(emptyList(), emptyList())
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        binder?.unregisterCallback(topologyCallback)
        serviceBinder?.unlinkToDeath(deathRecipient, 0)
        binder = null
        serviceBinder = null
        
        serviceConnection?.let { conn ->
            try {
                (context.applicationContext as? android.app.Application)?.unbindService(conn)
            } catch (e: Exception) {
                AppLogger.w("$TAG: Error unbinding service", e)
            }
        }
        serviceConnection = null
    }
}

/**
 * Immutable topology graph snapshot
 */
data class TopologyGraph(
    val nodes: List<Node>,
    val edges: List<Edge>
)

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
