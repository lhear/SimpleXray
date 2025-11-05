package com.simplexray.an.hyper.ui

import com.simplexray.an.domain.model.TrafficSnapshot
import com.simplexray.an.protocol.routing.RoutingRepository
import com.simplexray.an.protocol.streaming.StreamingRepository
import com.simplexray.an.game.GameOptimizationRepository
import com.simplexray.an.traffic.TrafficRepository
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * HyperUiRepository - Central state management for Hyper UI components
 * 
 * Features:
 * - Aggregates data from multiple repositories
 * - Coalesces identical snapshots (hash-based)
 * - Drops UI updates if delta < threshold
 * - Prioritizes fresh state over rendering backlog
 * - Throttles collectAsState() if events >120/s
 */
object HyperUiRepository {
    private const val TAG = "HyperUiRepository"
    private const val REPLAY_BUFFER = 10
    private const val EXTRA_BUFFER = 200
    private const val THROTTLE_THRESHOLD_MS = 8L // ~120 events/sec max
    private const val MIN_DELTA_THRESHOLD = 0.01 // 1% change threshold
    
    // Hot SharedFlow with replay buffer
    private val _hyperSnapshot = MutableSharedFlow<HyperSnapshot>(
        replay = REPLAY_BUFFER,
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val hyperSnapshot: SharedFlow<HyperSnapshot> = _hyperSnapshot.asSharedFlow()
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // State tracking
    private var lastSnapshot: HyperSnapshot? = null
    private var lastSnapshotHash: Int? = null
    private var lastEmitTime = 0L
    
    // Jitter history buffer (last 60 samples)
    private val jitterHistory = ArrayDeque<Float>(60)
    private val jitterHistoryLock = Any()
    
    // Burst detection
    private val packetTimestamps = ArrayDeque<Long>(100)
    private val packetTimestampsLock = Any()
    private var lastBurstCount = 0
    private var lastPacketsPerSecond = 0
    
    // DNS race tracking
    private val dnsRaceResults = ConcurrentHashMap<String, DnsRaceResult>()
    
    // QUIC warmup tracking
    private var quicWarmupState = QuicWarmupState.IDLE
    private var quicWarmupStartTime = 0L
    private val QUIC_WARMUP_TIMEOUT_MS = 10_000L // 10 seconds
    
    init {
        // Start observing traffic
        observeTraffic()
        
        // Start observing routing
        observeRouting()
        
        // Start observing streaming
        observeStreaming()
        
        // Start observing game optimization
        observeGameOptimization()
        
        // Start QUIC warmup timeout watcher
        watchQuicWarmup()
    }
    
    /**
     * Observe traffic repository for throughput and burst detection
     */
    private fun observeTraffic() {
        val trafficRepo = TrafficRepository.getInstanceOrNull() ?: run {
            AppLogger.w("$TAG: TrafficRepository not available")
            return
        }
        
        scope.launch {
            trafficRepo.trafficFlow
                .collect { sample ->
                    val throughputMBps = (sample.rxSpeedBps + sample.txSpeedBps) / (1024.0 * 1024.0)
                    
                    // Detect bursts (packets within 3ms window)
                    detectBurst(sample.timestamp)
                    
                    // Update jitter history (simulate from latency variance)
                    // In real implementation, jitter would come from actual network measurements
                    updateJitterHistory(sample.rxSpeedBps, sample.txSpeedBps)
                    
                    emitSnapshotIfChanged(
                        updateThroughput(throughputMBps)
                    )
                }
        }
    }
    
    /**
     * Observe routing repository for outbound tags and path status
     */
    private fun observeRouting() {
        scope.launch {
            RoutingRepository.routeSnapshot
                .collect { routeSnapshot ->
                    val outboundTag = routeSnapshot.routeTable.outboundTags.values.firstOrNull()
                    val pathStatus = determinePathStatus(routeSnapshot.status)
                    
                    emitSnapshotIfChanged(
                        updateRouting(outboundTag, pathStatus)
                    )
                }
        }
    }
    
    /**
     * Observe streaming repository for QUIC dominance
     */
    private fun observeStreaming() {
        scope.launch {
            StreamingRepository.streamingSnapshot
                .collect { streamingSnapshot ->
                    val isQuicDominant = streamingSnapshot.transportPreferences.values
                        .count { it == StreamingRepository.TransportType.QUIC } >
                        streamingSnapshot.transportPreferences.values
                            .count { it == StreamingRepository.TransportType.HTTP2 }
                    
                    // Check if QUIC is warming up
                    val quicSessions = streamingSnapshot.activeStreamingSessions.values
                        .filter { it.transportPreference == StreamingRepository.TransportType.QUIC }
                    
                    if (quicSessions.isNotEmpty() && quicWarmupState == QuicWarmupState.IDLE) {
                        startQuicWarmup()
                    }
                    
                    emitSnapshotIfChanged(
                        updateQuicState(isQuicDominant)
                    )
                }
        }
    }
    
    /**
     * Observe game optimization for multi-path racing
     */
    private fun observeGameOptimization() {
        scope.launch {
            GameOptimizationRepository.gameSnapshot
                .collect { gameSnapshot ->
                    // Extract multi-path info from game snapshot
                    // This is a placeholder - actual implementation would track active paths
                    val activePathCount = gameSnapshot.pinnedRoutes.size
                    val rttSpreadMs = (gameSnapshot.smoothedRtt * 0.2).toInt() // Simulate spread
                    
                    emitSnapshotIfChanged(
                        updateMultiPath(activePathCount, rttSpreadMs)
                    )
                }
        }
    }
    
    /**
     * Detect packet bursts (N packets within 3ms)
     */
    private fun detectBurst(timestamp: Long) {
        synchronized(packetTimestampsLock) {
            // Add current timestamp
            packetTimestamps.addLast(timestamp)
            
            // Remove old timestamps (> 3ms ago)
            val cutoff = timestamp - 3
            while (packetTimestamps.isNotEmpty() && packetTimestamps.first() < cutoff) {
                packetTimestamps.removeFirst()
            }
            
            // Calculate packets per second (over last 1 second)
            val oneSecondAgo = timestamp - 1000
            val recentPackets = packetTimestamps.count { it >= oneSecondAgo }
            lastPacketsPerSecond = recentPackets
            
            // Burst detected if > threshold packets in 3ms window
            lastBurstCount = packetTimestamps.size
        }
    }
    
    /**
     * Update jitter history (simulated from throughput variance)
     */
    private fun updateJitterHistory(rxSpeed: Float, txSpeed: Float) {
        synchronized(jitterHistoryLock) {
            // Simulate jitter from throughput variance
            val totalSpeed = rxSpeed + txSpeed
            val jitter = kotlin.math.abs(totalSpeed - (jitterHistory.lastOrNull() ?: totalSpeed)) / 1000f
            
            jitterHistory.addLast(jitter.coerceIn(0f, 100f))
            if (jitterHistory.size > 60) {
                jitterHistory.removeFirst()
            }
        }
    }
    
    /**
     * Determine path status from route status
     */
    private fun determinePathStatus(routeStatus: com.simplexray.an.protocol.routing.RouteStatus): PathStatus {
        return when (routeStatus) {
            com.simplexray.an.protocol.routing.RouteStatus.ACTIVE -> PathStatus.STABILIZING
            com.simplexray.an.protocol.routing.RouteStatus.ERROR -> PathStatus.DROPPING
            else -> PathStatus.UNKNOWN
        }
    }
    
    /**
     * Start QUIC warmup timer
     */
    private fun startQuicWarmup() {
        quicWarmupState = QuicWarmupState.WARMING
        quicWarmupStartTime = System.currentTimeMillis()
    }
    
    /**
     * Watch QUIC warmup timeout
     */
    private fun watchQuicWarmup() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                
                if (quicWarmupState == QuicWarmupState.WARMING) {
                    val elapsed = System.currentTimeMillis() - quicWarmupStartTime
                    
                    if (elapsed >= QUIC_WARMUP_TIMEOUT_MS) {
                        quicWarmupState = QuicWarmupState.TIMEOUT
                    } else if (elapsed >= QUIC_WARMUP_TIMEOUT_MS * 0.7) {
                        quicWarmupState = QuicWarmupState.READY
                    }
                    
                    val timeRemaining = QUIC_WARMUP_TIMEOUT_MS - elapsed
                    emitSnapshotIfChanged(
                        updateQuicWarmup(quicWarmupState, timeRemaining.coerceAtLeast(0))
                    )
                }
            }
        }
    }
    
    /**
     * Update snapshot with throughput
     */
    private fun updateThroughput(throughputMBps: Double): HyperSnapshot {
        val current = lastSnapshot ?: HyperSnapshot()
        synchronized(jitterHistoryLock) {
            return current.copy(
                throughputMBps = throughputMBps,
                burstIntensity = (lastBurstCount / 10f).coerceIn(0f, 1f),
                packetsPerSecond = lastPacketsPerSecond,
                packetBurstCount = lastBurstCount,
                jitterHistory = jitterHistory.toList()
            )
        }
    }
    
    /**
     * Update snapshot with routing info
     */
    private fun updateRouting(outboundTag: String?, pathStatus: PathStatus): HyperSnapshot {
        val current = lastSnapshot ?: HyperSnapshot()
        return current.copy(
            currentOutboundTag = outboundTag,
            pathStatus = pathStatus
        )
    }
    
    /**
     * Update snapshot with QUIC state
     */
    private fun updateQuicState(isQuicDominant: Boolean): HyperSnapshot {
        val current = lastSnapshot ?: HyperSnapshot()
        return current.copy(
            isQuicDominant = isQuicDominant
        )
    }
    
    /**
     * Update snapshot with multi-path info
     */
    private fun updateMultiPath(activePathCount: Int, rttSpreadMs: Int): HyperSnapshot {
        val current = lastSnapshot ?: HyperSnapshot()
        return current.copy(
            activePathCount = activePathCount,
            rttSpreadMs = rttSpreadMs,
            pathStatus = when {
                activePathCount > 1 -> PathStatus.RACING
                activePathCount == 1 -> PathStatus.STABILIZING
                else -> PathStatus.DROPPING
            }
        )
    }
    
    /**
     * Update snapshot with QUIC warmup
     */
    private fun updateQuicWarmup(state: QuicWarmupState, timeRemainingMs: Long): HyperSnapshot {
        val current = lastSnapshot ?: HyperSnapshot()
        return current.copy(
            quicWarmupState = state,
            quicWarmupTimeRemainingMs = timeRemainingMs
        )
    }
    
    /**
     * Emit snapshot only if changed (hash-based coalescing)
     */
    private suspend fun emitSnapshotIfChanged(newSnapshot: HyperSnapshot) {
        val now = System.currentTimeMillis()
        
        // Throttle: don't emit if too frequent
        if (now - lastEmitTime < THROTTLE_THRESHOLD_MS) {
            return
        }
        
        // Calculate hash
        val newHash = newSnapshot.hashCode()
        
        // Check if changed significantly
        val changed = lastSnapshotHash == null || newHash != lastSnapshotHash
        
        if (changed) {
            // Check if delta is significant enough
            val last = lastSnapshot
            if (last != null) {
                val deltaThroughput = kotlin.math.abs(newSnapshot.throughputMBps - last.throughputMBps)
                val maxThroughput = kotlin.math.max(newSnapshot.throughputMBps, last.throughputMBps)
                
                if (maxThroughput > 0 && deltaThroughput / maxThroughput < MIN_DELTA_THRESHOLD) {
                    // Delta too small, skip
                    return
                }
            }
            
            lastSnapshot = newSnapshot
            lastSnapshotHash = newHash
            lastEmitTime = now
            
            _hyperSnapshot.emit(newSnapshot)
        }
    }
    
    /**
     * Get current snapshot (for initial state)
     */
    fun getCurrentSnapshot(): HyperSnapshot {
        return lastSnapshot ?: HyperSnapshot()
    }
}

