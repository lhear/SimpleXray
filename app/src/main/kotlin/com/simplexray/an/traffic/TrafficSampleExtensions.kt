package com.simplexray.an.traffic

import com.simplexray.an.domain.model.TrafficSnapshot

/**
 * Extension functions to convert between TrafficSample and TrafficSnapshot
 * for backward compatibility with existing UI code.
 */

/**
 * Convert TrafficSample to TrafficSnapshot.
 * TrafficSnapshot uses Mbps for speeds, while TrafficSample uses Bps.
 */
fun TrafficSample.toTrafficSnapshot(): TrafficSnapshot {
    // Convert B/s to Mbps: (bytes/sec * 8) / 1_000_000
    val rxRateMbps = (rxSpeedBps * 8f / 1_000_000f)
    val txRateMbps = (txSpeedBps * 8f / 1_000_000f)
    
    return TrafficSnapshot(
        timestamp = timestamp,
        rxBytes = rxBytesTotal,
        txBytes = txBytesTotal,
        rxRateMbps = rxRateMbps,
        txRateMbps = txRateMbps,
        latencyMs = -1L, // Latency not available in TrafficSample
        isConnected = rxBytesTotal > 0 || txBytesTotal > 0
    )
}

/**
 * Convert TrafficSnapshot to TrafficSample.
 */
fun TrafficSnapshot.toTrafficSample(): TrafficSample {
    // Convert Mbps to B/s: (Mbps * 1_000_000) / 8
    val rxSpeedBps = (rxRateMbps * 1_000_000f / 8f)
    val txSpeedBps = (txRateMbps * 1_000_000f / 8f)
    
    return TrafficSample(
        timestamp = timestamp,
        rxBytesTotal = rxBytes,
        txBytesTotal = txBytes,
        rxSpeedBps = rxSpeedBps,
        txSpeedBps = txSpeedBps
    )
}

