package com.simplexray.an.stats

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class MockTrafficObserver(
    private val intervalMs: Long = 1000L
) {
    fun live(): Flow<BitratePoint> = channelFlow {
        var t = 0.0
        val rng = Random(System.currentTimeMillis())
        while (isActive) {
            val ts = System.currentTimeMillis()
            val base = abs(sin(t))
            val up = (base * 2_000_000L + rng.nextLong(0, 200_000))
            val down = (base * 4_000_000L + rng.nextLong(0, 400_000))
            trySend(BitratePoint(ts, up.toLong(), down.toLong()))
            t += (2 * PI) / 20.0
            delay(intervalMs)
        }
    }
}

