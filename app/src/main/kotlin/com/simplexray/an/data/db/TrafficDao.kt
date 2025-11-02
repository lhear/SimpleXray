package com.simplexray.an.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for traffic logs.
 * Provides methods to query and manipulate traffic history in the database.
 */
@Dao
interface TrafficDao {

    /**
     * Insert a new traffic log entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(traffic: TrafficEntity)

    /**
     * Insert multiple traffic log entries
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(traffic: List<TrafficEntity>)

    /**
     * Get all traffic logs ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM traffic_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<TrafficEntity>>

    /**
     * Get traffic logs for a specific time range
     */
    @Query("SELECT * FROM traffic_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<TrafficEntity>>

    /**
     * Get traffic logs for the last N hours
     */
    @Query("SELECT * FROM traffic_logs WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getLogsForLast(startTime: Long): Flow<List<TrafficEntity>>

    /**
     * Get traffic logs for today
     */
    @Query("SELECT * FROM traffic_logs WHERE timestamp >= :startOfDay ORDER BY timestamp ASC")
    fun getLogsForToday(startOfDay: Long): Flow<List<TrafficEntity>>

    /**
     * Get the latest N traffic logs
     */
    @Query("SELECT * FROM traffic_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestLogs(limit: Int): Flow<List<TrafficEntity>>

    /**
     * Get total bytes transferred today
     */
    @Query("""
        SELECT COALESCE(MAX(rxBytes) - MIN(rxBytes), 0) as rxTotal,
               COALESCE(MAX(txBytes) - MIN(txBytes), 0) as txTotal
        FROM traffic_logs
        WHERE timestamp >= :startOfDay
    """)
    suspend fun getTotalBytesToday(startOfDay: Long): TotalBytes?

    /**
     * Get peak speeds for a time range
     */
    @Query("""
        SELECT MAX(rxRateMbps) as maxRx,
               MAX(txRateMbps) as maxTx,
               AVG(rxRateMbps) as avgRx,
               AVG(txRateMbps) as avgTx
        FROM traffic_logs
        WHERE timestamp >= :startTime AND timestamp <= :endTime
    """)
    suspend fun getSpeedStats(startTime: Long, endTime: Long): SpeedStats?

    /**
     * Get average latency for a time range
     */
    @Query("""
        SELECT AVG(latencyMs) as avgLatency
        FROM traffic_logs
        WHERE timestamp >= :startTime
          AND timestamp <= :endTime
          AND latencyMs > 0
    """)
    suspend fun getAverageLatency(startTime: Long, endTime: Long): Long?

    /**
     * Delete logs older than a certain timestamp
     */
    @Query("DELETE FROM traffic_logs WHERE timestamp < :timestamp")
    suspend fun deleteLogsOlderThan(timestamp: Long): Int

    /**
     * Delete all logs
     */
    @Query("DELETE FROM traffic_logs")
    suspend fun deleteAll()

    /**
     * Get count of logs
     */
    @Query("SELECT COUNT(*) FROM traffic_logs")
    suspend fun getCount(): Int

    /**
     * Get logs count for today
     */
    @Query("SELECT COUNT(*) FROM traffic_logs WHERE timestamp >= :startOfDay")
    suspend fun getCountToday(startOfDay: Long): Int
}

/**
 * Data class for total bytes query result
 */
data class TotalBytes(
    val rxTotal: Long,
    val txTotal: Long
) {
    val total: Long get() = rxTotal + txTotal
}

/**
 * Data class for speed statistics query result
 */
data class SpeedStats(
    val maxRx: Float,
    val maxTx: Float,
    val avgRx: Float,
    val avgTx: Float
)
