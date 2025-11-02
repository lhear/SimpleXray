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
        // initial positions: x ~ 100 and 200, y ~ 100
        val a = res.first { it.node.id == "a" }
        val b = res.first { it.node.id == "b" }
        // They should not be exactly at the centerline defaults after forces
        val moved = (kotlin.math.abs(a.y - 100f) > 1e-3) || (kotlin.math.abs(b.y - 100f) > 1e-3)
        assertThat(moved).isTrue()
    }
}

