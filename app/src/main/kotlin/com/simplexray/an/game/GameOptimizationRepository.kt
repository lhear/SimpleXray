package com.simplexray.an.game

import android.content.Context
import com.simplexray.an.common.AppLogger
import com.simplexray.an.logging.LoggerRepository
import com.simplexray.an.perf.PerformanceOptimizer
import com.simplexray.an.service.IVpnServiceBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * GameOptimizationRepository - Singleton game optimization layer.
 * 
 * Responsibilities:
 * - Hot SharedFlow<GameSnapshot> with replay=16, extraBufferCapacity=128
 * - Game server classifier (suffix/prefix/IP ranges) + UDP/QUIC preference
 * - Route pinning: prefer stable outbound for given game host/port (TTL=120s)
 * - NAT keepalive (configurable interval, default 12s)
 * - MTU/MSS advisor: expose recommended MSS/MTU given recent PMTU signals
 * - DSCP tag suggestion (Expedited Forwarding EF=46) where platform permits
 * - Jitter guard & smoothing with sliding window
 * 
 * Architecture:
 * - Singleton pattern (survives Activity recreation)
 * - Hot SharedFlow<GameSnapshot> with replay=16, extraBuffer=128
 * - Thread-safe route pinning with TTL expiration
 * - Sliding window for RTT/loss/jitter smoothing
 */
object GameOptimizationRepository {
    private const val TAG = "GameOptimizationRepository"
    private const val REPLAY_BUFFER = 16
    private const val EXTRA_BUFFER = 128
    private const val DEFAULT_ROUTE_TTL_MS = 120_000L // 120 seconds
    private const val DEFAULT_NAT_KEEPALIVE_INTERVAL_MS = 12_000L // 12 seconds
    private const val JITTER_WINDOW_SIZE = 10
    private const val RTT_WINDOW_SIZE = 10
    private const val LOSS_WINDOW_SIZE = 10
    private const val MTU_MSS_DEFAULT = 1360 // Default MSS for games
    private const val LOG_RATE_LIMIT_MS = 30_000L // 30 seconds
    
