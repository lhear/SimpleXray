package com.simplexray.an

import android.app.Application
import android.util.Log
import com.simplexray.an.common.AppLogger
import androidx.work.Configuration
import androidx.work.WorkManager
import com.simplexray.an.db.TrafficPruneWorker
import com.simplexray.an.alert.BurstDetector
import com.simplexray.an.power.PowerAdaptive
import com.simplexray.an.telemetry.FpsMonitor
import com.simplexray.an.telemetry.MemoryMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class App : Application() {
    // TODO: Consider using Dispatchers.IO for I/O operations instead of Default
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // TODO: Add lifecycle-aware detector initialization to prevent memory leaks
    private var detector: BurstDetector? = null
    
    /**
     * Check if current process is the main application process (not :native)
     */
    private fun isMainProcess(): Boolean {
        val processName = try {
            val pid = android.os.Process.myPid()
            val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            activityManager?.runningAppProcesses?.find { it.pid == pid }?.processName
        } catch (e: Exception) {
            null
        }
        // Main process name is package name, native process has ":native" suffix
        // Return true only if processName exists, equals packageName, and doesn't contain ":native"
        return processName != null && processName == packageName && !processName.contains(":native")
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Only initialize WorkManager in main process
        // TProxyService runs in :native process where WorkManager is not needed
        if (isMainProcess()) {
            try {
                val config = Configuration.Builder()
                    .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
                    .build()
                WorkManager.initialize(this, config)
                
                // Schedule background maintenance only in main process
                try {
                    TrafficPruneWorker.schedule(this)
                } catch (e: Exception) {
                    AppLogger.e("Failed to schedule TrafficPruneWorker", e)
                }
            } catch (e: IllegalStateException) {
                // WorkManager might already be initialized, ignore
                AppLogger.d("WorkManager already initialized")
            } catch (e: Exception) {
                AppLogger.w("WorkManager initialization failed", e)
            }
            
            // Start burst/throttle detector (uses global BitrateBus)
            // TODO: Add error handling for detector initialization failures
            // BUG: If detector.start() throws exception, detector is still assigned but may be in invalid state
            detector = BurstDetector(this, appScope).also { it.start() }
            // Initialize power-adaptive polling
            // TODO: Add configuration option to enable/disable power-adaptive features
            PowerAdaptive.init(this)
            // Start telemetry monitors
            // TODO: Consider adding telemetry data persistence for debugging
            FpsMonitor.start()
            MemoryMonitor.start(appScope)
        } else {
            // In native process, skip WorkManager and UI-related initialization
            AppLogger.d("Running in native process, skipping WorkManager initialization")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Cleanup resources
        // BUG: onTerminate() is not called on Android - cleanup should be done in onLowMemory() or process death handling
        // BUG: Resources may leak if app is killed without onTerminate() being called
        PowerAdaptive.cleanup()
        detector?.stop()
        MemoryMonitor.stop()
        FpsMonitor.stop()
        appScope.cancel()
    }
}
