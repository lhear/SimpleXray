package com.simplexray.an.ui.streaming

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplexray.an.protocol.streaming.StreamingRepository
import kotlinx.coroutines.flow.collectAsState

/**
 * StreamingOptimizationScreen - Compose UI for streaming optimization status.
 * 
 * Usage:
 * ```
 * val streaming by StreamingRepository.streamingSnapshot.collectAsState(initial = StreamingRepository.StreamingSnapshot.idle())
 * 
 * StreamingOptimizationScreen(streaming = streaming)
 * ```
 */
@Composable
fun StreamingOptimizationScreen(
    streaming: StreamingRepository.StreamingSnapshot,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status header
        Text(
            text = "Streaming Optimization",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Status indicator
        StatusIndicator(streaming.status)
        
        // Active sessions
        if (streaming.activeSessions.isNotEmpty()) {
            Text(
                text = "Active Sessions (${streaming.activeSessions.size})",
                style = MaterialTheme.typography.titleMedium
            )
            
            streaming.activeSessions.values.forEach { session ->
                StreamingSessionCard(session)
            }
        } else {
            Text(
                text = "No active streaming sessions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Transport preferences
        if (streaming.transportPreferences.isNotEmpty()) {
            Text(
                text = "Transport Preferences",
                style = MaterialTheme.typography.titleMedium
            )
            
            streaming.transportPreferences.forEach { (domain, transport) ->
                TransportPreferenceCard(domain, transport)
            }
        }
        
        // Error display
        streaming.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Error: $error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: StreamingRepository.StreamingStatus) {
    val (color, text) = when (status) {
        StreamingRepository.StreamingStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant to "Idle"
        StreamingRepository.StreamingStatus.STREAMING -> MaterialTheme.colorScheme.primary to "Streaming"
        StreamingRepository.StreamingStatus.BUFFERING -> MaterialTheme.colorScheme.warning to "Buffering"
        StreamingRepository.StreamingStatus.ERROR -> MaterialTheme.colorScheme.error to "Error"
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(12.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = color
            ) {}
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
    }
}

@Composable
private fun StreamingSessionCard(session: StreamingRepository.StreamingSession) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = session.domain,
                style = MaterialTheme.typography.titleSmall
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Platform: ${session.platform.name}",
                    style = MaterialTheme.typography.bodySmall
                )
                
                Text(
                    text = "Transport: ${session.transportPreference.name}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            session.rtt?.let { rtt ->
                Text(
                    text = "RTT: ${rtt}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (session.consecutiveBitrateDrops > 0) {
                Text(
                    text = "⚠️ Bitrate drops: ${session.consecutiveBitrateDrops}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TransportPreferenceCard(domain: String, transport: StreamingRepository.TransportType) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = domain,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            
            Surface(
                color = when (transport) {
                    StreamingRepository.TransportType.QUIC -> MaterialTheme.colorScheme.primary
                    StreamingRepository.TransportType.HTTP2 -> MaterialTheme.colorScheme.secondary
                    StreamingRepository.TransportType.AUTO -> MaterialTheme.colorScheme.tertiary
                }.copy(alpha = 0.2f)
            ) {
                Text(
                    text = transport.name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * ViewModel for streaming optimization UI
 */
class StreamingViewModel {
    val streamingSnapshot = StreamingRepository.streamingSnapshot
}

