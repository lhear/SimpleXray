package com.simplexray.an.ui.performance

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.an.performance.optimizer.ProfileRecommendation
import com.simplexray.an.performance.optimizer.RecommendationTrigger
import com.simplexray.an.performance.optimizer.TuningState
import com.simplexray.an.viewmodel.PerformanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdaptiveTuningStatusCard(
    viewModel: PerformanceViewModel,
    modifier: Modifier = Modifier
) {
    val tuningState by viewModel.tuningState.collectAsStateWithLifecycle()
    val lastRecommendation by viewModel.lastRecommendation.collectAsStateWithLifecycle()
    val pendingRecommendation = tuningState.pendingRecommendation
    
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Adaptive Tuning",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Status indicator
                Surface(
                    color = if (tuningState.isActive) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (tuningState.isActive) "Active" else "Inactive",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tuningState.isActive) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Stats
            if (tuningState.isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("Patterns", "${tuningState.patternCount}")
                    if (tuningState.lastAppliedAt > 0) {
                        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        StatItem("Last Applied", dateFormat.format(Date(tuningState.lastAppliedAt)))
                    }
                }
            }
            
            // Pending recommendation
            pendingRecommendation?.let { recommendation ->
                RecommendationCard(
                    recommendation = recommendation,
                    onApply = {
                        viewModel.applyRecommendation(recommendation)
                    },
                    onDismiss = {
                        viewModel.provideFeedback(recommendation, accepted = false)
                    }
                )
            }
            
            // Last recommendation info
            lastRecommendation?.let { recommendation ->
                if (pendingRecommendation == null) {
                    LastRecommendationInfo(recommendation)
                }
            }
        }
    }
}

@Composable
fun RecommendationCard(
    recommendation: ProfileRecommendation,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Profile Recommendation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = recommendation.profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Confidence badge
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${recommendation.confidence}%",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            Text(
                text = recommendation.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            // Trigger info
            val triggerText = when (val trigger = recommendation.trigger) {
                is RecommendationTrigger.HighLatency -> "High Latency: ${trigger.latency}ms"
                is RecommendationTrigger.HighPacketLoss -> "Packet Loss: ${String.format("%.1f", trigger.packetLoss)}%"
                is RecommendationTrigger.LowBandwidth -> "Low Bandwidth: ${trigger.bandwidth / 1_000_000f} Mbps"
                is RecommendationTrigger.HighBandwidth -> "High Bandwidth: ${trigger.bandwidth / 1_000_000f} Mbps"
                is RecommendationTrigger.LearnedPattern -> "Learned Pattern: ${trigger.pattern}"
            }
            Text(
                text = triggerText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
fun LastRecommendationInfo(recommendation: ProfileRecommendation) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Last recommendation: ${recommendation.profile.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}


