package com.simplexray.an.hyper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplexray.an.BuildConfig

/**
 * HyperPanel - Diagnostic panel (optional, feature-flagged)
 * 
 * Features:
 * - Last outbound switch reason
 * - RTT histogram summary
 * - Jitter percentile
 * - QUIC RTT slope
 * - Burst counter
 */
@Composable
fun HyperPanel(
    metrics: HyperMetrics,
    modifier: Modifier = Modifier
) {
    // Feature flag check
    if (!BuildConfig.FEAT_HYPER_UI) {
        return
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Text(
                text = "Hyper Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Last outbound switch reason
            if (metrics.lastOutboundSwitchReason != null) {
                DiagnosticRow(
                    label = "Last Switch",
                    value = metrics.lastOutboundSwitchReason
                )
            }
            
            // RTT Histogram
            DiagnosticRow(
                label = "RTT Histogram",
                value = "P50: ${metrics.rttHistogram.p50}ms | P90: ${metrics.rttHistogram.p90}ms | P99: ${metrics.rttHistogram.p99}ms"
            )
            
            DiagnosticRow(
                label = "RTT Range",
                value = "${metrics.rttHistogram.min}ms - ${metrics.rttHistogram.max}ms"
            )
            
            // Jitter percentile
            DiagnosticRow(
                label = "Jitter (P95)",
                value = "%.2f ms".format(metrics.jitterPercentile)
            )
            
            // QUIC RTT slope
            DiagnosticRow(
                label = "QUIC RTT Slope",
                value = "%.2f ms/s".format(metrics.quicRttSlope)
            )
            
            // Burst counter
            DiagnosticRow(
                label = "Burst Counter",
                value = "${metrics.burstCounter}"
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

