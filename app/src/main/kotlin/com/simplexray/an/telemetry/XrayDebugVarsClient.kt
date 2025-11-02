package com.simplexray.an.telemetry

import android.util.Log
import com.simplexray.an.viewmodel.TrafficState
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lightweight client to read Xray-core's /debug/vars endpoint.
 * Aggregates uplink/downlink totals across inbounds/outbounds.
 */
class XrayDebugVarsClient(
    private val host: String = "127.0.0.1",
    private val port: Int,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(500, TimeUnit.MILLISECONDS)
        .build()
) {
    companion object {
        private const val TAG = "XrayDebugVarsClient"
        private const val PATH = "/debug/vars"
    }

    /**
     * Fetch aggregated traffic state from /debug/vars.
     * Returns null if request or parsing fails.
     */
    fun fetch(): TrafficState? {
        val url = "http://$host:$port$PATH"
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                parseTraffic(JSONObject(body))
            }
        } catch (e: Exception) {
            Log.d(TAG, "fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Parse traffic from /debug/vars JSON by summing keys that contain
     * uplink/downlink traffic counters (works across versions).
     */
    private fun parseTraffic(root: JSONObject): TrafficState {
        var uplink = 0L
        var downlink = 0L

        fun scan(obj: Any?) {
            when (obj) {
                is JSONObject -> {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.opt(k)
                        val keyLower = k.lowercase()
                        if (keyLower.contains("uplink") && v is Number) {
                            uplink += v.toLong()
                        } else if (keyLower.contains("downlink") && v is Number) {
                            downlink += v.toLong()
                        } else {
                            scan(v)
                        }
                    }
                }
                is Iterable<*> -> obj.forEach { scan(it) }
                is Array<*> -> obj.forEach { scan(it) }
            }
        }

        scan(root)
        return TrafficState(uplink = uplink, downlink = downlink)
    }
}

