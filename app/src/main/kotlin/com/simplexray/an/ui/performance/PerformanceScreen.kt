package com.simplexray.an.ui.performance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.ConnectionQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    currentProfile: PerformanceProfile,
    currentMetrics: PerformanceMetrics,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onAutoTuneToggled: (Boolean) -> Unit,
    autoTuneEnabled: Boolean = false,
    onBackClick: () -> Unit = {}
) {
    var selectedProfile by remember { mutableStateOf(currentProfile) }

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

            // Performance profiles
            item {
                Text("Performance Profiles", style = MaterialTheme.typography.titleLarge)
            }

            items(PerformanceProfile.getAll()) { profile ->
                ProfileCard(
                    profile = profile,
                    isSelected = profile == selectedProfile,
                    onClick = {
                        selectedProfile = profile
                        onProfileSelected(profile)
                    }
                )
            }
        }
    }
}

@Composable
fun MetricsCard(metrics: PerformanceMetrics) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current Performance", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            MetricRow("Latency", "${metrics.latency} ms")
            MetricRow("Download", "${metrics.downloadSpeed / 1024} KB/s")
            MetricRow("Upload", "${metrics.uploadSpeed / 1024} KB/s")
            MetricRow("Quality", metrics.getConnectionQuality().displayName)
            MetricRow("Memory", "${metrics.memoryUsage / 1024 / 1024} MB")
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
