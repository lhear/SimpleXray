package com.simplexray.an.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object MemoryMonitor {
    private var job: Job? = null
    fun start(scope: CoroutineScope, intervalMs: Long = 2000L) {
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            val rt = Runtime.getRuntime()
            while (isActive) {
                val used = (rt.totalMemory() - rt.freeMemory()).toDouble() / (1024 * 1024)
                TelemetryBus.onMemMb(used.toFloat())
                delay(intervalMs)
            }
        }
    }
    fun stop() { job?.cancel() }
}

