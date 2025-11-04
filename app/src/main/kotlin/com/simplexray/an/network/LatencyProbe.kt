package com.simplexray.an.network

import android.content.Context
import com.simplexray.an.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared utility for latency probing that can be used by both TrafficObserver and XrayStatsObserver.
 * Uses configurable endpoint from preferences to work in restricted regions.
 */
object LatencyProbe {
    private const val DEFAULT_ENDPOINT = "https://www.google.com/generate_204"
    private const val DEFAULT_TIMEOUT_MS = 2000
    
    /**
     * Probe latency by making a lightweight HTTP request to a configurable endpoint.
     * @param context Context to access preferences
     * @param timeoutMs Optional timeout in milliseconds (default: 2000ms)
     * @return Latency in milliseconds, or -1L if probe failed
     */
    suspend fun probe(context: Context, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Long = withContext(Dispatchers.IO) {
        try {
            val prefs = Preferences(context)
            val endpoint = prefs.latencyProbeEndpoint.takeIf { it.isNotEmpty() } ?: DEFAULT_ENDPOINT
            
            val startTime = System.currentTimeMillis()
            val url = URL(endpoint)
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                instanceFollowRedirects = false
                val code = responseCode
                disconnect()
                if (code in 200..399) {
                    System.currentTimeMillis() - startTime
                } else {
                    -1L
                }
            }
        } catch (e: Exception) {
            -1L
        }
    }
}

