package com.simplexray.an.topology

import kotlin.math.*

class ForceLayout(
    private val width: Float,
    private val height: Float,
    private val iterations: Int = 120 // Increased for better convergence
) {
    fun layout(
        nodes: List<Node>,
        edges: List<Edge>,
        pinned: Map<String, Pair<Float, Float>> = emptyMap()
    ): List<PositionedNode> {
        if (nodes.isEmpty()) return emptyList()
        val n = nodes.size
        // Better initial layout: circular or grid-based
        val posX = FloatArray(n)
        val posY = FloatArray(n)
        
        if (n == 1) {
            posX[0] = width / 2f
            posY[0] = height / 2f
        } else {
            // Circular initial layout for better distribution
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = min(width, height) * 0.3f
            for (i in 0 until n) {
                val angle = 2f * PI.toFloat() * i / n
                posX[i] = centerX + radius * cos(angle)
                posY[i] = centerY + radius * sin(angle)
            }
        }
        
        val index = nodes.mapIndexed { i, node -> node.id to i }.toMap()
        
        // Initialize pinned positions if provided
        for ((id, pair) in pinned) {
            val i = index[id] ?: continue
            posX[i] = pair.first.coerceIn(0f, width)
            posY[i] = pair.second.coerceIn(0f, height)
        }

        // Enhanced force constants based on graph density
        val area = width * height
        val k = sqrt(area / max(1, n).toFloat())
        val repulsion = k * k
        val spring = k

        // Enhanced cooling schedule for better convergence
        val initialTemperature = sqrt(width * width + height * height) / 10f
        val coolingRate = 0.95f
        
        var temperature = initialTemperature

        repeat(iterations) { iter ->
            val dispX = FloatArray(n)
            val dispY = FloatArray(n)

            // Enhanced repulsive forces with better distance handling
            for (i in 0 until n) {
                if (pinned.containsKey(nodes[i].id)) continue
                for (j in i + 1 until n) {
                    val dx = posX[i] - posX[j]
                    val dy = posY[i] - posY[j]
                    val distSq = dx * dx + dy * dy
                    val dist = max(0.01f, sqrt(distSq))
                    
                    // Avoid division by zero and improve force calculation
                    val force = if (dist > 0.01f) repulsion / distSq else repulsion
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    
                    dispX[i] += fx
                    dispY[i] += fy
                    dispX[j] -= fx
                    dispY[j] -= fy
                }
            }

            // Enhanced attractive forces with weight consideration
            for (e in edges) {
                val i = index[e.from] ?: continue
                val j = index[e.to] ?: continue
                
                val dx = posX[i] - posX[j]
                val dy = posY[i] - posY[j]
                val dist = max(0.01f, hypot(dx, dy))
                
                // Weight-based spring strength
                val weightFactor = e.weight.coerceIn(0.1f, 1f)
                val idealLength = spring * (0.5f + 0.5f * weightFactor)
                val force = (dist - idealLength) / spring * weightFactor
                
                val fx = (dx / dist) * force
                val fy = (dy / dist) * force
                
                if (!pinned.containsKey(nodes[i].id)) {
                    dispX[i] -= fx
                    dispY[i] -= fy
                }
                if (!pinned.containsKey(nodes[j].id)) {
                    dispX[j] += fx
                    dispY[j] += fy
                }
            }

            // Update positions with enhanced cooling schedule
            temperature *= coolingRate
            val maxMove = min(temperature, sqrt(width * width + height * height) * 0.1f)
            
            for (i in 0 until n) {
                val id = nodes[i].id
                if (pinned.containsKey(id)) continue
                
                val dx = dispX[i]
                val dy = dispY[i]
                val dist = max(0.01f, hypot(dx, dy))
                
                // Clamp movement to prevent oscillations
                val moveDist = min(dist, maxMove)
                posX[i] = clamp(posX[i] + (dx / dist) * moveDist, 0f, width)
                posY[i] = clamp(posY[i] + (dy / dist) * moveDist, 0f, height)
            }
        }

        return nodes.mapIndexed { i, node -> PositionedNode(node, posX[i], posY[i]) }
    }

    private fun clamp(v: Float, minV: Float, maxV: Float) = max(minV, min(maxV, v))
}
