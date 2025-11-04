package com.simplexray.an.worker

import android.content.Context
import androidx.work.CoroutineWorker
import com.simplexray.an.common.AppLogger
import androidx.work.WorkerParameters
import com.simplexray.an.data.db.TrafficDatabase
import com.simplexray.an.data.repository.TrafficRepository
import com.simplexray.an.data.repository.TrafficRepositoryFactory
import com.simplexray.an.network.TrafficObserver
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.telemetry.XrayStatsObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Background worker that periodically logs traffic statistics.
 * Runs every 15 minutes to persist current traffic data.
 *
 * This ensures traffic history is maintained even when the app is in the background.
 * 
 * TODO: Make logging interval configurable
 * TODO: Add exponential backoff for retry failures
 * TODO: Implement worker cancellation handling
 * TODO: Add metrics collection for worker performance
 */
class TrafficWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "traffic_logging_work"
    }

    private val repository: TrafficRepository
    private val scope: CoroutineScope
    private val trafficObserver: TrafficObserver
    private val xrayObserver: XrayStatsObserver

    init {
        // Initialize repository
        // PERF: TrafficDatabase.getInstance() may be expensive - should cache instance
        val database = TrafficDatabase.getInstance(context)
        repository = TrafficRepositoryFactory.create(database.trafficDao())

        // Initialize traffic observer with a supervisor scope
        // THREAD: SupervisorJob() + Dispatchers.IO creates new scope - should reuse application scope
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        trafficObserver = TrafficObserver(context, scope)
        // PERF: XrayStatsObserver.start() may be slow - should be async
        xrayObserver = XrayStatsObserver(context, scope).also { it.start() }
    }

    // TODO: Add timeout handling for long-running operations
    // TODO: Consider adding batch insertion for multiple snapshots
    override suspend fun doWork(): Result {
        return try {
            AppLogger.d("Starting traffic logging work")

            // Collect current traffic snapshot (prefer Xray stats)
            // TODO: Add fallback mechanism if both observers fail
            val snapshot = run {
                val x = xrayObserver.collectNow()
                if (x.isConnected && (x.rxBytes > 0 || x.txBytes > 0)) x else trafficObserver.collectNow()
            }

            // Only log if there's meaningful traffic (connected and has data)
            if (snapshot.isConnected && (snapshot.rxBytes > 0 || snapshot.txBytes > 0)) {
                repository.insert(snapshot)
                AppLogger.i("Traffic logged: ${snapshot.formatDownloadSpeed()} / ${snapshot.formatUploadSpeed()}")
            } else {
                AppLogger.d("Skipping log: no active connection or traffic")
            }

            // Clean up old logs using configurable retention window
            val prefs = Preferences(applicationContext)
            val retentionDays = prefs.trafficRetentionDays
            val deleted = repository.deleteLogsOlderThanDays(retentionDays)
            if (deleted > 0) {
                AppLogger.i("Cleaned up $deleted old traffic logs (retention: $retentionDays days)")
            }

            Result.success()
        } catch (e: Exception) {
            AppLogger.e("Error logging traffic", e)
            Result.retry() // Retry on failure
        } finally {
            // Cleanup resources after work is done
            try {
                xrayObserver.stop()
                trafficObserver.stop()
                scope.cancel()
            } catch (e: Exception) {
                AppLogger.w("Error cleaning up TrafficWorker resources", e)
            }
        }
    }
}
