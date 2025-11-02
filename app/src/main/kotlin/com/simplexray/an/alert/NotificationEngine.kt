package com.simplexray.an.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.simplexray.an.R

object NotificationEngine {
    private const val CHANNEL_ID = "monitor"
    private const val CHANNEL_NAME = "Network Monitor"
    private const val CHANNEL_DESC = "Live traffic alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyBurst(context: Context, upBps: Long, downBps: Long) {
        val title = "Burst detected"
        val text = "Up: ${formatBps(upBps)}  Down: ${formatBps(downBps)}"
        post(context, 1001, title, text)
    }

    fun notifyThrottle(context: Context, baseline: Long, current: Long) {
        val title = "Possible throttling"
        val text = "Baseline ${formatBps(baseline)} → Now ${formatBps(current)}"
        post(context, 1002, title, text)
    }

    fun notifyCdnSpike(context: Context, baseline: Float, current: Float, increasePercent: Int) {
        val title = "CDN traffic spike"
        val text = "CDN traffic increased by $increasePercent% (${(current * 100).toInt()}% of total)"
        post(context, 1003, title, text)
    }

    fun notifySuspiciousPattern(context: Context, pattern: String, details: String) {
        val title = "Suspicious pattern detected"
        val text = "$pattern: $details"
        post(context, 1004, title, text)
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun formatBps(bps: Long): String {
        val kb = 1_000L
        val mb = 1_000_000L
        val gb = 1_000_000_000L
        return when {
            bps >= gb -> String.format("%.2f Gbps", bps.toDouble() / gb)
            bps >= mb -> String.format("%.2f Mbps", bps.toDouble() / mb)
            bps >= kb -> String.format("%.2f Kbps", bps.toDouble() / kb)
            else -> "$bps bps"
        }
    }
}

