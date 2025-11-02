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
import java.net.HttpURLConnection
import java.net.URL

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
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TrafficObserver"
        private const val SAMPLE_INTERVAL_MS = 500L
        private const val LATENCY_PROBE_INTERVAL_MS = 5000L
        private const val MAX_HISTORY_SIZE = 120 // Keep 1 minute of data
        private const val BURST_THRESHOLD_MULTIPLIER = 3.0f
        private const val HEALTH_CHECK_URL = "https://www.google.com/generate_204"
        private const val HEALTH_CHECK_TIMEOUT_MS = 2000
    }

    private val _currentSnapshot = MutableStateFlow(TrafficSnapshot())
    val currentSnapshot: Flow<TrafficSnapshot> = _currentSnapshot.asStateFlow()

    private val _history = MutableStateFlow<List<TrafficSnapshot>>(emptyList())
    val history: Flow<List<TrafficSnapshot>> = _history.asStateFlow()

    private var previousSnapshot: TrafficSnapshot? = null
    private var isRunning = false
    private var lastLatencyProbe = 0L
    private val myUid = android.os.Process.myUid()

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
                val currentHistory = _history.value.toMutableList()
                currentHistory.add(snapshotWithLatency)
                if (currentHistory.size > MAX_HISTORY_SIZE) {
                    currentHistory.removeAt(0)
                }
                _history.value = currentHistory

            } catch (e: Exception) {
                Log.e(TAG, "Error observing traffic", e)
            }

            delay(SAMPLE_INTERVAL_MS)
        }
    }

    /**
     * Capture a traffic snapshot using TrafficStats
     */
    private fun captureSnapshot(): TrafficSnapshot {
        // Get UID-specific stats (more accurate for our app)
        val rxBytes = TrafficStats.getUidRxBytes(myUid)
        val txBytes = TrafficStats.getUidTxBytes(myUid)

        // Check if stats are valid
        val isConnected = rxBytes != TrafficStats.UNSUPPORTED.toLong() &&
                          txBytes != TrafficStats.UNSUPPORTED.toLong()

        return TrafficSnapshot(
            timestamp = System.currentTimeMillis(),
            rxBytes = if (isConnected) rxBytes else 0L,
            txBytes = if (isConnected) txBytes else 0L,
            isConnected = isConnected
        )
    }

    /**
     * Probe latency by making a lightweight HTTP request
     */
    private suspend fun probeLatency(): Long = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()

            val connection = URL(HEALTH_CHECK_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = HEALTH_CHECK_TIMEOUT_MS
            connection.readTimeout = HEALTH_CHECK_TIMEOUT_MS
            connection.instanceFollowRedirects = false

            val responseCode = connection.responseCode
            connection.disconnect()

            if (responseCode in 200..399) {
                val latency = System.currentTimeMillis() - startTime
                Log.d(TAG, "Latency probe: ${latency}ms")
                return@withContext latency
            } else {
                Log.w(TAG, "Latency probe failed with response code: $responseCode")
                return@withContext -1L
            }
        } catch (e: Exception) {
            Log.w(TAG, "Latency probe failed", e)
            return@withContext -1L
        }
    }

    /**
     * Detect burst traffic anomalies
     */
    private fun detectBurst(snapshot: TrafficSnapshot) {
        val historyList = _history.value
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
        _history.value = emptyList()
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
