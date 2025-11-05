package com.simplexray.an.hyper.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * BurstNotifier - Flashes when packet bursts detected
 * 
 * Features:
 * - Flashes when >N packets within 3ms
 * - Coalesces flashes when too frequent
 * - Non-intrusive visual feedback
 */
@Composable
fun BurstNotifier(
    packetBurstCount: Int,
    packetsPerSecond: Int,
    burstThreshold: Int = 10, // N packets
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    var lastBurstTime by remember { mutableStateOf(0L) }
    val coalesceWindowMs = 100L // Coalesce flashes within 100ms
    
    // Trigger flash when burst detected
    LaunchedEffect(packetBurstCount) {
        val now = System.currentTimeMillis()
        
        // Only flash if burst count exceeds threshold and enough time has passed
        if (packetBurstCount >= burstThreshold && (now - lastBurstTime) > coalesceWindowMs) {
            isVisible = true
            lastBurstTime = now
            
            // Auto-hide after 500ms
            delay(500)
            isVisible = false
        }
    }
    
    // Flash animation
    val flashAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(100),
        label = "burst_flash"
    )
    
    if (flashAlpha > 0.01f) {
        Row(
            modifier = modifier
                .alpha(flashAlpha)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFF5722).copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚡",
                fontSize = 14.sp
            )
            
            Text(
                text = "BURST",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 12.sp
            )
            
            Text(
                text = "${packetBurstCount} pkt",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontSize = 10.sp
            )
            
            Text(
                text = "${packetsPerSecond} pps",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 9.sp
            )
        }
    }
}

