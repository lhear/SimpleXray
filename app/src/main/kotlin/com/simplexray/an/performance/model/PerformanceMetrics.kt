package com.simplexray.an.performance.model

/**
 * Real-time performance metrics
 */
data class PerformanceMetrics(
    // Network metrics
    val uploadSpeed: Long = 0, // bytes per second
    val downloadSpeed: Long = 0, // bytes per second
    val totalUpload: Long = 0, // total bytes
    val totalDownload: Long = 0, // total bytes

    // Connection metrics
    val latency: Int = 0, // milliseconds
    val jitter: Int = 0, // milliseconds
    val packetLoss: Float = 0f, // percentage
    val connectionCount: Int = 0,
    val activeConnectionCount: Int = 0,

    // Resource metrics
    val cpuUsage: Float = 0f, // percentage
    val memoryUsage: Long = 0, // bytes
    val nativeMemoryUsage: Long = 0, // bytes

    // Quality metrics
    val connectionStability: Float = 100f, // 0-100 score
    val overallQuality: ConnectionQuality = ConnectionQuality.Good,

    // Timestamp
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Calculate connection quality score
     */
    fun calculateQualityScore(): Float {
        var score = 100f

        // Latency penalty (more aggressive for high latency)
        score -= when {
            latency < 50 -> 0f
            latency < 100 -> 15f
            latency < 200 -> 25f
            latency < 500 -> 45f
            latency < 1000 -> 55f
            else -> 80f
        }

        // Packet loss penalty (5 points per 1%)
        score -= packetLoss * 5

        // Jitter penalty
        score -= when {
            jitter < 10 -> 0f
            jitter < 30 -> 8f
            jitter < 50 -> 10f
            jitter < 100 -> 10f
            else -> 12f
        }

        return score.coerceIn(0f, 100f)
    }

    /**
     * Get connection quality enum
     */
    fun getConnectionQuality(): ConnectionQuality {
        val score = calculateQualityScore()
        return when {
            score >= 80 -> ConnectionQuality.Excellent
            score >= 60 -> ConnectionQuality.Good
            score >= 40 -> ConnectionQuality.Fair
            score >= 20 -> ConnectionQuality.Poor
            else -> ConnectionQuality.VeryPoor
        }
    }
}

/**
 * Connection quality classification
 */
enum class ConnectionQuality(val displayName: String, val color: Long) {
    Excellent("Excellent", 0xFF4CAF50), // Green
    Good("Good", 0xFF8BC34A), // Light Green
    Fair("Fair", 0xFFFFC107), // Amber
    Poor("Poor", 0xFFFF9800), // Orange
    VeryPoor("Very Poor", 0xFFF44336) // Red
}

/**
 * Historical metrics for trend analysis
 */
data class MetricsHistory(
    val metrics: List<PerformanceMetrics> = emptyList(),
    val maxSize: Int = 100
) {
    fun add(metric: PerformanceMetrics): MetricsHistory {
        val newList = (metrics + metric).takeLast(maxSize)
        return copy(metrics = newList)
    }

    fun getAverageLatency(): Int {
        return if (metrics.isEmpty()) 0 else metrics.map { it.latency }.average().toInt()
    }

    fun getAverageDownloadSpeed(): Long {
        return if (metrics.isEmpty()) 0 else metrics.map { it.downloadSpeed }.average().toLong()
    }

    fun getAverageUploadSpeed(): Long {
        return if (metrics.isEmpty()) 0 else metrics.map { it.uploadSpeed }.average().toLong()
    }

    fun getAverageQuality(): Float {
        return if (metrics.isEmpty()) 0f else metrics.map { it.calculateQualityScore() }.average().toFloat()
    }
}

/**
 * Connection statistics
 */
data class ConnectionStats(
    val connectedAt: Long = 0,
    val disconnectedAt: Long? = null,
    val totalDuration: Long = 0, // milliseconds
    val reconnectCount: Int = 0,
    val errorCount: Int = 0,
    val lastError: String? = null,
    val averageLatency: Int = 0,
    val peakDownloadSpeed: Long = 0,
    val peakUploadSpeed: Long = 0
) {
    fun getDurationMillis(): Long {
        return if (disconnectedAt != null) {
            disconnectedAt - connectedAt
        } else {
            System.currentTimeMillis() - connectedAt
        }
    }

    fun getDurationSeconds(): Long = getDurationMillis() / 1000

    fun getDurationMinutes(): Long = getDurationSeconds() / 60

    fun getDurationHours(): Long = getDurationMinutes() / 60
}

/**
 * Bandwidth usage statistics
 */
data class BandwidthStats(
    val timestamp: Long = System.currentTimeMillis(),
    val periodStart: Long = 0,
    val periodEnd: Long = 0,
    val totalUpload: Long = 0,
    val totalDownload: Long = 0,
    val peakUploadSpeed: Long = 0,
    val peakDownloadSpeed: Long = 0,
    val averageUploadSpeed: Long = 0,
    val averageDownloadSpeed: Long = 0
) {
    fun getTotalBytes(): Long = totalUpload + totalDownload

    fun getPeriodDuration(): Long = periodEnd - periodStart
}

/**
 * Performance event for logging
 */
sealed class PerformanceEvent(
    val timestamp: Long = System.currentTimeMillis()
) {
    data class ConnectionEstablished(val latency: Int) : PerformanceEvent()
    data class ConnectionLost(val reason: String) : PerformanceEvent()
    data class HighLatency(val latency: Int) : PerformanceEvent()
    data class PacketLoss(val percentage: Float) : PerformanceEvent()
    data class LowBandwidth(val speed: Long) : PerformanceEvent()
    data class HighMemoryUsage(val bytes: Long) : PerformanceEvent()
    data class OptimizationApplied(val optimization: String) : PerformanceEvent()
    data class ProfileChanged(val oldProfile: String, val newProfile: String) : PerformanceEvent()
}
