package com.simplexray.an.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import com.simplexray.an.ui.DashboardScreen
import com.simplexray.an.ui.SettingsScreen
import com.simplexray.an.db.TrafficPruneWorker
import com.simplexray.an.ui.components.TelemetryOverlay
import com.simplexray.an.config.ApiConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Schedule periodic pruning of time-series data
        TrafficPruneWorker.schedule(applicationContext)
        setContent {
            MaterialTheme { Surface { AppContent() } }
        }
    }
}

@Composable
private fun AppContent() {
    var tab by remember { mutableStateOf("dashboard") }
    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(12.dp)) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.matchParentSize()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { tab = "dashboard" }) { Text("Dashboard") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { tab = "categories" }) { Text("Categories") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { tab = "topology" }) { Text("Topology") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { tab = "heat" }) { Text("Heatmap") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { tab = "export" }) { Text("Export") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { tab = "settings" }) { Text("Settings") }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            "dashboard" -> DashboardScreen()
            "categories" -> com.simplexray.an.ui.CategoryScreen()
            "topology" -> com.simplexray.an.ui.TopologyScreen()
            "heat" -> com.simplexray.an.ui.HeatmapScreen()
            "export" -> com.simplexray.an.ui.ExportScreen()
            else -> SettingsScreen()
        }
        }
        if (ApiConfig.isTelemetry(androidx.compose.ui.platform.LocalContext.current)) {
            TelemetryOverlay(modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd))
        }
    }
}
