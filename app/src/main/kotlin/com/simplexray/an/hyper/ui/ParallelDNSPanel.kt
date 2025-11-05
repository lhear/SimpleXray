package com.simplexray.an.hyper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * ParallelDNSPanel - Shows DNS race results
 * 
 * Features:
 * - Time delta per resolver
 * - Winner highlighted
 * - TTL countdown
 */
@Composable
fun ParallelDNSPanel(
    dnsRaceResults: List<DnsRaceResult>,
    modifier: Modifier = Modifier
) {
    // TTL countdown state
    var ttlRemaining by remember { mutableStateOf(0) }
    
    // Update TTL countdown
    LaunchedEffect(dnsRaceResults) {
        val maxTtl = dnsRaceResults.maxOfOrNull { it.ttlSeconds } ?: 0
        ttlRemaining = maxTtl
        
        while (ttlRemaining > 0) {
            delay(1000)
            ttlRemaining--
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title
            Text(
                text = "DNS Race",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // TTL countdown
            if (ttlRemaining > 0) {
                Text(
                    text = "TTL: ${ttlRemaining}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
            }
            
            // DNS results
            if (dnsRaceResults.isEmpty()) {
                Text(
                    text = "No DNS race active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                dnsRaceResults.forEach { result ->
                    DnsRaceResultRow(
                        result = result,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun DnsRaceResultRow(
    result: DnsRaceResult,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = if (result.isWinner) {
        Color(0xFF4CAF50).copy(alpha = 0.2f) to Color(0xFF4CAF50) // Green for winner
    } else {
        Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Resolver name
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (result.isWinner) {
                Text(
                    text = "🏆",
                    fontSize = 12.sp
                )
            }
            Text(
                text = result.resolver,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontSize = 11.sp
            )
        }
        
        // Delta time
        Text(
            text = "${result.deltaMs}ms",
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (result.isWinner) FontWeight.Bold else FontWeight.Normal
        )
    }
}

