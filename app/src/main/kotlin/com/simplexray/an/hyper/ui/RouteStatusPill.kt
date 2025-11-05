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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * RouteStatusPill - Shows current outbound route status
 * 
 * Features:
 * - Current outbound tag
 * - Session pin status
 * - Path race winner
 * - Color: green if stabilizing, yellow if racing, red if dropping
 */
@Composable
fun RouteStatusPill(
    currentOutboundTag: String?,
    sessionPinStatus: String?,
    pathRaceWinner: String?,
    pathStatus: PathStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (pathStatus) {
        PathStatus.STABILIZING -> Color(0xFF4CAF50) to Color.White // Green
        PathStatus.RACING -> Color(0xFFFFC107) to Color.Black // Yellow
        PathStatus.DROPPING -> Color(0xFFF44336) to Color.White // Red
        PathStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Outbound tag
        if (currentOutboundTag != null) {
            Text(
                text = currentOutboundTag,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 12.sp
            )
        }
        
        // Session pin indicator
        if (sessionPinStatus != null) {
            Text(
                text = "📌",
                fontSize = 12.sp
            )
            Text(
                text = sessionPinStatus,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontSize = 10.sp
            )
        }
        
        // Path race winner
        if (pathRaceWinner != null && pathStatus == PathStatus.RACING) {
            Text(
                text = "🏆 $pathRaceWinner",
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Status indicator
        Text(
            text = when (pathStatus) {
                PathStatus.STABILIZING -> "●"
                PathStatus.RACING -> "⚡"
                PathStatus.DROPPING -> "⚠"
                PathStatus.UNKNOWN -> "?"
            },
            color = textColor,
            fontSize = 10.sp
        )
    }
}

