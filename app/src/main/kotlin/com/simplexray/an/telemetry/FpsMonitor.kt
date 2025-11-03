package com.simplexray.an.telemetry

import android.view.Choreographer

object FpsMonitor : Choreographer.FrameCallback {
    private var lastTimeNs = 0L
    private var frames = 0
    private var started = false

    fun start() {
        if (started) return
        started = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!started) return
        started = false
        Choreographer.getInstance().removeFrameCallback(this)
        // Reset state
        lastTimeNs = 0L
        frames = 0
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!started) return // Safety check
        if (lastTimeNs == 0L) lastTimeNs = frameTimeNanos
        frames++
        val deltaNs = frameTimeNanos - lastTimeNs
        if (deltaNs >= 1_000_000_000L) { // 1s
            val fps = frames * 1_000_000_000f / deltaNs
            TelemetryBus.onFps(fps)
            frames = 0
            lastTimeNs = frameTimeNanos
        }
        if (started) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}

