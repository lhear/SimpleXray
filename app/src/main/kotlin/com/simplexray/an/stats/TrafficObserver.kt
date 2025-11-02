package com.simplexray.an.stats

import com.xray.app.stats.command.QueryStatsRequest
import com.xray.app.stats.command.StatsServiceGrpcKt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlin.math.max

class TrafficObserver(
    private val stub: StatsServiceGrpcKt.StatsServiceCoroutineStub,
    private val initialIntervalMs: Long = 1000L,
    private val maxIntervalMs: Long = 5000L,
    private val intervalProvider: (() -> Long)? = null,
    private val deadlineProvider: (() -> Long)? = null
) {
    private val pattern = "(inbound|outbound)>>>.*>>>traffic>>>(uplink|downlink)"

    fun live(): Flow<BitratePoint> = channelFlow {
        var lastValues = mutableMapOf<String, Long>()
        var lastTs = System.currentTimeMillis()
        var interval = initialIntervalMs
        while (isActive) {
            val start = System.currentTimeMillis()
            try {
                val dl = (deadlineProvider?.invoke() ?: 2000L).coerceAtLeast(500L)
                val resp = stub.withDeadlineAfter(dl, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .queryStats(QueryStatsRequest.newBuilder()
                    .setPattern(pattern)
                    .setReset(false)
                    .build())
                val now = System.currentTimeMillis()
                val dtMs = max(1, now - lastTs)
                val delta = StatsMath.computeDelta(lastValues, resp.statList)
                lastValues = delta.nextMap.toMutableMap()
                lastTs = now
                val upBps = StatsMath.bitsPerSecond(delta.upBytes, dtMs)
                val downBps = StatsMath.bitsPerSecond(delta.downBytes, dtMs)
                trySend(BitratePoint(now, upBps, downBps))
                com.simplexray.an.telemetry.TelemetryBus.onPollLatency(System.currentTimeMillis() - start)
                interval = intervalProvider?.invoke() ?: initialIntervalMs
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Backoff on error
                interval = maxIntervalMs
            }
            val spent = System.currentTimeMillis() - start
            val sleep = max(0L, interval - spent)
            delay(sleep)
        }
    }
}
