package com.simplexray.an.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.simplexray.an.BuildConfig
import com.simplexray.an.protocol.streaming.StreamingRepository
import kotlinx.coroutines.flow.collectAsState
import kotlinx.coroutines.flow.first

/**
 * StreamingPanel - Optional UI component for streaming optimization status.
 * 
 * Shows:
 * - Current transport preference (QUIC/H2)
 * - CDN match status
 * - Smoothed throughput up/down
 * 
 * Only visible when BuildConfig.FEAT_STREAMING_PANEL is true.
 */
@Composable
fun StreamingPanel(
    modifier: Modifier = Modifier
) {
    // Feature flag check
    if (!BuildConfig.FEAT_STREAMING_PANEL) {
        return
    }
    
    val context = LocalContext.current
    
    // Collect streaming snapshot
    val snapshot by remember {
        StreamingRepository.streamingSnapshot
    }.collectAsState(initial = StreamingRepository.snapshot())
    
    // Show panel only if there's active streaming
    if (snapshot.status == StreamingRepository.StreamingStatus.STREAMING) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Text(
                    text = "Streaming Optimization",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Divider()
                
                // Active sessions
                snapshot.activeSessions.values.forEach { session ->
                    StreamingSessionCard(session)
                }
                
                // Transport preferences
                if (snapshot.transportPreferences.isNotEmpty()) {
                    Text(
                        text = "Transport Preferences",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    snapshot.transportPreferences.forEach { (domain, transport) ->
                        TransportPreferenceRow(domain, transport)
                    }
                }
                
                // CDN Classifications
                if (snapshot.cdnClassifications.isNotEmpty()) {
                    Text(
                        text = "CDN Matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    snapshot.cdnClassifications.values.take(5).forEach { classification ->
                        CdnClassificationRow(classification)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingSessionCard(
    session: StreamingRepository.StreamingSession
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.platform.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = session.transportPreference.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = session.domain,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            session.rtt?.let { rtt ->
                Text(
                    text = "RTT: ${rtt}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TransportPreferenceRow(
    domain: String,
    transport: StreamingRepository.TransportType
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = domain.take(30) + if (domain.length > 30) "..." else "",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = transport.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CdnClassificationRow(
    classification: StreamingRepository.CdnClassification
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = classification.normalizedDomain.take(30) + if (classification.normalizedDomain.length > 30) "..." else "",
            style = MaterialTheme.typography.bodySmall
        )
        classification.cdnProvider?.let { provider ->
            Text(
                text = provider.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

