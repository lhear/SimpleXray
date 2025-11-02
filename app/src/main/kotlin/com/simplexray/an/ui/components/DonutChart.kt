package com.simplexray.an.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun DonutChart(
    slices: List<Pair<Color, Float>>, // fraction 0..1
    modifier: Modifier = Modifier,
    thickness: Float = 24f
) {
    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        var start = -90f
        for ((color, frac) in slices) {
            val sweep = 360f * frac.coerceIn(0f, 1f)
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                size = Size(diameter, diameter),
                style = Stroke(width = thickness)
            )
            start += sweep
        }
    }
}

