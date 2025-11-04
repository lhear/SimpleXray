package com.simplexray.an.data.repository

import android.content.Context
import com.simplexray.an.common.AppLogger
import com.simplexray.an.data.db.PerformanceMetricsDao
import com.simplexray.an.data.db.PerformanceMetricsEntity
import com.simplexray.an.data.db.TrafficDatabase
import com.simplexray.an.data.db.toEntity
import com.simplexray.an.data.db.toMetrics
import com.simplexray.an.performance.model.PerformanceMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Repository for managing performance metrics history
 * 
 * TODO: Add metrics aggregation for summary statistics
 * TODO: Implement data compression for old metrics
 * TODO: Add metrics export functionality
 * TODO: Consider adding real-time metrics streaming
 */
class PerformanceMetricsRepository(context: Context) {
    private val dao: PerformanceMetricsDao = TrafficDatabase.getInstance(context).performanceMetricsDao()
    
    /**
     * Save a performance metric
     */
    suspend fun saveMetric(
        metric: PerformanceMetrics,
        profileId: String? = null,
        networkType: String? = null,
        performanceModeEnabled: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            try {
                dao.insert(metric.toEntity(profileId, networkType, performanceModeEnabled))
            } catch (e: Exception) {
                AppLogger.e("Failed to save performance metric", e)
            }
        }
    }
    
    /**
     * Save multiple metrics
     */
    suspend fun saveMetrics(
        metrics: List<PerformanceMetrics>,
        profileId: String? = null,
        networkType: String? = null,
        performanceModeEnabled: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            try {
                val entities = metrics.map { it.toEntity(profileId, networkType, performanceModeEnabled) }
                dao.insertAll(entities)
            } catch (e: Exception) {
                AppLogger.e("Failed to save performance metrics", e)
            }
        }
    }
    
    /**
     * Get metrics for time range
     */
    fun getMetricsInRange(startTime: Long, endTime: Long): Flow<List<PerformanceMetrics>> {
        return dao.getMetricsInRange(startTime, endTime).map { entities ->
            entities.map { it.toMetrics() }
        }
    }
    
    /**
     * Get metrics for today
     */
    suspend fun getMetricsForToday(): List<PerformanceMetrics> {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis
            
            dao.getMetricsForToday(startOfDay).map { it.toMetrics() }
        }
    }
    
    /**
     * Get metrics for last N days
     */
    suspend fun getMetricsForLastDays(days: Int): List<PerformanceMetrics> {
        return withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            dao.getMetricsSince(cutoff).map { it.toMetrics() }
        }
    }
    
    /**
     * Get metrics by profile
     */
    suspend fun getMetricsByProfile(profileId: String, days: Int = 7): List<PerformanceMetrics> {
        return withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            dao.getMetricsByProfile(profileId, cutoff).map { it.toMetrics() }
        }
    }
    
    /**
     * Get latest metrics
     */
    suspend fun getLatestMetrics(limit: Int = 100): List<PerformanceMetrics> {
        return withContext(Dispatchers.IO) {
            dao.getLatestMetrics(limit).map { it.toMetrics() }
        }
    }
    
    /**
     * Get average metrics for period
     */
    suspend fun getAverageMetrics(startTime: Long, endTime: Long): com.simplexray.an.data.db.PerformanceAverageMetrics? {
        return withContext(Dispatchers.IO) {
            dao.getAverageMetrics(startTime, endTime)
        }
    }
    
    /**
     * Get anomalies
     */
    suspend fun getAnomalies(
        days: Int = 7,
        latencyThreshold: Int = 200,
        throughputThreshold: Long = 100_000, // 100 KB/s
        packetLossThreshold: Float = 5f
    ): List<PerformanceMetrics> {
        return withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            dao.getAnomalies(cutoff, latencyThreshold, throughputThreshold, packetLossThreshold)
                .map { it.toMetrics() }
        }
    }
    
    /**
     * Clean up old metrics (keep last 30 days)
     * TODO: Make retention period configurable
     * TODO: Add progress reporting for large cleanup operations
     */
    suspend fun cleanupOldMetrics() {
        withContext(Dispatchers.IO) {
            try {
                val cutoff = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
                dao.deleteOldMetrics(cutoff)
            } catch (e: Exception) {
                AppLogger.e("Failed to cleanup old metrics", e)
            }
        }
    }
}

