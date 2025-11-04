package com.simplexray.an

import android.app.Application
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
import android.util.Log
import com.simplexray.an.common.AppLogger
import androidx.work.Configuration
import androidx.work.WorkManager
import com.simplexray.an.db.TrafficPruneWorker
import com.simplexray.an.alert.BurstDetector
import com.simplexray.an.power.PowerAdaptive
import com.simplexray.an.telemetry.FpsMonitor
import com.simplexray.an.telemetry.MemoryMonitor
import com.simplexray.an.logging.LoggerRepository
import com.simplexray.an.logging.SingleTimberTree
import com.simplexray.an.logging.LogEvent
import com.simplexray.an.traffic.TrafficRepository
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var detector: BurstDetector? = null
    private var trafficRepository: TrafficRepository? = null
    
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
        
        // Initialize global logging system FIRST (before any other logging)
        initializeLogging()
        
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
            try {
                detector = BurstDetector(this, appScope)
                detector?.start()
            } catch (e: Exception) {
                AppLogger.e("Failed to initialize burst detector", e)
                detector = null
            }
            // Initialize power-adaptive polling
            PowerAdaptive.init(this)
            // Start telemetry monitors
            FpsMonitor.start()
            MemoryMonitor.start(appScope)
            
            // Initialize traffic repository (Application-level singleton)
            // This runs in Application scope and survives Activity recreation
            try {
                trafficRepository = TrafficRepository.getInstance(this)
                AppLogger.d("TrafficRepository initialized")
            } catch (e: Exception) {
                AppLogger.e("Failed to initialize TrafficRepository", e)
            }
        } else {
            // In native process, skip WorkManager and UI-related initialization
            AppLogger.d("Running in native process, skipping WorkManager initialization")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        cleanup()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        cleanup()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            cleanup()
        }
    }
    
    private fun cleanup() {
        PowerAdaptive.cleanup()
        try {
            detector?.stop()
        } catch (e: Exception) {
            AppLogger.w("Error stopping detector", e)
        }
        detector = null
        MemoryMonitor.stop()
        FpsMonitor.stop()
        // Cleanup traffic repository
        try {
            trafficRepository?.cleanup()
        } catch (e: Exception) {
            AppLogger.w("Error cleaning up TrafficRepository", e)
        }
        trafficRepository = null
        appScope.cancel()
    }
    
    /**
     * Initialize global logging system.
     * This must be called before any other logging operations.
     */
    private fun initializeLogging() {
        try {
            // Plant Timber tree for global log capture
            Timber.plant(SingleTimberTree())
            
            // Log initialization event
            LoggerRepository.addInstrumentation(
                type = LogEvent.InstrumentationType.PROCESS_DEATH,
                message = "Application.onCreate() - Logging system initialized",
                data = mapOf(
                    "process" to if (isMainProcess()) "main" else "native",
                    "pid" to android.os.Process.myPid()
                )
            )
            
            // Install crash handler
            installCrashHandler()
            
            AppLogger.d("Global logging system initialized")
        } catch (e: Exception) {
            // Fallback: use Android Log if Timber fails
            Log.e("App", "Failed to initialize logging system", e)
        }
    }
    
    /**
     * Install global crash handler to capture fatal exceptions
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Log to LoggerRepository
                LoggerRepository.add(
                    LogEvent.create(
                        severity = LogEvent.Severity.FATAL,
                        tag = "CrashHandler",
                        message = "Uncaught exception: ${throwable.message}",
                        throwable = throwable,
                        vpnState = LoggerRepository.getVpnState()
                    )
                )
                
                // Also log to AppLogger (for Firebase Crashlytics)
                AppLogger.e("FATAL: Uncaught exception in thread ${thread.name}", throwable)
            } catch (e: Exception) {
                // If logging fails, at least log to Android Log
                Log.e("App", "Failed to log crash", e)
            } finally {
                // Call original handler
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
