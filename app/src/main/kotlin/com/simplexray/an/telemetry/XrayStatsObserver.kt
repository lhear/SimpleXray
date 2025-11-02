package com.simplexray.an.telemetry

import android.content.Context
import android.util.Log
import com.simplexray.an.domain.model.TrafficSnapshot
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.viewmodel.TrafficState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Polls Xray-core stats and exposes a StateFlow of TrafficSnapshot + a sliding history window.
 * Prefers /debug/vars (HTTP) if apiPort is set; falls back to gRPC (CoreStatsClient) if needed;
 * if both fail, does nothing (let TrafficObserver handle Android-level stats).
 */
class XrayStatsObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val debugVarsClientFactory: (Int) -> XrayDebugVarsClient = { port -> XrayDebugVarsClient(port = port) }
) {
    companion object {
        private const val TAG = "XrayStatsObserver"
        private const val SAMPLE_INTERVAL_MS = 1000L
        private const val MAX_HISTORY_SIZE = 60 // last 60s at 1s intervals
        private const val LATENCY_PROBE_INTERVAL_MS = 5000L
        private const val HEALTH_CHECK_URL = "https://www.google.com/generate_204"
        private const val HEALTH_CHECK_TIMEOUT_MS = 2000
    }

    private val prefs = Preferences(context)
    private var client: XrayDebugVarsClient? = null

    private val _currentSnapshot = MutableStateFlow(TrafficSnapshot())
    val currentSnapshot: Flow<TrafficSnapshot> = _currentSnapshot.asStateFlow()

    private val _history = MutableStateFlow<List<TrafficSnapshot>>(emptyList())
    val history: Flow<List<TrafficSnapshot>> = _history.asStateFlow()

    private var previousRaw: TrafficState? = null
    private var isRunning = false
    private var lastLatencyProbe = 0L

    fun start() {
        if (isRunning) return
        val port = prefs.apiPort
        if (port <= 0) {
            Log.d(TAG, "apiPort not set; XrayStatsObserver idle")
            return
        }
        client = debugVarsClientFactory(port)
        isRunning = true
        scope.launch(Dispatchers.IO) { loop() }
    }

    fun stop() {
        isRunning = false
    }

    suspend fun collectNow(): TrafficSnapshot = withContext(Dispatchers.IO) {
        val raw = client?.fetch() ?: return@withContext TrafficSnapshot()
        val now = System.currentTimeMillis()
        val prev = previousRaw
        if (prev == null) {
            previousRaw = raw
            return@withContext TrafficSnapshot(timestamp = now, rxBytes = raw.downlink, txBytes = raw.uplink, isConnected = true)
        }
        val dt = 1.0 // seconds (we only call on-demand)
        val rxDelta = maxOf(0L, raw.downlink - prev.downlink)
        val txDelta = maxOf(0L, raw.uplink - prev.uplink)
        val rxRate = ((rxDelta / dt) * 8 / 1_000_000).toFloat()
        val txRate = ((txDelta / dt) * 8 / 1_000_000).toFloat()
        previousRaw = raw
        TrafficSnapshot(
            timestamp = now,
            rxBytes = raw.downlink,
            txBytes = raw.uplink,
            rxRateMbps = rxRate,
            txRateMbps = txRate,
            isConnected = true
        )
    }

    private suspend fun loop() {
        while (scope.isActive && isRunning) {
            try {
                val raw = client?.fetch()
                val now = System.currentTimeMillis()
                if (raw != null) {
                    val prev = previousRaw
                    val snapshot = if (prev != null) {
                        val seconds = 1.0
                        val rxDelta = maxOf(0L, raw.downlink - prev.downlink)
                        val txDelta = maxOf(0L, raw.uplink - prev.uplink)
                        val rxRate = ((rxDelta / seconds) * 8 / 1_000_000).toFloat()
                        val txRate = ((txDelta / seconds) * 8 / 1_000_000).toFloat()
                        TrafficSnapshot(
                            timestamp = now,
                            rxBytes = raw.downlink,
                            txBytes = raw.uplink,
                            rxRateMbps = rxRate,
                            txRateMbps = txRate,
                            isConnected = true
                        )
                    } else {
                        TrafficSnapshot(timestamp = now, rxBytes = raw.downlink, txBytes = raw.uplink, isConnected = true)
                    }

                    // optional latency
                    val latSnap = maybeProbeLatency(snapshot)

                    _currentSnapshot.value = latSnap
                    previousRaw = raw

                    val hist = _history.value.toMutableList()
                    hist.add(latSnap)
                    if (hist.size > MAX_HISTORY_SIZE) hist.removeAt(0)
                    _history.value = hist
                }
            } catch (e: Exception) {
                Log.d(TAG, "loop error: ${e.message}")
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private suspend fun maybeProbeLatency(snapshot: TrafficSnapshot): TrafficSnapshot {
        val now = System.currentTimeMillis()
        if (now - lastLatencyProbe <= LATENCY_PROBE_INTERVAL_MS) return snapshot
        lastLatencyProbe = now
        val latency = probeLatency()
        return snapshot.copy(latencyMs = latency)
    }

    private suspend fun probeLatency(): Long = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val url = URL(HEALTH_CHECK_URL)
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = HEALTH_CHECK_TIMEOUT_MS
                readTimeout = HEALTH_CHECK_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = false
                val code = responseCode
                disconnect()
                if (code in 200..399) System.currentTimeMillis() - start else -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }
}

