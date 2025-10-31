package com.simplexray.an.performance.monitor

import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.ConnectionQuality
import com.simplexray.an.performance.model.PerformanceEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Analyzes connection quality and detects performance issues
 */
class ConnectionAnalyzer {
    private val _events = MutableSharedFlow<PerformanceEvent>()
    val events: SharedFlow<PerformanceEvent> = _events.asSharedFlow()

    // Thresholds for issue detection
    private val highLatencyThreshold = 200 // ms
    private val packetLossThreshold = 2f // percentage
    private val lowBandwidthThreshold = 100_000L // 100 KB/s
    private val highMemoryThreshold = 200 * 1024 * 1024L // 200 MB

    // State tracking
    private var lastQuality: ConnectionQuality? = null
    private var consecutiveHighLatency = 0
    private var consecutivePacketLoss = 0

    /**
     * Analyze metrics and detect issues
     */
    suspend fun analyze(metrics: PerformanceMetrics) {
        // Quality change detection
        val currentQuality = metrics.getConnectionQuality()
        if (lastQuality != null && lastQuality != currentQuality) {
            // Quality changed - could trigger adaptive optimization
        }
        lastQuality = currentQuality

        // High latency detection
        if (metrics.latency > highLatencyThreshold) {
            consecutiveHighLatency++
            if (consecutiveHighLatency >= 3) {
                _events.emit(PerformanceEvent.HighLatency(metrics.latency))
            }
        } else {
            consecutiveHighLatency = 0
        }

        // Packet loss detection
        if (metrics.packetLoss > packetLossThreshold) {
            consecutivePacketLoss++
            if (consecutivePacketLoss >= 3) {
                _events.emit(PerformanceEvent.PacketLoss(metrics.packetLoss))
            }
        } else {
            consecutivePacketLoss = 0
        }

        // Low bandwidth detection
        if (metrics.downloadSpeed > 0 && metrics.downloadSpeed < lowBandwidthThreshold) {
            _events.emit(PerformanceEvent.LowBandwidth(metrics.downloadSpeed))
        }

        // High memory usage detection
        if (metrics.memoryUsage > highMemoryThreshold) {
            _events.emit(PerformanceEvent.HighMemoryUsage(metrics.memoryUsage))
        }
    }

    /**
     * Analyze connection stability
     */
    fun analyzeStability(history: List<PerformanceMetrics>): StabilityAnalysis {
        if (history.isEmpty()) {
            return StabilityAnalysis(
                isStable = true,
                score = 100f,
                issues = emptyList()
            )
        }

        val issues = mutableListOf<String>()
        var stabilityScore = 100f

        // Check latency variance
        val latencies = history.map { it.latency }
        val avgLatency = latencies.average()
        val latencyVariance = latencies.map { (it - avgLatency) * (it - avgLatency) }.average()
        val latencyStdDev = kotlin.math.sqrt(latencyVariance)

        if (latencyStdDev > 50) {
            issues.add("High latency variation detected")
            stabilityScore -= 20f
        }

        // Check for disconnections/reconnections
        val qualityChanges = history.zipWithNext().count { (prev, curr) ->
            kotlin.math.abs(prev.calculateQualityScore() - curr.calculateQualityScore()) > 20
        }

        if (qualityChanges > history.size * 0.3) {
            issues.add("Frequent quality fluctuations")
            stabilityScore -= 30f
        }

        // Check bandwidth stability
        val downloadSpeeds = history.map { it.downloadSpeed }.filter { it > 0 }
        if (downloadSpeeds.isNotEmpty()) {
            val avgSpeed = downloadSpeeds.average()
            val speedVariance = downloadSpeeds.map { (it - avgSpeed) * (it - avgSpeed) }.average()
            val speedCoeffVar = if (avgSpeed > 0) kotlin.math.sqrt(speedVariance) / avgSpeed else 0.0

            if (speedCoeffVar > 0.5) {
                issues.add("Unstable bandwidth")
                stabilityScore -= 15f
            }
        }

        return StabilityAnalysis(
            isStable = stabilityScore >= 70f,
            score = stabilityScore.coerceIn(0f, 100f),
            issues = issues
        )
    }

