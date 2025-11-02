package com.simplexray.an.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.simplexray.an.domain.model.TrafficSnapshot

/**
 * Room entity for storing traffic history.
 * Stores periodic snapshots of network traffic for analysis and charting.
 */
@Entity(tableName = "traffic_logs")
data class TrafficEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Timestamp when the traffic was measured
     */
    val timestamp: Long,

    /**
     * Total bytes received (cumulative)
     */
    val rxBytes: Long,

    /**
     * Total bytes transmitted (cumulative)
     */
    val txBytes: Long,

    /**
     * Download rate in Mbps at this snapshot
     */
    val rxRateMbps: Float,

    /**
     * Upload rate in Mbps at this snapshot
     */
    val txRateMbps: Float,

    /**
     * Latency in milliseconds (-1 if not measured)
     */
    val latencyMs: Long = -1L,

    /**
     * Whether the connection was active
     */
    val isConnected: Boolean = false
)

/**
 * Extension functions to convert between domain model and entity
 */
fun TrafficSnapshot.toEntity(): TrafficEntity {
    return TrafficEntity(
        timestamp = timestamp,
        rxBytes = rxBytes,
        txBytes = txBytes,
        rxRateMbps = rxRateMbps,
        txRateMbps = txRateMbps,
        latencyMs = latencyMs,
        isConnected = isConnected
    )
}

fun TrafficEntity.toSnapshot(): TrafficSnapshot {
    return TrafficSnapshot(
        timestamp = timestamp,
        rxBytes = rxBytes,
        txBytes = txBytes,
        rxRateMbps = rxRateMbps,
        txRateMbps = txRateMbps,
        latencyMs = latencyMs,
        isConnected = isConnected
    )
}
