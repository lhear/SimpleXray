package com.simplexray.an.stats

data class BitratePoint(
    val timestampMs: Long,
    val uplinkBps: Long,
    val downlinkBps: Long
)

