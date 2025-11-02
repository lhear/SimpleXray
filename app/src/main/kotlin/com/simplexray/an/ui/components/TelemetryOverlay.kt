package com.simplexray.an.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.simplexray.an.telemetry.TelemetryBus

@Composable
fun TelemetryOverlay(modifier: Modifier = Modifier) {
    val fps = TelemetryBus.fps.collectAsState().value
    val mem = TelemetryBus.memMb.collectAsState().value
    val poll = TelemetryBus.pollLatencyMs.collectAsState().value
    Column(
        modifier = modifier
            .background(Color(0x99000000), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(String.format("FPS: %.1f", fps), color = Color.White)
        Text(String.format("Mem: %.1f MB", mem), color = Color.White)
        Text(String.format("Poll: %.0f ms", poll), color = Color.White)
    }
}

