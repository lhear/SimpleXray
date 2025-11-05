package com.simplexray.an.hyper.ui

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
 * QUICWarmupBadge - Shows QUIC congestion window warm-up state
 * 
 * Features:
 * - Shows when QUIC congestion windows are warming
 * - Times out after N seconds
 * - Visual feedback for warm-up progress
 */
@Composable
fun QUICWarmupBadge(
    warmupState: QuicWarmupState,
    timeRemainingMs: Long,
    modifier: Modifier = Modifier
) {
    if (warmupState == QuicWarmupState.IDLE) {
        return
    }
    
    // Pulse animation for warming state
    val infiniteTransition = rememberInfiniteTransition(label = "quic_warmup")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = repeatable(
            iterations = Int.MAX_VALUE,
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val (backgroundColor, textColor, icon) = when (warmupState) {
        QuicWarmupState.WARMING -> {
            Triple(
                Color(0xFFFFC107).copy(alpha = 0.3f), // Yellow
                Color(0xFFFF9800), // Orange text
                "🔥"
            )
        }
        QuicWarmupState.READY -> {
            Triple(
                Color(0xFF4CAF50).copy(alpha = 0.3f), // Green
                Color(0xFF4CAF50), // Green text
                "✓"
            )
        }
        QuicWarmupState.TIMEOUT -> {
            Triple(
                Color(0xFFF44336).copy(alpha = 0.3f), // Red
                Color(0xFFF44336), // Red text
                "⚠"
            )
        }
        QuicWarmupState.IDLE -> return
    }
    
    val alpha = if (warmupState == QuicWarmupState.WARMING) pulseAlpha else 1f
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .alpha(alpha)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 14.sp
        )
        
        Text(
            text = "QUIC",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = 11.sp
        )
        
        when (warmupState) {
            QuicWarmupState.WARMING -> {
                val secondsRemaining = (timeRemainingMs / 1000).toInt()
                Text(
                    text = "Warming... ${secondsRemaining}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontSize = 10.sp
                )
            }
            QuicWarmupState.READY -> {
                Text(
                    text = "Ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontSize = 10.sp
                )
            }
            QuicWarmupState.TIMEOUT -> {
                Text(
                    text = "Timeout",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontSize = 10.sp
                )
            }
            QuicWarmupState.IDLE -> {}
        }
    }
}

