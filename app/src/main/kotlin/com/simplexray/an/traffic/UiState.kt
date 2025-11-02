package com.simplexray.an.traffic

/**
 * Minimal UI state for traffic visualizations.
 * - currentRxMbps/currentTxMbps: latest Mbps values
 * - history: last 120 samples (timestamp + rx/tx Mbps)
 * - latencyMs/jitterMs: reserved for expansion
 */
data class UiState(
    val currentRxMbps: Float = 0f,
    val currentTxMbps: Float = 0f,
    val history: List<TrafficSample> = emptyList(),
    val latencyMs: Long? = null,
    val jitterMs: Long? = null
) {
    fun trimmed(): UiState = copy(history = history.takeLast(120))
}

data class TrafficSample(
    val timestamp: Long,
    val rxMbps: Float,
    val txMbps: Float
)


