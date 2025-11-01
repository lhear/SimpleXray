package com.simplexray.an.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.common.CoreStatsClient
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.MetricsHistory
import com.simplexray.an.performance.monitor.PerformanceMonitor
import com.simplexray.an.performance.monitor.ConnectionAnalyzer
import com.simplexray.an.performance.monitor.Bottleneck
import com.simplexray.an.protocol.visualization.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset

/**
 * Unified ViewModel combining Performance Monitoring and Network Visualization
 * with real-time Xray core integration
 */
class UnifiedMonitoringViewModel(
    application: Application,
    private var coreStatsClient: CoreStatsClient? = null
) : AndroidViewModel(application) {

    private val performanceMonitor = PerformanceMonitor(application, 1000, coreStatsClient)
    private val connectionAnalyzer = ConnectionAnalyzer()

    // Performance Monitoring State
    private val _currentMetrics = MutableStateFlow(PerformanceMetrics())
    val currentMetrics: StateFlow<PerformanceMetrics> = _currentMetrics.asStateFlow()

    private val _metricsHistory = MutableStateFlow(MetricsHistory())
    val metricsHistory: StateFlow<MetricsHistory> = _metricsHistory.asStateFlow()

    private val _bottlenecks = MutableStateFlow<List<Bottleneck>>(emptyList())
    val bottlenecks: StateFlow<List<Bottleneck>> = _bottlenecks.asStateFlow()

    // Network Visualization State
    private val _topology = MutableStateFlow(createInitialTopology())
    val topology: StateFlow<NetworkTopology> = _topology.asStateFlow()

    private val _latencyHistory = MutableStateFlow<List<TimeSeriesData>>(emptyList())
    val latencyHistory: StateFlow<List<TimeSeriesData>> = _latencyHistory.asStateFlow()

    private val _bandwidthHistory = MutableStateFlow<List<TimeSeriesData>>(emptyList())
    val bandwidthHistory: StateFlow<List<TimeSeriesData>> = _bandwidthHistory.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    // Data points for charts
    private val latencyPoints = mutableListOf<GraphDataPoint>()
    private val uploadPoints = mutableListOf<GraphDataPoint>()
    private val downloadPoints = mutableListOf<GraphDataPoint>()

    init {
        startMonitoring()
    }

    /**
     * Set CoreStatsClient for real-time data from Xray core
     */
    fun setCoreStatsClient(client: CoreStatsClient?) {
        coreStatsClient = client
        performanceMonitor.setCoreStatsClient(client)
    }

    /**
     * Start unified monitoring (performance + network visualization)
     */
    fun startMonitoring() {
        if (_isMonitoring.value) return

        _isMonitoring.value = true
        performanceMonitor.start()

        viewModelScope.launch {
            // Collect real-time metrics from PerformanceMonitor
            performanceMonitor.currentMetrics.collect { metrics ->
                if (!_isMonitoring.value) return@collect

                val currentTime = System.currentTimeMillis()

                // Update performance metrics
                _currentMetrics.value = metrics

                // Detect bottlenecks
                _bottlenecks.value = connectionAnalyzer.detectBottlenecks(metrics)

                // Update visualization data
                updateVisualizationData(metrics, currentTime)
            }
        }

        viewModelScope.launch {
            // Collect metrics history from PerformanceMonitor
            performanceMonitor.metricsHistory.collect { history ->
                _metricsHistory.value = history
            }
        }
    }

    /**
     * Update network visualization data from performance metrics
     */
    private fun updateVisualizationData(metrics: PerformanceMetrics, currentTime: Long) {
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

        // Update topology with real latency and bandwidth from metrics
        updateTopologyStats(metrics)
    }

    /**
     * Update network topology with real metrics
     */
    private fun updateTopologyStats(metrics: PerformanceMetrics) {
        val currentTopology = _topology.value
        val updatedConnections = currentTopology.connections.map { connection ->
            when (connection.fromNodeId) {
                "client" -> connection.copy(
                    latency = (metrics.latency / 2).coerceAtLeast(1),
                    bandwidth = metrics.uploadSpeed
                )
                "proxy" -> connection.copy(
                    latency = metrics.latency,
                    bandwidth = metrics.downloadSpeed
                )
                else -> connection
            }
        }

        _topology.value = currentTopology.copy(connections = updatedConnections)
    }

    /**
     * Create initial network topology
     */
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
                label = "Xray Proxy",
                type = NetworkNode.NodeType.PROXY_SERVER,
                status = NetworkNode.NodeStatus.ACTIVE,
                position = Offset(400f, 300f),
                metadata = mapOf("protocol" to "VLESS/VMESS")
            ),
            NetworkNode(
                id = "dns",
                label = "DNS Server",
                type = NetworkNode.NodeType.DNS_SERVER,
                status = NetworkNode.NodeStatus.ACTIVE,
                position = Offset(400f, 150f),
                metadata = mapOf("server" to "Configured DNS")
            ),
            NetworkNode(
                id = "target",
                label = "Target Server",
                type = NetworkNode.NodeType.TARGET_SERVER,
                status = NetworkNode.NodeStatus.ACTIVE,
                position = Offset(700f, 300f),
                metadata = mapOf("type" to "Internet")
            )
        )

        val connections = listOf(
            NetworkConnection(
                fromNodeId = "client",
                toNodeId = "proxy",
                latency = 50,
                bandwidth = 0,
                protocol = "VLESS/VMESS",
                status = NetworkConnection.ConnectionStatus.ESTABLISHED
            ),
            NetworkConnection(
                fromNodeId = "client",
                toNodeId = "dns",
                latency = 30,
                bandwidth = 0,
                protocol = "DNS",
                status = NetworkConnection.ConnectionStatus.ESTABLISHED
            ),
            NetworkConnection(
                fromNodeId = "proxy",
                toNodeId = "target",
                latency = 100,
                bandwidth = 0,
                protocol = "TCP",
                status = NetworkConnection.ConnectionStatus.ESTABLISHED
            )
        )

        return NetworkTopology(nodes, connections)
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        _isMonitoring.value = false
        performanceMonitor.stop()
    }

    /**
     * Refresh topology
     */
    fun refreshTopology() {
        _topology.value = createInitialTopology()
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
        performanceMonitor.stop()
    }
}
