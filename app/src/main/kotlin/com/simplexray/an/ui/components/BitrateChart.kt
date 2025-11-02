package com.simplexray.an.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simplexray.an.stats.BitratePoint
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.component.shape.shader.linearGradient
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.chart.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.DefaultEntry
import com.patrykandpatrick.vico.core.entry.entryOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun BitrateChart(points: List<BitratePoint>, modifier: Modifier = Modifier) {
    val upEntries = points.mapIndexed { idx, p -> DefaultEntry(idx.toFloat(), p.uplinkBps.toFloat()) }
    val downEntries = points.mapIndexed { idx, p -> DefaultEntry(idx.toFloat(), p.downlinkBps.toFloat()) }

    val producer = remember(points) { ChartEntryModelProducer(upEntries, downEntries) }

    ProvideChartStyle(com.patrykandpatrick.vico.compose.style.rememberChartStyle()) {
        Chart(
            chart = LineChart(),
            chartModelProducer = producer,
            modifier = modifier
        )
    }
}

