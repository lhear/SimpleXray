package com.simplexray.an.stats

import com.xray.app.stats.command.QueryStatsResponse

object StatsMath {
    data class DeltaResult(
        val upBytes: Long,
        val downBytes: Long,
        val nextMap: Map<String, Long>
    )

    fun computeDelta(prev: Map<String, Long>, stats: List<com.xray.app.stats.command.Stat>): DeltaResult {
        var up = 0L
        var down = 0L
        val next = prev.toMutableMap()
        for (s in stats) {
            val name = s.name
            val value = s.value
            val p = prev[name]
            if (p != null) {
                val d = (value - p).coerceAtLeast(0)
                if (name.endsWith("uplink")) up += d
                if (name.endsWith("downlink")) down += d
            }
            next[name] = value
        }
        return DeltaResult(up, down, next)
    }

    fun bytesPerSecond(bytes: Long, dtMs: Long): Long {
        val ms = dtMs.coerceAtLeast(1)
        return (bytes * 1000L) / ms
    }

    fun bitsPerSecond(bytes: Long, dtMs: Long): Long = bytesPerSecond(bytes, dtMs) * 8L
}

