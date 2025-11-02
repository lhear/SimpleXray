package com.simplexray.an.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.simplexray.an.R
import com.simplexray.an.activity.MainActivity
import com.simplexray.an.data.db.TrafficDatabase
import com.simplexray.an.network.TrafficObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Home screen widget showing current network traffic statistics.
 * Displays download/upload speeds, latency, and daily usage.
 */
class TrafficWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Collect current traffic data
        val trafficData = collectTrafficData(context)

        provideContent {
            GlanceTheme {
                TrafficWidgetContent(trafficData)
            }
        }
    }

    private suspend fun collectTrafficData(context: Context): TrafficData {
        return try {
            // Create traffic observer
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val observer = TrafficObserver(context, scope)

            // Collect current snapshot
            val snapshot = observer.collectNow()

            // Get today's total from database
            val database = TrafficDatabase.getInstance(context)
            val totalBytes = database.trafficDao().getTotalBytesToday(getStartOfDayMillis())

            TrafficData(
                downloadSpeed = snapshot.formatDownloadSpeed(),
                uploadSpeed = snapshot.formatUploadSpeed(),
                latency = if (snapshot.latencyMs >= 0) "${snapshot.latencyMs}ms" else "---",
                dailyUsage = formatBytes(totalBytes?.total ?: 0L),
                isConnected = snapshot.isConnected
            )
        } catch (e: Exception) {
            TrafficData()
        }
    }

    private fun getStartOfDayMillis(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

@Composable
private fun TrafficWidgetContent(data: TrafficData) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "SimpleXray Traffic",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Connection status
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .background(
                            if (data.isConnected) {
                                ColorProvider(androidx.compose.ui.graphics.Color(0xFF4CAF50))
                            } else {
                                ColorProvider(androidx.compose.ui.graphics.Color(0xFFF44336))
                            }
                        )
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = if (data.isConnected) "Connected" else "Disconnected",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Download speed
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "↓ ${data.downloadSpeed}",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(androidx.compose.ui.graphics.Color(0xFF2196F3))
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Upload speed
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "↑ ${data.uploadSpeed}",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(androidx.compose.ui.graphics.Color(0xFF4CAF50))
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Latency
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏱ ${data.latency}",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Daily usage
            Text(
                text = "Today: ${data.dailyUsage}",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Data class for widget display
 */
data class TrafficData(
    val downloadSpeed: String = "0 Kbps",
    val uploadSpeed: String = "0 Kbps",
    val latency: String = "---",
    val dailyUsage: String = "0 B",
    val isConnected: Boolean = false
)

/**
 * Widget receiver that handles widget updates
 */
class TrafficWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrafficWidget()
}
