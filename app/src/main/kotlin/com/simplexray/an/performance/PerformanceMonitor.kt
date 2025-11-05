package com.simplexray.an.performance

import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Performance Monitoring & Metrics
 * Tracks performance improvements and system metrics
 */
class PerformanceMonitor {
    
    data class PerformanceMetrics(
        val throughputMBps: Double = 0.0,
        val latencyMs: Double = 0.0,
        val cpuUsagePercent: Double = 0.0,
        val memoryUsageMB: Long = 0,
        val gcPauseCount: Long = 0,
        val gcPauseTimeMs: Long = 0,
        val activeConnections: Int = 0,
        val bytesTransferred: Long = 0,
        val packetsTransferred: Long = 0
    )
    
    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()
    
    private var startTime = System.currentTimeMillis()
    private var bytesSent = 0L
    private var bytesReceived = 0L
    private var packetsSent = 0L
    private var packetsReceived = 0L
    
    /**
     * Update throughput metrics
     */
    fun updateThroughput(sentBytes: Long, receivedBytes: Long, sentPackets: Long, receivedPackets: Long) {
        bytesSent += sentBytes
        bytesReceived += receivedBytes
        packetsSent += sentPackets
        packetsReceived += receivedPackets
        
        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        if (elapsed > 0) {
            val totalBytes = bytesSent + bytesReceived
            val throughputMBps = (totalBytes / (1024.0 * 1024.0)) / elapsed
            
            _metrics.value = _metrics.value.copy(
                throughputMBps = throughputMBps,
                bytesTransferred = totalBytes,
                packetsTransferred = packetsSent + packetsReceived
            )
        }
    }
    
    /**
     * Update latency measurement
     */
    fun updateLatency(latencyMs: Double) {
        _metrics.value = _metrics.value.copy(
            latencyMs = latencyMs
        )
    }
    
    /**
     * Update connection count
     */
    fun updateConnections(count: Int) {
        _metrics.value = _metrics.value.copy(
            activeConnections = count
        )
    }
    
    /**
     * Get performance improvement percentage
     */
    fun getImprovementPercentage(baselineThroughput: Double): Double {
        val current = _metrics.value.throughputMBps
        if (baselineThroughput > 0 && current > baselineThroughput) {
            return ((current - baselineThroughput) / baselineThroughput) * 100.0
        }
        return 0.0
    }
    
    /**
     * Reset metrics
     */
    fun reset() {
        startTime = System.currentTimeMillis()
        bytesSent = 0L
        bytesReceived = 0L
        packetsSent = 0L
        packetsReceived = 0L
        _metrics.value = PerformanceMetrics()
    }
    
    /**
     * Get formatted metrics string
     */
    fun getFormattedMetrics(): String {
        val m = _metrics.value
        return """
            Performance Metrics:
            - Throughput: ${String.format("%.2f", m.throughputMBps)} MB/s
            - Latency: ${String.format("%.2f", m.latencyMs)} ms
            - CPU Usage: ${String.format("%.1f", m.cpuUsagePercent)}%
            - Memory: ${m.memoryUsageMB} MB
            - Active Connections: ${m.activeConnections}
            - Bytes Transferred: ${m.bytesTransferred / 1024 / 1024} MB
            - Packets: ${m.packetsTransferred}
        """.trimIndent()
    }
    
    companion object {
        private const val TAG = "PerformanceMonitor"
        
        @Volatile
        private var INSTANCE: PerformanceMonitor? = null
        
        fun getInstance(): PerformanceMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceMonitor().also { INSTANCE = it }
            }
        }
    }
}



