package com.simplexray.an.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.simplexray.an.heat.HeatmapView
import com.simplexray.an.heat.HeatmapViewModel
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HeatmapScreen(vm: HeatmapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val grid = vm.grid.collectAsState().value
    var tapped by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Triple<Int, Int, Float>?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (grid.isEmpty()) Text("No heat data") else HeatmapView(grid = grid, modifier = Modifier.fillMaxSize(), onCellTap = { r, c, v -> tapped = Triple(r, c, v) })
        if (grid.isNotEmpty()) {
            val categories = listOf("CDN", "Video", "Social", "Gaming", "Other")
            Surface(modifier = Modifier.padding(12.dp)) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(8.dp)) {
                    Text("Heatmap (rows): ${categories.joinToString(", ")}")
                    val lastCol = grid.map { it.lastOrNull() ?: 0f }
                    val percent = lastCol.map { (it * 100).toInt() }
                    Text("Current: CDN ${percent[0]}% • Video ${percent[1]}% • Social ${percent[2]}% • Gaming ${percent[3]}% • Other ${percent[4]}%", color = Color.Gray)
                    tapped?.let { (r, c, v) ->
                        val p = (v * 100).toInt()
                        Text("Tap: ${categories.getOrNull(r) ?: r} @ t=${c} → ${p}%")
                    }
                }
            }
        }
    }
}
