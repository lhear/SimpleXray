package com.simplexray.an.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.protocol.visualization.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel for Network Visualization screen
 */
class NetworkVisualizationViewModel : ViewModel() {

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

    fun startMonitoring() {
        if (_isMonitoring.value) return

        _isMonitoring.value = true
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val latencyPoints = mutableListOf<GraphDataPoint>()
            val uploadPoints = mutableListOf<GraphDataPoint>()
            val downloadPoints = mutableListOf<GraphDataPoint>()

            while (_isMonitoring.value) {
                val currentTime = System.currentTimeMillis()
                val elapsedSeconds = ((currentTime - startTime) / 1000).toInt()

                // Simulate latency data
                latencyPoints.add(
                    GraphDataPoint(
                        timestamp = currentTime,
                        value = (40 + Random.nextInt(-10, 20)).toFloat()
                    )
                )

                // Simulate upload data
                uploadPoints.add(
                    GraphDataPoint(
                        timestamp = currentTime,
                        value = (500 + Random.nextInt(-100, 200)).toFloat()
                    )
                )

                // Simulate download data
                downloadPoints.add(
                    GraphDataPoint(
                        timestamp = currentTime,
                        value = (2000 + Random.nextInt(-400, 800)).toFloat()
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

                // Update topology with random latency variations
                updateTopologyStats()

                delay(1000)
            }
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
    }

    private fun updateTopologyStats() {
        val currentTopology = _topology.value
        val updatedConnections = currentTopology.connections.map { connection ->
            connection.copy(
                latency = connection.latency + Random.nextInt(-5, 5),
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
    }
}
