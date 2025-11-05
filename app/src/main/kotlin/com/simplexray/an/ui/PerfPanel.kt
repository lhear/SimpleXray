package com.simplexray.an.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simplexray.an.BuildConfig
import com.simplexray.an.perf.PerformanceOptimizer
import kotlinx.coroutines.delay

/**
 * Performance Diagnostics Panel
 * 
 * Displays real-time performance metrics from PerformanceOptimizer:
 * - Recompositions avoided
 * - Snapshots coalesced/dropped
 * - Outbound churn avoided
 * - Binder events coalesced
 * - Sniff throttles
 * 
 * Only visible when BuildConfig.FEAT_PERF_PANEL is true (feature flag)
 */
@Composable
fun PerfPanel(
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.FEAT_PERF_PANEL) {
        return // Feature disabled
    }
    
    val context = LocalContext.current
    var diagnostics by remember { mutableStateOf(PerformanceOptimizer.getDiagnostics()) }
    
    // Update diagnostics every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            diagnostics = PerformanceOptimizer.getDiagnostics()
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
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
                Text(
                    text = "Performance Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(
                    onClick = { PerformanceOptimizer.resetDiagnostics() }
                ) {
                    Text("Reset")
                }
            }
            
            Divider()
            
            // Metrics
            PerformanceMetricRow(
                label = "Recompositions Avoided",
                value = diagnostics.recompositionsAvoided.toString(),
                description = "Compose recompositions skipped due to unchanged state"
            )
            
            PerformanceMetricRow(
                label = "Snapshots Dropped",
                value = diagnostics.snapshotsDropped.toString(),
                description = "Duplicate snapshots filtered out"
            )
            
            PerformanceMetricRow(
                label = "Outbound Churn Avoided",
                value = diagnostics.outboundChurnAvoided.toString(),
                description = "Redundant outbound tag changes prevented"
            )
            
            PerformanceMetricRow(
                label = "Binder Events Coalesced",
                value = diagnostics.binderEventsCoalesced.toString(),
                description = "Binder callbacks batched within coalescing window"
            )
            
            PerformanceMetricRow(
                label = "Sniff Throttles",
                value = diagnostics.sniffThrottles.toString(),
                description = "DNS/sniff operations skipped (cache hits)"
            )
            
            // Summary
            Divider()
            
            val totalOptimizations = diagnostics.recompositionsAvoided +
                                   diagnostics.snapshotsDropped +
                                   diagnostics.outboundChurnAvoided +
                                   diagnostics.binderEventsCoalesced +
                                   diagnostics.sniffThrottles
            
            Text(
                text = "Total Optimizations: $totalOptimizations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PerformanceMetricRow(
    label: String,
    value: String,
    description: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

