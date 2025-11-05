package com.simplexray.an.hyper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectAsState

/**
 * HyperUiScreen - Example integration screen showing all Hyper UI components
 * 
 * This demonstrates how to use all Hyper UI components together.
 * In production, components can be integrated into existing screens.
 */
@Composable
fun HyperUiScreen(
    modifier: Modifier = Modifier
) {
    // Collect hyper snapshot from repository
    val snapshot by remember {
        HyperUiRepository.hyperSnapshot
    }.collectAsState(
        initial = HyperUiRepository.getCurrentSnapshot()
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hyper UI") }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Throughput speed bar
            HyperSpeedBar(
                throughputMBps = snapshot.throughputMBps,
                burstIntensity = snapshot.burstIntensity,
                isQuicDominant = snapshot.isQuicDominant,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Route status pill
            RouteStatusPill(
                currentOutboundTag = snapshot.currentOutboundTag,
                sessionPinStatus = snapshot.sessionPinStatus,
                pathRaceWinner = snapshot.pathRaceWinner,
                pathStatus = snapshot.pathStatus,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Multi-path indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MultiPathIndicator(
                    activePathCount = snapshot.activePathCount,
                    rttSpreadMs = snapshot.rttSpreadMs,
                    modifier = Modifier.weight(1f)
                )
                
                // Jitter sparkline
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Jitter",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    HyperSparkline(
                        jitterHistory = snapshot.jitterHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    )
                }
            }
            
            // DNS race panel
            ParallelDNSPanel(
                dnsRaceResults = snapshot.dnsRaceResults,
                modifier = Modifier.fillMaxWidth()
            )
            
            // QUIC warmup badge
            if (snapshot.quicWarmupState != QuicWarmupState.IDLE) {
                QUICWarmupBadge(
                    warmupState = snapshot.quicWarmupState,
                    timeRemainingMs = snapshot.quicWarmupTimeRemainingMs,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Burst notifier
            BurstNotifier(
                packetBurstCount = snapshot.packetBurstCount,
                packetsPerSecond = snapshot.packetsPerSecond,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Diagnostic panel (feature-flagged)
            HyperPanel(
                metrics = HyperMetrics(), // Would come from actual metrics
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

