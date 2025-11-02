package com.simplexray.an.performance.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import com.simplexray.an.common.CoreStatsClient
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.MetricsHistory
import com.simplexray.an.performance.model.ConnectionStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.withContext

/**
 * Real-time performance monitoring system with Xray core integration
 */
class PerformanceMonitor(
    private val context: Context,
    private val updateInterval: Long = 1000, // milliseconds
    private var coreStatsClient: CoreStatsClient? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _currentMetrics = MutableStateFlow(PerformanceMetrics())
    val currentMetrics: StateFlow<PerformanceMetrics> = _currentMetrics.asStateFlow()

    private val _metricsHistory = MutableStateFlow(MetricsHistory())
    val metricsHistory: StateFlow<MetricsHistory> = _metricsHistory.asStateFlow()

    private val _connectionStats = MutableStateFlow(ConnectionStats())
    val connectionStats: StateFlow<ConnectionStats> = _connectionStats.asStateFlow()

    // Basic safe system metrics (cpu%, memoryMB, uptimeSeconds, batteryTemp)
    private val _systemMetrics = MutableStateFlow<SystemPerformanceMetrics>(
        SystemPerformanceMetrics.Basic(cpuPercent = 0f, memoryMB = 0, uptimeSeconds = 0, batteryTemp = Float.NaN)
    )
    val systemMetrics: StateFlow<SystemPerformanceMetrics> = _systemMetrics.asStateFlow()

    private var monitoringJob: Job? = null
    private var isRunning = false

    // Platform/vendor flags
    private val isMiui: Boolean by lazy { isMIUIDevice() }
    private val isQPlus: Boolean by lazy { isAtLeastAndroid10() }

    // Smoothing
    private val alpha = 0.2f
    private var smoothedDownloadBps: Long = 0L
    private var smoothedUploadBps: Long = 0L
    private var smoothedCpuPercent: Float = 0f
    private var smoothedMemBytes: Long = 0L

    // Network monitoring state
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastUpdateTime: Long = 0

    // Latency monitoring
    private val latencyHistory = mutableListOf<Int>()
    private val maxLatencyHistory = 20
    private var lastLatencyProbeTime: Long = 0
    private val latencyProbeInterval: Long = 5000 // Probe latency every 5 seconds
    
    // Packet loss estimation (based on latency probe failures)
    private val latencyProbeResults = mutableListOf<Boolean>() // true = success, false = failure
    private val maxProbeHistory = 20 // Track last 20 probes

    // CPU monitoring state
    private var lastProcCpuTicks: Long = 0
    private var lastCpuWallTimeMs: Long = 0

    // Bandwidth tracking
    private var peakDownloadSpeed: Long = 0
    private var peakUploadSpeed: Long = 0
    private val bandwidthHistory = mutableListOf<Pair<Long, Long>>() // (download, upload)
    private val maxBandwidthHistory = 60 // Last 60 seconds

    // Connection tracking
    private var currentConnectionCount: Int = 0
    private var currentActiveConnectionCount: Int = 0

    /**
     * Start monitoring
     */
    fun start() {
        if (isRunning) return

        isRunning = true
        lastUpdateTime = SystemClock.elapsedRealtime()

        monitoringJob = scope.launch {
            // Initialize baseline traffic values
            lastRxBytes = getTotalRxBytes()
            lastTxBytes = getTotalTxBytes()

            while (isActive && isRunning) {
                updateMetrics()
                delay(updateInterval)
            }
        }
    }

    /**
     * Stop monitoring
     */
    fun stop() {
        isRunning = false
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * Update interval
     */
    fun setUpdateInterval(interval: Long) {
        if (interval != updateInterval) {
            stop()
            start()
        }
    }

    /**
     * Record connection established
     */
    fun onConnectionEstablished() {
        _connectionStats.value = ConnectionStats(
            connectedAt = System.currentTimeMillis()
        )
    }

    /**
     * Record connection lost
     */
    fun onConnectionLost() {
        val current = _connectionStats.value
        _connectionStats.value = current.copy(
            disconnectedAt = System.currentTimeMillis()
        )
    }

    /**
     * Record latency measurement
     */
    fun recordLatency(latencyMs: Int) {
        latencyHistory.add(latencyMs)
        if (latencyHistory.size > maxLatencyHistory) {
            latencyHistory.removeAt(0)
        }
    }

    /**
     * Update all metrics (safe + smoothed + throttled)
     */
    private suspend fun updateMetrics() {
        val nowMs = SystemClock.elapsedRealtime()
        val timeDelta = nowMs - lastUpdateTime
        if (timeDelta < updateInterval / 2) return

        // Network metrics from Xray core or safe system APIs
        val currentRxBytes = getTotalRxBytes()
        val currentTxBytes = getTotalTxBytes()

        val rxDelta = (currentRxBytes - lastRxBytes).coerceAtLeast(0)
        val txDelta = (currentTxBytes - lastTxBytes).coerceAtLeast(0)

        val rawDownloadBps = if (timeDelta > 0) (rxDelta * 1000L / timeDelta) else 0L
        val rawUploadBps = if (timeDelta > 0) (txDelta * 1000L / timeDelta) else 0L

        smoothedDownloadBps = smoothLong(smoothedDownloadBps, rawDownloadBps)
        smoothedUploadBps = smoothLong(smoothedUploadBps, rawUploadBps)

        if (smoothedDownloadBps > peakDownloadSpeed) peakDownloadSpeed = smoothedDownloadBps
        if (smoothedUploadBps > peakUploadSpeed) peakUploadSpeed = smoothedUploadBps

        bandwidthHistory.add(0, Pair(smoothedDownloadBps, smoothedUploadBps))
        if (bandwidthHistory.size > maxBandwidthHistory) {
            bandwidthHistory.removeAt(bandwidthHistory.lastIndex)
        }

        // Resource metrics
        val cpuUsage = getCpuUsage()
        smoothedCpuPercent = smoothFloat(smoothedCpuPercent, cpuUsage).coerceIn(0f, 100f)
        val memoryUsage = getMemoryUsage()
        smoothedMemBytes = smoothLong(smoothedMemBytes, memoryUsage).coerceAtLeast(0)
        val nativeMemoryUsage = getNativeMemoryUsage()

        // Latency metrics - probe latency periodically
        if (nowMs - lastLatencyProbeTime >= latencyProbeInterval) {
            lastLatencyProbeTime = nowMs
            scope.launch {
                try {
                    measureLatency()
                } catch (e: Exception) {
                    // Silently handle latency probe failures to prevent crashes
                    android.util.Log.d("PerformanceMonitor", "Latency probe error", e)
                }
            }
        }

        // Connection metrics - update from Xray core if available
        updateConnectionCounts()

        val avgLatency = if (latencyHistory.isNotEmpty()) latencyHistory.average().toInt() else 0
        val jitter = calculateJitter()
        val packetLoss = calculatePacketLoss()

        val metricsWithoutQuality = PerformanceMetrics(
            uploadSpeed = smoothedUploadBps,
            downloadSpeed = smoothedDownloadBps,
            totalUpload = currentTxBytes,
            totalDownload = currentRxBytes,
            latency = avgLatency,
            jitter = jitter,
            packetLoss = packetLoss,
            connectionCount = currentConnectionCount,
            activeConnectionCount = currentActiveConnectionCount,
            cpuUsage = smoothedCpuPercent,
            memoryUsage = smoothedMemBytes,
            nativeMemoryUsage = nativeMemoryUsage.coerceAtLeast(0),
            connectionStability = calculateStability(),
            timestamp = System.currentTimeMillis()
        )

        val metrics = metricsWithoutQuality.copy(
            overallQuality = metricsWithoutQuality.getConnectionQuality()
        )

        _currentMetrics.value = metrics
        _metricsHistory.value = _metricsHistory.value.add(metrics)

        // Publish basic system metrics
        _systemMetrics.value = SystemPerformanceMetrics.Basic(
            cpuPercent = smoothedCpuPercent,
            memoryMB = (smoothedMemBytes / (1024L * 1024L)).toInt().coerceAtLeast(0),
            uptimeSeconds = getUptimeSeconds(),
            batteryTemp = getBatteryTemperatureC()
        )

        lastRxBytes = currentRxBytes
        lastTxBytes = currentTxBytes
        lastUpdateTime = nowMs
    }

    /**
     * Set CoreStatsClient for Xray core integration
     */
    fun setCoreStatsClient(client: CoreStatsClient?) {
        coreStatsClient = client
    }

    /**
     * Get total received bytes using safe APIs
     */
    private suspend fun getTotalRxBytes(): Long {
        // Prefer Xray core if available
        coreStatsClient?.getTraffic()?.downlink?.let { return it }
        val uid = Process.myUid()
        return try {
            val uidRx = TrafficStats.getUidRxBytes(uid)
            when {
                uidRx >= 0 -> uidRx
                else -> TrafficStats.getTotalRxBytes().takeIf { it >= 0 } ?: lastRxBytes
            }
        } catch (_: Throwable) {
            lastRxBytes
        }
    }

    /**
     * Get total transmitted bytes using safe APIs
     */
    private suspend fun getTotalTxBytes(): Long {
        coreStatsClient?.getTraffic()?.uplink?.let { return it }
        val uid = Process.myUid()
        return try {
            val uidTx = TrafficStats.getUidTxBytes(uid)
            when {
                uidTx >= 0 -> uidTx
                else -> TrafficStats.getTotalTxBytes().takeIf { it >= 0 } ?: lastTxBytes
            }
        } catch (_: Throwable) {
            lastTxBytes
        }
    }

    /**
     * Get CPU usage percentage using /proc/self/stat and wall time.
     */
    private fun getCpuUsage(): Float {
        return try {
            val nowMs = SystemClock.elapsedRealtime()
            val statFile = File("/proc/self/stat")
            val line = statFile.readText()
            val rparen = line.indexOfLast { it == ')' }
            if (rparen <= 0 || rparen + 2 >= line.length) return smoothedCpuPercent
            val rest = line.substring(rparen + 2).trim().split(" ")
            if (rest.size < 15) return smoothedCpuPercent

            val utimeTicks = rest[11].toLongOrNull() ?: return smoothedCpuPercent
            val stimeTicks = rest[12].toLongOrNull() ?: return smoothedCpuPercent
            val procTicks = utimeTicks + stimeTicks

            val clkTck = try { Os.sysconf(OsConstants._SC_CLK_TCK).coerceAtLeast(1) } catch (_: Throwable) { 100L }

            val cpuDeltaTicks = (procTicks - lastProcCpuTicks).coerceAtLeast(0)
            val wallDeltaMs = (nowMs - lastCpuWallTimeMs).coerceAtLeast(1)

            lastProcCpuTicks = procTicks
            lastCpuWallTimeMs = nowMs

            if (cpuDeltaTicks == 0L || wallDeltaMs <= 0L) return smoothedCpuPercent

            val procCpuMs = cpuDeltaTicks * 1000f / clkTck.toFloat()
            val percent = (procCpuMs / wallDeltaMs.toFloat()) * 100f
            percent.coerceIn(0f, 100f)
        } catch (_: Throwable) {
            // Fallback to smoothed value; avoid logging
            smoothedCpuPercent
        }
    }

    /**
     * Get memory usage in bytes via ActivityManager
     */
    private fun getMemoryUsage(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return smoothedMemBytes
            val pid = Process.myPid()
            val info = activityManager.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()
            if (info != null) info.totalPss.toLong() * 1024L else smoothedMemBytes
        } catch (_: Throwable) {
            smoothedMemBytes
        }
    }

    /**
     * Get native memory usage in bytes
     */
    private fun getNativeMemoryUsage(): Long {
        return try {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            memoryInfo.nativePss.toLong() * 1024 // Convert KB to bytes
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Update connection counts from Xray core stats
     */
    private suspend fun updateConnectionCounts() {
        try {
            // Try to get connection info from Xray core
            val connectionInfo = coreStatsClient?.getConnectionCounts()
            if (connectionInfo != null) {
                currentConnectionCount = connectionInfo.first
                currentActiveConnectionCount = connectionInfo.second
            } else {
                // Fallback: use system stats if available
                val sysStats = coreStatsClient?.getSystemStats()
                if (sysStats != null) {
                    // NumGoroutine is a good indicator of active connections in Xray
                    val goroutines = sysStats.numGoroutine
                    currentConnectionCount = goroutines
                    currentActiveConnectionCount = goroutines
                } else {
                    // Fallback: use connection stats from ConnectionStats if available
                    val stats = _connectionStats.value
                    if (stats.connectedAt > 0) {
                        currentConnectionCount = 1
                        currentActiveConnectionCount = if (stats.disconnectedAt == null) 1 else 0
                    } else {
                        currentConnectionCount = 0
                        currentActiveConnectionCount = 0
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail - connection count is best effort
            android.util.Log.d("PerformanceMonitor", "Failed to update connection counts", e)
        }
    }

    /**
     * Measure latency using a lightweight HTTP request
     */
    private suspend fun measureLatency() {
        withContext(Dispatchers.IO) {
            var probeSuccess = false
            try {
                val startTime = System.currentTimeMillis()
                val connection = URL("https://www.cloudflare.com/cdn-cgi/trace")
                    .openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                    instanceFollowRedirects = false
                    connect()
                }

                val endTime = System.currentTimeMillis()
                val latency = (endTime - startTime).toInt()

                if (connection.responseCode in 200..399 && latency > 0) {
                    recordLatency(latency)
                    probeSuccess = true
                }

                connection.disconnect()
            } catch (e: Exception) {
                // Silently fail - latency measurement is best effort
                android.util.Log.d("PerformanceMonitor", "Latency probe failed", e)
            } finally {
                // Record probe result for packet loss estimation
                recordLatencyProbeResult(probeSuccess)
            }
        }
    }

    /**
     * Record latency probe result for packet loss estimation
     */
    private fun recordLatencyProbeResult(success: Boolean) {
        latencyProbeResults.add(success)
        if (latencyProbeResults.size > maxProbeHistory) {
            latencyProbeResults.removeAt(0)
        }
    }

    /**
     * Calculate packet loss percentage based on latency probe failures
     */
    private fun calculatePacketLoss(): Float {
        if (latencyProbeResults.isEmpty()) return 0f
        
        val failedCount = latencyProbeResults.count { !it }
        val totalCount = latencyProbeResults.size
        val lossPercentage = (failedCount.toFloat() / totalCount.toFloat()) * 100f
        
        return lossPercentage.coerceIn(0f, 100f)
    }

    /**
     * Calculate jitter (latency variation)
     */
    private fun calculateJitter(): Int {
        if (latencyHistory.size < 2) return 0

        var totalVariation = 0
        for (i in 1 until latencyHistory.size) {
            totalVariation += kotlin.math.abs(latencyHistory[i] - latencyHistory[i - 1])
        }

        return totalVariation / (latencyHistory.size - 1)
    }

    /**
     * Calculate connection stability score (0-100)
     */
    private fun calculateStability(): Float {
        // Based on jitter and packet loss
        val jitter = calculateJitter()
        val jitterPenalty = when {
            jitter < 10 -> 0f
            jitter < 30 -> 10f
            jitter < 50 -> 20f
            else -> 30f
        }

        return (100f - jitterPenalty).coerceIn(0f, 100f)
    }

    /**
     * Get peak download speed
     */
    fun getPeakDownloadSpeed(): Long = peakDownloadSpeed

    /**
     * Get peak upload speed
     */
    fun getPeakUploadSpeed(): Long = peakUploadSpeed

    /**
     * Get bandwidth history (last 60 seconds)
     */
    fun getBandwidthHistory(): List<Pair<Long, Long>> = bandwidthHistory.toList()

    /**
     * Get average download speed from history
     */
    fun getAverageDownloadSpeed(): Long {
        return if (bandwidthHistory.isEmpty()) 0
        else bandwidthHistory.map { it.first }.average().toLong()
    }

    /**
     * Get average upload speed from history
     */
    fun getAverageUploadSpeed(): Long {
        return if (bandwidthHistory.isEmpty()) 0
        else bandwidthHistory.map { it.second }.average().toLong()
    }

    /**
     * Reset peak bandwidth tracking
     */
    fun resetPeakBandwidth() {
        peakDownloadSpeed = 0
        peakUploadSpeed = 0
    }

    // Helpers
    private fun isAtLeastAndroid10(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private fun isMIUIDevice(): Boolean {
        val m = Build.MANUFACTURER?.lowercase() ?: ""
        val b = Build.BRAND?.lowercase() ?: ""
        return m.contains("xiaomi") || b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
    }
    private fun smoothLong(prev: Long, next: Long): Long = (prev + alpha * (next - prev).toFloat()).toLong()
    private fun smoothFloat(prev: Float, next: Float): Float = prev + alpha * (next - prev)
    private fun getBatteryTemperatureC(): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (tenths > 0) tenths / 10f else Float.NaN
        } catch (_: Throwable) {
            Float.NaN
        }
    }
    private fun getUptimeSeconds(): Long = SystemClock.elapsedRealtime() / 1000L

    /**
     * Get process memory info
     */
    fun getProcessMemoryInfo(): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return "Memory info unavailable"
        val pid = Process.myPid()
        val processMemoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))

        if (processMemoryInfo.isNotEmpty()) {
            val info = processMemoryInfo[0]
            return buildString {
                appendLine("Total PSS: ${info.totalPss / 1024} MB")
                appendLine("Dalvik PSS: ${info.dalvikPss / 1024} MB")
                appendLine("Native PSS: ${info.nativePss / 1024} MB")
                appendLine("Other PSS: ${info.otherPss / 1024} MB")
            }
        }

        return "Memory info unavailable"
    }
}