    /**
     * Detect bottlenecks
     */
    fun detectBottlenecks(metrics: PerformanceMetrics): List<Bottleneck> {
        val bottlenecks = mutableListOf<Bottleneck>()

        // High latency bottleneck
        if (metrics.latency > 100) {
            bottlenecks.add(
                Bottleneck(
                    type = BottleneckType.HighLatency,
                    severity = when {
                        metrics.latency > 500 -> BottleneckSeverity.Critical
                        metrics.latency > 200 -> BottleneckSeverity.High
                        else -> BottleneckSeverity.Medium
                    },
                    description = "High latency: ${metrics.latency}ms",
                    recommendation = "Consider switching to a closer server or better network"
                )
            )
        }

        // Memory bottleneck
        if (metrics.memoryUsage > 150 * 1024 * 1024) {
            bottlenecks.add(
                Bottleneck(
                    type = BottleneckType.Memory,
                    severity = when {
                        metrics.memoryUsage > 300 * 1024 * 1024 -> BottleneckSeverity.Critical
                        metrics.memoryUsage > 200 * 1024 * 1024 -> BottleneckSeverity.High
                        else -> BottleneckSeverity.Medium
                    },
                    description = "High memory usage: ${metrics.memoryUsage / 1024 / 1024}MB",
                    recommendation = "Switch to Battery Saver mode or reduce buffer size"
                )
            )
        }

        // CPU bottleneck
        if (metrics.cpuUsage > 50f) {
            bottlenecks.add(
                Bottleneck(
                    type = BottleneckType.CPU,
                    severity = when {
                        metrics.cpuUsage > 80f -> BottleneckSeverity.Critical
                        metrics.cpuUsage > 60f -> BottleneckSeverity.High
                        else -> BottleneckSeverity.Medium
                    },
                    description = "High CPU usage: ${metrics.cpuUsage.toInt()}%",
                    recommendation = "Disable compression or reduce parallel connections"
                )
            )
        }

        // Packet loss bottleneck
        if (metrics.packetLoss > 1f) {
            bottlenecks.add(
                Bottleneck(
                    type = BottleneckType.PacketLoss,
                    severity = when {
                        metrics.packetLoss > 5f -> BottleneckSeverity.Critical
                        metrics.packetLoss > 2f -> BottleneckSeverity.High
                        else -> BottleneckSeverity.Medium
                    },
                    description = "Packet loss: ${metrics.packetLoss}%",
                    recommendation = "Check network connection or try different protocol"
                )
            )
        }

        // Low bandwidth bottleneck
        if (metrics.downloadSpeed < lowBandwidthThreshold && metrics.downloadSpeed > 0) {
            bottlenecks.add(
                Bottleneck(
                    type = BottleneckType.LowBandwidth,
                    severity = when {
                        metrics.downloadSpeed < 50_000L -> BottleneckSeverity.Critical
                        metrics.downloadSpeed < 100_000L -> BottleneckSeverity.High
                        else -> BottleneckSeverity.Medium
                    },
                    description = "Low bandwidth: ${metrics.downloadSpeed / 1024} KB/s",
                    recommendation = "Reduce buffer size or switch to Battery Saver mode"
                )
            )
        }

        // Connection quality bottleneck (generic connection issues)
        if (metrics.latency > 150 && metrics.packetLoss > 0.5f) {
            bottlenecks.add(
                Bottleneck(
                    type = BottleneckType.Connection,
                    severity = when {
                        metrics.latency > 300 && metrics.packetLoss > 2f -> BottleneckSeverity.Critical
                        metrics.latency > 200 || metrics.packetLoss > 1f -> BottleneckSeverity.High
                        else -> BottleneckSeverity.Medium
                    },
                    description = "Poor connection quality: Latency ${metrics.latency}ms, Packet Loss ${metrics.packetLoss}%",
                    recommendation = "Check network connection or move to a better location"
                )
            )
        }

        return bottlenecks
    }

    /**
     * Recommend optimization profile based on current metrics
     */
    fun recommendProfile(metrics: PerformanceMetrics, currentProfile: String): String? {
        // If quality is poor and using aggressive profile, suggest downgrade
        if (metrics.getConnectionQuality() == ConnectionQuality.Poor ||
            metrics.getConnectionQuality() == ConnectionQuality.VeryPoor
        ) {
            if (currentProfile == "turbo" || currentProfile == "streaming") {
                return "balanced"
            }
        }

        // If memory is high, suggest battery saver
        if (metrics.memoryUsage > 200 * 1024 * 1024) {
            if (currentProfile != "battery_saver") {
                return "battery_saver"
            }
        }

        // If quality is excellent and in conservative mode, suggest upgrade
        if (metrics.getConnectionQuality() == ConnectionQuality.Excellent) {
            if (currentProfile == "battery_saver") {
                return "balanced"
            }
        }

        return null
    }
}

/**
 * Stability analysis result
 */
data class StabilityAnalysis(
    val isStable: Boolean,
    val score: Float,
    val issues: List<String>
)

/**
 * Performance bottleneck
 */
data class Bottleneck(
    val type: BottleneckType,
    val severity: BottleneckSeverity,
    val description: String,
    val recommendation: String
)

enum class BottleneckType {
    HighLatency,
    PacketLoss,
    LowBandwidth,
    Memory,
    CPU,
    Connection
}

enum class BottleneckSeverity {
    Low,
    Medium,
    High,
    Critical
}
