package com.simplexray.an.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.simplexray.an.data.db.TrafficDatabase
import com.simplexray.an.data.repository.TrafficRepository
import com.simplexray.an.data.repository.TrafficRepositoryFactory
import com.simplexray.an.network.TrafficObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Background worker that periodically logs traffic statistics.
 * Runs every 15 minutes to persist current traffic data.
 *
 * This ensures traffic history is maintained even when the app is in the background.
 */
class TrafficWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TrafficWorker"
        const val WORK_NAME = "traffic_logging_work"
    }

    private val repository: TrafficRepository
    private val trafficObserver: TrafficObserver

    init {
        // Initialize repository
        val database = TrafficDatabase.getInstance(context)
        repository = TrafficRepositoryFactory.create(database.trafficDao())

        // Initialize traffic observer with a supervisor scope
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        trafficObserver = TrafficObserver(context, scope)
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting traffic logging work")

            // Collect current traffic snapshot
            val snapshot = trafficObserver.collectNow()

            // Only log if there's meaningful traffic (connected and has data)
            if (snapshot.isConnected && (snapshot.rxBytes > 0 || snapshot.txBytes > 0)) {
                repository.insert(snapshot)
                Log.i(TAG, "Traffic logged: ${snapshot.formatDownloadSpeed()} / ${snapshot.formatUploadSpeed()}")
            } else {
                Log.d(TAG, "Skipping log: no active connection or traffic")
            }

            // Clean up old logs (older than 30 days)
            val deleted = repository.deleteLogsOlderThanDays(30)
            if (deleted > 0) {
                Log.i(TAG, "Cleaned up $deleted old traffic logs")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error logging traffic", e)
            Result.retry() // Retry on failure
        }
    }
}
