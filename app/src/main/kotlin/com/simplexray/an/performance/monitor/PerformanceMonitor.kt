package com.simplexray.an.performance.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
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
import java.io.RandomAccessFile

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

    private var monitoringJob: Job? = null
    private var isRunning = false

    // Network monitoring state
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastUpdateTime: Long = 0

    // Latency monitoring
    private val latencyHistory = mutableListOf<Int>()
    private val maxLatencyHistory = 20

    // CPU monitoring state
    private var lastCpuTime: Long = 0
    private var lastCpuUpdateTime: Long = 0

    // Bandwidth tracking
    private var peakDownloadSpeed: Long = 0
    private var peakUploadSpeed: Long = 0
    private val bandwidthHistory = mutableListOf<Pair<Long, Long>>() // (download, upload)
    private val maxBandwidthHistory = 60 // Last 60 seconds

    /**
     * Start monitoring
     */
    fun start() {
        if (isRunning) return

        isRunning = true
        lastUpdateTime = System.currentTimeMillis()

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
     * Update all metrics
     */
    private suspend fun updateMetrics() {
        try {
            val currentTime = System.currentTimeMillis()
            val timeDelta = currentTime - lastUpdateTime

            if (timeDelta <= 0) return

            // Network metrics from Xray core or system
            val currentRxBytes = getTotalRxBytes()
            val currentTxBytes = getTotalTxBytes()

            val rxDelta = maxOf(0, currentRxBytes - lastRxBytes)
            val txDelta = maxOf(0, currentTxBytes - lastTxBytes)

            val downloadSpeed = (rxDelta * 1000 / timeDelta).coerceAtLeast(0)
            val uploadSpeed = (txDelta * 1000 / timeDelta).coerceAtLeast(0)

            // Track peak bandwidth
            if (downloadSpeed > peakDownloadSpeed) {
                peakDownloadSpeed = downloadSpeed
            }
            if (uploadSpeed > peakUploadSpeed) {
                peakUploadSpeed = uploadSpeed
            }

            // Add to bandwidth history
            bandwidthHistory.add(0, Pair(downloadSpeed, uploadSpeed))
            if (bandwidthHistory.size > maxBandwidthHistory) {
                bandwidthHistory.removeAt(bandwidthHistory.lastIndex)
            }

            // Resource metrics
            val cpuUsage = getCpuUsage()
            val memoryUsage = getMemoryUsage()
            val nativeMemoryUsage = getNativeMemoryUsage()

            // Latency metrics
            val avgLatency = if (latencyHistory.isNotEmpty()) {
                latencyHistory.average().toInt()
            } else 0

            val jitter = calculateJitter()

            // Create metrics snapshot (without quality first)
            val metricsWithoutQuality = PerformanceMetrics(
                uploadSpeed = uploadSpeed,
                downloadSpeed = downloadSpeed,
                totalUpload = currentTxBytes,
                totalDownload = currentRxBytes,
                latency = avgLatency,
                jitter = jitter,
                packetLoss = 0f, // TODO: Implement packet loss detection
                connectionCount = 0, // TODO: Get from Xray stats
                activeConnectionCount = 0,
                cpuUsage = cpuUsage.coerceIn(0f, 100f),
                memoryUsage = memoryUsage.coerceAtLeast(0),
                nativeMemoryUsage = nativeMemoryUsage.coerceAtLeast(0),
                connectionStability = calculateStability(),
                timestamp = currentTime
            )

            // Calculate quality based on actual metrics
            val metrics = metricsWithoutQuality.copy(
                overallQuality = metricsWithoutQuality.getConnectionQuality()
            )

            _currentMetrics.value = metrics
            _metricsHistory.value = _metricsHistory.value.add(metrics)

            // Update for next iteration
            lastRxBytes = currentRxBytes
            lastTxBytes = currentTxBytes
            lastUpdateTime = currentTime
        } catch (e: Exception) {
            android.util.Log.e("PerformanceMonitor", "Error updating metrics", e)
            // Continue monitoring despite the error
        }
    }

    /**
     * Set CoreStatsClient for Xray core integration
     */
    fun setCoreStatsClient(client: CoreStatsClient?) {
        coreStatsClient = client
    }

    /**
     * Get total received bytes from Xray core (if available) or system
     */
    private suspend fun getTotalRxBytes(): Long {
        return try {
            // Try to get from Xray core first
            coreStatsClient?.getTraffic()?.downlink?.let { return it }

            // Fallback to system-wide network stats
            val file = File("/proc/net/dev")
            if (!file.exists()) return 0

            var total = 0L
            file.readLines().forEach { line ->
                if (line.contains(":")) {
                    val parts = line.trim().split(Regex("\\s+"))
                    // Check bounds before accessing array indices
                    if (parts.size > 1 && !parts[0].startsWith("lo:")) {
                        total += parts[1].toLongOrNull() ?: 0
                    }
                }
            }
            total
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get total transmitted bytes from Xray core (if available) or system
     */
    private suspend fun getTotalTxBytes(): Long {
        return try {
            // Try to get from Xray core first
            coreStatsClient?.getTraffic()?.uplink?.let { return it }

            // Fallback to system-wide network stats
            val file = File("/proc/net/dev")
            if (!file.exists()) return 0

            var total = 0L
            file.readLines().forEach { line ->
                if (line.contains(":")) {
                    val parts = line.trim().split(Regex("\\s+"))
                    // Check bounds before accessing array indices
                    if (parts.size > 9 && !parts[0].startsWith("lo:")) {
                        total += parts[9].toLongOrNull() ?: 0
                    }
                }
            }
            total
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get CPU usage percentage (properly calculated with delta)
     */
    private fun getCpuUsage(): Float {
        return try {
            val currentTime = System.currentTimeMillis()
            val pid = Process.myPid()

            // Read process CPU time
            val statFile = File("/proc/$pid/stat")
            if (!statFile.exists()) return 0f

            val stats = statFile.readText().split(" ")
            if (stats.size < 17) return 0f

            val utime = stats[13].toLongOrNull() ?: 0
            val stime = stats[14].toLongOrNull() ?: 0
            val currentCpuTime = utime + stime

            // Read system CPU time
            val uptimeFile = File("/proc/uptime")
            if (!uptimeFile.exists()) return 0f

            val uptime = uptimeFile.readText().trim().split(" ")[0].toDoubleOrNull() ?: 0.0
            val uptimeJiffies = (uptime * 100).toLong() // Convert to jiffies (100 Hz)

            // Calculate percentage if we have previous data
            if (lastCpuTime > 0 && lastCpuUpdateTime > 0) {
                val timeDelta = currentTime - lastCpuUpdateTime
                if (timeDelta > 0) {
                    val cpuDelta = currentCpuTime - lastCpuTime
                    val percentage = (cpuDelta.toFloat() / (timeDelta / 10f)).coerceIn(0f, 100f)

                    lastCpuTime = currentCpuTime
                    lastCpuUpdateTime = currentTime

                    return percentage
                }
            }

            // Initialize for next time
            lastCpuTime = currentCpuTime
            lastCpuUpdateTime = currentTime

            return 0f // First run, no delta available
        } catch (e: Exception) {
            android.util.Log.w("PerformanceMonitor", "Error calculating CPU usage", e)
            0f
        }
    }

    /**
     * Get memory usage in bytes
     */
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /**
     * Get native memory usage in bytes
     */
    private fun getNativeMemoryUsage(): Long {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.nativePss.toLong() * 1024 // Convert KB to bytes
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
