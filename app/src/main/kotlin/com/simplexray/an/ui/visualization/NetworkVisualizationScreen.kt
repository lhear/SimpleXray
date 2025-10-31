package com.simplexray.an.ui.visualization

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.protocol.visualization.*
import com.simplexray.an.viewmodel.NetworkVisualizationViewModel
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkVisualizationScreen(
    onBackClick: () -> Unit = {},
    viewModel: NetworkVisualizationViewModel = viewModel()
) {
    val topology by viewModel.topology.collectAsState()
    val latencyHistory by viewModel.latencyHistory.collectAsState()
    val bandwidthHistory by viewModel.bandwidthHistory.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Visualization") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshTopology() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Monitoring status
            MonitoringStatusCard(
                isMonitoring = isMonitoring,
                onToggle = {
                    if (isMonitoring) viewModel.stopMonitoring()
                    else viewModel.startMonitoring()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Network Topology Graph
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(400.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Network Topology",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NetworkTopologyGraph(
                        topology = topology,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection Details
            ConnectionDetailsCard(topology.connections)

            Spacer(modifier = Modifier.height(16.dp))

            // Latency Graph
            if (latencyHistory.isNotEmpty()) {
                TimeSeriesGraphCard(
                    title = "Latency",
                    series = latencyHistory,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bandwidth Graph
            if (bandwidthHistory.isNotEmpty()) {
                TimeSeriesGraphCard(
                    title = "Bandwidth",
                    series = bandwidthHistory,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Node Details
            NodeDetailsCard(topology.nodes)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MonitoringStatusCard(
    isMonitoring: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoring)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isMonitoring) Icons.Default.Visibility else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isMonitoring) "Monitoring Active" else "Monitoring Paused",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isMonitoring) "Real-time network updates" else "Tap to start monitoring",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isMonitoring,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun NetworkTopologyGraph(
    topology: NetworkTopology,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Draw connections first (so they appear behind nodes)
        topology.connections.forEach { connection ->
            val fromNode = topology.nodes.find { it.id == connection.fromNodeId }
            val toNode = topology.nodes.find { it.id == connection.toNodeId }

            if (fromNode != null && toNode != null) {
                val connectionColor = when (connection.status) {
                    NetworkConnection.ConnectionStatus.ESTABLISHED -> Color(0xFF4CAF50)
                    NetworkConnection.ConnectionStatus.CONNECTING -> Color(0xFFFFC107)
                    NetworkConnection.ConnectionStatus.DISCONNECTED -> Color(0xFF9E9E9E)
                    NetworkConnection.ConnectionStatus.ERROR -> Color(0xFFF44336)
                }

                // Animated connection line
                drawLine(
                    color = connectionColor.copy(alpha = pulseAlpha),
                    start = fromNode.position,
                    end = toNode.position,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )

                // Latency label in the middle
                val midPoint = Offset(
                    (fromNode.position.x + toNode.position.x) / 2,
                    (fromNode.position.y + toNode.position.y) / 2 - 20
                )
                drawCircle(
                    color = Color.White,
                    radius = 20f,
                    center = midPoint
                )
                drawCircle(
                    color = connectionColor,
                    radius = 18f,
                    center = midPoint,
                    style = Stroke(width = 2f)
                )
            }
        }

        // Draw nodes
        topology.nodes.forEach { node ->
            val nodeColor = when (node.status) {
                NetworkNode.NodeStatus.ACTIVE -> Color(0xFF2196F3)
                NetworkNode.NodeStatus.INACTIVE -> Color(0xFF9E9E9E)
                NetworkNode.NodeStatus.WARNING -> Color(0xFFFFC107)
                NetworkNode.NodeStatus.ERROR -> Color(0xFFF44336)
            }

            // Outer circle (glow effect)
            drawCircle(
                color = nodeColor.copy(alpha = 0.3f),
                radius = 50f,
                center = node.position
            )

            // Main circle
            drawCircle(
                color = Color.White,
                radius = 40f,
                center = node.position
            )

            // Border
            drawCircle(
                color = nodeColor,
                radius = 38f,
                center = node.position,
                style = Stroke(width = 3f)
            )

            // Inner circle
            drawCircle(
                color = nodeColor.copy(alpha = 0.2f),
                radius = 30f,
                center = node.position
            )
        }
    }

    // Node labels (overlay)
    Box(modifier = modifier) {
        topology.nodes.forEach { node ->
            Column(
                modifier = Modifier
                    .offset(
                        x = (node.position.x - 50).dp,
                        y = (node.position.y + 50).dp
                    )
                    .width(100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = node.type.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConnectionDetailsCard(connections: List<NetworkConnection>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Active Connections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            connections.forEach { connection ->
                ConnectionItem(connection)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ConnectionItem(connection: NetworkConnection) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${connection.fromNodeId} → ${connection.toNodeId}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = connection.protocol,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatBadge(
                    icon = Icons.Default.Speed,
                    value = "${connection.latency}ms",
                    color = when {
                        connection.latency < 50 -> Color(0xFF4CAF50)
                        connection.latency < 100 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }
                )
                StatusIndicator(connection.status)
            }
        }
    }
}

@Composable
private fun StatBadge(
    icon: ImageVector,
    value: String,
    color: Color
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusIndicator(status: NetworkConnection.ConnectionStatus) {
    val (color, text) = when (status) {
        NetworkConnection.ConnectionStatus.ESTABLISHED -> Color(0xFF4CAF50) to "Active"
        NetworkConnection.ConnectionStatus.CONNECTING -> Color(0xFFFFC107) to "Connecting"
        NetworkConnection.ConnectionStatus.DISCONNECTED -> Color(0xFF9E9E9E) to "Disconnected"
        NetworkConnection.ConnectionStatus.ERROR -> Color(0xFFF44336) to "Error"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TimeSeriesGraphCard(
    title: String,
    series: List<TimeSeriesData>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimeSeriesGraph(
                series = series,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TimeSeriesGraph(
    series: List<TimeSeriesData>,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty() || series.all { it.dataPoints.isEmpty() }) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val padding = 40f

        series.forEach { timeSeries ->
            if (timeSeries.dataPoints.isEmpty()) return@forEach

            val points = timeSeries.dataPoints
            val maxValue = points.maxOfOrNull { it.value } ?: 1f
            val minValue = points.minOfOrNull { it.value } ?: 0f
            val valueRange = max(maxValue - minValue, 1f)

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = padding + (index.toFloat() / (points.size - 1).coerceAtLeast(1)) * (canvasWidth - 2 * padding)
                val y = canvasHeight - padding - ((point.value - minValue) / valueRange) * (canvasHeight - 2 * padding)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(timeSeries.color),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )

            // Draw points
            points.forEachIndexed { index, point ->
                val x = padding + (index.toFloat() / (points.size - 1).coerceAtLeast(1)) * (canvasWidth - 2 * padding)
                val y = canvasHeight - padding - ((point.value - minValue) / valueRange) * (canvasHeight - 2 * padding)

                if (index == points.size - 1) {
                    drawCircle(
                        color = Color(timeSeries.color),
                        radius = 6f,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeDetailsCard(nodes: List<NetworkNode>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Network Nodes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            nodes.forEach { node ->
                NodeItem(node)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun NodeItem(node: NetworkNode) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val nodeIcon = when (node.type) {
                NetworkNode.NodeType.CLIENT -> Icons.Default.PhoneAndroid
                NetworkNode.NodeType.PROXY_SERVER -> Icons.Default.Security
                NetworkNode.NodeType.TARGET_SERVER -> Icons.Default.Computer
                NetworkNode.NodeType.CDN_NODE -> Icons.Default.Cloud
                NetworkNode.NodeType.DNS_SERVER -> Icons.Filled.Info
                NetworkNode.NodeType.ROUTER -> Icons.Default.Router
            }

            Icon(
                nodeIcon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = when (node.status) {
                    NetworkNode.NodeStatus.ACTIVE -> Color(0xFF4CAF50)
                    NetworkNode.NodeStatus.INACTIVE -> Color(0xFF9E9E9E)
                    NetworkNode.NodeStatus.WARNING -> Color(0xFFFFC107)
                    NetworkNode.NodeStatus.ERROR -> Color(0xFFF44336)
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = node.type.name.replace("_", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                node.metadata.forEach { (key, value) ->
                    Text(
                        text = "$key: $value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            StatusIndicatorNode(node.status)
        }
    }
}

@Composable
private fun StatusIndicatorNode(status: NetworkNode.NodeStatus) {
    val (color, text) = when (status) {
        NetworkNode.NodeStatus.ACTIVE -> Color(0xFF4CAF50) to "Active"
        NetworkNode.NodeStatus.INACTIVE -> Color(0xFF9E9E9E) to "Inactive"
        NetworkNode.NodeStatus.WARNING -> Color(0xFFFFC107) to "Warning"
        NetworkNode.NodeStatus.ERROR -> Color(0xFFF44336) to "Error"
    }

    Column(horizontalAlignment = Alignment.End) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
