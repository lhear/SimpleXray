package com.simplexray.an.performance.monitor

sealed class SystemPerformanceMetrics {
    data class Basic(
        val cpuPercent: Float,
        val memoryMB: Int,
        val uptimeSeconds: Long,
        val batteryTemp: Float
    ) : SystemPerformanceMetrics()
}

