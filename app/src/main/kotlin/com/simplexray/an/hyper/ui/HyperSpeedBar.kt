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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * HyperSpeedBar - Ultra-low-latency throughput indicator
 * 
 * Features:
 * - Animates throughput (MB/s) with color shift
 * - Pulses on microbursts
 * - Glows when QUIC path is dominant
 * - Uses rememberInfiniteTransition for smooth animations
 */
@Composable
fun HyperSpeedBar(
    throughputMBps: Double,
    burstIntensity: Float,
    isQuicDominant: Boolean,
    modifier: Modifier = Modifier
) {
    val maxThroughputMBps = 100.0 // Normalize to 100 MB/s max
    
    // Normalize throughput (0.0-1.0)
    val normalizedThroughput = remember(throughputMBps) {
        (throughputMBps / maxThroughputMBps).coerceIn(0.0, 1.0)
    }
    
    // Infinite transition for continuous animations
    val infiniteTransition = rememberInfiniteTransition(label = "speed_bar")
    
    // Color shift based on throughput (blue -> green -> yellow -> red)
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = normalizedThroughput.toFloat(),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "color_shift"
    )
    
    // Pulse animation for bursts (0.0-1.0)
    val burstPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = repeatable(
            iterations = Int.MAX_VALUE,
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "burst_pulse"
    )
    
    // QUIC glow intensity
    val quicGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = repeatable(
            iterations = Int.MAX_VALUE,
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "quic_glow"
    )
    
    // Calculate colors
    val baseColor = when {
        colorShift < 0.33f -> Color(0xFF2196F3) // Blue (low)
        colorShift < 0.66f -> Color(0xFF4CAF50) // Green (medium)
        else -> Color(0xFFFF9800) // Orange (high)
    }
    
    val burstColor = Color(0xFFFF5722) // Red-orange for bursts
    val quicColor = Color(0xFF00BCD4) // Cyan for QUIC
    
    // Combine colors based on state
    val barColor = when {
        isQuicDominant -> {
            // Blend QUIC cyan with base color
            Color(
                red = (baseColor.red + quicColor.red * quicGlow) / 2f,
                green = (baseColor.green + quicColor.green * quicGlow) / 2f,
                blue = (baseColor.blue + quicColor.blue * quicGlow) / 2f,
                alpha = 1f
            )
        }
        burstIntensity > 0.3f -> {
            // Blend burst color when pulsing
            val pulseIntensity = burstIntensity * burstPulse
            Color(
                red = (baseColor.red + burstColor.red * pulseIntensity) / (1f + pulseIntensity),
                green = (baseColor.green + burstColor.green * pulseIntensity) / (1f + pulseIntensity),
                blue = (baseColor.blue + burstColor.blue * pulseIntensity) / (1f + pulseIntensity),
                alpha = 1f
            )
        }
        else -> baseColor
    }
    
    // Gradient for visual appeal
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            barColor.copy(alpha = 0.8f),
            barColor,
            barColor.copy(alpha = 0.9f)
        )
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Throughput text
        Text(
            text = "%.2f MB/s".format(throughputMBps),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Animated fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalizedThroughput.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(gradient)
            )
        }
        
        // QUIC indicator
        if (isQuicDominant) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "QUIC",
                style = MaterialTheme.typography.labelSmall,
                color = quicColor,
                fontSize = 10.sp
            )
        }
        
        // Burst indicator
        if (burstIntensity > 0.3f) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "BURST",
                style = MaterialTheme.typography.labelSmall,
                color = burstColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

