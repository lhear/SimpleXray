package com.simplexray.an.ui.streaming

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.protocol.streaming.StreamingOptimizer.*
import com.simplexray.an.viewmodel.StreamingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingScreen(
    onBackClick: () -> Unit = {},
    viewModel: StreamingViewModel = viewModel()
) {
    val selectedPlatform by viewModel.selectedPlatform.collectAsState()
    val platformConfigs by viewModel.platformConfigs.collectAsState()
    val streamingStats by viewModel.streamingStats.collectAsState()
    val isOptimizationEnabled by viewModel.isOptimizationEnabled.collectAsState()
    val currentQuality by viewModel.currentQuality.collectAsState()
    val bufferHealth by viewModel.bufferHealth.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showPlatformDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Streaming Optimization") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleOptimization() }) {
                        Icon(
                            imageVector = if (isOptimizationEnabled) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (isOptimizationEnabled) "Optimization On" else "Optimization Off",
                            tint = if (isOptimizationEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Platforms") },
                    icon = { Icon(Icons.Default.List, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Statistics") },
                    icon = { Icon(Icons.Default.Info, null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, null) }
                )
            }

            when (selectedTab) {
                0 -> PlatformsTab(
                    platforms = viewModel.getAllPlatforms(),
                    selectedPlatform = selectedPlatform,
                    onPlatformClick = {
                        viewModel.selectPlatform(it)
                        showPlatformDialog = true
                    }
                )
                1 -> StatisticsTab(
                    streamingStats = streamingStats,
                    bufferHealth = bufferHealth,
                    currentQuality = currentQuality
                )
                2 -> SettingsTab(
                    platformConfigs = platformConfigs,
                    onConfigUpdate = { platform, config ->
                        viewModel.updatePlatformConfig(platform, config)
                    },
                    onResetConfig = { platform ->
                        viewModel.resetPlatformConfig(platform)
                    }
                )
            }
        }

        // Platform Detail Dialog
        if (showPlatformDialog && selectedPlatform != null) {
            PlatformDetailDialog(
                platform = selectedPlatform!!,
                config = platformConfigs[selectedPlatform]!!,
                stats = streamingStats[selectedPlatform],
                onDismiss = { showPlatformDialog = false },
                onConfigChange = { config ->
                    viewModel.updatePlatformConfig(selectedPlatform!!, config)
                },
                onQualityChange = { quality ->
                    viewModel.setQuality(quality)
                }
            )
        }
    }
}

@Composable
private fun PlatformsTab(
    platforms: List<StreamingPlatform>,
    selectedPlatform: StreamingPlatform?,
    onPlatformClick: (StreamingPlatform) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Supported Platforms",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(platforms) { platform ->
            PlatformCard(
                platform = platform,
                isSelected = platform == selectedPlatform,
                onClick = { onPlatformClick(platform) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformCard(
    platform: StreamingPlatform,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    platform.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Buffer: ${platform.config.bufferAhead}s, Max: ${platform.config.maxBitrate / 1_000_000}Mbps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (platform.config.lowLatencyMode) {
                    Text(
                        "Low Latency Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun StatisticsTab(
    streamingStats: Map<StreamingPlatform, StreamingStats>,
    bufferHealth: BufferHealth,
    currentQuality: StreamQuality
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Overall Stats Card
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Overall Statistics",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StatRow("Current Quality", currentQuality.displayName)
                    StatRow("Buffer Health", bufferHealth.name)

                    val totalSegments = streamingStats.values.sumOf { it.segmentsDownloaded }
                    val totalFailed = streamingStats.values.sumOf { it.segmentsFailed }
                    val successRate = if (totalSegments + totalFailed > 0) {
                        (totalSegments.toFloat() / (totalSegments + totalFailed)) * 100
                    } else 100f

                    StatRow("Success Rate", "${successRate.toInt()}%")
                    StatRow("Total Rebuffers", streamingStats.values.sumOf { it.rebufferCount }.toString())
                }
            }
        }

        item {
            Text(
                "Platform Statistics",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(streamingStats.entries.toList()) { (platform, stats) ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        platform.displayName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    StatRow("Quality", stats.currentQuality.displayName)
                    StatRow("Buffer Level", "${stats.bufferLevel}s")
                    StatRow("Avg Bitrate", "${stats.averageBitrate / 1_000_000}Mbps")
                    StatRow("Success Rate", "${stats.successRate.toInt()}%")
                    StatRow("Rebuffers", stats.rebufferCount.toString())
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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

@Composable
private fun SettingsTab(
    platformConfigs: Map<StreamingPlatform, StreamingConfig>,
    onConfigUpdate: (StreamingPlatform, StreamingConfig) -> Unit,
    onResetConfig: (StreamingPlatform) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Platform Configurations",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(platformConfigs.entries.toList()) { (platform, config) ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            platform.displayName,
                            style = MaterialTheme.typography.titleSmall
                        )
                        TextButton(onClick = { onResetConfig(platform) }) {
                            Text("Reset")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ConfigRow("Adaptive Bitrate", if (config.adaptiveBitrate) "Enabled" else "Disabled")
                    ConfigRow("Preferred Quality", config.preferredQuality.displayName)
                    ConfigRow("Buffer Ahead", "${config.bufferAhead}s")
                    ConfigRow("Initial Buffer", "${config.initialBuffer}s")
                    ConfigRow("Max Bitrate", "${config.maxBitrate / 1_000_000}Mbps")
                    ConfigRow("Prefetch", if (config.enablePrefetch) "Enabled" else "Disabled")
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PlatformDetailDialog(
    platform: StreamingPlatform,
    config: StreamingConfig,
    stats: StreamingStats?,
    onDismiss: () -> Unit,
    onConfigChange: (StreamingConfig) -> Unit,
    onQualityChange: (StreamQuality) -> Unit
) {
    var selectedQuality by remember { mutableStateOf(config.preferredQuality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(platform.displayName) },
        text = {
            Column {
                Text(
                    "Quality Selection",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                StreamQuality.entries.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = quality == selectedQuality,
                            onClick = {
                                selectedQuality = quality
                                onQualityChange(quality)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(quality.displayName)
                    }
                }

                if (stats != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Current Statistics",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    StatRow("Buffer Level", "${stats.bufferLevel}s")
                    StatRow("Avg Bitrate", "${stats.averageBitrate / 1_000_000}Mbps")
                    StatRow("Success Rate", "${stats.successRate.toInt()}%")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
