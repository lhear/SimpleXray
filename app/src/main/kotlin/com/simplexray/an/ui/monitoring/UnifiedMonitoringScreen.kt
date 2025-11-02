package com.simplexray.an.ui.monitoring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.MetricsHistory
import com.simplexray.an.performance.monitor.Bottleneck
import com.simplexray.an.protocol.visualization.*
import com.simplexray.an.ui.performance.MonitoringDashboard
import com.simplexray.an.ui.visualization.NetworkTopologyView
import com.simplexray.an.ui.visualization.TimeSeriesChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh

/**
 * Unified monitoring screen combining performance monitoring and network visualization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMonitoringScreen(
    currentMetrics: PerformanceMetrics,
    metricsHistory: MetricsHistory,
    bottlenecks: List<Bottleneck>,
    topology: NetworkTopology,
    latencyHistory: List<TimeSeriesData>,
    bandwidthHistory: List<TimeSeriesData>,
    onRefreshTopology: () -> Unit = {},
    onRunSpeedTest: () -> Unit = {},
    onExportData: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unified Network Monitor") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshTopology) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Topology"
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
            // Network Topology Section
            item {
                Text(
                    "Network Topology",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        NetworkTopologyView(
                            topology = topology,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Real-time traffic visualization from Xray core",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Real-time Charts Section
            item {
                Text(
                    "Real-time Metrics",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Bandwidth Chart
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Network Speed",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        TimeSeriesChart(
                            data = bandwidthHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Download: ${formatSpeed(currentMetrics.downloadSpeed)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            )
                            Text(
                                "Upload: ${formatSpeed(currentMetrics.uploadSpeed)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.ui.graphics.Color(0xFFFF9800)
                            )
                        }
                    }
                }
            }

            // Latency Chart
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Latency & Jitter",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        TimeSeriesChart(
                            data = latencyHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Latency: ${currentMetrics.latency} ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Jitter: ${currentMetrics.jitter} ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Performance Dashboard Section
            item {
                Text(
                    "Performance Dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Quick Stats Card
            item {
                QuickStatsCard(metrics = currentMetrics)
            }

            // Connection Quality
            item {
                ConnectionQualityCard(metrics = currentMetrics)
            }

            // Resource Usage
            item {
                ResourceUsageCard(metrics = currentMetrics)
            }

            // Bottlenecks
            if (bottlenecks.isNotEmpty()) {
                item {
                    Text(
                        "Performance Issues",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                bottlenecks.forEach { bottleneck ->
                    item {
                        BottleneckCard(bottleneck = bottleneck)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStatsCard(metrics: PerformanceMetrics) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Quick Stats", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn("Download", formatSpeed(metrics.downloadSpeed))
                StatColumn("Upload", formatSpeed(metrics.uploadSpeed))
                StatColumn("Latency", "${metrics.latency} ms")
                StatColumn("Quality", "${metrics.calculateQualityScore().toInt()}%")
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

            // Quality Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
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

            // Quality Score
            Text("Quality Score", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
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

            // Stability
            Text("Connection Stability", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
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
    val memoryPercent = ((metrics.memoryUsage.toFloat() / maxMemory) * 100).coerceIn(0f, 100f)

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Resource Usage", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // CPU Usage
            Text("CPU Usage", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
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

            // Memory Usage
            Text("Memory Usage", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
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

private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
    }
}
