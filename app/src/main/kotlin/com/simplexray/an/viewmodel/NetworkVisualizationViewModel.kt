package com.simplexray.an.viewmodel

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.common.CoreStatsClient
import com.simplexray.an.performance.monitor.PerformanceMonitor
import com.simplexray.an.protocol.visualization.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel for Network Visualization screen with real-time Xray core integration
 */
class NetworkVisualizationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private var coreStatsClient: CoreStatsClient? = null
    private val performanceMonitor = PerformanceMonitor(application, 1000, coreStatsClient)

    private val _topology = MutableStateFlow(createInitialTopology())
    val topology: StateFlow<NetworkTopology> = _topology.asStateFlow()

    private val _latencyHistory = MutableStateFlow<List<TimeSeriesData>>(emptyList())
    val latencyHistory: StateFlow<List<TimeSeriesData>> = _latencyHistory.asStateFlow()

    private val _bandwidthHistory = MutableStateFlow<List<TimeSeriesData>>(emptyList())
    val bandwidthHistory: StateFlow<List<TimeSeriesData>> = _bandwidthHistory.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    init {
        startMonitoring()
    }

    private fun createInitialTopology(): NetworkTopology {
        val nodes = listOf(
            NetworkNode(
                id = "client",
                label = "Your Device",
                type = NetworkNode.NodeType.CLIENT,
                status = NetworkNode.NodeStatus.ACTIVE,
                position = Offset(100f, 300f),
                metadata = mapOf("device" to "Android")
            ),
            NetworkNode(
                id = "proxy",
                label = "Proxy Server",
                type = NetworkNode.NodeType.PROXY_SERVER,
                status = NetworkNode.NodeStatus.ACTIVE,
                position = Offset(400f, 300f),
                metadata = mapOf("location" to "Singapore", "ip" to "192.168.1.1")
            ),
            NetworkNode(
                id = "dns",
                label = "DNS Server",
                type = NetworkNode.NodeType.DNS_SERVER,
                status = NetworkNode.NodeStatus.ACTIVE,
                position = Offset(400f, 150f),
                metadata = mapOf("server" to "8.8.8.8")
            ),
            NetworkNode(
                id = "target",
                label = "Target Server",
                type = NetworkNode.NodeType.TARGET_SERVER,
                status = NetworkNode.NodeStatus.ACTIVE,
                position = Offset(700f, 300f),
                metadata = mapOf("domain" to "example.com")
            )
        )

        val connections = listOf(
            NetworkConnection(
                fromNodeId = "client",
                toNodeId = "proxy",
                latency = 50,
                bandwidth = 10_000_000,
                protocol = "VLESS",
                status = NetworkConnection.ConnectionStatus.ESTABLISHED
            ),
            NetworkConnection(
                fromNodeId = "client",
                toNodeId = "dns",
                latency = 30,
                bandwidth = 1_000_000,
                protocol = "DNS",
                status = NetworkConnection.ConnectionStatus.ESTABLISHED
            ),
            NetworkConnection(
                fromNodeId = "proxy",
                toNodeId = "target",
                latency = 100,
                bandwidth = 50_000_000,
                protocol = "TCP",
                status = NetworkConnection.ConnectionStatus.ESTABLISHED
            )
        )

        return NetworkTopology(nodes, connections)
    }

    /**
     * Set CoreStatsClient for real-time data
     */
    fun setCoreStatsClient(client: CoreStatsClient?) {
        coreStatsClient = client
        performanceMonitor.setCoreStatsClient(client)
    }

    fun startMonitoring() {
        if (_isMonitoring.value) return

        _isMonitoring.value = true
        performanceMonitor.start()

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val latencyPoints = mutableListOf<GraphDataPoint>()
            val uploadPoints = mutableListOf<GraphDataPoint>()
            val downloadPoints = mutableListOf<GraphDataPoint>()

            // Collect real-time metrics from PerformanceMonitor
            performanceMonitor.currentMetrics.collect { metrics ->
                if (!_isMonitoring.value) return@collect

                val currentTime = System.currentTimeMillis()

                // Real latency data from metrics
                latencyPoints.add(
                    GraphDataPoint(
                        timestamp = currentTime,
                        value = metrics.latency.toFloat()
                    )
                )

                // Real upload speed data (convert bytes/s to KB/s)
                uploadPoints.add(
                    GraphDataPoint(
                        timestamp = currentTime,
                        value = (metrics.uploadSpeed / 1024f)
                    )
                )

                // Real download speed data (convert bytes/s to KB/s)
                downloadPoints.add(
                    GraphDataPoint(
                        timestamp = currentTime,
                        value = (metrics.downloadSpeed / 1024f)
                    )
                )

                // Keep last 60 data points (1 minute if updating every second)
                if (latencyPoints.size > 60) {
                    latencyPoints.removeAt(0)
                    uploadPoints.removeAt(0)
                    downloadPoints.removeAt(0)
                }

                _latencyHistory.value = listOf(
                    TimeSeriesData(
                        name = "Latency",
                        dataPoints = latencyPoints.toList(),
                        unit = "ms",
                        color = 0xFF2196F3
                    )
                )

                _bandwidthHistory.value = listOf(
                    TimeSeriesData(
                        name = "Upload",
                        dataPoints = uploadPoints.toList(),
                        unit = "KB/s",
                        color = 0xFFFF9800
                    ),
                    TimeSeriesData(
                        name = "Download",
                        dataPoints = downloadPoints.toList(),
                        unit = "KB/s",
                        color = 0xFF4CAF50
                    )
                )

                // Update topology with real latency from metrics
                updateTopologyStats(metrics.latency)
            }
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        performanceMonitor.stop()
    }

    private fun updateTopologyStats(realLatency: Int = 0) {
        val currentTopology = _topology.value
        val updatedConnections = currentTopology.connections.map { connection ->
            // Use real latency when available, otherwise use previous value with small variation
            val newLatency = if (realLatency > 0) {
                when (connection.fromNodeId) {
                    "client" -> realLatency / 2 // Client to proxy is about half
                    "proxy" -> realLatency // Proxy to target
                    else -> connection.latency + Random.nextInt(-5, 5)
                }
            } else {
                connection.latency + Random.nextInt(-5, 5)
            }

            connection.copy(
                latency = newLatency.coerceAtLeast(1),
                bandwidth = connection.bandwidth + Random.nextLong(-1000000, 1000000)
            )
        }

        _topology.value = currentTopology.copy(connections = updatedConnections)
    }

    fun refreshTopology() {
        viewModelScope.launch {
            // Simulate loading
            delay(500)
            _topology.value = createInitialTopology()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
        performanceMonitor.stop()
    }
}
