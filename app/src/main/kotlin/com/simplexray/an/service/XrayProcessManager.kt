package com.simplexray.an.service

import android.content.Context
import com.simplexray.an.prefs.Preferences

/**
 * Minimal process manager facade to expose runtime ports used by Xray-core.
 * Currently proxies to Preferences for the API (stats) port.
 */
object XrayProcessManager {
    data class Ports(
        val statsPort: Int
    )

    @Volatile
    private var current: Ports? = null

    fun updateFrom(context: Context) {
        val prefs = Preferences(context)
        current = Ports(statsPort = prefs.apiPort)
    }

    val statsPort: Int
        get() = current?.statsPort ?: 10085
}

