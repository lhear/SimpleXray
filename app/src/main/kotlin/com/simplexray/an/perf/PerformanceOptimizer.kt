package com.simplexray.an.perf

import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque

/**
 * PerformanceOptimizer - Singleton performance optimization layer.
 * 
 * Responsibilities:
 * - Throttle binder callbacks with coalescing window (80-120ms)
 * - Deduplicate identical snapshots (hash & timestamp)
 * - Maintain performance counters
 * - Optimize traffic samples, recompositions, outbound changes
 * - Provide diagnostics for performance monitoring
 * 
 * Architecture:
 * - Singleton pattern (survives Activity recreation)
 * - Coalescing window for binder events (80-120ms)
 * - Snapshot deduplication with hash-based comparison
 * - Thread-safe counters for diagnostics
 */
object PerformanceOptimizer {
    private const val TAG = "PerformanceOptimizer"
    
    // Coalescing window configuration
    private const val BINDER_COALESCE_WINDOW_MS_MIN = 80L
    private const val BINDER_COALESCE_WINDOW_MS_MAX = 120L
    private const val SNAPSHOT_COALESCE_WINDOW_MS = 100L
    private const val OUTBOUND_CHURN_TTL_MS = 45_000L // 45 seconds
    
    // Snapshot deduplication
    private val snapshotHashes = ConcurrentHashMap<String, Long>() // hash -> timestamp
    private val snapshotMutex = Mutex()
    
    // Binder event coalescing
    private val binderEventQueue = ArrayDeque<BinderEvent>()
    private val binderEventMutex = Mutex()
    private var binderCoalesceJob: kotlinx.coroutines.Job? = null
    
    // Outbound churn guard
    private val outboundResults = ConcurrentHashMap<String, OutboundResult>() // host -> result
    
    // Performance counters
    private val recompositionsAvoided = AtomicLong(0)
    private val snapshotsDropped = AtomicLong(0)
    private val outboundChurnAvoided = AtomicLong(0)
    private val binderEventsCoalesced = AtomicLong(0)
    private val sniffThrottles = AtomicLong(0)
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Binder event for coalescing
     */
    private data class BinderEvent(
        val type: BinderEventType,
        val data: Any? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    enum class BinderEventType {
        TRAFFIC_UPDATE,
        TOPOLOGY_UPDATE,
        ROUTING_UPDATE,
        STREAMING_UPDATE,
        GAME_UPDATE
    }
    
    /**
     * Outbound result cache entry
     */
    private data class OutboundResult(
        val host: String,
        val outboundTag: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return (System.currentTimeMillis() - timestamp) > OUTBOUND_CHURN_TTL_MS
        }
    }
    
    /**
     * Optimize traffic sample - deduplicate and coalesce
     */
    suspend fun optimizeTrafficSample(rx: Long, tx: Long): TrafficSampleResult = snapshotMutex.withLock {
        val hash = calculateHash("traffic", rx, tx)
        val now = System.currentTimeMillis()
        
        // Check if identical to last snapshot
        snapshotHashes[hash]?.let { lastTimestamp ->
            if (now - lastTimestamp < SNAPSHOT_COALESCE_WINDOW_MS) {
                snapshotsDropped.incrementAndGet()
                return TrafficSampleResult(shouldEmit = false, rx = rx, tx = tx)
            }
        }
        
        // Update hash
        snapshotHashes[hash] = now
        
        // Cleanup old hashes (keep last 1000)
        if (snapshotHashes.size > 1000) {
            val oldest = snapshotHashes.entries.minByOrNull { it.value }?.key
            oldest?.let { snapshotHashes.remove(it) }
        }
        
        return TrafficSampleResult(shouldEmit = true, rx = rx, tx = tx)
    }
    
    /**
     * Check if recomposition should be skipped (hash-based)
     */
    fun shouldRecompose(hash: String): Boolean {
        val now = System.currentTimeMillis()
        val lastHash = snapshotHashes[hash]
        
        return if (lastHash == null || (now - lastHash) > SNAPSHOT_COALESCE_WINDOW_MS) {
            snapshotHashes[hash] = now
            true
        } else {
            recompositionsAvoided.incrementAndGet()
            false
        }
    }
    
