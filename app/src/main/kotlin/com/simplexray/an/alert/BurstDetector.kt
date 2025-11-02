package com.simplexray.an.alert

import android.content.Context
import com.simplexray.an.stats.BitrateBus
import com.simplexray.an.stats.BitratePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.sqrt

class BurstDetector(
    private val context: Context,
    private val scope: CoroutineScope,
    private val windowShort: Int = 15,
    private val windowLong: Int = 120
) {
    private var job: Job? = null
    private var lastBurstTs = 0L
    private var lastThrottleTs = 0L

    fun start() {
        NotificationEngine.ensureChannel(context)
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            val shortUp = Ring(windowShort)
            val shortDown = Ring(windowShort)
            val longDown = Ring(windowLong)
            BitrateBus.flow.collect { p ->
                shortUp.add(p.uplinkBps.toDouble())
                shortDown.add(p.downlinkBps.toDouble())
                longDown.add(p.downlinkBps.toDouble())
                checkBurst(p, shortUp, shortDown)
                checkThrottle(p, shortDown, longDown)
            }
        }
    }

    fun stop() { job?.cancel() }

    private fun checkBurst(p: BitratePoint, up: Ring, down: Ring) {
        val now = System.currentTimeMillis()
        val (muU, sigmaU) = up.stats()
        val (muD, sigmaD) = down.stats()
        val zThreshold = com.simplexray.an.config.ApiConfig.getAlertBurstZ(context)
        val minBurstBps = com.simplexray.an.config.ApiConfig.getAlertBurstMinBps(context)
        val coolDownMs = com.simplexray.an.config.ApiConfig.getAlertCooldownMs(context)
        val zU = if (sigmaU > 0) (p.uplinkBps - muU) / sigmaU else 0.0
        val zD = if (sigmaD > 0) (p.downlinkBps - muD) / sigmaD else 0.0
        if ((zU >= zThreshold && p.uplinkBps >= minBurstBps) || (zD >= zThreshold && p.downlinkBps >= minBurstBps)) {
            if (now - lastBurstTs > coolDownMs) {
                NotificationEngine.notifyBurst(context, p.uplinkBps, p.downlinkBps)
                lastBurstTs = now
            }
        }
    }

    private fun checkThrottle(p: BitratePoint, shortDown: Ring, longDown: Ring) {
        val now = System.currentTimeMillis()
        val shortMean = shortDown.mean()
        val longMean = longDown.mean()
        val throttleDropRatio = com.simplexray.an.config.ApiConfig.getAlertThrottleRatio(context)
        val minLongMean = com.simplexray.an.config.ApiConfig.getAlertMinLongMean(context).toDouble()
        val coolDownMs = com.simplexray.an.config.ApiConfig.getAlertCooldownMs(context)
        if (longMean >= minLongMean && shortMean < longMean * throttleDropRatio) {
            if (now - lastThrottleTs > coolDownMs) {
                NotificationEngine.notifyThrottle(context, longMean.toLong(), shortMean.toLong())
                lastThrottleTs = now
            }
        }
    }

    private class Ring(private val capacity: Int) {
        private val deque = ArrayDeque<Double>(capacity)
        private var sum = 0.0
        private var sumSq = 0.0

        fun add(v: Double) {
            deque.addLast(v)
            sum += v
            sumSq += v * v
            if (deque.size > capacity) {
                val old = deque.removeFirst()
                sum -= old
                sumSq -= old * old
            }
        }

        fun mean(): Double = if (deque.isEmpty()) 0.0 else sum / deque.size
        fun variance(): Double = if (deque.size < 2) 0.0 else (sumSq / deque.size) - (mean() * mean())
        fun std(): Double = sqrt(max(0.0, variance()))
        fun stats(): Pair<Double, Double> = mean() to std()
    }
}
