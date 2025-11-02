package com.simplexray.an.heat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

fun normalize(values: List<Float>): List<Float> {
    if (values.isEmpty()) return values
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 1f
    val range = (max - min).takeIf { it > 1e-6 } ?: 1f
    return values.map { (it - min) / range }
}

fun colorRamp(v: Float): Color {
    val x = v.coerceIn(0f, 1f)
    val r = (255f * x).roundToInt()
    val g = (255f * (1f - kotlin.math.abs(0.5f - x) * 2f)).roundToInt()
    val b = (255f * (1f - x)).roundToInt()
    return Color(r, g, b)
}

@Composable
fun HeatmapView(
    grid: List<List<Float>>, // normalized 0..1
    modifier: Modifier = Modifier,
    onCellTap: (row: Int, col: Int, value: Float) -> Unit = { _, _, _ -> }
) {
    Canvas(modifier = modifier.pointerInput(grid) {
        detectTapGestures { offset ->
            val rows = grid.size
            val cols = grid.firstOrNull()?.size ?: 0
            if (rows == 0 || cols == 0) return@detectTapGestures
            val cellW = size.width / cols
            val cellH = size.height / rows
            val c = (offset.x / cellW).toInt().coerceIn(0, cols - 1)
            val r = (offset.y / cellH).toInt().coerceIn(0, rows - 1)
            onCellTap(r, c, grid[r][c])
        }
    }) {
        val rows = grid.size
        val cols = grid.firstOrNull()?.size ?: 0
        if (rows == 0 || cols == 0) return@Canvas
        val cellW = size.width / cols
        val cellH = size.height / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val v = grid[r][c].coerceIn(0f, 1f)
                val color = colorRamp(v)
                val left = c * cellW
                val top = r * cellH
                drawRect(color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(cellW, cellH))
            }
        }
    }
}
