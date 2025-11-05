package com.simplexray.an.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simplexray.an.BuildConfig
import com.simplexray.an.game.GameOptimizationRepository
import kotlinx.coroutines.flow.collectAsState
import kotlinx.coroutines.flow.StateFlow

/**
 * Game optimization panel UI component (optional, feature-flagged).
 * 
 * Shows:
 * - Game detected status
 * - Pinned outbound
 * - RTT/loss/jitter (smoothed)
 * - MTU/MSS advisory
 * - Network state (good/fair/poor)
 */
@Composable
fun GamePanel(
    modifier: Modifier = Modifier
) {
    // Feature flag check
    if (!BuildConfig.FEAT_GAME_PANEL) {
        return
    }
    
    val snapshot by remember {
        GameOptimizationRepository.gameSnapshot
    }.collectAsState(
        initial = GameOptimizationRepository.snapshot()
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                text = "Game Optimization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Divider()
            
            // Game detection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Game Detected:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (snapshot.pinnedRoutes.isNotEmpty()) "Yes" else "No",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (snapshot.pinnedRoutes.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // Pinned routes
            if (snapshot.pinnedRoutes.isNotEmpty()) {
                snapshot.pinnedRoutes.forEach { route ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Route:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${route.host}:${route.port}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Network metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "RTT",
                    value = String.format("%.1f ms", snapshot.smoothedRtt)
                )
                MetricItem(
                    label = "Loss",
                    value = String.format("%.2f%%", snapshot.smoothedLoss)
                )
                MetricItem(
                    label = "Jitter",
                    value = String.format("%.1f ms", snapshot.smoothedJitter)
                )
            }
            
            // Network state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Network State:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = when (snapshot.networkState) {
                        GameOptimizationRepository.NetworkState.GOOD -> MaterialTheme.colorScheme.primaryContainer
                        GameOptimizationRepository.NetworkState.FAIR -> MaterialTheme.colorScheme.secondaryContainer
                        GameOptimizationRepository.NetworkState.POOR -> MaterialTheme.colorScheme.errorContainer
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = snapshot.networkState.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // MTU/MSS advisory
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recommended MSS:",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${snapshot.recommendedMss} bytes",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recommended MTU:",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${snapshot.recommendedMtu} bytes",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // NAT keepalive status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NAT Keepalive:",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (snapshot.natKeepaliveActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (snapshot.natKeepaliveActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

