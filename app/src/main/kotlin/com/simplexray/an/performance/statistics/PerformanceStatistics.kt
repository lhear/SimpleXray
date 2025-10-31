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
     * Calculate percentile
     */
    private fun calculatePercentile(sortedValues: List<Float>, percentile: Int): Float {
        if (sortedValues.isEmpty()) return 0f

        val index = ((percentile / 100.0) * sortedValues.size).toInt()
        return sortedValues[index.coerceIn(0, sortedValues.size - 1)]
    }

    /**
     * Calculate standard deviation
     */
    private fun calculateStdDev(values: List<Float>): Float {
        return sqrt(calculateVariance(values))
    }

    /**
     * Calculate variance
     */
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f

        val mean = values.average().toFloat()
        val squaredDiffs = values.map { (it - mean) * (it - mean) }
        return squaredDiffs.average().toFloat()
    }

    /**
     * Generate performance report
     */
    fun generateReport(metricsHistory: List<PerformanceMetrics>): PerformanceReport {
        if (metricsHistory.isEmpty()) {
            return PerformanceReport()
        }

        val downloadSpeeds = metricsHistory.map { it.downloadSpeed.toFloat() }
        val uploadSpeeds = metricsHistory.map { it.uploadSpeed.toFloat() }
        val latencies = metricsHistory.map { it.latency.toFloat() }
        val cpuUsages = metricsHistory.map { it.cpuUsage }
        val memoryUsages = metricsHistory.map { it.memoryUsage.toFloat() }

        return PerformanceReport(
            downloadStats = calculateStats(downloadSpeeds),
            uploadStats = calculateStats(uploadSpeeds),
            latencyStats = calculateStats(latencies),
            cpuStats = calculateStats(cpuUsages),
            memoryStats = calculateStats(memoryUsages),
            totalDataPoints = metricsHistory.size,
            averageQuality = metricsHistory.map { it.calculateQualityScore() }.average().toFloat(),
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
    val downloadStats: MetricStats = MetricStats(),
    val uploadStats: MetricStats = MetricStats(),
    val latencyStats: MetricStats = MetricStats(),
    val cpuStats: MetricStats = MetricStats(),
    val memoryStats: MetricStats = MetricStats(),
    val totalDataPoints: Int = 0,
    val averageQuality: Float = 0f,
    val uptime: Long = 0
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
