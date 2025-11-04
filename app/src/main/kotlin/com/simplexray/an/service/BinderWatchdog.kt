package com.simplexray.an.service

import android.os.IBinder
import android.os.RemoteException
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight watchdog that monitors binder health and logs binder death events.
 * Automatically detects when binder becomes stale and triggers recovery.
 */
class BinderWatchdog(
    private val scope: CoroutineScope,
    private val binderProvider: () -> IVpnServiceBinder?,
    private val onBinderDeath: () -> Unit
) {
    companion object {
        private const val TAG = "BinderWatchdog"
        private const val CHECK_INTERVAL_MS = 5000L // Check every 5 seconds
        private const val MAX_CONSECUTIVE_FAILURES = 3 // Trigger recovery after 3 failures
    }
    
    private var consecutiveFailures = 0
    private var isMonitoring = false
    
    /**
     * Start monitoring binder health
     */
    fun start() {
        if (isMonitoring) {
            AppLogger.d("$TAG: Already monitoring")
            return
        }
        
        isMonitoring = true
        scope.launch(Dispatchers.IO) {
            while (scope.isActive && isMonitoring) {
                try {
                    val binder = binderProvider()
                    if (binder == null) {
                        consecutiveFailures++
                        AppLogger.d("$TAG: Binder is null (failures: $consecutiveFailures)")
                        
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            AppLogger.w("$TAG: Binder unavailable for ${consecutiveFailures} checks, triggering recovery")
                            onBinderDeath()
                            consecutiveFailures = 0
                        }
                    } else {
                        // Test binder by calling a lightweight method
                        try {
                            binder.isConnected()
                            consecutiveFailures = 0 // Reset on success
                        } catch (e: RemoteException) {
                            consecutiveFailures++
                            AppLogger.w("$TAG: Binder call failed: ${e.message} (failures: $consecutiveFailures)")
                            
                            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                                AppLogger.w("$TAG: Binder appears dead (${consecutiveFailures} failures), triggering recovery")
                                onBinderDeath()
                                consecutiveFailures = 0
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w("$TAG: Error checking binder health", e)
                    consecutiveFailures++
                }
                
                delay(CHECK_INTERVAL_MS)
            }
        }
        
        AppLogger.d("$TAG: Started monitoring")
    }
    
    /**
     * Stop monitoring
     */
    fun stop() {
        isMonitoring = false
        consecutiveFailures = 0
        AppLogger.d("$TAG: Stopped monitoring")
    }
}

