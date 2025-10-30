package com.simplexray.an.protocol.visualization

import androidx.compose.ui.geometry.Offset

/**
 * Network topology visualization model
 */
data class NetworkTopology(
    val nodes: List<NetworkNode>,
    val connections: List<NetworkConnection>
)

/**
 * Network node (device, server, etc.)
 */
data class NetworkNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val status: NodeStatus,
    val position: Offset,
    val metadata: Map<String, String> = emptyMap()
) {
    enum class NodeType {
        CLIENT,
        PROXY_SERVER,
        TARGET_SERVER,
        CDN_NODE,
        DNS_SERVER,
        ROUTER
    }

    enum class NodeStatus {
        ACTIVE,
        INACTIVE,
        WARNING,
        ERROR
    }
}

/**
 * Connection between nodes
 */
data class NetworkConnection(
    val fromNodeId: String,
    val toNodeId: String,
    val latency: Int, // ms
    val bandwidth: Long, // bytes/sec
    val protocol: String,
    val status: ConnectionStatus
) {
    enum class ConnectionStatus {
        ESTABLISHED,
        CONNECTING,
        DISCONNECTED,
        ERROR
    }
}

/**
 * Geographic server visualization
 */
data class ServerMapMarker(
    val serverId: String,
    val serverName: String,
    val latitude: Double,
    val longitude: Double,
    val latency: Int,
    val load: Float, // 0.0-1.0
    val status: MarkerStatus
) {
    enum class MarkerStatus {
        ONLINE,
        OFFLINE,
        OVERLOADED,
        MAINTENANCE
    }
}

/**
 * Performance graph data point
 */
data class GraphDataPoint(
    val timestamp: Long,
    val value: Float,
    val label: String? = null
)

/**
 * Time series data for graphs
 */
data class TimeSeriesData(
    val name: String,
    val dataPoints: List<GraphDataPoint>,
    val unit: String,
    val color: Long = 0xFF2196F3
)
