package com.simplexray.an.db

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.simplexray.an.data.db.TrafficDatabase
import com.simplexray.an.data.repository.TrafficRepository
import com.simplexray.an.data.repository.TrafficRepositoryFactory
import com.simplexray.an.prefs.Preferences
import java.util.concurrent.TimeUnit

/**
 * Worker to prune old traffic data from TrafficDatabase.
 * Consolidated to use TrafficDatabase instead of maintaining separate AppDatabase.
 * Prune cadence is tied to the logging frequency to avoid purging data too aggressively.
 */
class TrafficPruneWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            // Use TrafficDatabase instead of AppDatabase to consolidate pruning
            val db = TrafficDatabase.getInstance(applicationContext)
            val repository = TrafficRepositoryFactory.create(db.trafficDao())
            
            // Use configurable retention window from preferences
            val prefs = Preferences(applicationContext)
            val retentionDays = prefs.trafficRetentionDays
            val deleted = repository.deleteLogsOlderThanDays(retentionDays)
            
            if (deleted > 0) {
                android.util.Log.d("TrafficPruneWorker", "Pruned $deleted old traffic logs (retention: $retentionDays days)")
            }
            
            Result.success()
        } catch (t: Throwable) {
            android.util.Log.e("TrafficPruneWorker", "Error pruning traffic logs", t)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "traffic-prune"
        
        /**
         * Schedule periodic pruning. The cadence is tied to the logging frequency:
         * - If logging every 15 minutes, prune every 12 hours (safe margin)
         * - If logging every 30 minutes, prune every 24 hours
         * This ensures we don't purge data too aggressively.
         */
        fun schedule(context: Context) {
            val prefs = Preferences(context)
            val loggingIntervalMinutes = maxOf(15, prefs.trafficSamplingIntervalMinutes)
            
            // Prune at a cadence that's 24-48x the logging frequency to ensure data isn't purged too aggressively
            // For 15-minute logging: prune every 12 hours (48x)
            // For 30-minute logging: prune every 24 hours (48x)
            val pruneIntervalHours = maxOf(12, (loggingIntervalMinutes * 48) / 60)
            
            val req = PeriodicWorkRequestBuilder<TrafficPruneWorker>(
                pruneIntervalHours.toLong(),
                TimeUnit.HOURS
            ).build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }
    }
}

