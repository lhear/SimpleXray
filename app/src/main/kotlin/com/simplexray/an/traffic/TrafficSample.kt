package com.simplexray.an.traffic

/**
 * Represents a traffic sample with timestamp, total bytes, and computed speeds.
 * Used for real-time monitoring and historical analysis.
 * 
 * All values are thread-safe and immutable.
 */
data class TrafficSample(
    val timestamp: Long = System.currentTimeMillis(),
    val rxBytesTotal: Long = 0L,
    val txBytesTotal: Long = 0L,
    val rxSpeedBps: Float = 0f,  // bytes per second
    val txSpeedBps: Float = 0f   // bytes per second
) {
    /**
     * Total bytes (download + upload)
     */
    val totalBytes: Long
        get() = rxBytesTotal + txBytesTotal

    /**
     * Total speed in bytes per second
     */
    val totalSpeedBps: Float
        get() = rxSpeedBps + txSpeedBps

    /**
     * Format download speed with appropriate units
     */
    fun formatRxSpeed(): String {
        return when {
            rxSpeedBps >= 1_048_576f -> "%.2f MB/s".format(rxSpeedBps / 1_048_576f)
            rxSpeedBps >= 1024f -> "%.2f KB/s".format(rxSpeedBps / 1024f)
            else -> "%.0f B/s".format(rxSpeedBps)
        }
    }

    /**
     * Format upload speed with appropriate units
     */
    fun formatTxSpeed(): String {
        return when {
            txSpeedBps >= 1_048_576f -> "%.2f MB/s".format(txSpeedBps / 1_048_576f)
            txSpeedBps >= 1024f -> "%.2f KB/s".format(txSpeedBps / 1024f)
            else -> "%.0f B/s".format(txSpeedBps)
        }
    }

    /**
     * Format total speed with appropriate units
     */
    fun formatTotalSpeed(): String {
        val total = totalSpeedBps
        return when {
            total >= 1_048_576f -> "%.2f MB/s".format(total / 1_048_576f)
            total >= 1024f -> "%.2f KB/s".format(total / 1024f)
            else -> "%.0f B/s".format(total)
        }
    }

    companion object {
        /**
         * Calculate delta between two samples, handling overflow and long pauses.
         * Returns new sample with computed speeds.
         */
        fun calculateDelta(previous: TrafficSample, current: TrafficSample): TrafficSample {
            val timeDeltaSeconds = (current.timestamp - previous.timestamp) / 1000.0
            
            // Handle invalid or zero time delta
            if (timeDeltaSeconds <= 0 || timeDeltaSeconds > 60.0) {
                // If pause > 60s, reset speeds to avoid incorrect calculations
                return current.copy(rxSpeedBps = 0f, txSpeedBps = 0f)
            }

            // Calculate byte deltas with overflow handling
            // Long values can overflow, so we use maxOf to ensure non-negative
            val rxDelta = maxOf(0L, current.rxBytesTotal - previous.rxBytesTotal)
            val txDelta = maxOf(0L, current.txBytesTotal - previous.txBytesTotal)

            // Handle potential overflow: if current < previous, assume counter reset
            // This can happen when system restarts or VPN reconnects
            val rxDeltaSafe = if (current.rxBytesTotal < previous.rxBytesTotal) {
                0L // Counter reset, can't calculate accurate delta
            } else {
                rxDelta
            }
            
            val txDeltaSafe = if (current.txBytesTotal < previous.txBytesTotal) {
                0L // Counter reset, can't calculate accurate delta
            } else {
                txDelta
            }

            // Calculate speeds in bytes per second
            val rxSpeedBps = (rxDeltaSafe / timeDeltaSeconds).toFloat()
            val txSpeedBps = (txDeltaSafe / timeDeltaSeconds).toFloat()

            return current.copy(
                rxSpeedBps = rxSpeedBps,
                txSpeedBps = txSpeedBps
            )
        }
    }
}

