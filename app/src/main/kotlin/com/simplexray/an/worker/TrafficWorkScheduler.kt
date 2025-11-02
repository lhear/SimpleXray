package com.simplexray.an.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Utility class to schedule periodic traffic logging work.
 */
object TrafficWorkScheduler {

    /**
     * Schedule periodic traffic logging work.
     * Runs every 15 minutes to persist traffic data.
     */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Only when connected
            .build()

        val workRequest = PeriodicWorkRequestBuilder<TrafficWorker>(
            repeatInterval = 15, // Every 15 minutes
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
