package com.simplexray.an.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TelemetryBus {
    private val _fps = MutableStateFlow(0f)
    private val _memMb = MutableStateFlow(0f)
    private val _pollLatencyMs = MutableStateFlow(0f)

    val fps: StateFlow<Float> = _fps
    val memMb: StateFlow<Float> = _memMb
    val pollLatencyMs: StateFlow<Float> = _pollLatencyMs

    fun onFps(value: Float) { _fps.value = value }
    fun onMemMb(value: Float) { _memMb.value = value }
    fun onPollLatency(ms: Long) { _pollLatencyMs.value = ms.toFloat() }
}

