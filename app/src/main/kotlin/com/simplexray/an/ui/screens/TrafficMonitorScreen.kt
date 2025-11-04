package com.simplexray.an.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.simplexray.an.ui.viewmodel.TrafficViewModel
import kotlinx.coroutines.launch

/**
 * Real-time traffic monitoring screen with live charts and speed metrics.
 * Displays detailed network performance analytics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficMonitorScreen(
    viewModel: TrafficViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Create chart model producer for Vico
    val chartModelProducer = remember { CartesianChartModelProducer() }

    // Update chart when history changes
    LaunchedEffect(uiState.history.snapshots) {
        if (uiState.history.snapshots.isNotEmpty()) {
            // TODO: Cap the data window before mapping to avoid reallocating entire histories on every recomposition.
            val rxValues = uiState.history.snapshots.map { it.rxRateMbps.toDouble() }
            val txValues = uiState.history.snapshots.map { it.txRateMbps.toDouble() }

            chartModelProducer.runTransaction {
                lineSeries {
                    series(rxValues)
                    series(txValues)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traffic Monitor") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { viewModel.resetSession() }) {
                        Icon(Icons.Default.RestartAlt, "Reset Session")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status banner
            ConnectionStatusBanner(isConnected = uiState.isConnected)

            // Burst warning banner
            AnimatedVisibility(
                visible = uiState.isBurst && uiState.burstMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                BurstWarningBanner(message = uiState.burstMessage ?: "")
            }

            // Throttle warning banner
            AnimatedVisibility(
                visible = uiState.isThrottled && uiState.throttleMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ThrottleWarningBanner(message = uiState.throttleMessage ?: "")
            }

            // Current speed gauges
            CurrentSpeedCard(
                downloadSpeed = uiState.currentSnapshot.formatDownloadSpeed(),
                uploadSpeed = uiState.currentSnapshot.formatUploadSpeed(),
                latency = uiState.currentSnapshot.latencyMs
            )

            // Real-time chart
            if (uiState.history.snapshots.isNotEmpty()) {
                TrafficChartCard(chartModelProducer = chartModelProducer)
            }

            // Today's statistics
            TodayStatsCard(
                totalUsage = uiState.formatTodayTotal(),
                downloadUsage = uiState.formatTodayDownload(),
                uploadUsage = uiState.formatTodayUpload(),
                avgLatency = uiState.todayAvgLatency,
                peakDownload = uiState.todaySpeedStats.maxRx,
                peakUpload = uiState.todaySpeedStats.maxTx
            )

            // Session statistics
            SessionStatsCard(
                totalBytes = uiState.currentSnapshot.totalBytes,
                timestamp = uiState.currentSnapshot.timestamp
            )
        }
    }
}

@Composable
private fun ConnectionStatusBanner(isConnected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                style = MaterialTheme.typography.labelLarge,
                color = if (isConnected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}

@Composable
private fun BurstWarningBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun ThrottleWarningBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NetworkCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun CurrentSpeedCard(
    downloadSpeed: String,
    uploadSpeed: String,
    latency: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Current Speed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedGauge(
                    label = "Download",
                    speed = downloadSpeed,
                    icon = Icons.Default.ArrowDownward,
                    color = MaterialTheme.colorScheme.primary
                )

                SpeedGauge(
                    label = "Upload",
                    speed = uploadSpeed,
                    icon = Icons.Default.ArrowUpward,
                    color = MaterialTheme.colorScheme.secondary
                )

                LatencyGauge(
                    latency = latency,
                    icon = Icons.Default.Speed
                )
            }
        }
    }
}

@Composable
private fun SpeedGauge(
    label: String,
    speed: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = speed,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LatencyGauge(
    latency: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val latencyColor = when {
        latency < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        latency < 50 -> Color(0xFF4CAF50)
        latency < 100 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = latencyColor,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = if (latency >= 0) "${latency}ms" else "---",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = latencyColor
        )
        Text(
            text = "Latency",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrafficChartCard(chartModelProducer: CartesianChartModelProducer) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Real-time Traffic",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendItem(color = MaterialTheme.colorScheme.primary, label = "Download")
                LegendItem(color = MaterialTheme.colorScheme.secondary, label = "Upload")
            }

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    rememberLineCartesianLayer(),
                    rememberLineCartesianLayer(),
                    rememberLineCartesianLayer(),
                    rememberLineCartesianLayer(),
                    rememberLineCartesianLayer(),
                    startAxis = rememberStartAxis(label = rememberAxisLabelComponent()),
                    bottomAxis = rememberBottomAxis(label = rememberAxisLabelComponent())
                ),
                modelProducer = chartModelProducer,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TodayStatsCard(
    totalUsage: String,
    downloadUsage: String,
    uploadUsage: String,
    avgLatency: Long,
    peakDownload: Float,
    peakUpload: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Today's Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "Total Usage", value = totalUsage)
                StatItem(label = "Download", value = downloadUsage)
                StatItem(label = "Upload", value = uploadUsage)
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "Peak Download", value = "%.2f Mbps".format(peakDownload))
                StatItem(label = "Peak Upload", value = "%.2f Mbps".format(peakUpload))
                StatItem(label = "Avg Latency", value = if (avgLatency >= 0) "${avgLatency}ms" else "---")
            }
        }
    }
}

@Composable
private fun SessionStatsCard(totalBytes: Long, timestamp: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Session Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val totalFormatted = when {
                totalBytes >= 1_073_741_824 -> "%.2f GB".format(totalBytes / 1_073_741_824.0)
                totalBytes >= 1_048_576 -> "%.2f MB".format(totalBytes / 1_048_576.0)
                totalBytes >= 1024 -> "%.2f KB".format(totalBytes / 1024.0)
                else -> "$totalBytes B"
            }

            StatItem(label = "Total Transferred", value = totalFormatted)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
