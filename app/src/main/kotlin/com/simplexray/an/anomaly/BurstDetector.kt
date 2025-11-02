package com.simplexray.an.anomaly

import com.simplexray.an.common.MovingAverage
import com.simplexray.an.domain.model.TrafficSnapshot

/**
 * Detects short-term bursts against a moving average baseline.
 */
class BurstDetector(
    window: Int = 10,
    private val multiplier: Float = 3f
) {
    private val rxAvg = MovingAverage(window)
    private val txAvg = MovingAverage(window)

    fun evaluate(snapshot: TrafficSnapshot): Boolean {
        val avgRx = rxAvg.add(snapshot.rxRateMbps)
        val avgTx = txAvg.add(snapshot.txRateMbps)
        val rxBurst = snapshot.rxRateMbps > avgRx * multiplier
        val txBurst = snapshot.txRateMbps > avgTx * multiplier
        return rxBurst || txBurst
    }

    fun reset() {
        rxAvg.clear(); txAvg.clear()
    }
}

