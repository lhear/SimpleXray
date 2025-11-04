package com.simplexray.an.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.simplexray.an.prefs.Preferences
import java.util.concurrent.TimeUnit

/**
 * Utility class to schedule periodic traffic logging work.
 */
object TrafficWorkScheduler {

    /**
     * Schedule periodic traffic logging work.
     * Uses user-configurable sampling interval or defaults to 15 minutes.
     * Respects user opt-in preference for background logging.
     */
    fun schedule(context: Context) {
        val prefs = Preferences(context)
        
        // Respect user opt-in preference for background logging
        if (!prefs.backgroundTrafficLoggingEnabled) {
            cancel(context)
            return
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Only when connected
            .build()

        // Use user-configurable sampling interval (minimum 15 minutes for WorkManager)
        val intervalMinutes = maxOf(15, prefs.trafficSamplingIntervalMinutes)

        val workRequest = PeriodicWorkRequestBuilder<TrafficWorker>(
            repeatInterval = intervalMinutes.toLong(),
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TrafficWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if already scheduled
            workRequest
        )
    }

    /**
     * Cancel traffic logging work
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TrafficWorker.WORK_NAME)
    }
}