    // Hot SharedFlow with replay buffer
    private val _gameSnapshot = MutableSharedFlow<GameSnapshot>(
        replay = REPLAY_BUFFER,
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val gameSnapshot: SharedFlow<GameSnapshot> = _gameSnapshot.asSharedFlow()
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Context reference
    @Volatile
    private var context: Context? = null
    
    // Binder reference
    @Volatile
    private var binder: IVpnServiceBinder? = null
    private var serviceBinder: android.os.IBinder? = null
    
    // Route pinning: GameKey -> PinnedRoute
    private val pinnedRoutes = ConcurrentHashMap<GameKey, PinnedRoute>()
    
    // Sliding windows for metrics
    private val rttWindow = ArrayDeque<Int>(RTT_WINDOW_SIZE)
    private val lossWindow = ArrayDeque<Float>(LOSS_WINDOW_SIZE)
    private val jitterWindow = ArrayDeque<Int>(JITTER_WINDOW_SIZE)
    
    // MTU/MSS tracking
    private val lastInferredMtu = AtomicReference<Int?>(null)
    private val lastPmtuSignal = AtomicReference<Long>(0L)
    
    // NAT keepalive state
    private var natKeepaliveJob: kotlinx.coroutines.Job? = null
    private var natKeepaliveIntervalMs = DEFAULT_NAT_KEEPALIVE_INTERVAL_MS
    
    // Log rate limiting
    private val lastLogTime = ConcurrentHashMap<String, Long>()
    
    // Current snapshot state
    private val currentSnapshot = AtomicReference<GameSnapshot>(GameSnapshot())
    
    /**
     * Initialize repository with application context
     */
    fun initialize(appContext: Context) {
        if (context == null) {
            context = appContext.applicationContext
            AppLogger.d("$TAG: Initialized with application context")
            
            // Emit initial snapshot
            scope.launch {
                emitSnapshot(GameSnapshot())
            }
        }
    }
    
    /**
     * Classify host/port and determine game class
     */
    fun classify(host: String, port: Int, isUdp: Boolean): GameClass {
        val isGame = GameMatcher.isGameHost(host)
        val gameId = GameMatcher.gameIdFor(host)
        
        return GameClass(
            isGame = isGame,
            gameId = gameId,
            host = host,
            port = port,
            isUdp = isUdp,
            preferredTransport = if (isGame && isUdp) TransportPref.UDP_QUIC else TransportPref.AUTO
        )
    }
    
    /**
     * Prefer transport based on RTT, loss, and protocol
     */
    fun preferTransport(rttMs: Int, lossPct: Float, isUdp: Boolean): TransportPref {
        // If UDP/QUIC and conditions are good, prefer it
        if (isUdp) {
            // Prefer UDP/QUIC if RTT < 200ms and loss < 8%
            if (rttMs < 200 && lossPct < 8.0f) {
                return TransportPref.UDP_QUIC
            }
            // If loss > 8% or RTT > 200ms for >10s, consider TCP fallback
            if (lossPct > 8.0f || rttMs > 200) {
                // Check if poor conditions persist (would need time tracking)
                return TransportPref.UDP_QUIC_WITH_FALLBACK
            }
            return TransportPref.UDP_QUIC
        }
        
        return TransportPref.AUTO
    }
    
    /**
     * Pin route for game host/port with TTL
     */
    fun pinRoute(key: GameKey, outboundTag: String, ttlMs: Long = DEFAULT_ROUTE_TTL_MS) {
        val route = PinnedRoute(
            key = key,
            outboundTag = outboundTag,
            timestamp = System.currentTimeMillis(),
            ttlMs = ttlMs
        )
        
        pinnedRoutes[key] = route
        
        rateLimitedLog("$TAG: Pinned route for ${key.host}:${key.port} -> $outboundTag (TTL: ${ttlMs}ms)")
        
        // Schedule expiration check
        scope.launch {
            delay(ttlMs)
            if (pinnedRoutes.containsKey(key)) {
                val expired = pinnedRoutes[key]
                if (expired != null && System.currentTimeMillis() - expired.timestamp >= expired.ttlMs) {
                    pinnedRoutes.remove(key)
                    rateLimitedLog("$TAG: Route pin expired for ${key.host}:${key.port}")
                    emitSnapshot(updateSnapshot())
                }
            }
        }
        
        // Update snapshot
        scope.launch {
            emitSnapshot(updateSnapshot())
        }
    }
    
    /**
     * Record RTT sample
     */
    fun onRttSample(ms: Int) {
        synchronized(rttWindow) {
            rttWindow.addLast(ms)
            if (rttWindow.size > RTT_WINDOW_SIZE) {
                rttWindow.removeFirst()
            }
        }
        
        scope.launch {
            emitSnapshot(updateSnapshot())
        }
    }
    
    /**
     * Record loss sample
     */
    fun onLossSample(pct: Float) {
        synchronized(lossWindow) {
            lossWindow.addLast(pct)
            if (lossWindow.size > LOSS_WINDOW_SIZE) {
                lossWindow.removeFirst()
            }
        }
        
        scope.launch {
            emitSnapshot(updateSnapshot())
        }
    }
    
    /**
     * Record jitter sample
     */
    fun onJitterSample(ms: Int) {
        synchronized(jitterWindow) {
            jitterWindow.addLast(ms)
            if (jitterWindow.size > JITTER_WINDOW_SIZE) {
                jitterWindow.removeFirst()
            }
        }
        
        scope.launch {
            emitSnapshot(updateSnapshot())
        }
    }
    
    /**
     * Get current snapshot
     */
    fun snapshot(): GameSnapshot {
        return currentSnapshot.get()
    }
    
    /**
     * Handle network change
     */
    fun onNetworkChange() {
        // Clear route pins on network change
        pinnedRoutes.clear()
        
        // Reset MTU/MSS tracking
        lastInferredMtu.set(null)
        lastPmtuSignal.set(0L)
        
        rateLimitedLog("$TAG: Network change detected, cleared route pins")
        
        scope.launch {
            emitSnapshot(updateSnapshot())
        }
    }
    
    /**
     * Handle screen off
     */
    fun onScreenOff() {
        // Optionally reduce keepalive frequency or stop NAT keepalive
        // For now, keep it running but log event
        rateLimitedLog("$TAG: Screen off detected")
    }
    
    /**
     * Handle config reload
     */
    fun onConfigReload() {
        // Reapply pinned routes if still valid
        val now = System.currentTimeMillis()
        pinnedRoutes.entries.removeIf { (_, route) ->
            now - route.timestamp >= route.ttlMs
        }
        
        rateLimitedLog("$TAG: Config reloaded, ${pinnedRoutes.size} routes still valid")
        
        scope.launch {
            emitSnapshot(updateSnapshot())
        }
    }
    
    /**
     * Get recommended MSS/MTU
     */
    fun getRecommendedMss(): Int {
        val inferred = lastInferredMtu.get()
        if (inferred != null) {
            // MSS = MTU - 40 (IP header + TCP header)
            return max(1280, inferred - 40)
        }
        return MTU_MSS_DEFAULT
    }
    
    /**
     * Get recommended MTU
     */
    fun getRecommendedMtu(): Int {
        val inferred = lastInferredMtu.get()
        return inferred ?: (MTU_MSS_DEFAULT + 40)
    }
    
    /**
     * Record PMTU signal (ICMP Frag Needed)
     */
    fun onPmtuSignal(inferredMtu: Int) {
        lastInferredMtu.set(inferredMtu)
        lastPmtuSignal.set(System.currentTimeMillis())
        
        rateLimitedLog("$TAG: PMTU signal received, inferred MTU: $inferredMtu")
        
        scope.launch {
            emitSnapshot(updateSnapshot())
        }
    }
    
    /**
     * Get smoothed jitter
     */
    fun getSmoothedJitter(): Float {
        synchronized(jitterWindow) {
            if (jitterWindow.isEmpty()) return 0f
            return jitterWindow.average().toFloat()
        }
    }
    
    /**
     * Get smoothed RTT
     */
    fun getSmoothedRtt(): Float {
        synchronized(rttWindow) {
            if (rttWindow.isEmpty()) return 0f
            return rttWindow.average().toFloat()
        }
    }
    
    /**
     * Get smoothed loss
     */
    fun getSmoothedLoss(): Float {
        synchronized(lossWindow) {
            if (lossWindow.isEmpty()) return 0f
            return lossWindow.average().toFloat()
        }
    }
    
    /**
     * Classify network state
     */
    fun classifyNetworkState(): NetworkState {
        val rtt = getSmoothedRtt()
        val loss = getSmoothedLoss()
        val jitter = getSmoothedJitter()
        
        return when {
            rtt < 50 && loss < 1.0f && jitter < 10 -> NetworkState.GOOD
            rtt < 100 && loss < 3.0f && jitter < 30 -> NetworkState.FAIR
            else -> NetworkState.POOR
        }
    }
    
    /**
     * Start NAT keepalive (lightweight UDP tick)
     */
    fun startNatKeepalive(intervalMs: Long = DEFAULT_NAT_KEEPALIVE_INTERVAL_MS) {
        natKeepaliveIntervalMs = intervalMs
        
        natKeepaliveJob?.cancel()
        natKeepaliveJob = scope.launch {
            while (true) {
                try {
                    // Send lightweight UDP keepalive (stub - platform specific)
                    sendNatKeepaliveTick()
                    delay(natKeepaliveIntervalMs)
                } catch (e: Exception) {
                    AppLogger.w("$TAG: Error in NAT keepalive", e)
                    break
                }
            }
        }
        
        rateLimitedLog("$TAG: NAT keepalive started (interval: ${intervalMs}ms)")
    }
    
    /**
     * Stop NAT keepalive
     */
    fun stopNatKeepalive() {
        natKeepaliveJob?.cancel()
        natKeepaliveJob = null
        
        rateLimitedLog("$TAG: NAT keepalive stopped")
    }
    
    /**
     * Send NAT keepalive tick (platform-specific stub)
     */
    private fun sendNatKeepaliveTick() {
        // Platform-specific implementation would go here
        // For Android, this might use a lightweight UDP socket
        // This is a no-op stub
    }
    
    /**
     * Get DSCP tag suggestion (Expedited Forwarding EF=46)
     */
    fun getDscpTagSuggestion(): Int? {
        // EF = 46 (Expedited Forwarding)
        // Return null if platform doesn't support DSCP tagging
        // Otherwise return 46
        // For now, return null (platform check would be needed)
        return null
    }
    
    /**
     * Handle binder reconnect
     */
    fun onBinderReconnected(binder: IVpnServiceBinder, serviceBinder: android.os.IBinder) {
        this.binder = binder
        this.serviceBinder = serviceBinder
        
        // Reapply pinned routes
        scope.launch {
            reapplyPinnedRoutes()
            emitSnapshot(updateSnapshot())
        }
        
        rateLimitedLog("$TAG: Binder reconnected, reapplying ${pinnedRoutes.size} routes")
    }
    
    /**
     * Reapply pinned routes after reconnect
     */
    private suspend fun reapplyPinnedRoutes() {
        // Re-register with routing system
        for ((key, route) in pinnedRoutes) {
            if (System.currentTimeMillis() - route.timestamp < route.ttlMs) {
                // Route still valid, reapply
                GameOutboundTagger.tagGameDomain(key.host, key.port, route.outboundTag)
            }
        }
    }
    
    /**
     * Update snapshot from current state
     */
    private fun updateSnapshot(): GameSnapshot {
        val pinnedRoutesList = pinnedRoutes.values
            .filter { System.currentTimeMillis() - it.timestamp < it.ttlMs }
            .map { it.key }
            .toList()
        
        return GameSnapshot(
            timestamp = System.currentTimeMillis(),
            pinnedRoutes = pinnedRoutesList,
            smoothedRtt = getSmoothedRtt(),
            smoothedLoss = getSmoothedLoss(),
            smoothedJitter = getSmoothedJitter(),
            networkState = classifyNetworkState(),
            recommendedMss = getRecommendedMss(),
            recommendedMtu = getRecommendedMtu(),
            natKeepaliveActive = natKeepaliveJob?.isActive == true
        )
    }
    
    /**
     * Emit snapshot to SharedFlow
     */
    private suspend fun emitSnapshot(snapshot: GameSnapshot) {
        // Coalesce identical snapshots
        val snapshotHash = snapshot.pinnedRoutes.map { "${it.host}:${it.port}" }.sorted().joinToString(",") +
                          "|${snapshot.smoothedRtt}|${snapshot.smoothedLoss}|${snapshot.smoothedJitter}"
        
        if (!PerformanceOptimizer.shouldRecompose(snapshotHash)) {
            return // Skip identical snapshot
        }
        
        currentSnapshot.set(snapshot)
        _gameSnapshot.emit(snapshot)
    }
    
    /**
     * Rate-limited logging
     */
    private fun rateLimitedLog(message: String, key: String = "default") {
        val now = System.currentTimeMillis()
        val lastTime = lastLogTime.getOrDefault(key, 0L)
        
        if (now - lastTime > LOG_RATE_LIMIT_MS) {
            AppLogger.d(message)
            LoggerRepository.add(
                com.simplexray.an.logging.LogEvent.Info(
                    message = message,
                    tag = TAG
                )
            )
            lastLogTime[key] = now
        }
    }
    
    /**
     * Game key for route pinning
     */
    data class GameKey(
        val host: String,
        val port: Int,
        val protocol: String = "udp"
    )
    
    /**
     * Pinned route with TTL
     */
    private data class PinnedRoute(
        val key: GameKey,
        val outboundTag: String,
        val timestamp: Long,
        val ttlMs: Long
    )
    
    /**
     * Game class
     */
    data class GameClass(
        val isGame: Boolean,
        val gameId: GameMatcher.GameId?,
        val host: String,
        val port: Int,
        val isUdp: Boolean,
        val preferredTransport: TransportPref
    )
    
    /**
     * Transport preference
     */
    enum class TransportPref {
        UDP_QUIC,
        UDP_QUIC_WITH_FALLBACK,
        TCP,
        AUTO
    }
    
    /**
     * Network state
     */
    enum class NetworkState {
        GOOD,
        FAIR,
        POOR
    }
    
    /**
     * Game snapshot
     */
    data class GameSnapshot(
        val timestamp: Long = System.currentTimeMillis(),
        val pinnedRoutes: List<GameKey> = emptyList(),
        val smoothedRtt: Float = 0f,
        val smoothedLoss: Float = 0f,
        val smoothedJitter: Float = 0f,
        val networkState: NetworkState = NetworkState.GOOD,
        val recommendedMss: Int = MTU_MSS_DEFAULT,
        val recommendedMtu: Int = MTU_MSS_DEFAULT + 40,
        val natKeepaliveActive: Boolean = false
    )
}

