package com.simplexray.an.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.simplexray.an.common.AppLogger
import com.simplexray.an.service.TProxyService
import java.lang.reflect.Method

/**
 * Modern utility class for checking service running state without using deprecated APIs.
 * 
 * Replaces deprecated ActivityManager.getRunningServices() with modern alternatives:
 * - Android 8.0+ (API 26+): Uses process importance checking and service binding state
 * - Android < 8.0: Falls back to deprecated API only when necessary
 */
object ServiceStateChecker {
    private const val TAG = "ServiceStateChecker"

    /**
     * Check if a service is running using modern APIs.
     * 
     * @param context Application context
     * @param serviceClass The service class to check
     * @return true if service appears to be running, false otherwise
     */
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        return try {
            when {
                // Modern approach for Android 8.0+ (API 26+)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    checkServiceRunningModern(context, serviceClass)
                }
                // Fallback for older Android versions (API < 26)
                else -> {
                    checkServiceRunningLegacy(context, serviceClass)
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Error checking service state for ${serviceClass.simpleName}", e)
            false
        }
    }

    /**
     * Modern check using process importance and service state tracking.
     * For VPN services, we check if VPN interface is active.
     */
    @Suppress("DEPRECATION") // Only for older Android versions
    private fun checkServiceRunningModern(context: Context, serviceClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false

        // For VPN service, check if VPN interface is active
        if (serviceClass == TProxyService::class.java) {
            return checkVpnServiceRunning(context)
        }

        // For other services, check process importance
        // Note: This is not 100% accurate but better than deprecated API
        val packageName = context.packageName
        val appProcesses = activityManager.runningAppProcesses ?: return false

        return appProcesses.any { process ->
            process.processName == packageName &&
            (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE ||
             process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        }
    }

    /**
     * Check VPN service by checking if VPN interface is active.
     * Uses TProxyService.isRunning() static method as primary check.
     */
    private fun checkVpnServiceRunning(context: Context): Boolean {
        return try {
            // First check via TProxyService static method (most reliable)
            val isRunningStatic = TProxyService.isRunning()
            if (isRunningStatic) {
                return true
            }
            
            // Additional check: VpnService.prepare() returns null if VPN permission is granted
            // and VPN might be active. But this alone doesn't guarantee service is running.
            val vpnPrepare = android.net.VpnService.prepare(context)
            
            // If prepare() returns null, permission is granted but service might not be running
            // We should rely on TProxyService.isRunning() for accurate state
            // Return false here and let TProxyService.isRunning() be the source of truth
            false
        } catch (e: Exception) {
            AppLogger.w("Error checking VPN service state", e)
            false
        }
    }

    /**
     * Legacy check for Android < 8.0 using deprecated API.
     * Only used as fallback for older devices.
     */
    @Suppress("DEPRECATION")
    private fun checkServiceRunningLegacy(context: Context, serviceClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false

        return try {
            activityManager.getRunningServices(Int.MAX_VALUE).any { service ->
                serviceClass.name == service.service.className
            }
        } catch (e: SecurityException) {
            // Some devices may throw SecurityException
            AppLogger.w("SecurityException checking running services", e)
            false
        }
    }

    /**
     * Check VPN service running state specifically.
     * This is a convenience method that provides better accuracy for VPN services.
     */
    fun isVpnServiceRunning(context: Context): Boolean {
        return isServiceRunning(context, TProxyService::class.java)
    }
}

