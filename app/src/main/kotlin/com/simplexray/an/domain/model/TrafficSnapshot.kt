package com.simplexray.an.domain.model

/**
 * Represents a snapshot of network traffic at a specific moment in time.
 * Used for real-time monitoring and historical analysis.
 */
data class TrafficSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxRateMbps: Float = 0f,
    val txRateMbps: Float = 0f,
    val latencyMs: Long = -1L,
    val isConnected: Boolean = false
) {
    /**
     * Total bytes transferred (download + upload)
     */
    val totalBytes: Long
        get() = rxBytes + txBytes

    /**
     * Total rate in Mbps
     */
    val totalRateMbps: Float
        get() = rxRateMbps + txRateMbps

    /**
     * Human-readable download speed
     */
    fun formatDownloadSpeed(): String {
        return when {
            rxRateMbps >= 1000 -> "%.2f Gbps".format(rxRateMbps / 1000)
            rxRateMbps >= 1 -> "%.2f Mbps".format(rxRateMbps)
            else -> "%.0f Kbps".format(rxRateMbps * 1000)
        }
    }

    /**
     * Human-readable upload speed
     */
    fun formatUploadSpeed(): String {
        return when {
            txRateMbps >= 1000 -> "%.2f Gbps".format(txRateMbps / 1000)
            txRateMbps >= 1 -> "%.2f Mbps".format(txRateMbps)
            else -> "%.0f Kbps".format(txRateMbps * 1000)
        }
    }

    /**
     * Human-readable total data transferred
     */
    fun formatTotalData(): String {
        val total = totalBytes.toDouble()
        return when {
            total >= 1_073_741_824 -> "%.2f GB".format(total / 1_073_741_824)
            total >= 1_048_576 -> "%.2f MB".format(total / 1_048_576)
            total >= 1024 -> "%.2f KB".format(total / 1024)
            else -> "$total B"
        }
    }

    companion object {
        /**
         * Calculate the difference between two snapshots
         */
        fun calculateDelta(previous: TrafficSnapshot, current: TrafficSnapshot): TrafficSnapshot {
            val timeDeltaSeconds = (current.timestamp - previous.timestamp) / 1000.0
            if (timeDeltaSeconds <= 0) {
                return current
            }

            val rxDelta = maxOf(0L, current.rxBytes - previous.rxBytes)
            val txDelta = maxOf(0L, current.txBytes - previous.txBytes)

            // Convert bytes/sec to Mbps: (bytes/sec * 8) / 1_000_000
            val rxRateMbps = ((rxDelta / timeDeltaSeconds) * 8 / 1_000_000).toFloat()
            val txRateMbps = ((txDelta / timeDeltaSeconds) * 8 / 1_000_000).toFloat()

            return current.copy(
                rxRateMbps = rxRateMbps,
                txRateMbps = txRateMbps
            )
        }
    }
}

/**
 * Represents historical traffic data for charting
 */
data class TrafficHistory(
    val snapshots: List<TrafficSnapshot> = emptyList(),
    val maxRxRate: Float = 0f,
    val maxTxRate: Float = 0f,
    val avgRxRate: Float = 0f,
    val avgTxRate: Float = 0f
) {
    companion object {
        fun from(snapshots: List<TrafficSnapshot>): TrafficHistory {
            if (snapshots.isEmpty()) {
                return TrafficHistory()
            }

            val rxRates = snapshots.map { it.rxRateMbps }
            val txRates = snapshots.map { it.txRateMbps }

            return TrafficHistory(
                snapshots = snapshots,
                maxRxRate = rxRates.maxOrNull() ?: 0f,
                maxTxRate = txRates.maxOrNull() ?: 0f,
                avgRxRate = rxRates.average().toFloat(),
                avgTxRate = txRates.average().toFloat()
            )
        }
    }
}
