package com.simplexray.an.ui.protocol

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
import com.simplexray.an.protocol.optimization.*
import com.simplexray.an.viewmodel.ProtocolOptimizationViewModel
import com.simplexray.an.viewmodel.ProtocolOptimizationViewModel.OptimizationProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolOptimizationScreen(
    onBackClick: () -> Unit = {},
    viewModel: ProtocolOptimizationViewModel = viewModel()
) {
    val config by viewModel.config.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protocol Optimization") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to Defaults"
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
                    text = { Text("Profiles") },
                    icon = { Icon(Icons.Default.Face, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Configuration") },
                    icon = { Icon(Icons.Default.Settings, null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Statistics") },
                    icon = { Icon(Icons.Default.Info, null) }
                )
            }

            when (selectedTab) {
                0 -> ProfilesTab(
                    selectedProfile = selectedProfile,
                    onProfileSelect = { viewModel.applyProfile(it) }
                )
                1 -> ConfigurationTab(
                    config = config,
                    onConfigChange = { viewModel.updateConfig(it) },
                    onToggleHttp3 = { viewModel.toggleHttp3() },
                    onToggleTls13 = { viewModel.toggleTls13() },
                    onToggleBrotli = { viewModel.toggleBrotli() },
                    onToggleGzip = { viewModel.toggleGzip() },
                    onBrotliQualityChange = { viewModel.setBrotliQuality(it) },
                    onGzipLevelChange = { viewModel.setGzipLevel(it) }
                )
                2 -> StatisticsTab(stats = stats)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesTab(
    selectedProfile: OptimizationProfile,
    onProfileSelect: (OptimizationProfile) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Optimization Profiles",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val profiles = listOf(
            OptimizationProfile.GAMING to ProfileInfo(
                "Gaming",
                "Low latency, 0-RTT enabled, minimal compression",
                Icons.Default.PlayArrow
            ),
            OptimizationProfile.STREAMING to ProfileInfo(
                "Streaming",
                "High throughput, compression enabled, server push",
                Icons.Default.Add
            ),
            OptimizationProfile.BATTERY_SAVER to ProfileInfo(
                "Battery Saver",
                "Minimal features, reduced CPU usage, HTTP/2 only",
                Icons.Default.Build
            ),
            OptimizationProfile.BALANCED to ProfileInfo(
                "Balanced",
                "Default configuration, good balance of all features",
                Icons.Default.CheckCircle
            )
        )

        items(profiles) { (profile, info) ->
            Card(
                onClick = { onProfileSelect(profile) },
                colors = CardDefaults.cardColors(
                    containerColor = if (profile == selectedProfile)
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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = info.icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = if (profile == selectedProfile)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                info.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                info.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (profile == selectedProfile) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationTab(
    config: ProtocolConfig,
    onConfigChange: (ProtocolConfig) -> Unit,
    onToggleHttp3: () -> Unit,
    onToggleTls13: () -> Unit,
    onToggleBrotli: () -> Unit,
    onToggleGzip: () -> Unit,
    onBrotliQualityChange: (Int) -> Unit,
    onGzipLevelChange: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HTTP/3 & QUIC Section
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "HTTP/3 & QUIC",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SwitchRow(
                        label = "Enable HTTP/3",
                        checked = config.http3Enabled,
                        onCheckedChange = { onToggleHttp3() }
                    )

                    if (config.http3Enabled) {
                        InfoRow("QUIC Version", config.quicVersion.name)
                        InfoRow("Max Streams", config.quicMaxStreams.toString())
                        InfoRow("Idle Timeout", "${config.quicIdleTimeout}ms")
                    }
                }
            }
        }

        // TLS Section
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "TLS Configuration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SwitchRow(
                        label = "Enable TLS 1.3",
                        checked = config.tls13Enabled,
                        onCheckedChange = { onToggleTls13() }
                    )

                    if (config.tls13Enabled) {
                        SwitchRow(
                            label = "0-RTT (Early Data)",
                            checked = config.tls13EarlyData,
                            onCheckedChange = {
                                onConfigChange(config.copy(tls13EarlyData = !config.tls13EarlyData))
                            }
                        )
                        SwitchRow(
                            label = "Session Tickets",
                            checked = config.tls13SessionTickets,
                            onCheckedChange = {
                                onConfigChange(config.copy(tls13SessionTickets = !config.tls13SessionTickets))
                            }
                        )
                    }

                    Text(
                        "Cipher Suites: ${config.preferredCipherSuites.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Compression Section
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Compression",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SwitchRow(
                        label = "Brotli Compression",
                        checked = config.brotliEnabled,
                        onCheckedChange = { onToggleBrotli() }
                    )

                    if (config.brotliEnabled) {
                        Text(
                            "Brotli Quality: ${config.brotliQuality}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Slider(
                            value = config.brotliQuality.toFloat(),
                            onValueChange = { onBrotliQualityChange(it.toInt()) },
                            valueRange = 0f..11f,
                            steps = 10
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SwitchRow(
                        label = "Gzip Compression",
                        checked = config.gzipEnabled,
                        onCheckedChange = { onToggleGzip() }
                    )

                    if (config.gzipEnabled) {
                        Text(
                            "Gzip Level: ${config.gzipLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Slider(
                            value = config.gzipLevel.toFloat(),
                            onValueChange = { onGzipLevelChange(it.toInt()) },
                            valueRange = 0f..9f,
                            steps = 8
                        )
                    }
                }
            }
        }

        // Advanced Features Section
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Advanced Features",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SwitchRow(
                        label = "HPACK (HTTP/2)",
                        checked = config.hpackEnabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(hpackEnabled = !config.hpackEnabled))
                        }
                    )

                    SwitchRow(
                        label = "QPACK (HTTP/3)",
                        checked = config.qpackEnabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(qpackEnabled = !config.qpackEnabled))
                        }
                    )

                    SwitchRow(
                        label = "Server Push",
                        checked = config.serverPushEnabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(serverPushEnabled = !config.serverPushEnabled))
                        }
                    )

                    SwitchRow(
                        label = "Multiplexing",
                        checked = config.multiplexingEnabled,
                        onCheckedChange = {
                            onConfigChange(config.copy(multiplexingEnabled = !config.multiplexingEnabled))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticsTab(stats: ProtocolStats) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Protocol Usage",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StatRow("HTTP/3 Requests", stats.http3Requests.toString())
                    StatRow("HTTP/2 Requests", stats.http2Requests.toString())
                    StatRow("HTTP/1 Requests", stats.http1Requests.toString())
                    StatRow("Total Requests", stats.totalRequests.toString())

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { stats.http3Percentage / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "HTTP/3 Usage: ${stats.http3Percentage.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "TLS Statistics",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StatRow("TLS 1.3 Connections", stats.tls13Connections.toString())
                    StatRow("TLS 1.2 Connections", stats.tls12Connections.toString())
                    StatRow("Avg Handshake Time", "${stats.avgHandshakeTime}ms")

                    Spacer(modifier = Modifier.height(8.dp))

                    StatRow("0-RTT Success", stats.zeroRttSuccess.toString())
                    StatRow("0-RTT Failed", stats.zeroRttFailed.toString())
                    StatRow("0-RTT Success Rate", "${stats.zeroRttSuccessRate.toInt()}%")
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Compression Statistics",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StatRow("Brotli Saved", "${stats.brotliCompressionSaved / 1_000_000}MB")
                    StatRow("Gzip Saved", "${stats.gzipCompressionSaved / 1_000_000}MB")
                    StatRow("Total Saved", "${stats.totalCompressionSaved / 1_000_000}MB")
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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

private data class ProfileInfo(
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
