package com.simplexray.an.topology

data class Node(
    val id: String,
    val label: String,
    val type: Type = Type.Domain,
    val weight: Float = 1f,
    val instantContribution: Float = 0f, // Current byte contribution
    val lastSeen: Long = System.currentTimeMillis() // Last time this node had activity
) {
    enum class Type { Domain, IP }
}

data class Edge(
    val from: String,
    val to: String,
    val weight: Float = 1f
)

data class PositionedNode(
    val node: Node,
    val x: Float,
    val y: Float
)

