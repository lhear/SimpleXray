package com.simplexray.an.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.stats.TrafficViewModel
import com.simplexray.an.ui.components.BitrateChart

@Composable
fun DashboardScreen(vm: TrafficViewModel = viewModel()) {
    val state by vm.current.collectAsState()
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Live Bitrate",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Up: ${state.points.lastOrNull()?.uplinkBps ?: 0} bps | Down: ${state.points.lastOrNull()?.downlinkBps ?: 0} bps",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            BitrateChart(points = state.points, modifier = Modifier.weight(1f))
        }
    }
}

