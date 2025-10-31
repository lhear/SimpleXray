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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringDashboard(
    currentMetrics: PerformanceMetrics,
    history: MetricsHistory,
    bottlenecks: List<Bottleneck>,
    onRunSpeedTest: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Performance Monitor") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Real-time metrics
            item {
                RealTimeMetricsCard(metrics = currentMetrics)
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

            // Speed test button
            item {
                Button(
                    onClick = onRunSpeedTest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run Speed Test")
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
