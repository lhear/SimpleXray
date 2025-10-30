package com.simplexray.an.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Helper class for managing battery optimization settings.
 * 
 * This ensures the VPN service runs reliably in the background
 * without being killed by Android's battery optimization.
 */
object BatteryOptimizationHelper {

    /**
     * Check if battery optimization is disabled for the app
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return false
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true // Not applicable for older versions
    }

    /**
     * Request battery optimization exemption
     * Opens system settings for user to grant permission
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(context)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general battery optimization settings
                    openBatteryOptimizationSettings(context)
                }
            }
        }
    }

    /**
     * Open battery optimization settings page
     */
    private fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore if settings not available
            }
        }
    }

    /**
     * Check if device is in doze mode
     */
    fun isDeviceIdleMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return false
            return powerManager.isDeviceIdleMode
        }
        return false
    }
}
