package com.simplexray.an.traffic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

/**
 * Simple live traffic screen that shows current download/upload Mbps labels.
 */
@Composable
fun TrafficScreen(
    viewModel: TrafficViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val rx = uiState.currentSnapshot.rxRateMbps
    val tx = uiState.currentSnapshot.txRateMbps

    val chartModelProducer = androidx.compose.runtime.remember { CartesianChartModelProducer() }

    // Update chart model from history (keep last 120 samples)
    LaunchedEffect(uiState.history.snapshots) {
        val snapshots = uiState.history.snapshots.takeLast(120)
        if (snapshots.isNotEmpty()) {
            val rxValues = snapshots.map { it.rxRateMbps.toDouble() }
            val txValues = snapshots.map { it.txRateMbps.toDouble() }
            chartModelProducer.runTransaction {
                lineSeries {
                    series(rxValues)
                    series(txValues)
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Traffic") }) }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Mbps labels
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Download: %.2f Mbps".format(rx),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    text = "Upload: %.2f Mbps".format(tx),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(Modifier.height(8.dp))

            // Burst and throttle warning pills
            AnimatedVisibility(visible = uiState.isBurst && uiState.burstMessage != null, enter = fadeIn(), exit = fadeOut()) {
                PillBanner(text = uiState.burstMessage ?: "Burst detected", color = MaterialTheme.colorScheme.tertiary)
            }
            AnimatedVisibility(visible = uiState.isThrottled && uiState.throttleMessage != null, enter = fadeIn(), exit = fadeOut()) {
                PillBanner(text = uiState.throttleMessage ?: "Possible throttling", color = MaterialTheme.colorScheme.error)
            }

            // Line chart for last 60s (120 samples at 500ms)
            TrafficChart(
                chartModelProducer = chartModelProducer,
                modifier = Modifier
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PillBanner(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

