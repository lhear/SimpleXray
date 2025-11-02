package com.simplexray.an.ui.visualization

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.simplexray.an.protocol.visualization.TimeSeriesData

/**
 * Time series chart component for displaying metrics over time
 */
@Composable
fun TimeSeriesChart(
    data: List<TimeSeriesData>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Time Series Chart\n(Visualization coming soon)",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
