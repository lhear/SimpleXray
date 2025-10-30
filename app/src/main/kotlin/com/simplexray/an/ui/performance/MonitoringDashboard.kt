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

            MetricRow("Download Speed", "${metrics.downloadSpeed / 1024} KB/s")
            MetricRow("Upload Speed", "${metrics.uploadSpeed / 1024} KB/s")
            MetricRow("Latency", "${metrics.latency} ms")
            MetricRow("Jitter", "${metrics.jitter} ms")
            MetricRow("Packet Loss", "${metrics.packetLoss}%")
            MetricRow("Connections", "${metrics.connectionCount}")
        }
    }
}

@Composable
fun ConnectionQualityCard(metrics: PerformanceMetrics) {
    val quality = metrics.getConnectionQuality()

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection Quality", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Status")
                Text(quality.displayName)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Score")
                Text("${metrics.calculateQualityScore().toInt()}/100")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Stability")
                Text("${metrics.connectionStability.toInt()}%")
            }
        }
    }
}

@Composable
fun ResourceUsageCard(metrics: PerformanceMetrics) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Resource Usage", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            MetricRow("CPU Usage", "${metrics.cpuUsage.toInt()}%")
            MetricRow("Memory", "${metrics.memoryUsage / 1024 / 1024} MB")
            MetricRow("Native Memory", "${metrics.nativeMemoryUsage / 1024 / 1024} MB")
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
