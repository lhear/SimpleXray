package com.simplexray.an.traffic

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineSpec
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer

@Composable
fun TrafficChart(
    chartModelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier,
    title: String = "Real-time Traffic",
    downloadColor: Color = MaterialTheme.colorScheme.primary,
    uploadColor: Color = MaterialTheme.colorScheme.secondary
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineSpec = listOf(
                        rememberLineSpec(shader = null, lineColor = downloadColor),
                        rememberLineSpec(shader = null, lineColor = uploadColor)
                    )
                ),
                startAxis = rememberStartAxis(label = rememberAxisLabelComponent()),
                bottomAxis = rememberBottomAxis(label = rememberAxisLabelComponent())
            ),
            modelProducer = chartModelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}

