package com.simplexray.an.network

import android.content.Context
import android.net.TrafficStats
import android.util.Log
import com.simplexray.an.domain.model.TrafficSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import java.util.ArrayDeque

/**
 * Observes network traffic in real-time and exposes it as a Flow.
 * Measures upload/download throughput using Android TrafficStats API.
 *
 * Features:
 * - Real-time speed measurement (500ms intervals)
 * - Automatic latency probing
 * - Burst detection
 * - Connection health monitoring
 * - Memory-efficient history buffer (max 120 samples = 1 minute)
 */
class TrafficObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    sampleIntervalMs: Long = DEFAULT_SAMPLE_INTERVAL_MS
) {
    companion object {
        private const val TAG = "TrafficObserver"
        private const val DEFAULT_SAMPLE_INTERVAL_MS = 500L
        private const val LATENCY_PROBE_INTERVAL_MS = 5000L
        private const val MAX_HISTORY_SIZE = 120 // Keep 1 minute of data
        private const val BURST_THRESHOLD_MULTIPLIER = 3.0f
    }
    
    private val sampleIntervalMs: Long

    private val _currentSnapshot = MutableStateFlow(TrafficSnapshot())
    val currentSnapshot: Flow<TrafficSnapshot> = _currentSnapshot.asStateFlow()

    // PERF: Use ArrayDeque for O(1) add/remove operations instead of O(n) list operations
    private val _historyQueue = ArrayDeque<TrafficSnapshot>(MAX_HISTORY_SIZE)
    private val _history = MutableStateFlow<List<TrafficSnapshot>>(emptyList())
    val history: Flow<List<TrafficSnapshot>> = _history.asStateFlow()

    private var previousSnapshot: TrafficSnapshot? = null
    @Volatile private var isRunning = false
    private var lastLatencyProbe = 0L
    private val myUid = android.os.Process.myUid()
    
    init {
        this.sampleIntervalMs = sampleIntervalMs
    }

    /**
     * Start observing traffic
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "TrafficObserver already running")
            return
        }

        isRunning = true
        scope.launch(Dispatchers.IO) {
            observeTraffic()
        }

        Log.i(TAG, "TrafficObserver started")
    }

    /**
     * Stop observing traffic
     */
    fun stop() {
        isRunning = false
        Log.i(TAG, "TrafficObserver stopped")
    }

    /**
     * Collect a single snapshot immediately (used by WorkManager)
     */
    suspend fun collectNow(): TrafficSnapshot = withContext(Dispatchers.IO) {
        val snapshot = captureSnapshot()

        // Calculate rate if we have previous snapshot
        previousSnapshot?.let { prev ->
            return@withContext TrafficSnapshot.calculateDelta(prev, snapshot)
        } ?: snapshot
    }

    /**
     * Main observation loop
     */
    private suspend fun observeTraffic() {
        while (scope.isActive && isRunning) {
            try {
                val snapshot = captureSnapshot()

                // Calculate rate based on previous snapshot
                val snapshotWithRate = previousSnapshot?.let { prev ->
                    TrafficSnapshot.calculateDelta(prev, snapshot)
                } ?: snapshot

                // Probe latency periodically
                val now = System.currentTimeMillis()
                val snapshotWithLatency = if (now - lastLatencyProbe > LATENCY_PROBE_INTERVAL_MS) {
                    lastLatencyProbe = now
                    val latency = probeLatency()
                    snapshotWithRate.copy(latencyMs = latency)
                } else {
                    snapshotWithRate
                }

                // Check for burst anomaly
                detectBurst(snapshotWithLatency)

                // Update state
                _currentSnapshot.value = snapshotWithLatency
                previousSnapshot = snapshot

                // Update history (keep last MAX_HISTORY_SIZE samples)
                // PERF: Use ArrayDeque for O(1) operations instead of O(n) list operations
                synchronized(_historyQueue) {
                    _historyQueue.addLast(snapshotWithLatency)
                    if (_historyQueue.size > MAX_HISTORY_SIZE) {
                        _historyQueue.removeFirst()
                    }
                    // PERF: Convert to list only when needed for Flow emission
                    _history.value = _historyQueue.toList()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error observing traffic", e)
            }

            delay(sampleIntervalMs)
        }
    }

    /**
     * Capture a traffic snapshot using TrafficStats
     */
    // PERF: TrafficStats.getUidRxBytes() is syscall - should cache or batch calls
    // NETWORK: Called every 500ms - may cause system overhead
    private fun captureSnapshot(): TrafficSnapshot {
        // Get UID-specific stats (more accurate for our app)
        // PERF: Multiple TrafficStats calls - should batch or cache
        var rxBytes = TrafficStats.getUidRxBytes(myUid)
        var txBytes = TrafficStats.getUidTxBytes(myUid)
        
        // Provide a fallback to process-wide totals when UID counters are unsupported
        val isUidSupported = rxBytes != TrafficStats.UNSUPPORTED.toLong() &&
                             txBytes != TrafficStats.UNSUPPORTED.toLong()
        
        if (!isUidSupported) {
            // Fallback to process-wide totals
            // PERF: Additional syscalls if UID stats fail - should cache result
            rxBytes = TrafficStats.getTotalRxBytes()
            txBytes = TrafficStats.getTotalTxBytes()
        }

        // Check if stats are valid
        val isConnected = rxBytes != TrafficStats.UNSUPPORTED.toLong() &&
                          txBytes != TrafficStats.UNSUPPORTED.toLong()

        // PERF: System.currentTimeMillis() is syscall - consider using monotonic clock
        return TrafficSnapshot(
            timestamp = System.currentTimeMillis(),
            rxBytes = if (isConnected) rxBytes else 0L,
            txBytes = if (isConnected) txBytes else 0L,
            isConnected = isConnected
        )
    }

    /**
     * Probe latency using shared utility with configurable endpoint
     */
    private suspend fun probeLatency(): Long {
        val latency = LatencyProbe.probe(context)
        if (latency >= 0) {
            Log.d(TAG, "Latency probe: ${latency}ms")
        } else {
            Log.w(TAG, "Latency probe failed")
        }
        return latency
    }

    /**
     * Detect burst traffic anomalies
     */
    private fun detectBurst(snapshot: TrafficSnapshot) {
        val historyList = synchronized(_historyQueue) { _historyQueue.toList() }
        if (historyList.size < 10) return // Need enough samples

        // Calculate moving average of last 10 samples
        val recentSnapshots = historyList.takeLast(10)
        val avgRxRate = recentSnapshots.map { it.rxRateMbps }.average().toFloat()
        val avgTxRate = recentSnapshots.map { it.txRateMbps }.average().toFloat()

        // Detect burst
        val rxBurst = snapshot.rxRateMbps > avgRxRate * BURST_THRESHOLD_MULTIPLIER
        val txBurst = snapshot.txRateMbps > avgTxRate * BURST_THRESHOLD_MULTIPLIER

        if (rxBurst || txBurst) {
            Log.i(TAG, "Burst detected! RX: ${snapshot.rxRateMbps} Mbps (avg: $avgRxRate), " +
                      "TX: ${snapshot.txRateMbps} Mbps (avg: $avgTxRate)")
        }
    }

    /**
     * Reset all traffic stats (useful for session-based tracking)
     */
    fun reset() {
        previousSnapshot = null
        _currentSnapshot.value = TrafficSnapshot()
        synchronized(_historyQueue) {
            _historyQueue.clear()
            _history.value = emptyList()
        }
        Log.i(TAG, "Traffic stats reset")
    }

    /**
     * Get current download speed in Mbps
     */
    fun getCurrentDownloadSpeed(): Float = _currentSnapshot.value.rxRateMbps

    /**
     * Get current upload speed in Mbps
     */
    fun getCurrentUploadSpeed(): Float = _currentSnapshot.value.txRateMbps

    /**
     * Get current latency in milliseconds
     */
    fun getCurrentLatency(): Long = _currentSnapshot.value.latencyMs

    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean = _currentSnapshot.value.isConnected
}
