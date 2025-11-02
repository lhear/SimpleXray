package com.simplexray.an.topology

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ForceLayout(
    private val width: Float,
    private val height: Float,
    private val iterations: Int = 100
) {
    fun layout(
        nodes: List<Node>,
        edges: List<Edge>,
        pinned: Map<String, Pair<Float, Float>> = emptyMap()
    ): List<PositionedNode> {
        if (nodes.isEmpty()) return emptyList()
        val n = nodes.size
        val posX = FloatArray(n) { (it + 1) * (width / (n + 1)) }
        val posY = FloatArray(n) { height / 2f }
        val index = nodes.mapIndexed { i, node -> node.id to i }.toMap()
        // Initialize pinned positions if provided
        for ((id, pair) in pinned) {
            val i = index[id] ?: continue
            posX[i] = pair.first
            posY[i] = pair.second
        }

        val k = sqrt((width * height) / max(1, n).toFloat())
        val repulsion = k * k
        val spring = k

        repeat(iterations) {
            val dispX = FloatArray(n)
            val dispY = FloatArray(n)

            // Repulsive forces
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val dx = posX[i] - posX[j]
                    val dy = posY[i] - posY[j]
                    val dist = max(0.01f, hypot(dx, dy))
                    val force = repulsion / dist
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    dispX[i] += fx; dispY[i] += fy
                    dispX[j] -= fx; dispY[j] -= fy
                }
            }

            // Attractive forces (springs)
            for (e in edges) {
                val i = index[e.from] ?: continue
                val j = index[e.to] ?: continue
                val dx = posX[i] - posX[j]
                val dy = posY[i] - posY[j]
                val dist = max(0.01f, hypot(dx, dy))
                val force = (dist * dist) / spring
                val fx = (dx / dist) * force
                val fy = (dy / dist) * force
                dispX[i] -= fx; dispY[i] -= fy
                dispX[j] += fx; dispY[j] += fy
            }

            // Update positions with a simple temperature schedule
            val t = (1f - it / iterations.toFloat()) * (width + height) / 100f
            for (i in 0 until n) {
                // Skip moving pinned nodes
                val id = nodes[i].id
                if (pinned.containsKey(id)) continue
                val dx = dispX[i]
                val dy = dispY[i]
                val dist = max(0.01f, hypot(dx, dy))
                posX[i] = clamp(posX[i] + (dx / dist) * min(t, dist), 0f, width)
                posY[i] = clamp(posY[i] + (dy / dist) * min(t, dist), 0f, height)
            }
        }

        return nodes.mapIndexed { i, node -> PositionedNode(node, posX[i], posY[i]) }
    }

    private fun clamp(v: Float, minV: Float, maxV: Float) = max(minV, min(maxV, v))
}
