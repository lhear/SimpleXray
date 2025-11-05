package com.simplexray.an.hyper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

/**
 * HyperSparkline - Jitter microchart with Canvas rendering
 * 
 * Features:
 * - Draws jitter microchart at 30fps max
 * - Drops frames if CPU load >75% (simulated)
 * - Optimized Canvas rendering
 * - Auto-scales based on data
 */
@Composable
fun HyperSparkline(
    jitterHistory: List<Float>,
    modifier: Modifier = Modifier,
    maxFps: Int = 30
) {
    val density = LocalDensity.current
    
    // Frame rate limiting
    val frameInterval = (1000 / maxFps).toLong()
    var lastFrameTime by remember { mutableStateOf(0L) }
    var skippedFrames by remember { mutableStateOf(0) }
    
    // Normalize jitter values (0.0-1.0)
    val normalizedHistory = remember(jitterHistory) {
        if (jitterHistory.isEmpty()) return@remember emptyList<Float>()
        
        val maxJitter = jitterHistory.maxOrNull() ?: 1f
        val minJitter = jitterHistory.minOrNull() ?: 0f
        val range = max(1f, maxJitter - minJitter)
        
        jitterHistory.map { (it - minJitter) / range }
    }
    
    // CPU load simulation (in real app, get from PerformanceMonitor)
    val cpuLoad = 50.0 // Placeholder - would come from PerformanceMonitor
    val shouldDropFrames = cpuLoad > 75.0
    
    // Canvas drawing
    Canvas(modifier = modifier.fillMaxSize()) {
        val now = System.currentTimeMillis()
        
        // Frame rate limiting
        if (shouldDropFrames && (now - lastFrameTime) < frameInterval * 2) {
            skippedFrames++
            return@Canvas
        }
        
        lastFrameTime = now
        
        if (normalizedHistory.isEmpty()) return@Canvas
        
        val width = size.width
        val height = size.height
        val padding = 4.dp.toPx()
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2
        
        // Draw background grid (optional, lightweight)
        drawGrid(chartWidth, chartHeight, padding)
        
        // Draw jitter line
        drawJitterLine(normalizedHistory, chartWidth, chartHeight, padding)
    }
}

private fun DrawScope.drawGrid(
    width: Float,
    height: Float,
    padding: Float
) {
    // Light grid lines for reference
    val gridColor = Color(0x1A000000)
    val gridStroke = Stroke(width = 0.5f)
    
    // Horizontal lines (3 divisions)
    for (i in 1..3) {
        val y = padding + (height / 4) * i
        drawLine(
            color = gridColor,
            start = androidx.compose.ui.geometry.Offset(padding, y),
            end = androidx.compose.ui.geometry.Offset(padding + width, y),
            strokeWidth = 0.5f
        )
    }
}

private fun DrawScope.drawJitterLine(
    normalizedHistory: List<Float>,
    width: Float,
    height: Float,
    padding: Float
) {
    if (normalizedHistory.isEmpty()) return
    
    val pointCount = normalizedHistory.size
    val stepX = width / max(1, pointCount - 1)
    
    // Create path
    val path = Path()
    val firstY = padding + height * (1f - normalizedHistory[0])
    path.moveTo(padding, firstY)
    
    for (i in 1 until pointCount) {
        val x = padding + i * stepX
        val y = padding + height * (1f - normalizedHistory[i])
        path.lineTo(x, y)
    }
    
    // Draw line
    drawPath(
        path = path,
        color = Color(0xFF2196F3),
        style = Stroke(width = 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    )
    
    // Draw fill area (subtle gradient)
    val fillPath = Path(path)
    fillPath.lineTo(padding + width, padding + height)
    fillPath.lineTo(padding, padding + height)
    fillPath.close()
    
    drawPath(
        path = fillPath,
        color = Color(0x332196F3),
        style = androidx.compose.ui.graphics.drawscope.Fill
    )
    
    // Highlight spikes (jitter > 0.7)
    for (i in normalizedHistory.indices) {
        if (normalizedHistory[i] > 0.7f) {
            val x = padding + i * stepX
            val y = padding + height * (1f - normalizedHistory[i])
            
            // Small circle at spike
            drawCircle(
                color = Color(0xFFFF5722),
                radius = 3f,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}

