package com.simplexray.an.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import com.simplexray.an.topology.Edge
import com.simplexray.an.topology.ForceLayout
import com.simplexray.an.topology.Node
import com.simplexray.an.topology.PositionedNode
import kotlin.math.*

@Composable
fun NetworkGraphCanvas(
    nodes: List<Node>,
    edges: List<Edge>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    cdnBadge: (Node) -> Boolean = { false },
    onNodeClick: (Node) -> Unit = {},
    onBackgroundTap: () -> Unit = {},
    resetKey: Int = 0,
    viewResetKey: Int = 0,
    selectedNodeId: String? = null,
    fitToGraphKey: Int = 0,
    onFitToGraphRequest: (() -> Unit)? = null,
    highlightPaths: Boolean = true, // Ultimate: highlight paths from selected node
    onZoomIn: (() -> Unit)? = null, // Ultimate: zoom in callback
    onZoomOut: (() -> Unit)? = null // Ultimate: zoom out callback
) {
    var lastSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Animated scale and pan for smooth transitions
    var targetScale by remember { mutableStateOf(1f) }
    var targetPan by remember { mutableStateOf(Offset.Zero) }
    
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 300f
        ),
        label = "scale"
    )
    
    val pan by animateOffsetAsState(
        targetValue = targetPan,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 300f
        ),
        label = "pan"
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    val pinnedNorm = remember(context, resetKey) { com.simplexray.an.topology.TopologyLayoutStore.load(context) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    
    // Animated pulse state for selected node - updates continuously for smooth animation
    var pulseState by remember { mutableStateOf(0f) }
    // Traffic flow animation state
    var trafficFlowState by remember { mutableStateOf(0f) }
    
    androidx.compose.runtime.LaunchedEffect(selectedNodeId) {
        if (selectedNodeId != null) {
            while (true) {
                val time = System.currentTimeMillis()
                pulseState = ((time % 2000) / 2000f) // 0 to 1
                kotlinx.coroutines.delay(16) // ~60fps
            }
        } else {
            pulseState = 0f
        }
    }
    
    // Continuous traffic flow animation
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            trafficFlowState = (trafficFlowState + 0.02f) % 1f
            kotlinx.coroutines.delay(16) // ~60fps
        }
    }
    
    // Fit to graph functionality
    val fitToGraph = {
        val w = lastSize.width.toFloat()
        val h = lastSize.height.toFloat()
        if (w > 0f && h > 0f && nodes.isNotEmpty()) {
            val layout = ForceLayout(w, h, iterations = 80)
            val pinnedAbs = pinnedNorm.mapValues { it.value.first * w to it.value.second * h }
            val positioned = layout.layout(nodes, edges, pinned = pinnedAbs)
            
            // Calculate bounding box
            val minX = positioned.minOfOrNull { it.x } ?: 0f
            val maxX = positioned.maxOfOrNull { it.x } ?: w
            val minY = positioned.minOfOrNull { it.y } ?: 0f
            val maxY = positioned.maxOfOrNull { it.y } ?: h
            
            val contentWidth = maxX - minX
            val contentHeight = maxY - minY
            
            if (contentWidth > 0f && contentHeight > 0f) {
                // Calculate scale to fit with padding
                val padding = 40f
                val scaleX = (w - padding * 2) / contentWidth
                val scaleY = (h - padding * 2) / contentHeight
                val newScale = minOf(scaleX, scaleY).coerceIn(0.5f, 4f)
                
                // Calculate pan to center
                val centerX = (minX + maxX) / 2f
                val centerY = (minY + maxY) / 2f
                val newPanX = (w / 2f) - (centerX * newScale)
                val newPanY = (h / 2f) - (centerY * newScale)
                
                targetScale = newScale
                targetPan = Offset(newPanX, newPanY)
                
                com.simplexray.an.topology.TopologyViewStateStore.save(
                    context,
                    newScale,
                    newPanX / w,
                    newPanY / h
                )
            }
        }
    }
    
    // Fit to graph when key changes
    androidx.compose.runtime.LaunchedEffect(fitToGraphKey) {
        if (fitToGraphKey > 0 && nodes.isNotEmpty()) {
            kotlinx.coroutines.delay(100) // Small delay to ensure size is set
            fitToGraph()
        }
    }
    
    // Reset view when viewResetKey changes
    androidx.compose.runtime.LaunchedEffect(viewResetKey) {
        if (viewResetKey > 0) {
            targetScale = 1f
            targetPan = Offset.Zero
            com.simplexray.an.topology.TopologyViewStateStore.clear(context)
        }
    }

    // Use pulseState as key to force recomposition when it changes
    Canvas(modifier = modifier
        .onSizeChanged { lastSize = it }
        .pointerInput(nodes, edges, lastSize, pan, scale) {
            detectDragGestures(onDragStart = { offset: Offset ->
                val w = lastSize.width.toFloat()
                val h = lastSize.height.toFloat()
                if (w <= 0f || h <= 0f) return@detectDragGestures
                val layout = ForceLayout(w, h, iterations = 80)
                val pinnedAbs = pinnedNorm.mapValues { it.value.first * w to it.value.second * h }
                val pos = layout.layout(nodes, edges, pinned = pinnedAbs)
                val currentPan = pan
                val currentScale = scale
                val worldX = (offset.x - currentPan.x) / currentScale
                val worldY = (offset.y - currentPan.y) / currentScale
                val near: PositionedNode? = pos.minByOrNull { p: PositionedNode ->
                    val dx = p.x - worldX
                    val dy = p.y - worldY
                    dx * dx + dy * dy
                }
                if (near != null) {
                    val d2 = (near.x - worldX) * (near.x - worldX) + (near.y - worldY) * (near.y - worldY)
                    if (d2 <= 20f * 20f) {
                        draggingId = near.node.id
                        if (!pinnedNorm.containsKey(near.node.id)) {
                            pinnedNorm[near.node.id] = (near.x / w) to (near.y / h)
                        }
                    }
                }
            }, onDragEnd = {
                draggingId?.let { com.simplexray.an.topology.TopologyLayoutStore.save(context, pinnedNorm) }
                draggingId = null
            }) { _, drag: Offset ->
                val id = draggingId ?: return@detectDragGestures
                val w = lastSize.width.toFloat()
                val h = lastSize.height.toFloat()
                if (w <= 0f || h <= 0f) return@detectDragGestures
                val currentScale = scale
                val worldDelta = Offset(drag.x / currentScale, drag.y / currentScale)
                val cur = pinnedNorm[id] ?: (0.5f to 0.5f)
                val curAbs = Offset(cur.first * w, cur.second * h)
                val nextAbs = curAbs + worldDelta
                val nx = (nextAbs.x / w).coerceIn(0f, 1f)
                val ny = (nextAbs.y / h).coerceIn(0f, 1f)
                pinnedNorm[id] = nx to ny
            }
        }
        .pointerInput(nodes, edges, lastSize) {
            detectTransformGestures { _, p, z, _ ->
                targetScale = (targetScale * z).coerceIn(0.5f, 4f)
                targetPan += p
                val w = lastSize.width.toFloat()
                val h = lastSize.height.toFloat()
                if (w > 0f && h > 0f) {
                    // Save target values for persistence
                    com.simplexray.an.topology.TopologyViewStateStore.save(
                        context,
                        targetScale,
                        targetPan.x / w,
                        targetPan.y / h
                    )
                }
            }
        }
        .pointerInput(nodes, edges, lastSize, pan, scale, fitToGraphKey) {
            detectTapGestures(
                onDoubleTap = {
                    // Double tap to fit to graph
                    onFitToGraphRequest?.invoke()
                },
                onLongPress = { offset: Offset ->
                    val w = lastSize.width.toFloat()
                    val h = lastSize.height.toFloat()
                    if (w <= 0f || h <= 0f) return@detectTapGestures
                    val layout = ForceLayout(w, h, iterations = 80)
                    val pinnedAbs = pinnedNorm.mapValues { it.value.first * w to it.value.second * h }
                    val pos = layout.layout(nodes, edges, pinned = pinnedAbs)
                    val currentPan = pan
                    val currentScale = scale
                    val worldX = (offset.x - currentPan.x) / currentScale
                    val worldY = (offset.y - currentPan.y) / currentScale
                    val near: PositionedNode? = pos.minByOrNull { p: PositionedNode ->
                        val dx = p.x - worldX
                        val dy = p.y - worldY
                        dx * dx + dy * dy
                    }
                    if (near != null) {
                        val d2 = (near.x - worldX) * (near.x - worldX) + (near.y - worldY) * (near.y - worldY)
                        if (d2 <= 20f * 20f) {
                            if (pinnedNorm.containsKey(near.node.id)) pinnedNorm.remove(near.node.id) else {
                                pinnedNorm[near.node.id] = (near.x / w) to (near.y / h)
                            }
                            com.simplexray.an.topology.TopologyLayoutStore.save(context, pinnedNorm)
                        }
                    }
                },
                onTap = { offset: Offset ->
                    val w = lastSize.width.toFloat()
                    val h = lastSize.height.toFloat()
                    if (w <= 0f || h <= 0f) return@detectTapGestures
                    val layout = ForceLayout(w, h, iterations = 80)
                    val pinnedAbs = pinnedNorm.mapValues { it.value.first * w to it.value.second * h }
                    val pos = layout.layout(nodes, edges, pinned = pinnedAbs)
                    val currentPan = pan
                    val currentScale = scale
                    val worldX = (offset.x - currentPan.x) / currentScale
                    val worldY = (offset.y - currentPan.y) / currentScale
                    val near: PositionedNode? = pos.minByOrNull { p: PositionedNode ->
                        val dx = p.x - worldX
                        val dy = p.y - worldY
                        dx * dx + dy * dy
                    }
                    if (near != null) {
                        val d2 = (near.x - worldX) * (near.x - worldX) + (near.y - worldY) * (near.y - worldY)
                        if (d2 <= 20f * 20f) onNodeClick(near.node) else onBackgroundTap()
                    } else {
                        onBackgroundTap()
                    }
                }
            )
        }
    ) {
        val w = size.width
        val h = size.height
        // Read pulseState to ensure recomposition when it changes
        val currentPulse = pulseState
        // Load persisted view state once when size becomes available
        if (w > 0f && h > 0f && targetScale == 1f && targetPan == Offset.Zero) {
            val vs = com.simplexray.an.topology.TopologyViewStateStore.load(context)
            if (vs != null) {
                targetScale = vs.scale.coerceIn(0.5f, 4f)
                targetPan = Offset(vs.panX * w, vs.panY * h)
            }
        }
        val layout = ForceLayout(w, h, iterations = 80)
        val pinnedAbs = pinnedNorm.mapValues { it.value.first * w to it.value.second * h }
        val positioned = layout.layout(nodes, edges, pinned = pinnedAbs)
        val index = positioned.associateBy { it.node.id }

        withTransform({
            translate(pan.x, pan.y)
            scale(scale, scale)
        }) {
                // Calculate max weight for normalization
                val maxWeight = edges.maxOfOrNull { it.weight }?.coerceAtLeast(0.01f) ?: 1f
                
                // Ultimate: Calculate paths from selected node for highlighting
                val highlightedPaths = if (highlightPaths && selectedNodeId != null) {
                    val pathSet = mutableSetOf<String>()
                    val queue = mutableListOf(selectedNodeId)
                    val visited = mutableSetOf<String>()
                    
                    while (queue.isNotEmpty()) {
                        val current = queue.removeAt(0)
                        if (current in visited) continue
                        visited.add(current)
                        
                        edges.filter { it.from == current || it.to == current }.forEach { edge ->
                            val other = if (edge.from == current) edge.to else edge.from
                            val pathKey = if (edge.from == current) "${edge.from}->${edge.to}" else "${edge.to}->${edge.from}"
                            pathSet.add(pathKey)
                            
                            if (other !in visited && other != selectedNodeId) {
                                queue.add(other)
                            }
                        }
                    }
                    pathSet
                } else emptySet()
                
                // Draw edges with ultimate enhancements
                for (e in edges) {
                    val a = index[e.from]?.let { Offset(it.x, it.y) } ?: continue
                    val b = index[e.to]?.let { Offset(it.x, it.y) } ?: continue
                    
                    val normalizedWeight = (e.weight / maxWeight).coerceIn(0.05f, 1f)
                    val stroke = 0.5f + 5f * normalizedWeight
                    val distance = hypot(b.x - a.x, b.y - a.y)
                    
                    // Ultimate: Check if edge is in highlighted path
                    val pathKey1 = "${e.from}->${e.to}"
                    val pathKey2 = "${e.to}->${e.from}"
                    val isHighlighted = highlightPaths && selectedNodeId != null && 
                        (pathKey1 in highlightedPaths || pathKey2 in highlightedPaths)
                    
                    // Enhanced edge color with weight-based gradient
                    val edgeAlpha = if (isHighlighted) {
                        0.8f + 0.2f * normalizedWeight // Brighter for highlighted paths
                    } else {
                        0.4f + 0.6f * normalizedWeight
                    }
                    val baseEdgeColor = if (isHighlighted) {
                        Color(0xFFFFD700) // Gold for highlighted paths
                    } else {
                        Color(0xFF64B5F6)
                    }
                    val edgeColor = baseEdgeColor.copy(alpha = edgeAlpha)
                    
                    // Draw edge glow/shadow for better visibility (enhanced for highlighted paths)
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas as android.graphics.Canvas
                        val glowAlpha = if (isHighlighted) {
                            (100 * normalizedWeight).toInt().coerceIn(100, 200)
                        } else {
                            (50 * normalizedWeight).toInt()
                        }
                        val glowColor = if (isHighlighted) {
                            android.graphics.Color.argb(glowAlpha, 255, 215, 0)
                        } else {
                            android.graphics.Color.argb(glowAlpha, 100, 181, 246)
                        }
                        val paint = android.graphics.Paint().apply {
                            color = glowColor
                            strokeWidth = if (isHighlighted) stroke * 3f else stroke * 2.5f
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            maskFilter = android.graphics.BlurMaskFilter(
                                stroke * (if (isHighlighted) 3f else 2f),
                                android.graphics.BlurMaskFilter.Blur.NORMAL
                            )
                            isAntiAlias = true
                        }
                        nativeCanvas.drawLine(a.x, a.y, b.x, b.y, paint)
                    }
                    
                    // Traffic flow animation - animated particles along edge
                    if (normalizedWeight > 0.2f && distance > 20f) {
                        val flowProgress = (trafficFlowState + (e.hashCode() % 100) / 100f) % 1f
                        val particlePos = Offset(
                            a.x + (b.x - a.x) * flowProgress,
                            a.y + (b.y - a.y) * flowProgress
                        )
                        drawCircle(
                            color = Color(0xFFFFFFFF).copy(alpha = normalizedWeight),
                            radius = 3f * normalizedWeight,
                            center = particlePos
                        )
                        // Glow around particle
                        drawCircle(
                            color = baseEdgeColor.copy(alpha = 0.3f * normalizedWeight),
                            radius = 6f * normalizedWeight,
                            center = particlePos
                        )
                    }
                    
                    // Draw gradient edge
                    val gradientBrush = Brush.linearGradient(
                        colors = listOf(
                            edgeColor.copy(alpha = edgeAlpha * 0.7f),
                            edgeColor,
                            edgeColor.copy(alpha = edgeAlpha * 0.7f)
                        ),
                        start = a,
                        end = b
                    )
                    
                    drawLine(
                        brush = gradientBrush,
                        start = a,
                        end = b,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }

                // Calculate max node weight for size scaling
                val maxNodeWeight = positioned.maxOfOrNull { 
                    max(it.node.weight, it.node.instantContribution) 
                }?.coerceAtLeast(0.01f) ?: 1f
                
                // Draw nodes with ultimate enhancements
                for (p in positioned) {
                    val isSelected = p.node.id == selectedNodeId
                    val nodeActivity = max(p.node.weight, p.node.instantContribution)
                    val activityRatio = (nodeActivity / maxNodeWeight).coerceIn(0.1f, 1f)
                    
                    // Enhanced color scheme with gradients
                    val baseColor = when (p.node.type) {
                        Node.Type.Domain -> Color(0xFF42A5F5) // Bright blue
                        Node.Type.IP -> Color(0xFF66BB6A) // Green
                    }
                    val color = if (isSelected) {
                        Color(0xFFFFD700) // Gold for selected
                    } else {
                        // Color intensity based on activity
                        baseColor.copy(alpha = 0.7f + 0.3f * activityRatio)
                    }
                    
                    val center = Offset(p.x, p.y)
                    // Dynamic node size based on activity and selection
                    val baseRadius = 8f + 6f * activityRatio
                    val radius = if (isSelected) {
                        baseRadius + 4f + 2f * sin(currentPulse * 2f * PI.toFloat())
                    } else {
                        baseRadius
                    }
                    val strokeWidth = if (isSelected) 6f else 3f + 2f * activityRatio
                    
                    // Ultimate glow effect for active nodes
                    if (activityRatio > 0.5f || isSelected) {
                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas as android.graphics.Canvas
                            val glowRadius = radius * 2.5f
                            val glowAlpha = if (isSelected) 0.4f else 0.2f * activityRatio
                            val paint = android.graphics.Paint().apply {
                                val colorArgb = color.toArgb()
                                setARGB(
                                    (glowAlpha * 255).toInt(),
                                    android.graphics.Color.red(colorArgb),
                                    android.graphics.Color.green(colorArgb),
                                    android.graphics.Color.blue(colorArgb)
                                )
                                maskFilter = android.graphics.BlurMaskFilter(
                                    radius * 1.5f,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                                style = android.graphics.Paint.Style.FILL
                                isAntiAlias = true
                            }
                            nativeCanvas.drawCircle(center.x, center.y, glowRadius, paint)
                        }
                    }
                    
                    // Multiple selection rings for ultimate visual effect
                    if (isSelected) {
                        val pulse = sin(currentPulse * 2f * PI.toFloat())
                        val normalizedPulse = (pulse + 1f) / 2f // 0 to 1
                        
                        // Outer pulsing ring
                        val outerRingRadius = radius + 8f + 6f * normalizedPulse
                        drawCircle(
                            color = color.copy(alpha = 0.15f + 0.2f * normalizedPulse),
                            radius = outerRingRadius,
                            center = center
                        )
                        
                        // Middle ring
                        val middleRingRadius = radius + 4f + 3f * normalizedPulse
                        drawCircle(
                            color = color.copy(alpha = 0.25f + 0.3f * normalizedPulse),
                            radius = middleRingRadius,
                            center = center,
                            style = Stroke(width = 2f)
                        )
                    }
                    
                    // Draw node with gradient fill for depth
                    val nodeGradient = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = color.alpha + 0.2f),
                            color,
                            color.copy(alpha = color.alpha * 0.7f)
                        ),
                        center = center,
                        radius = radius * 1.2f
                    )
                    
                    drawCircle(
                        brush = nodeGradient,
                        radius = radius,
                        center = center
                    )
                    
                    // Enhanced border
                    drawCircle(
                        color = color.copy(alpha = 1f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )
                    
                    // Inner highlight for 3D effect
                    if (activityRatio > 0.3f) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f * activityRatio),
                            radius = radius * 0.4f,
                            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f)
                        )
                    }

                    // Enhanced CDN badge with glow
                    if (cdnBadge(p.node)) {
                        val badgeOffset = Offset(center.x + radius * 1.2f, center.y - radius * 1.2f)
                        // Glow
                        drawCircle(
                            color = Color(0xFFFFA000).copy(alpha = 0.4f),
                            radius = 10f,
                            center = badgeOffset
                        )
                        // Badge
                        drawCircle(
                            color = Color(0xFFFFA000),
                            radius = 7f,
                            center = badgeOffset
                        )
                        // Badge highlight
                        drawCircle(
                            color = Color.White.copy(alpha = 0.5f),
                            radius = 3f,
                            center = Offset(badgeOffset.x - 2f, badgeOffset.y - 2f)
                        )
                    }

                    // Ultimate labels with enhanced styling
                    if (showLabels) {
                        drawIntoCanvas { canvas ->
                            @Suppress("NAME_SHADOWING")
                            val nativeCanvas = canvas as android.graphics.Canvas
                            val text = p.node.label
                            val textSize = (12f + 4f * activityRatio).coerceIn(10f, 16f)
                            
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                setColor(android.graphics.Color.WHITE)
                                this.textSize = textSize * scale.coerceIn(0.5f, 2f)
                                typeface = android.graphics.Typeface.create(
                                    android.graphics.Typeface.MONOSPACE,
                                    android.graphics.Typeface.BOLD
                                )
                                // Text shadow for better readability
                                setShadowLayer(2f, 1f, 1f, android.graphics.Color.argb(200, 0, 0, 0))
                            }
                            
                            // Enhanced background with gradient
                            val pad = 4f + 2f * activityRatio
                            val x = center.x + radius * 1.5f
                            val y = center.y - radius * 1.5f
                            val width = paint.measureText(text)
                            val fm = paint.fontMetrics
                            val rect = android.graphics.RectF(
                                x - pad,
                                y + fm.top - pad,
                                x + width + pad,
                                y + fm.bottom + pad
                            )
                            
                            // Background with blur effect
                            val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                val bgAlpha = (180 + 75 * activityRatio).toInt().coerceIn(180, 255)
                                setARGB(bgAlpha, 0, 0, 0)
                                style = android.graphics.Paint.Style.FILL
                                maskFilter = android.graphics.BlurMaskFilter(
                                    4f,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            }
                            
                            nativeCanvas.drawRoundRect(rect, 8f, 8f, bg)
                            
                            // Border for label
                            val border = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                val borderColor = when (p.node.type) {
                                    Node.Type.Domain -> android.graphics.Color.parseColor("#42A5F5")
                                    Node.Type.IP -> android.graphics.Color.parseColor("#66BB6A")
                                }
                                setColor(borderColor)
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 1.5f
                            }
                            nativeCanvas.drawRoundRect(rect, 8f, 8f, border)
                            
                            nativeCanvas.drawText(text, x, y, paint)
                        }
                    }
                }
        }
    }
}
