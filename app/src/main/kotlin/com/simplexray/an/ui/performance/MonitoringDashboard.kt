package com.simplexray.an.ui.performance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.MetricsHistory
import com.simplexray.an.performance.monitor.Bottleneck
import com.simplexray.an.performance.statistics.PerformanceStatistics
import com.simplexray.an.performance.statistics.PerformanceScore
import com.simplexray.an.ui.performance.components.MetricsChart
import com.simplexray.an.ui.performance.components.MultiLineChart
import com.simplexray.an.ui.performance.components.ChartDataset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Speed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringDashboard(
    currentMetrics: PerformanceMetrics,
    history: MetricsHistory,
    bottlenecks: List<Bottleneck>,
    onRunSpeedTest: () -> Unit,
    onExportData: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val statistics = remember { PerformanceStatistics() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Monitor") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Performance Score
            item {
                PerformanceScoreCard(
                    score = statistics.calculatePerformanceScore(currentMetrics)
                )
            }

            // Real-time charts
            item {
                RealTimeChartsCard(history = history)
            }

            // Real-time metrics
            item {
                RealTimeMetricsCard(metrics = currentMetrics)
            }

            // Detailed statistics
            if (history.metrics.isNotEmpty()) {
                item {
                    DetailedStatisticsCard(
                        statistics = statistics,
                        history = history
                    )
                }
            }

            // Connection quality
            item {
                ConnectionQualityCard(metrics = currentMetrics)
            }

            // Resource usage
            item {
                ResourceUsageCard(metrics = currentMetrics)
            }

            // Bottlenecks
            if (bottlenecks.isNotEmpty()) {
                item {
                    Text("Performance Issues", style = MaterialTheme.typography.titleMedium)
                }
                bottlenecks.forEach { bottleneck ->
                    item {
                        BottleneckCard(bottleneck = bottleneck)
                    }
                }
            }

            // Action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRunSpeedTest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Speed Test")
                    }

                    OutlinedButton(
                        onClick = onExportData,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export")
                    }
                }
            }
        }
    }
}

