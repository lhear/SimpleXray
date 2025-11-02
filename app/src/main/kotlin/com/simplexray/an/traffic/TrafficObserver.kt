package com.simplexray.an.traffic

import android.content.Context
import android.util.Log
import com.simplexray.an.service.XrayProcessManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real-time traffic observer using Xray /debug/vars via callbackFlow.
 * Samples every 500ms, converts byte deltas to Mbps, protects against negatives,
 * and exposes a 60-sample sliding history.
 */
class TrafficObserver(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TrafficObserver2"
        private const val SAMPLE_MS = 500L
        private const val HISTORY_SIZE = 120 // 60s @ 500ms
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(400, TimeUnit.MILLISECONDS)
        .readTimeout(400, TimeUnit.MILLISECONDS)
        .build()

    private val _history = MutableStateFlow<List<TrafficSnapshot>>(emptyList())
    val history: Flow<List<TrafficSnapshot>> = _history.asStateFlow()

    val currentSnapshot: Flow<TrafficSnapshot> = callbackFlow {
        // Refresh manager ports
        XrayProcessManager.updateFrom(context)

        var lastBytesDown = -1L
        var lastBytesUp = -1L
        var lastTs = 0L

        val job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val port = XrayProcessManager.statsPort
                    val url = "http://127.0.0.1:$port/debug/vars"
                    val req = Request.Builder().url(url).get().build()
                    val now = System.currentTimeMillis()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string()
                    resp.close()
                    if (body != null) {
                        val totals = parseTotals(JSONObject(body))
                        val dt = (now - lastTs) / 1000.0
                        val snapshot = if (lastTs > 0 && dt > 0) {
                            val rxDelta = (totals.down - lastBytesDown).coerceAtLeast(0L)
                            val txDelta = (totals.up - lastBytesUp).coerceAtLeast(0L)
                            val rxRate = ((rxDelta / dt) * 8 / 1_000_000).toFloat()
                            val txRate = ((txDelta / dt) * 8 / 1_000_000).toFloat()
                            TrafficSnapshot(
                                timestamp = now,
                                rxBytes = totals.down,
                                txBytes = totals.up,
                                rxRateMbps = rxRate,
                                txRateMbps = txRate,
                                isConnected = true
                            )
                        } else {
                            TrafficSnapshot(timestamp = now, rxBytes = totals.down, txBytes = totals.up, isConnected = true)
                        }

                        lastTs = now
                        lastBytesDown = totals.down
                        lastBytesUp = totals.up

                        trySend(snapshot).onFailure { /* drop */ }

                        // update history
                        val hist = _history.value.toMutableList()
                        hist.add(snapshot)
                        if (hist.size > HISTORY_SIZE) hist.removeAt(0)
                        _history.value = hist
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "sample error: ${e.message}")
                }
                delay(SAMPLE_MS)
            }
        }

        awaitClose { job.cancel() }
    }

    private data class Totals(val up: Long, val down: Long)

    private fun parseTotals(root: JSONObject): Totals {
        var up = 0L
        var down = 0L
        fun scan(obj: Any?) {
            when (obj) {
                is JSONObject -> {
                    val it = obj.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        val v = obj.opt(k)
                        val kl = k.lowercase()
                        if ((kl.contains("uplink") || kl.endsWith(".uplink")) && v is Number) {
                            up += v.toLong()
                        } else if ((kl.contains("downlink") || kl.endsWith(".downlink")) && v is Number) {
                            down += v.toLong()
                        } else {
                            scan(v)
                        }
                    }
                }
            }
        }
        scan(root)
        return Totals(up, down)
    }
}