    /**
     * Debounce binder event - coalesce within window
     */
    suspend fun debounceBinderEvent(event: BinderEventType, data: Any? = null) {
        binderEventMutex.withLock {
            binderEventQueue.addLast(BinderEvent(event, data))
            
            // Start coalescing job if not running
            if (binderCoalesceJob == null || binderCoalesceJob?.isCompleted == true) {
                binderCoalesceJob = scope.launch {
                    processBinderEventQueue()
                }
            }
        }
    }
    
    /**
     * Process binder event queue with coalescing
     */
    private suspend fun processBinderEventQueue() {
        while (true) {
            delay(BINDER_COALESCE_WINDOW_MS_MIN)
            
            val events = binderEventMutex.withLock {
                if (binderEventQueue.isEmpty()) {
                    binderCoalesceJob = null
                    return
                }
                
                val batch = mutableListOf<BinderEvent>()
                val now = System.currentTimeMillis()
                
                // Collect events within window
                while (binderEventQueue.isNotEmpty()) {
                    val event = binderEventQueue.removeFirst()
                    batch.add(event)
                    
                    // If oldest event is beyond max window, stop collecting
                    if (batch.size > 1 && (now - batch.first().timestamp) > BINDER_COALESCE_WINDOW_MS_MAX) {
                        break
                    }
                }
                
                batch
            }
            
            if (events.isEmpty()) {
                binderCoalesceJob = null
                return
            }
            
            // Coalesce events by type
            val coalesced = events.groupBy { it.type }
            
            // Emit coalesced events (would be forwarded to actual handlers)
            coalesced.forEach { (type, eventList) ->
                binderEventsCoalesced.addAndGet(eventList.size.toLong() - 1)
                // Event would be forwarded here - actual implementation depends on caller
            }
            
            // If queue is empty, stop
            if (binderEventQueue.isEmpty()) {
                binderCoalesceJob = null
                return
            }
        }
    }
    
    /**
     * Optimize outbound change - prevent churn for identical results
     */
    fun optimizeOutboundChange(host: String, outboundTag: String): Boolean {
        val result = outboundResults[host]
        
        // If same result within TTL, skip
        if (result != null && !result.isExpired() && result.outboundTag == outboundTag) {
            outboundChurnAvoided.incrementAndGet()
            return false // Don't apply
        }
        
        // Update cache
        outboundResults[host] = OutboundResult(host, outboundTag)
        
        // Cleanup expired entries
        if (outboundResults.size > 500) {
            outboundResults.entries.removeIf { it.value.isExpired() }
        }
        
        return true // Apply change
    }
    
    /**
     * Record sniff throttle
     */
    fun recordSniffThrottle() {
        sniffThrottles.incrementAndGet()
    }
    
    /**
     * Calculate hash for snapshot deduplication
     */
    private fun calculateHash(type: String, vararg values: Any): String {
        return "$type:${values.joinToString(":")}"
    }
    
    /**
     * Get performance diagnostics
     */
    fun getDiagnostics(): PerformanceDiagnostics {
        return PerformanceDiagnostics(
            recompositionsAvoided = recompositionsAvoided.get(),
            snapshotsDropped = snapshotsDropped.get(),
            outboundChurnAvoided = outboundChurnAvoided.get(),
            binderEventsCoalesced = binderEventsCoalesced.get(),
            sniffThrottles = sniffThrottles.get()
        )
    }
    
    /**
     * Reset diagnostics counters
     */
    fun resetDiagnostics() {
        recompositionsAvoided.set(0)
        snapshotsDropped.set(0)
        outboundChurnAvoided.set(0)
        binderEventsCoalesced.set(0)
        sniffThrottles.set(0)
    }
    
    /**
     * Cleanup on app destroy
     */
    fun cleanup() {
        binderCoalesceJob?.cancel()
        binderEventQueue.clear()
        snapshotHashes.clear()
        outboundResults.clear()
    }
}

/**
 * Traffic sample optimization result
 */
data class TrafficSampleResult(
    val shouldEmit: Boolean,
    val rx: Long,
    val tx: Long
)

/**
 * Performance diagnostics
 */
data class PerformanceDiagnostics(
    val recompositionsAvoided: Long,
    val snapshotsDropped: Long,
    val outboundChurnAvoided: Long,
    val binderEventsCoalesced: Long,
    val sniffThrottles: Long
)