@Composable
fun RealTimeMetricsCard(metrics: PerformanceMetrics) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Real-Time Metrics", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // Network Speed
            Text("Network Speed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            MetricRow("Download", formatSpeed(metrics.downloadSpeed))
            MetricRow("Upload", formatSpeed(metrics.uploadSpeed))
            MetricRow("Total Downloaded", formatBytes(metrics.totalDownload))
            MetricRow("Total Uploaded", formatBytes(metrics.totalUpload))

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Connection Quality
            Text("Connection Quality", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            MetricRow("Latency", "${metrics.latency} ms")
            MetricRow("Jitter", "${metrics.jitter} ms")
            MetricRow("Packet Loss", String.format("%.2f%%", metrics.packetLoss))
            MetricRow("Stability", "${metrics.connectionStability.toInt()}%")

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Connections
            Text("Connections", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            MetricRow("Active", "${metrics.activeConnectionCount}")
            MetricRow("Total", "${metrics.connectionCount}")
        }
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

@Composable
fun ConnectionQualityCard(metrics: PerformanceMetrics) {
    val quality = metrics.getConnectionQuality()
    val qualityScore = metrics.calculateQualityScore()
    val qualityColor = androidx.compose.ui.graphics.Color(quality.color.toULong())

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection Quality", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // Quality Status with colored badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Status", style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = qualityColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        quality.displayName,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = qualityColor,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quality Score with progress bar
            Text("Quality Score", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { (qualityScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = qualityColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${qualityScore.toInt()}/100", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stability with progress bar
            Text("Connection Stability", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { (metrics.connectionStability / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = when {
                        metrics.connectionStability > 80 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        metrics.connectionStability > 60 -> androidx.compose.ui.graphics.Color(0xFFFFC107)
                        else -> androidx.compose.ui.graphics.Color(0xFFF44336)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${metrics.connectionStability.toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ResourceUsageCard(metrics: PerformanceMetrics) {
    val runtime = Runtime.getRuntime()
    val maxMemory = runtime.maxMemory()
    val totalMemory = runtime.totalMemory()
    val memoryPercent = ((metrics.memoryUsage.toFloat() / maxMemory) * 100).coerceIn(0f, 100f)

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Resource Usage", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // CPU Usage with progress
            Text("CPU Usage", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { (metrics.cpuUsage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = when {
                        metrics.cpuUsage > 80 -> MaterialTheme.colorScheme.error
                        metrics.cpuUsage > 50 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${metrics.cpuUsage.toInt()}%", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Memory Usage with progress
            Text("Memory Usage", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { (memoryPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = when {
                        memoryPercent > 80 -> MaterialTheme.colorScheme.error
                        memoryPercent > 60 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${metrics.memoryUsage / 1024 / 1024} MB", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Native Memory
            MetricRow("Native Memory", "${metrics.nativeMemoryUsage / 1024 / 1024} MB")
            MetricRow("Max Memory", "${maxMemory / 1024 / 1024} MB")
            MetricRow("Total Memory", "${totalMemory / 1024 / 1024} MB")
        }
    }
}

@Composable
fun BottleneckCard(bottleneck: Bottleneck) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (bottleneck.severity) {
                com.simplexray.an.performance.monitor.BottleneckSeverity.Critical ->
                    MaterialTheme.colorScheme.errorContainer
                com.simplexray.an.performance.monitor.BottleneckSeverity.High ->
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                bottleneck.description,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                bottleneck.recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PerformanceScoreCard(score: PerformanceScore) {
    val gradeColor = when (score.grade) {
        "A+", "A" -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
        "B" -> androidx.compose.ui.graphics.Color(0xFF8BC34A) // Light Green
        "C" -> androidx.compose.ui.graphics.Color(0xFFFFC107) // Amber
        "D" -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
        else -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
    }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Performance Score", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // Overall score with grade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${score.overall.toInt()}/100",
                        style = MaterialTheme.typography.displayMedium,
                        color = gradeColor
                    )
                    Text(
                        "Overall Score",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = gradeColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        score.grade,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.displaySmall,
                        color = gradeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Score breakdown
            Text("Score Breakdown", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            ScoreBreakdownRow("Latency", score.latencyScore, 40f)
            ScoreBreakdownRow("Bandwidth", score.bandwidthScore, 30f)
            ScoreBreakdownRow("Stability", score.stabilityScore, 20f)
            ScoreBreakdownRow("Resources", score.resourceScore, 10f)
        }
    }
}

@Composable
fun ScoreBreakdownRow(label: String, score: Float, maxScore: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        LinearProgressIndicator(
            progress = { (score / maxScore).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(2f)
                .height(8.dp),
            color = when {
                score / maxScore > 0.8f -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                score / maxScore > 0.6f -> androidx.compose.ui.graphics.Color(0xFFFFC107)
                else -> androidx.compose.ui.graphics.Color(0xFFF44336)
            }
        )

        Text(
            "${score.toInt()}/${maxScore.toInt()}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(60.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun RealTimeChartsCard(history: MetricsHistory) {
    if (history.metrics.isEmpty()) return

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Real-Time Monitoring", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Bandwidth chart
            val downloadSpeeds = history.metrics.map { (it.downloadSpeed / (1024f * 1024f)) } // MB/s
            val uploadSpeeds = history.metrics.map { (it.uploadSpeed / (1024f * 1024f)) } // MB/s

            MultiLineChart(
                datasets = listOf(
                    ChartDataset(
                        label = "Download",
                        data = downloadSpeeds,
                        color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    ),
                    ChartDataset(
                        label = "Upload",
                        data = uploadSpeeds,
                        color = androidx.compose.ui.graphics.Color(0xFF2196F3)
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Network Speed (MB/s)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // CPU usage chart
            val cpuUsages = history.metrics.map { it.cpuUsage }

            MetricsChart(
                data = cpuUsages,
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color(0xFFFF9800),
                maxValue = 100f,
                label = "CPU Usage (%)"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Memory usage chart
            val memoryUsages = history.metrics.map { (it.memoryUsage / (1024f * 1024f)) } // MB

            MetricsChart(
                data = memoryUsages,
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color(0xFF9C27B0),
                label = "Memory Usage (MB)"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Latency chart
            val latencies = history.metrics.map { it.latency.toFloat() }

            MetricsChart(
                data = latencies,
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color(0xFFF44336),
                label = "Latency (ms)"
            )
        }
    }
}

@Composable
fun DetailedStatisticsCard(
    statistics: PerformanceStatistics,
    history: MetricsHistory
) {
    val report = statistics.generateReport(history.metrics)

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Detailed Statistics", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // Download stats
            Text("Download Speed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            StatRow("Min", formatSpeed((report.downloadStats.min * 1024 * 1024).toLong()))
            StatRow("Max", formatSpeed((report.downloadStats.max * 1024 * 1024).toLong()))
            StatRow("Mean", formatSpeed((report.downloadStats.mean * 1024 * 1024).toLong()))
            StatRow("Median", formatSpeed((report.downloadStats.median * 1024 * 1024).toLong()))
            StatRow("95th %ile", formatSpeed((report.downloadStats.p95 * 1024 * 1024).toLong()))

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Upload stats
            Text("Upload Speed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            StatRow("Min", formatSpeed((report.uploadStats.min * 1024 * 1024).toLong()))
            StatRow("Max", formatSpeed((report.uploadStats.max * 1024 * 1024).toLong()))
            StatRow("Mean", formatSpeed((report.uploadStats.mean * 1024 * 1024).toLong()))
            StatRow("Median", formatSpeed((report.uploadStats.median * 1024 * 1024).toLong()))

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Latency stats
            Text("Latency", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            StatRow("Min", "${report.latencyStats.min.toInt()} ms")
            StatRow("Max", "${report.latencyStats.max.toInt()} ms")
            StatRow("Mean", "${report.latencyStats.mean.toInt()} ms")
            StatRow("Median", "${report.latencyStats.median.toInt()} ms")
            StatRow("95th %ile", "${report.latencyStats.p95.toInt()} ms")
            StatRow("99th %ile", "${report.latencyStats.p99.toInt()} ms")

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Overall stats
            Text("Session Summary", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            StatRow("Data Points", "${report.totalDataPoints}")
            StatRow("Average Quality", String.format("%.1f/100", report.averageQuality))
            StatRow("Uptime", formatDuration(report.uptime))
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
