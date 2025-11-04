package com.simplexray.an.topology

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * Stabilized force-directed layout with fixed timestep, velocity damping,
 * and frame throttling (30fps max).
 * 
 * Architecture fixes:
 * - Fixed timestep (no oscillation)
 * - Velocity damping = 0.92
 * - Spring constant = 0.1
 * - Max iterations per tick limited
 * - Runs on Dispatchers.Default (off main thread)
 * - Frame throttled to ~30fps
 */
class StabilizedForceLayout(
    private val width: Float,
    private val height: Float,
    private val scope: CoroutineScope
) {
    // Force constants
    private val springConstant = 0.1f
    private val repulsionConstant = 1000f
    private val velocityDamping = 0.92f
    private val maxVelocity = 10f
    private val minDistance = 1f
    private val idealDistance = 50f
    
    // Fixed timestep (16.67ms = ~60fps, but we throttle to 30fps)
    private val timestepMs = 33L // ~30fps
    
    // Layout state
    private val positions = mutableMapOf<String, Pair<Float, Float>>()
    private val velocities = mutableMapOf<String, Pair<Float, Float>>()
    private val pinnedNodes = mutableSetOf<String>()
    
    // Flow for position updates (throttled)
    private val _positionFlow = MutableStateFlow<Map<String, Pair<Float, Float>>>(emptyMap())
    val positionFlow: StateFlow<Map<String, Pair<Float, Float>>> = _positionFlow.asStateFlow()
    
    // Active layout job
    private var layoutJob: Job? = null
    
    /**
     * Start layout computation loop
     */
    fun startLayout(
        nodes: List<Node>,
        edges: List<Edge>,
        pinned: Map<String, Pair<Float, Float>> = emptyMap()
    ) {
        // Cancel existing job
        layoutJob?.cancel()
        
        // Initialize positions
        initializePositions(nodes, pinned)
        
        // Start layout loop on background thread
        layoutJob = scope.launch(Dispatchers.Default) {
            layoutLoop(nodes, edges)
        }
    }
    
    /**
     * Stop layout computation
     */
    fun stopLayout() {
        layoutJob?.cancel()
        layoutJob = null
    }
    
    /**
     * Update pinned nodes
     */
    fun updatePinned(pinned: Map<String, Pair<Float, Float>>) {
        pinnedNodes.clear()
        pinnedNodes.addAll(pinned.keys)
        pinned.forEach { (id, pos) ->
            positions[id] = pos
            velocities[id] = 0f to 0f
        }
    }
    
    /**
     * Initialize node positions (circular or from pinned)
     */
    private fun initializePositions(nodes: List<Node>, pinned: Map<String, Pair<Float, Float>>) {
        positions.clear()
        velocities.clear()
        pinnedNodes.clear()
        
        if (nodes.isEmpty()) return
        
        val n = nodes.size
        val centerX = width / 2f
        val centerY = height / 2f
        
        if (n == 1) {
            val node = nodes[0]
            positions[node.id] = pinned[node.id] ?: (centerX to centerY)
            velocities[node.id] = 0f to 0f
            if (pinned.containsKey(node.id)) {
                pinnedNodes.add(node.id)
            }
        } else {
            val radius = min(width, height) * 0.3f
            
            nodes.forEachIndexed { i, node ->
                if (pinned.containsKey(node.id)) {
                    positions[node.id] = pinned[node.id]!!
                    pinnedNodes.add(node.id)
                } else {
                    val angle = 2f * PI.toFloat() * i / n
                    val x = centerX + radius * cos(angle)
                    val y = centerY + radius * sin(angle)
                    positions[node.id] = x.coerceIn(0f, width) to y.coerceIn(0f, height)
                }
                velocities[node.id] = 0f to 0f
            }
        }
    }
    
    /**
     * Main layout loop with fixed timestep and frame throttling
     */
    private suspend fun layoutLoop(nodes: List<Node>, edges: List<Edge>) {
        // Create ticker for fixed timestep (30fps)
        val ticker = ticker(timestepMs, 0, kotlinx.coroutines.channels.TickerMode.FIXED_DELAY)
        
        try {
            var iteration = 0
            val maxIterations = 300 // Limit iterations for stabilization
            
            for (tick in ticker) {
                if (!scope.isActive) break
                
                // Limit total iterations
                if (iteration >= maxIterations) {
                    // Continue with reduced update frequency
                    if (iteration % 3 == 0) {
                        updateLayout(nodes, edges)
                        emitPositions()
                    }
                    iteration++
                    continue
                }
                
                updateLayout(nodes, edges)
                
                // Emit positions every frame (throttled by ticker)
                emitPositions()
                
                iteration++
                
                // Check for convergence (low total velocity)
                val totalVelocity = velocities.values.sumOf { (vx, vy) ->
                    sqrt((vx * vx + vy * vy).toDouble())
                }
                
                if (totalVelocity < 0.1 && iteration > 50) {
                    // Converged, reduce update frequency
                    if (iteration % 5 == 0) {
                        emitPositions()
                    }
                    iteration++
                    continue
                }
            }
        } finally {
            ticker.cancel()
        }
    }
    
    /**
     * Update layout for one timestep
     */
    private fun updateLayout(nodes: List<Node>, edges: List<Edge>) {
        val forces = mutableMapOf<String, Pair<Float, Float>>()
        
        // Initialize forces
        nodes.forEach { node ->
            if (!pinnedNodes.contains(node.id)) {
                forces[node.id] = 0f to 0f
            }
        }
        
        // Calculate repulsive forces (Coulomb-like)
        nodes.forEachIndexed { i, nodeI ->
            if (pinnedNodes.contains(nodeI.id)) return@forEachIndexed
            
            val posI = positions[nodeI.id] ?: return@forEachIndexed
            var fx = 0f
            var fy = 0f
            
            nodes.forEachIndexed { j, nodeJ ->
                if (i == j || pinnedNodes.contains(nodeJ.id)) return@forEachIndexed
                
                val posJ = positions[nodeJ.id] ?: return@forEachIndexed
                val dx = posI.first - posJ.first
                val dy = posI.second - posJ.second
                val distSq = dx * dx + dy * dy
                val dist = max(minDistance, sqrt(distSq))
                
                // Repulsive force: k * q1 * q2 / r^2
                val force = repulsionConstant / distSq
                fx += (dx / dist) * force
                fy += (dy / dist) * force
            }
            
            val currentForce = forces[nodeI.id] ?: (0f to 0f)
            forces[nodeI.id] = (currentForce.first + fx) to (currentForce.second + fy)
        }
        
        // Calculate attractive forces (spring-like)
        edges.forEach { edge ->
            val posFrom = positions[edge.from] ?: return@forEach
            val posTo = positions[edge.to] ?: return@forEach
            
            val dx = posFrom.first - posTo.first
            val dy = posFrom.second - posTo.second
            val dist = max(minDistance, hypot(dx, dy))
            
            // Spring force: k * (r - r0) where r0 is ideal distance weighted by edge weight
            val weightFactor = edge.weight.coerceIn(0.1f, 1f)
            val idealDist = idealDistance * (0.5f + 0.5f * weightFactor)
            val force = springConstant * (dist - idealDist)
            
            val fx = (dx / dist) * force
            val fy = (dy / dist) * force
            
            if (!pinnedNodes.contains(edge.from)) {
                val currentForce = forces[edge.from] ?: (0f to 0f)
                forces[edge.from] = (currentForce.first - fx) to (currentForce.second - fy)
            }
            
            if (!pinnedNodes.contains(edge.to)) {
                val currentForce = forces[edge.to] ?: (0f to 0f)
                forces[edge.to] = (currentForce.first + fx) to (currentForce.second + fy)
            }
        }
        
        // Update velocities and positions
        nodes.forEach { node ->
            if (pinnedNodes.contains(node.id)) return@forEach
            
            val force = forces[node.id] ?: (0f to 0f)
            val velocity = velocities[node.id] ?: (0f to 0f)
            
            // Apply force to velocity
            var vx = velocity.first + force.first
            var vy = velocity.second + force.second
            
            // Apply damping
            vx *= velocityDamping
            vy *= velocityDamping
            
            // Clamp velocity
            val speed = hypot(vx, vy)
            if (speed > maxVelocity) {
                vx = (vx / speed) * maxVelocity
                vy = (vy / speed) * maxVelocity
            }
            
            velocities[node.id] = vx to vy
            
            // Update position
            val pos = positions[node.id] ?: return@forEach
            var newX = pos.first + vx
            var newY = pos.second + vy
            
            // Boundary constraints
            newX = newX.coerceIn(0f, width)
            newY = newY.coerceIn(0f, height)
            
            positions[node.id] = newX to newY
        }
    }
    
    /**
     * Emit current positions to flow
     */
    private fun emitPositions() {
        _positionFlow.value = positions.toMap()
    }
    
    /**
     * Get current positions (synchronous read)
     */
    fun getPositions(): Map<String, Pair<Float, Float>> = positions.toMap()
}

