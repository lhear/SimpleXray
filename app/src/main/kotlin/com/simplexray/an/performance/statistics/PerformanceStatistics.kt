package com.simplexray.an.performance.statistics

import com.simplexray.an.performance.model.PerformanceMetrics
import kotlin.math.sqrt

/**
 * Detailed performance statistics calculator
 */
class PerformanceStatistics {

    /**
     * Calculate detailed statistics for a metric
     */
    fun calculateStats(values: List<Float>): MetricStats {
        if (values.isEmpty()) {
            return MetricStats()
        }

        val sorted = values.sorted()
        val size = sorted.size

        return MetricStats(
            min = sorted.first(),
            max = sorted.last(),
            mean = values.average().toFloat(),
            median = if (size % 2 == 0) {
                (sorted[size / 2 - 1] + sorted[size / 2]) / 2f
            } else {
                sorted[size / 2]
            },
            p95 = calculatePercentile(sorted, 95),
            p99 = calculatePercentile(sorted, 99),
            stdDev = calculateStdDev(values),
            variance = calculateVariance(values),
            count = size
        )
    }

    /**
     * Calculate percentile (using linear interpolation for more accurate results)
     */
    private fun calculatePercentile(sortedValues: List<Float>, percentile: Int): Float {
        if (sortedValues.isEmpty()) return 0f
        if (sortedValues.size == 1) return sortedValues[0]

        val rank = (percentile / 100.0) * (sortedValues.size - 1)
        val lower = rank.toInt()
        val upper = lower + 1
        val fraction = (rank - lower).toFloat()

        return if (upper >= sortedValues.size) {
            sortedValues.last()
        } else {
            sortedValues[lower] * (1 - fraction) + sortedValues[upper] * fraction
        }
    }

    /**
     * Calculate standard deviation
     */
    private fun calculateStdDev(values: List<Float>): Float {
        return sqrt(calculateVariance(values))
    }

    /**
     * Calculate variance (using Bessel's correction for sample variance)
     */
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        if (values.size == 1) return 0f

