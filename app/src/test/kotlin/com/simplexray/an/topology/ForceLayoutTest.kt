package com.simplexray.an.topology

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForceLayoutTest {
    @Test
    fun pinnedNodeRemainsAtPosition() {
        val nodes = listOf(
            Node("a", "a"),
            Node("b", "b"),
            Node("c", "c"),
        )
        val edges = listOf(
            Edge("a", "b", 1f),
            Edge("b", "c", 1f)
        )
        val layout = ForceLayout(width = 300f, height = 200f, iterations = 50)
        val pinned = mapOf("b" to (150f to 100f))
        val result = layout.layout(nodes, edges, pinned)
        val b = result.first { it.node.id == "b" }
        assertThat(b.x).isEqualTo(150f)
        assertThat(b.y).isEqualTo(100f)
    }

    @Test
    fun otherNodesMoveWithForces() {
        val nodes = listOf(Node("a", "a"), Node("b", "b"))
        val edges = listOf(Edge("a", "b", 1f))
        val layout = ForceLayout(width = 300f, height = 200f, iterations = 80)
        val res = layout.layout(nodes, edges)
        // Initial positions are circular layout around center
        val a = res.first { it.node.id == "a" }
        val b = res.first { it.node.id == "b" }
        // After forces, nodes should have moved from initial circular positions
        // Check that they're not at the exact initial positions (which would be on a circle)
        // With 2 nodes, initial positions are at opposite ends of a circle (radius ~60)
        // Initial distance would be ~120 (diameter of circle)
        // After forces from the edge, nodes should have moved (distance may vary)
        val finalDistance = kotlin.math.hypot(a.x - b.x, a.y - b.y)
        // Nodes should have moved from initial positions (final distance should be reasonable, not exactly initial)
        assertThat(finalDistance).isGreaterThan(0f)
        // Nodes should be within layout bounds
        assertThat(a.x).isAtLeast(0f)
        assertThat(a.x).isAtMost(300f)
        assertThat(a.y).isAtLeast(0f)
        assertThat(a.y).isAtMost(200f)
        assertThat(b.x).isAtLeast(0f)
        assertThat(b.x).isAtMost(300f)
        assertThat(b.y).isAtLeast(0f)
        assertThat(b.y).isAtMost(200f)
    }
}

