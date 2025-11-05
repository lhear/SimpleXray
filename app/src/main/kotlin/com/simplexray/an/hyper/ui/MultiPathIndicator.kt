package com.simplexray.an.hyper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * MultiPathIndicator - Shows active multi-path racing status
 * 
 * Features:
 * - Number of actively racing paths
 * - Bar height proportional to RTT spread
 * - Visual feedback for path diversity
 */
@Composable
fun MultiPathIndicator(
    activePathCount: Int,
    rttSpreadMs: Int,
    modifier: Modifier = Modifier
) {
    val maxRttSpread = 200 // Normalize to 200ms max
    val normalizedSpread = (rttSpreadMs.toFloat() / maxRttSpread).coerceIn(0f, 1f)
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Title
        Text(
            text = "Multi-Path",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp
        )
        
        // Path count display
        Text(
            text = "$activePathCount",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 24.sp
        )
        
        // RTT spread bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Fill proportional to RTT spread
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalizedSpread)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            normalizedSpread > 0.7f -> Color(0xFFFF5722) // Red - high spread
                            normalizedSpread > 0.4f -> Color(0xFFFFC107) // Yellow - medium spread
                            else -> Color(0xFF4CAF50) // Green - low spread
                        }
                    )
            )
        }
        
        // RTT spread label
        Text(
            text = "${rttSpreadMs}ms spread",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )
    }
}

