package com.simplexray.an.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for performance metrics history
 */
@Dao
interface PerformanceMetricsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metric: PerformanceMetricsEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metrics: List<PerformanceMetricsEntity>)
    
    /**
     * Get metrics for a time range
     */
    @Query("SELECT * FROM performance_metrics WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getMetricsInRange(startTime: Long, endTime: Long): Flow<List<PerformanceMetricsEntity>>
    
    /**
     * Get metrics for today
     */
    @Query("SELECT * FROM performance_metrics WHERE timestamp >= :startOfDay ORDER BY timestamp ASC")
    suspend fun getMetricsForToday(startOfDay: Long): List<PerformanceMetricsEntity>
    
    /**
     * Get metrics for the last N days
     */
    @Query("SELECT * FROM performance_metrics WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    suspend fun getMetricsSince(startTime: Long): List<PerformanceMetricsEntity>
    
    /**
     * Get latest N metrics
     */
    @Query("SELECT * FROM performance_metrics ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestMetrics(limit: Int): List<PerformanceMetricsEntity>
    
    /**
     * Get metrics by profile
     */
    @Query("SELECT * FROM performance_metrics WHERE profileId = :profileId AND timestamp >= :startTime ORDER BY timestamp ASC")
    suspend fun getMetricsByProfile(profileId: String, startTime: Long): List<PerformanceMetricsEntity>
    
    /**
     * Get average metrics for time period
     */
    @Query("""
        SELECT 
            AVG(downloadSpeed) as avgDownload,
            AVG(uploadSpeed) as avgUpload,
            AVG(latency) as avgLatency,
            AVG(packetLoss) as avgPacketLoss,
            AVG(cpuUsage) as avgCpu,
            AVG(memoryUsage) as avgMemory
        FROM performance_metrics 
        WHERE timestamp >= :startTime AND timestamp <= :endTime
    """)
    suspend fun getAverageMetrics(startTime: Long, endTime: Long): PerformanceAverageMetrics?
    
    /**
     * Get metrics with anomalies (latency spikes, throughput drops)
     */
    @Query("""
        SELECT * FROM performance_metrics 
        WHERE timestamp >= :startTime 
        AND (latency > :latencyThreshold OR downloadSpeed < :throughputThreshold OR packetLoss > :packetLossThreshold)
        ORDER BY timestamp DESC
    """)
    suspend fun getAnomalies(
        startTime: Long,
        latencyThreshold: Int,
        throughputThreshold: Long,
        packetLossThreshold: Float
    ): List<PerformanceMetricsEntity>
    
    /**
     * Delete old metrics (older than cutoff)
     */
    @Query("DELETE FROM performance_metrics WHERE timestamp < :cutoff")
    suspend fun deleteOldMetrics(cutoff: Long)
    
    /**
     * Get count of metrics
     */
    @Query("SELECT COUNT(*) FROM performance_metrics")
    suspend fun getMetricsCount(): Int
}

/**
 * Result class for average metrics query
 * Column names must match the SELECT statement exactly
 */
data class PerformanceAverageMetrics(
    val avgDownload: Double?,
    val avgUpload: Double?,
    val avgLatency: Double?,
    val avgPacketLoss: Double?,
    val avgCpu: Double?,
    val avgMemory: Double?
)

