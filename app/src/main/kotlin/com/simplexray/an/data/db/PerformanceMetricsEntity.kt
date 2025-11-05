package com.simplexray.an.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.simplexray.an.performance.model.PerformanceMetrics

/**
 * Room entity for storing historical performance metrics
 */
@Entity(
    tableName = "performance_metrics",
    indices = [Index(value = ["timestamp"]), Index(value = ["profileId", "timestamp"])]
)
data class PerformanceMetricsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val timestamp: Long,
    
    // Network metrics
    val uploadSpeed: Long,
    val downloadSpeed: Long,
    val totalUpload: Long,
    val totalDownload: Long,
    
    // Connection metrics
    val latency: Int,
    val jitter: Int,
    val packetLoss: Float,
    val connectionCount: Int,
    val activeConnectionCount: Int,
    
    // Resource metrics
    val cpuUsage: Float,
    val memoryUsage: Long,
    val nativeMemoryUsage: Long,
    
    // Quality metrics
    val connectionStability: Float,
    val overallQuality: String, // ConnectionQuality enum as string
    
    // Context
    val profileId: String? = null, // Performance profile in use
    val networkType: String? = null, // WiFi, Mobile, etc.
    val performanceModeEnabled: Boolean = false
)

/**
 * Convert PerformanceMetrics to Entity
 */
fun PerformanceMetrics.toEntity(
    profileId: String? = null,
    networkType: String? = null,
    performanceModeEnabled: Boolean = false
): PerformanceMetricsEntity {
    return PerformanceMetricsEntity(
        timestamp = this.timestamp,
        uploadSpeed = this.uploadSpeed,
        downloadSpeed = this.downloadSpeed,
        totalUpload = this.totalUpload,
        totalDownload = this.totalDownload,
        latency = this.latency,
        jitter = this.jitter,
        packetLoss = this.packetLoss,
        connectionCount = this.connectionCount,
        activeConnectionCount = this.activeConnectionCount,
        cpuUsage = this.cpuUsage,
        memoryUsage = this.memoryUsage,
        nativeMemoryUsage = this.nativeMemoryUsage,
        connectionStability = this.connectionStability,
        overallQuality = this.overallQuality.name,
        profileId = profileId,
        networkType = networkType,
        performanceModeEnabled = performanceModeEnabled
    )
}

/**
 * Convert Entity to PerformanceMetrics
 */
fun PerformanceMetricsEntity.toMetrics(): PerformanceMetrics {
    return PerformanceMetrics(
        uploadSpeed = this.uploadSpeed,
        downloadSpeed = this.downloadSpeed,
        totalUpload = this.totalUpload,
        totalDownload = this.totalDownload,
        latency = this.latency,
        jitter = this.jitter,
        packetLoss = this.packetLoss,
        connectionCount = this.connectionCount,
        activeConnectionCount = this.activeConnectionCount,
        cpuUsage = this.cpuUsage,
        memoryUsage = this.memoryUsage,
        nativeMemoryUsage = this.nativeMemoryUsage,
        connectionStability = this.connectionStability,
        overallQuality = try {
            com.simplexray.an.performance.model.ConnectionQuality.valueOf(this.overallQuality)
        } catch (e: Exception) {
            com.simplexray.an.performance.model.ConnectionQuality.Good
        },
        timestamp = this.timestamp
    )
}


