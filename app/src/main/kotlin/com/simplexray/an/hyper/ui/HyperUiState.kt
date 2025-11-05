package com.simplexray.an.hyper.ui

import androidx.compose.runtime.Immutable

/**
 * Hyper UI State Models - optimized for ultra-low-latency updates
 * All models are @Immutable for Compose stability
 */

@Immutable
data class HyperSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val throughputMBps: Double = 0.0,
    val burstIntensity: Float = 0f, // 0.0-1.0, pulses on microbursts
    val isQuicDominant: Boolean = false,
    val jitterHistory: List<Float> = emptyList(), // Last 60 samples for sparkline
    val currentOutboundTag: String? = null,
    val sessionPinStatus: String? = null,
    val pathRaceWinner: String? = null,
    val pathStatus: PathStatus = PathStatus.UNKNOWN,
    val activePathCount: Int = 0,
    val rttSpreadMs: Int = 0,
    val dnsRaceResults: List<DnsRaceResult> = emptyList(),
    val quicWarmupState: QuicWarmupState = QuicWarmupState.IDLE,
    val quicWarmupTimeRemainingMs: Long = 0L,
    val packetBurstCount: Int = 0,
    val packetsPerSecond: Int = 0
)

@Immutable
enum class PathStatus {
    STABILIZING, // Green - path is stable
    RACING,      // Yellow - multiple paths racing
    DROPPING     // Red - paths dropping
}

@Immutable
data class DnsRaceResult(
    val resolver: String,
    val deltaMs: Long,
    val isWinner: Boolean = false,
    val ttlSeconds: Int = 0
)

@Immutable
enum class QuicWarmupState {
    IDLE,
    WARMING,
    READY,
    TIMEOUT
}

@Immutable
data class HyperMetrics(
    val lastOutboundSwitchReason: String? = null,
    val rttHistogram: RttHistogram = RttHistogram(),
    val jitterPercentile: Float = 0f,
    val quicRttSlope: Float = 0f,
    val burstCounter: Long = 0L
)

@Immutable
data class RttHistogram(
    val p50: Int = 0,
    val p90: Int = 0,
    val p99: Int = 0,
    val min: Int = 0,
    val max: Int = 0
)