        val mean = values.average().toFloat()
        val squaredDiffs = values.map { (it - mean) * (it - mean) }
        // Use Bessel's correction (n-1) for sample variance
        return squaredDiffs.sum() / (values.size - 1)
    }

    /**
     * Generate performance report
     */
    fun generateReport(metricsHistory: List<PerformanceMetrics>): PerformanceReport {
        if (metricsHistory.isEmpty()) {
            return PerformanceReport()
        }

        // Convert bytes to MB/s for better readability in statistics
        val downloadSpeeds = metricsHistory.map { it.downloadSpeed / (1024f * 1024f) }
        val uploadSpeeds = metricsHistory.map { it.uploadSpeed / (1024f * 1024f) }
        val latencies = metricsHistory.map { it.latency.toFloat() }
        val jitters = metricsHistory.map { it.jitter.toFloat() }
        val packetLosses = metricsHistory.map { it.packetLoss }
        val cpuUsages = metricsHistory.map { it.cpuUsage }
        val memoryUsages = metricsHistory.map { it.memoryUsage / (1024f * 1024f) } // Convert to MB

        // Calculate total data transferred
        val totalDownload = metricsHistory.lastOrNull()?.totalDownload ?: 0L
        val totalUpload = metricsHistory.lastOrNull()?.totalUpload ?: 0L
        
        // Calculate average connection counts
        val avgConnectionCount = if (metricsHistory.isNotEmpty()) {
            metricsHistory.map { it.connectionCount.toFloat() }.average().toInt()
        } else 0
        val avgActiveConnectionCount = if (metricsHistory.isNotEmpty()) {
            metricsHistory.map { it.activeConnectionCount.toFloat() }.average().toInt()
        } else 0

        return PerformanceReport(
            downloadStats = calculateStats(downloadSpeeds),
            uploadStats = calculateStats(uploadSpeeds),
            latencyStats = calculateStats(latencies),
            jitterStats = calculateStats(jitters),
            packetLossStats = calculateStats(packetLosses),
            cpuStats = calculateStats(cpuUsages),
            memoryStats = calculateStats(memoryUsages),
            totalDataPoints = metricsHistory.size,
            averageQuality = metricsHistory.map { it.calculateQualityScore() }.average().toFloat(),
            totalDownload = totalDownload,
            totalUpload = totalUpload,
            avgConnectionCount = avgConnectionCount,
            avgActiveConnectionCount = avgActiveConnectionCount,
            uptime = if (metricsHistory.size > 1) {
                metricsHistory.last().timestamp - metricsHistory.first().timestamp
            } else 0L
        )
    }

    /**
     * Calculate performance score (0-100)
     */
    fun calculatePerformanceScore(metrics: PerformanceMetrics): PerformanceScore {
        var score = 100f

        // Latency score (40% weight)
        val latencyScore = when {
            metrics.latency < 50 -> 40f
            metrics.latency < 100 -> 35f
            metrics.latency < 200 -> 25f
            metrics.latency < 500 -> 15f
            else -> 5f
        }

        // Bandwidth score (30% weight)
        val downloadMbps = metrics.downloadSpeed / (1024f * 1024f)
        val bandwidthScore = when {
            downloadMbps > 10 -> 30f
            downloadMbps > 5 -> 25f
            downloadMbps > 1 -> 20f
            downloadMbps > 0.5f -> 15f
            else -> 5f
        }

        // Stability score (20% weight)
        val stabilityScore = (metrics.connectionStability / 100f) * 20f

        // Resource efficiency (10% weight)
        val resourceScore = when {
            metrics.cpuUsage < 30 && metrics.memoryUsage < 100 * 1024 * 1024 -> 10f
            metrics.cpuUsage < 50 && metrics.memoryUsage < 200 * 1024 * 1024 -> 7f
            metrics.cpuUsage < 70 -> 5f
            else -> 2f
        }

        val totalScore = (latencyScore + bandwidthScore + stabilityScore + resourceScore).coerceIn(0f, 100f)

        return PerformanceScore(
            overall = totalScore,
            latencyScore = latencyScore,
            bandwidthScore = bandwidthScore,
            stabilityScore = stabilityScore,
            resourceScore = resourceScore,
            grade = when {
                totalScore >= 90 -> "A+"
                totalScore >= 80 -> "A"
                totalScore >= 70 -> "B"
                totalScore >= 60 -> "C"
                totalScore >= 50 -> "D"
                else -> "F"
            }
        )
    }
}

/**
 * Statistics for a single metric
 */
data class MetricStats(
    val min: Float = 0f,
    val max: Float = 0f,
    val mean: Float = 0f,
    val median: Float = 0f,
    val p95: Float = 0f,
    val p99: Float = 0f,
    val stdDev: Float = 0f,
    val variance: Float = 0f,
    val count: Int = 0
)

/**
 * Comprehensive performance report
 */
data class PerformanceReport(
    val downloadStats: MetricStats = MetricStats(), // MB/s
    val uploadStats: MetricStats = MetricStats(), // MB/s
    val latencyStats: MetricStats = MetricStats(), // ms
    val jitterStats: MetricStats = MetricStats(), // ms
    val packetLossStats: MetricStats = MetricStats(), // percentage
    val cpuStats: MetricStats = MetricStats(), // percentage
    val memoryStats: MetricStats = MetricStats(), // MB
    val totalDataPoints: Int = 0,
    val averageQuality: Float = 0f,
    val totalDownload: Long = 0L, // bytes
    val totalUpload: Long = 0L, // bytes
    val avgConnectionCount: Int = 0,
    val avgActiveConnectionCount: Int = 0,
    val uptime: Long = 0 // milliseconds
)

/**
 * Performance score breakdown
 */
data class PerformanceScore(
    val overall: Float = 0f,
    val latencyScore: Float = 0f,
    val bandwidthScore: Float = 0f,
    val stabilityScore: Float = 0f,
    val resourceScore: Float = 0f,
    val grade: String = "N/A"
)
