package com.simplexray.an

import android.app.Application
import com.simplexray.an.db.TrafficPruneWorker
import com.simplexray.an.alert.BurstDetector
import com.simplexray.an.power.PowerAdaptive
import com.simplexray.an.telemetry.FpsMonitor
import com.simplexray.an.telemetry.MemoryMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detector: BurstDetector? = null
    override fun onCreate() {
        super.onCreate()
        // Schedule background maintenance
        TrafficPruneWorker.schedule(this)
        // Start burst/throttle detector (uses global BitrateBus)
        detector = BurstDetector(this, appScope).also { it.start() }
        // Initialize power-adaptive polling
        PowerAdaptive.init(this)
        // Start telemetry monitors
        FpsMonitor.start()
        MemoryMonitor.start(appScope)
    }
}
