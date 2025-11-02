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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
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
    selectedNodeId: String? = null
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
    
    androidx.compose.runtime.LaunchedEffect(viewResetKey) {
        // External trigger to reset transform and clear persisted view state
        targetScale = 1f
        targetPan = Offset.Zero
        com.simplexray.an.topology.TopologyViewStateStore.clear(context)
    }
    
    // Auto fit to graph when nodes first appear or reset
    androidx.compose.runtime.LaunchedEffect(nodes.isEmpty(), viewResetKey) {
        if (nodes.isNotEmpty() && viewResetKey > 0) {
            kotlinx.coroutines.delay(100) // Small delay to ensure size is set
            fitToGraph()
        }
    }

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
        .pointerInput(nodes, edges, lastSize, pan, scale) {
            detectTapGestures(
                onDoubleTap = {
                    // Double tap to fit to graph
                    fitToGraph()
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

        translate(pan.x, pan.y) {
            scale(scale, scale) {
                // Draw edges
                for (e in edges) {
                    val a = index[e.from]?.let { Offset(it.x, it.y) } ?: continue
                    val b = index[e.to]?.let { Offset(it.x, it.y) } ?: continue
                    val stroke = 1f + 4f * e.weight.coerceIn(0f, 1f)
                    drawLine(
                        color = Color(0xFF9E9E9E),
                        start = a, end = b,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }

                // Draw nodes
                for (p in positioned) {
                    val isSelected = p.node.id == selectedNodeId
                    val baseColor = when (p.node.type) {
                        Node.Type.Domain -> Color(0xFF42A5F5)
                        Node.Type.IP -> Color(0xFF66BB6A)
                    }
                    val color = if (isSelected) {
                        Color(0xFFFFD700) // Gold for selected
                    } else {
                        baseColor
                    }
                    val center = Offset(p.x, p.y)
                    val radius = if (isSelected) 14f else 10f
                    val strokeWidth = if (isSelected) 6f else 4f
                    
                    // Selection ring animation - smooth pulse
                    if (isSelected) {
                        // Smooth sine wave for pulse
                        val pulse = kotlin.math.sin(pulseState * 2f * kotlin.math.PI.toFloat()).coerceIn(-1f, 1f)
                        val normalizedPulse = (pulse + 1f) / 2f // 0 to 1
                        val alpha = 0.2f + 0.25f * normalizedPulse
                        val ringRadius = radius + 4f + 4f * normalizedPulse
                        drawCircle(
                            color = color.copy(alpha = alpha),
                            radius = ringRadius,
                            center = center
                        )
                    }
                    
                    drawCircle(color = color, radius = radius, center = center, style = Stroke(width = strokeWidth))

                    // CDN badge
                    if (cdnBadge(p.node)) {
                        val badgeOffset = Offset(center.x + 12f, center.y - 12f)
                        drawCircle(color = Color(0xFFFFA000), radius = 6f, center = badgeOffset)
                    }

                    // Labels
                    if (showLabels) {
                        drawIntoCanvas { canvas ->
                            @Suppress("NAME_SHADOWING")
                            val nativeCanvas = canvas as android.graphics.Canvas
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                setColor(android.graphics.Color.WHITE)
                                textSize = 24f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
                            }
                            val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                setColor(android.graphics.Color.parseColor("#66000000"))
                                style = android.graphics.Paint.Style.FILL
                            }
                            val text = p.node.label
                            val pad = 6f
                            val x = center.x + 14f
                            val y = center.y - 14f
                            val width = paint.measureText(text)
                            val fm = paint.fontMetrics
                            val rect = android.graphics.RectF(
                                x - pad,
                                y + fm.top - pad,
                                x + width + pad,
                                y + fm.bottom + pad
                            )
                            nativeCanvas.drawRoundRect(rect, 8f, 8f, bg)
                            nativeCanvas.drawText(text, x, y, paint)
                        }
                    }
                }
            }
        }
    }
}
