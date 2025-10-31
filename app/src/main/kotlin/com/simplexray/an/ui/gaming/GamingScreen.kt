package com.simplexray.an.ui.gaming

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.protocol.gaming.GamingOptimizer.GameProfile
import com.simplexray.an.viewmodel.GamingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamingScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: GamingViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return GamingViewModel(context.applicationContext as android.app.Application) as T
            }
        }
    )
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()
    val currentPing by viewModel.currentPing.collectAsState()
    val jitterLevel by viewModel.jitterLevel.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gaming Optimization") },
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
            // Status Card
            if (isOptimizing && selectedProfile != null) {
                item {
                    ActiveOptimizationCard(
                        profile = selectedProfile!!,
                        currentPing = currentPing,
                        jitterLevel = jitterLevel,
                        onDeactivate = { viewModel.clearSelection() }
                    )
                }
            }

            item {
                Text(
                    "Select Game Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            val gameProfiles = viewModel.getAllGameProfiles()

            items(gameProfiles) { profile ->
                GameProfileCard(
                    profile = profile,
                    isSelected = selectedProfile == profile,
                    onClick = {
                        if (selectedProfile == profile) {
                            viewModel.clearSelection()
                        } else {
                            viewModel.selectGameProfile(profile)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveOptimizationCard(
    profile: GameProfile,
    currentPing: Int,
    jitterLevel: Int,
    onDeactivate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Active Optimization",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                FilledTonalButton(onClick = onDeactivate) {
                    Text("Deactivate")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricBadge(
                    icon = Icons.Default.Speed,
                    label = "Ping",
                    value = "${currentPing}ms",
                    color = when {
                        currentPing < 50 -> Color(0xFF4CAF50)
                        currentPing < 100 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }
                )
                MetricBadge(
                    icon = Icons.Default.NetworkCheck,
                    label = "Jitter",
                    value = "${jitterLevel}ms",
                    color = when {
                        jitterLevel < 15 -> Color(0xFF4CAF50)
                        jitterLevel < 30 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }
                )
                MetricBadge(
                    icon = Icons.Default.CheckCircle,
                    label = "Status",
                    value = "Active",
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
private fun MetricBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun GameProfileCard(
    profile: GameProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainer
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
                    profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    getProfileDescription(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConfigChip("${profile.config.maxLatency}ms max latency")
                    ConfigChip("${profile.config.jitterTolerance}ms jitter")
                    ConfigChip(profile.protocol.name)
                }
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Select",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConfigChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun getProfileDescription(profile: GameProfile): String {
    return when (profile) {
        GameProfile.PUBG_MOBILE -> "Ultra-low latency, UDP optimization"
        GameProfile.FREE_FIRE -> "Ping stabilization, jitter reduction"
        GameProfile.COD_MOBILE -> "Fast path routing, QoS priority"
        GameProfile.MOBILE_LEGENDS -> "Lag compensation, packet prioritization"
        GameProfile.GENSHIN_IMPACT -> "Stable connection, bandwidth management"
        GameProfile.CLASH_OF_CLANS -> "Connection stability focus"
        GameProfile.VALORANT_MOBILE -> "Low latency mode, anti-jitter"
        GameProfile.GENERIC_FPS -> "General FPS game optimization"
        GameProfile.GENERIC_MOBA -> "General MOBA game optimization"
    }
}
