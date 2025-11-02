package com.simplexray.an.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.simplexray.an.stats.BitratePoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

@Composable
fun BitrateChart(points: List<BitratePoint>, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(points) {
        producer.runTransaction {
            if (points.isNotEmpty()) {
                val upValues = points.map { it.uplinkBps.toFloat() }
                val downValues = points.map { it.downlinkBps.toFloat() }
                lineSeries {
                    series(upValues)
                    series(downValues)
                }
            }
        }
    }
    
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = rememberStartAxis(label = rememberAxisLabelComponent()),
            bottomAxis = rememberBottomAxis(label = rememberAxisLabelComponent())
        ),
        modelProducer = producer,
        modifier = modifier
    )
}

