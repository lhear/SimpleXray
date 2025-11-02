package com.simplexray.an.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.simplexray.an.R
import com.simplexray.an.alert.NotificationEngine
import com.simplexray.an.config.ApiConfig
import com.simplexray.an.db.AppDatabase
import com.simplexray.an.grpc.GrpcChannelFactory
import com.simplexray.an.xray.AssetsInstaller
import com.simplexray.an.stats.BitrateBus
import com.simplexray.an.stats.MockTrafficObserver
import com.simplexray.an.stats.TrafficObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MonitorService : Service() {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var notifyJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationEngine.ensureChannel(this)
        // Ensure rule/asset files are copied to filesDir for xray-core usage
        AssetsInstaller.ensureAssets(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_ID, buildNotification("Monitoring active"))
        if (com.simplexray.an.config.ApiConfig.isAutostartXray(this)) {
            try { com.simplexray.an.xray.XrayCoreLauncher.start(this) } catch (_: Throwable) {}
        }
        startMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        notifyJob?.cancel()
        if (com.simplexray.an.config.ApiConfig.isAutostartXray(this)) {
            try { com.simplexray.an.xray.XrayCoreLauncher.stop() } catch (_: Throwable) {}
        }
    }

    private fun startMonitoring() {
        job?.cancel()
        val ctx = applicationContext
        val mock = ApiConfig.isMock(ctx)
        job = scope.launch {
            val dao = AppDatabase.get(ctx).trafficDao()
            if (mock) {
                MockTrafficObserver().live().collect { p ->
                    dao.insertSample(com.simplexray.an.db.TrafficSample(timestampMs = p.timestampMs, uplinkBps = p.uplinkBps, downlinkBps = p.downlinkBps))
                    BitrateBus.emit(p)
                }
            } else {
                val stub = GrpcChannelFactory.statsStub()
                val observer = TrafficObserver(stub, intervalProvider = { com.simplexray.an.power.PowerAdaptive.intervalMs(ctx) })
                observer.live().collect { p ->
                    dao.insertSample(com.simplexray.an.db.TrafficSample(timestampMs = p.timestampMs, uplinkBps = p.uplinkBps, downlinkBps = p.downlinkBps))
                    BitrateBus.emit(p)
                }
            }
        }
        notifyJob?.cancel()
        notifyJob = scope.launch(Dispatchers.Default) {
            BitrateBus.flow.collect { p ->
                val text = "Up ${formatBps(p.uplinkBps)}  Down ${formatBps(p.downlinkBps)}"
                NotificationManagerCompat.from(this@MonitorService).notify(ONGOING_ID, buildNotification(text))
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("SimpleXray Monitoring")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
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

    companion object {
        private const val CHANNEL_ID = "monitor"
        private const val ONGOING_ID = 2001

        fun start(context: Context) {
            val i = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
