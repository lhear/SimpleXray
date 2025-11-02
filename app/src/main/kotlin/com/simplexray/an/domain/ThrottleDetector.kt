package com.simplexray.an.domain

import com.simplexray.an.domain.model.TrafficSnapshot

/**
 * Simple carrier throttling heuristic.
 * Flags throttling when average throughput in the last 30s drops below 30%
 * of the previous 30s while latency rises by >50% (and connection remains active).
 */
class ThrottleDetector {
    data class Result(
        val isThrottled: Boolean,
        val message: String? = null,
        val severity: Int = 0 // 0..3
    )

    fun evaluate(history: List<TrafficSnapshot>): Result {
        if (history.size < 60) return Result(false)

        val last30 = history.takeLast(30)
        val prev30 = history.dropLast(30).takeLast(30)
        if (last30.isEmpty() || prev30.isEmpty()) return Result(false)

        val avgRxLast = last30.map { it.rxRateMbps }.average().toFloat()
        val avgTxLast = last30.map { it.txRateMbps }.average().toFloat()
        val avgRxPrev = prev30.map { it.rxRateMbps }.average().toFloat()
        val avgTxPrev = prev30.map { it.txRateMbps }.average().toFloat()

        val avgLatLast = last30.map { it.latencyMs }.filter { it > 0 }.average().toFloat()
        val avgLatPrev = prev30.map { it.latencyMs }.filter { it > 0 }.average().toFloat()

        val prevRate = (avgRxPrev + avgTxPrev).coerceAtLeast(0.01f)
        val lastRate = avgRxLast + avgTxLast
        val rateRatio = lastRate / prevRate

        val latRatio = if (avgLatPrev > 0 && avgLatLast > 0) {
            avgLatLast / avgLatPrev
        } else 1f

        val throttled = rateRatio < 0.3f && latRatio > 1.5f
        if (!throttled) return Result(false)

        val severity = when {
            rateRatio < 0.15f && latRatio > 2.0f -> 3
            rateRatio < 0.22f && latRatio > 1.8f -> 2
            else -> 1
        }
        val msg = "Possible carrier throttling: throughput down ${(100 - rateRatio * 100).toInt()}%, latency up ${(latRatio * 100 - 100).toInt()}%"
        return Result(true, msg, severity)
    }
}

