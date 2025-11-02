package com.simplexray.an.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.simplexray.an.domain.Category
import com.simplexray.an.domain.CategoryViewModel
import com.simplexray.an.ui.components.DonutChart

@Composable
fun CategoryScreen(vm: CategoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val dist = vm.dist.collectAsState().value
    val colors = mapOf(
        Category.CDN to Color(0xFF42A5F5),
        Category.Social to Color(0xFFAB47BC),
        Category.Gaming to Color(0xFFFF7043),
        Category.Video to Color(0xFF66BB6A),
        Category.Other to Color(0xFF9E9E9E)
    )
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Traffic Categories")
        val slices = dist.entries.mapNotNull { entry ->
            colors[entry.key]?.let { color -> color to entry.value }
        }
        DonutChart(slices = slices, modifier = Modifier.height(220.dp))
    }
}
