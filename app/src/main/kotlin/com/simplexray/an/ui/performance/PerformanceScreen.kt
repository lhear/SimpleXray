package com.simplexray.an.ui.performance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.ConnectionQuality
import com.simplexray.an.performance.BatteryImpactMonitor
import com.simplexray.an.performance.PerformanceBenchmark
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    currentProfile: PerformanceProfile,
    currentMetrics: PerformanceMetrics,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onAutoTuneToggled: (Boolean) -> Unit,
    autoTuneEnabled: Boolean = false,
    onBackClick: () -> Unit = {},
    onShowMonitoring: () -> Unit = {},
    onAdvancedSettingsClick: () -> Unit = {},
    batteryData: BatteryImpactMonitor.BatteryImpactData? = null,
    benchmarkResults: List<PerformanceBenchmark.BenchmarkResult> = emptyList(),
    isRunningBenchmark: Boolean = false,
    onRunBenchmark: () -> Unit = {},
    onRunComprehensiveBenchmark: () -> Unit = {}
) {
    // Sync selectedProfile with currentProfile from ViewModel
    var selectedProfile by remember { mutableStateOf(currentProfile) }
    
    // Update selectedProfile when currentProfile changes (e.g., from auto-tune)
    LaunchedEffect(currentProfile) {
        selectedProfile = currentProfile
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Settings") },
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
            // Current metrics card
            item {
                MetricsCard(metrics = currentMetrics)
            }
            
            // Battery impact card
            batteryData?.let { data ->
                item {
                    BatteryImpactCard(batteryData = data)
                }
            }
            
            // Benchmark card
            item {
                BenchmarkCard(
                    results = benchmarkResults,
                    isRunning = isRunningBenchmark,
                    onRunBenchmark = onRunBenchmark,
                    onRunComprehensiveBenchmark = onRunComprehensiveBenchmark
                )
            }

            // Auto-tune toggle
            item {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Tune", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Automatically adjust settings based on connection quality",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoTuneEnabled,
                            onCheckedChange = onAutoTuneToggled
                        )
                    }
                }
            }

            // Advanced Monitoring button
            item {
                Button(
                    onClick = onShowMonitoring,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Advanced Performance Monitoring")
                }
            }
            
            // Advanced Settings button
            item {
                OutlinedButton(
                    onClick = onAdvancedSettingsClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Advanced Settings")
                }
            }

            // Performance profiles
            item {
                Text("Performance Profiles", style = MaterialTheme.typography.titleLarge)
            }

            items(PerformanceProfile.getAll()) { profile ->
                ProfileCard(
                    profile = profile,
                    isSelected = profile == selectedProfile,
                    onClick = {
                        try {
                            selectedProfile = profile
                            onProfileSelected(profile)
                        } catch (e: Exception) {
                            android.util.Log.e("PerformanceScreen", "Error selecting profile: ${profile.name}", e)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MetricsCard(metrics: PerformanceMetrics) {
    // Format metrics safely
    val latencyText = try { "${metrics.latency} ms" } catch (e: Exception) { "N/A" }
    val downloadText = try { "${metrics.downloadSpeed / 1024} KB/s" } catch (e: Exception) { "N/A" }
    val uploadText = try { "${metrics.uploadSpeed / 1024} KB/s" } catch (e: Exception) { "N/A" }
    val qualityText = try { metrics.getConnectionQuality().displayName } catch (e: Exception) { "Unknown" }
    val memoryText = try { "${metrics.memoryUsage / 1024 / 1024} MB" } catch (e: Exception) { "N/A" }
    
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current Performance", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            MetricRow("Latency", latencyText)
            MetricRow("Download", downloadText)
            MetricRow("Upload", uploadText)
            MetricRow("Quality", qualityText)
            MetricRow("Memory", memoryText)
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun BatteryImpactCard(batteryData: BatteryImpactMonitor.BatteryImpactData) {
    val warningLevel = when {
        batteryData.currentBatteryLevel < 10 -> BatteryImpactMonitor.WarningLevel.Critical
        batteryData.currentBatteryLevel < 20 -> BatteryImpactMonitor.WarningLevel.High
        batteryData.estimatedDrainPerHour > 15f -> BatteryImpactMonitor.WarningLevel.Medium
        batteryData.batteryTemperature > 40f -> BatteryImpactMonitor.WarningLevel.Medium
        else -> BatteryImpactMonitor.WarningLevel.None
    }
    
    val cardColor = when (warningLevel) {
        BatteryImpactMonitor.WarningLevel.Critical -> MaterialTheme.colorScheme.errorContainer
        BatteryImpactMonitor.WarningLevel.High -> MaterialTheme.colorScheme.tertiaryContainer
        BatteryImpactMonitor.WarningLevel.Medium -> MaterialTheme.colorScheme.secondaryContainer
        BatteryImpactMonitor.WarningLevel.None -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val iconColor = when (warningLevel) {
        BatteryImpactMonitor.WarningLevel.Critical -> MaterialTheme.colorScheme.error
        BatteryImpactMonitor.WarningLevel.High -> MaterialTheme.colorScheme.tertiary
        BatteryImpactMonitor.WarningLevel.Medium -> MaterialTheme.colorScheme.secondary
        BatteryImpactMonitor.WarningLevel.None -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val batteryIcon = when {
        batteryData.isCharging -> Icons.Default.BatteryFull
        batteryData.currentBatteryLevel >= 90 -> Icons.Default.Battery6Bar
        batteryData.currentBatteryLevel >= 75 -> Icons.Default.Battery5Bar
        batteryData.currentBatteryLevel >= 60 -> Icons.Default.Battery4Bar
        batteryData.currentBatteryLevel >= 40 -> Icons.Default.Battery3Bar
        batteryData.currentBatteryLevel >= 20 -> Icons.Default.Battery2Bar
        batteryData.currentBatteryLevel >= 10 -> Icons.Default.Battery1Bar
        else -> Icons.Default.Battery0Bar
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = batteryIcon,
                        contentDescription = "Battery",
                        tint = iconColor
                    )
                    Text(
                        "Battery Impact",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (warningLevel != BatteryImpactMonitor.WarningLevel.None) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = iconColor
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "${batteryData.currentBatteryLevel}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Battery Level",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${String.format("%.1f", batteryData.estimatedDrainPerHour)}%/h",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Drain Rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Temp: ${String.format("%.1f", batteryData.batteryTemperature)}°C",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (batteryData.isCharging) {
                    Text(
                        "Charging",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (warningLevel != BatteryImpactMonitor.WarningLevel.None) {
                Text(
                    when (warningLevel) {
                        BatteryImpactMonitor.WarningLevel.Critical -> 
                            "⚠️ Battery critically low. Consider disabling performance mode."
                        BatteryImpactMonitor.WarningLevel.High -> 
                            "⚠️ Battery is low. Performance mode may drain battery faster."
                        BatteryImpactMonitor.WarningLevel.Medium -> 
                            if (batteryData.batteryTemperature > 40f) {
                                "⚠️ Battery temperature is high. Consider reducing optimizations."
                            } else {
                                "⚠️ High battery drain detected."
                            }
                        BatteryImpactMonitor.WarningLevel.None -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = iconColor
                )
            }
        }
    }
}

@Composable
fun BenchmarkCard(
    results: List<PerformanceBenchmark.BenchmarkResult>,
    isRunning: Boolean,
    onRunBenchmark: () -> Unit,
    onRunComprehensiveBenchmark: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Performance Benchmark",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunBenchmark,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRunning) "Running..." else "Run Benchmark")
                }
                
                Button(
                    onClick = onRunComprehensiveBenchmark,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Comprehensive")
                }
            }
            
            if (results.isNotEmpty()) {
                Text(
                    "Results (${results.size} tests)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                results.forEach { result ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                result.testName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Baseline: ${String.format("%.2f", result.baselineMs)} ms → " +
                                "Optimized: ${String.format("%.2f", result.optimizedMs)} ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${String.format("%.1f", result.improvementPercent)}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (result.improvementPercent > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (result.throughputMBps > 0) {
                        Text(
                            "Throughput: ${String.format("%.2f", result.throughputMBps)} MB/s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                
                val avgImprovement = results.map { it.improvementPercent }.average()
                Text(
                    "Average Improvement: ${String.format("%.1f", avgImprovement)}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "No benchmark results yet. Run a benchmark to see performance improvements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProfileCard(
    profile: PerformanceProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            Text(
                profile.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
