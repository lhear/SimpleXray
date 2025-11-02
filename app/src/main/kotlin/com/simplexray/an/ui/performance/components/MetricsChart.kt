package com.simplexray.an.ui.performance.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Real-time metrics chart component
 */
@Composable
fun MetricsChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    maxValue: Float? = null,
    showGrid: Boolean = true,
    label: String = ""
) {
    Card(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                if (data.isEmpty()) return@Canvas

                val width = size.width
                val height = size.height
                val dataMax = maxValue ?: data.maxOrNull() ?: 1f
                val dataMin = 0f

                // Draw grid
                if (showGrid) {
                    val gridColor = Color.Gray.copy(alpha = 0.2f)
                    val gridLines = 4

                    for (i in 0..gridLines) {
                        val y = height * i / gridLines
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }
                }

                // Draw chart line
                val path = Path()
                val stepX = width / (data.size - 1).coerceAtLeast(1)
                
                // Avoid division by zero when all values are the same
                val range = dataMax - dataMin
                val safeRange = if (range == 0f) 1f else range

                data.forEachIndexed { index, value ->
                    val x = index * stepX
                    val normalizedValue = ((value - dataMin) / safeRange).coerceIn(0f, 1f)
                    val y = height - (normalizedValue * height)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                // Draw the line
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )

                // Draw filled area under the line
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    color = color.copy(alpha = 0.1f)
                )

                // Draw current value indicator
                if (data.isNotEmpty()) {
                    val lastValue = data.last()
                    val range = dataMax - dataMin
                    val safeRange = if (range == 0f) 1f else range
                    val normalizedValue = ((lastValue - dataMin) / safeRange).coerceIn(0f, 1f)
                    val y = height - (normalizedValue * height)

                    drawCircle(
                        color = color,
                        radius = 6f,
                        center = Offset(width, y)
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 3f,
                        center = Offset(width, y)
                    )
                }
            }

            // Value labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (data.isNotEmpty()) {
                    Text(
                        text = String.format("%.1f", data.last()),
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
                Text(
                    text = String.format("%.0f", maxValue ?: data.maxOrNull() ?: 0f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Multi-line metrics chart
 */
@Composable
fun MultiLineChart(
    datasets: List<ChartDataset>,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    showLegend: Boolean = true
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                if (datasets.isEmpty() || datasets.all { it.data.isEmpty() }) return@Canvas

                val width = size.width
                val height = size.height
                val allData = datasets.flatMap { it.data }
                val dataMax = maxValue ?: allData.maxOrNull() ?: 1f
                val dataMin = 0f

                // Draw grid
                val gridColor = Color.Gray.copy(alpha = 0.2f)
                val gridLines = 4

                for (i in 0..gridLines) {
                    val y = height * i / gridLines
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // Draw each dataset
                datasets.forEach { dataset ->
                    if (dataset.data.isEmpty()) return@forEach

                    val path = Path()
                    val maxDataSize = datasets.maxOf { it.data.size }
                    val stepX = width / (maxDataSize - 1).coerceAtLeast(1)
                    
                    // Avoid division by zero when all values are the same
                    val range = dataMax - dataMin
                    val safeRange = if (range == 0f) 1f else range

                    dataset.data.forEachIndexed { index, value ->
                        val x = index * stepX
                        val normalizedValue = ((value - dataMin) / safeRange).coerceIn(0f, 1f)
                        val y = height - (normalizedValue * height)

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = dataset.color,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }
            }

            // Legend
            if (showLegend) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    datasets.forEach { dataset ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(2.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(color = dataset.color)
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = dataset.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ChartDataset(
    val label: String,
    val data: List<Float>,
    val color: Color
)
