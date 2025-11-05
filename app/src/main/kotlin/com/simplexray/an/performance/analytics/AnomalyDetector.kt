package com.simplexray.an.performance.analytics

import com.simplexray.an.performance.model.PerformanceMetrics

/**
 * Detects anomalies in performance metrics
 */
class AnomalyDetector {
    
    data class Anomaly(
        val type: AnomalyType,
        val severity: AnomalySeverity,
        val timestamp: Long,
        val value: Any,
        val threshold: Any,
        val description: String
    )
    
    enum class AnomalyType {
        LatencySpike,
        ThroughputDrop,
        PacketLoss,
        BatteryDrain,
        MemoryLeak,
        CPUOverload
    }
    
    enum class AnomalySeverity {
        Low,
        Medium,
        High,
        Critical
    }
    
    /**
     * Detect anomalies in a single metric
     */
    fun detectAnomalies(
        metric: PerformanceMetrics,
        baseline: PerformanceMetrics? = null
    ): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Latency spike detection
        if (metric.latency > 200) {
            val severity = when {
                metric.latency > 1000 -> AnomalySeverity.Critical
                metric.latency > 500 -> AnomalySeverity.High
                metric.latency > 300 -> AnomalySeverity.Medium
                else -> AnomalySeverity.Low
            }
            anomalies.add(
                Anomaly(
                    type = AnomalyType.LatencySpike,
                    severity = severity,
                    timestamp = metric.timestamp,
                    value = metric.latency,
                    threshold = 200,
                    description = "Latency spike detected: ${metric.latency}ms (threshold: 200ms)"
                )
            )
        }
        
        // Throughput drop detection
        if (baseline != null && metric.downloadSpeed > 0 && baseline.downloadSpeed > 0) {
            val dropPercentage = ((baseline.downloadSpeed - metric.downloadSpeed).toFloat() / baseline.downloadSpeed) * 100
            if (dropPercentage > 50) {
                val severity = when {
                    dropPercentage > 80 -> AnomalySeverity.Critical
                    dropPercentage > 65 -> AnomalySeverity.High
                    else -> AnomalySeverity.Medium
                }
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.ThroughputDrop,
                        severity = severity,
                        timestamp = metric.timestamp,
                        value = metric.downloadSpeed,
                        threshold = baseline.downloadSpeed,
                        description = "Throughput dropped ${String.format("%.1f", dropPercentage)}%: ${metric.downloadSpeed / 1024} KB/s (baseline: ${baseline.downloadSpeed / 1024} KB/s)"
                    )
                )
            }
        } else if (metric.downloadSpeed > 0 && metric.downloadSpeed < 100_000) {
            // Low throughput without baseline
            anomalies.add(
                Anomaly(
                    type = AnomalyType.ThroughputDrop,
                    severity = AnomalySeverity.Medium,
                    timestamp = metric.timestamp,
                    value = metric.downloadSpeed,
                    threshold = 100_000L,
                    description = "Low throughput detected: ${metric.downloadSpeed / 1024} KB/s (threshold: 100 KB/s)"
                )
            )
        }
        
        // Packet loss detection
        if (metric.packetLoss > 5f) {
            val severity = when {
                metric.packetLoss > 20f -> AnomalySeverity.Critical
                metric.packetLoss > 10f -> AnomalySeverity.High
                metric.packetLoss > 7f -> AnomalySeverity.Medium
                else -> AnomalySeverity.Low
            }
            anomalies.add(
                Anomaly(
                    type = AnomalyType.PacketLoss,
                    severity = severity,
                    timestamp = metric.timestamp,
                    value = metric.packetLoss,
                    threshold = 5f,
                    description = "High packet loss: ${String.format("%.2f", metric.packetLoss)}% (threshold: 5%)"
                )
            )
        }
        
        // CPU overload detection
        if (metric.cpuUsage > 80f) {
            val severity = when {
                metric.cpuUsage > 95f -> AnomalySeverity.Critical
                metric.cpuUsage > 90f -> AnomalySeverity.High
                else -> AnomalySeverity.Medium
            }
            anomalies.add(
                Anomaly(
                    type = AnomalyType.CPUOverload,
                    severity = severity,
                    timestamp = metric.timestamp,
                    value = metric.cpuUsage,
                    threshold = 80f,
                    description = "High CPU usage: ${String.format("%.1f", metric.cpuUsage)}% (threshold: 80%)"
                )
            )
        }
        
        // Memory leak detection (requires history)
        if (metric.memoryUsage > 300 * 1024 * 1024) { // 300 MB
            anomalies.add(
                Anomaly(
                    type = AnomalyType.MemoryLeak,
                    severity = AnomalySeverity.Medium,
                    timestamp = metric.timestamp,
                    value = metric.memoryUsage,
                    threshold = 300 * 1024 * 1024L,
                    description = "High memory usage: ${metric.memoryUsage / 1024 / 1024} MB (threshold: 300 MB)"
                )
            )
        }
        
        return anomalies
    }
    
    /**
     * Detect anomalies in a sequence of metrics
     */
    fun detectAnomaliesInSequence(metrics: List<PerformanceMetrics>): List<Anomaly> {
        if (metrics.isEmpty()) return emptyList()
        
        val anomalies = mutableListOf<Anomaly>()
        val baseline = metrics.first()
        
        // Calculate moving average for baseline
        val windowSize = minOf(10, metrics.size)
        val recentMetrics = metrics.takeLast(windowSize)
        val avgLatency = recentMetrics.map { it.latency }.average()
        val avgDownload = recentMetrics.map { it.downloadSpeed.toDouble() }.average()
        
        val dynamicBaseline = PerformanceMetrics(
            latency = avgLatency.toInt(),
            downloadSpeed = avgDownload.toLong(),
            packetLoss = recentMetrics.map { it.packetLoss }.average().toFloat(),
            cpuUsage = recentMetrics.map { it.cpuUsage }.average().toFloat(),
            memoryUsage = recentMetrics.map { it.memoryUsage }.average().toLong(),
            timestamp = metrics.last().timestamp
        )
        
        // Check latest metric against baseline
        anomalies.addAll(detectAnomalies(metrics.last(), dynamicBaseline))
        
        // Check for trends
        if (metrics.size >= 5) {
            val recent = metrics.takeLast(5)
            val latencyTrend = recent.map { it.latency }
            val increasingLatency = latencyTrend.zipWithNext().all { (a, b) -> b >= a }
            
            if (increasingLatency && latencyTrend.last() > avgLatency * 1.5) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.LatencySpike,
                        severity = AnomalySeverity.Medium,
                        timestamp = metrics.last().timestamp,
                        value = latencyTrend.last(),
                        threshold = avgLatency,
                        description = "Latency trend increasing: ${latencyTrend.last()}ms (avg: ${avgLatency.toInt()}ms)"
                    )
                )
            }
        }
        
        return anomalies
    }
}


