package com.simplexray.an.heat

import android.content.Context
import com.simplexray.an.config.ApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class HeatmapRepository(
    private val context: Context,
    externalScope: CoroutineScope? = null
) {
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ownsScope = externalScope == null // Track if we own the scope
    private val _grid = MutableStateFlow<List<List<Float>>>(emptyList())
    val grid: Flow<List<List<Float>>> = _grid.asStateFlow()
    private var topologyRepo: com.simplexray.an.topology.TopologyRepository? = null

    fun start() {
        if (ApiConfig.isMock(context)) startMock() else startLive()
    }

    private fun startMock() {
        scope.launch {
            val rows = 24
            val cols = 40
            val rnd = Random(System.currentTimeMillis())
            var base = mockGrid(rows, cols)
            while (isActive) {
                // Slightly vary the grid to look "alive"
                base = base.map { row -> row.map { (it + (rnd.nextFloat() - 0.5f) * 0.02f).coerceIn(0f, 1f) } }
                _grid.emit(base)
                delay(1000)
            }
        }
    }

    private fun startLive() {
        scope.launch {
            val cols = 40
            val categories = listOf(
                com.simplexray.an.domain.Category.CDN,
                com.simplexray.an.domain.Category.Video,
                com.simplexray.an.domain.Category.Social,
                com.simplexray.an.domain.Category.Gaming,
                com.simplexray.an.domain.Category.Other
            )
            val rows = categories.size
            val buffer = ArrayDeque<Float>(rows * cols)
            repeat(rows * cols) { buffer.addLast(0f) }

            // Build a lightweight topology feed locally for category weights
            val repo = com.simplexray.an.topology.TopologyRepository(context, com.simplexray.an.grpc.GrpcChannelFactory.statsStub(), scope)
            topologyRepo = repo
            repo.start()
            repo.graph.collect { (nodes, edges) ->
                if (nodes.isEmpty()) return@collect
                val classifier = com.simplexray.an.domain.DomainClassifier(context)
                // Aggregate domain edge weights by category
                val edgeByDom = edges.filter { it.from.startsWith("dom:") }
                val domWeight = mutableMapOf<String, Float>()
                edgeByDom.forEach { e -> domWeight[e.from] = (domWeight[e.from] ?: 0f) + e.weight }
                val totals = mutableMapOf<com.simplexray.an.domain.Category, Float>()
                for (n in nodes) {
                    if (n.type == com.simplexray.an.topology.Node.Type.Domain && n.id.startsWith("dom:")) {
                        val cat = classifier.classify(n.label)
                        val w = domWeight[n.id] ?: n.weight
                        totals[cat] = (totals[cat] ?: 0f) + w
                    }
                }
                val sum = totals.values.sum().takeIf { it > 0f } ?: 1f
                val column = categories.map { (totals[it] ?: 0f) / sum }

                // shift buffer and add new column
                if (buffer.size >= rows * cols) repeat(cols) { buffer.removeFirst() }
                for (r in 0 until rows) buffer.addLast(column[r])

                val list = buffer.toList()
                val grid = (0 until rows).map { r -> (0 until cols).map { c -> list[r + c * rows] } }
                _grid.emit(grid)
            }
        }
    }

    /**
     * Stop the repository and cleanup resources
     * Only cancels scope if we own it (externalScope was null)
     */
    fun stop() {
        // Stop TopologyRepository if we created it
        topologyRepo?.stop()
        topologyRepo = null
        
        // Cancel scope only if we own it
        if (ownsScope) {
            scope.cancel()
        }
    }
}

private fun mockGrid(rows: Int, cols: Int): List<List<Float>> {
    val rnd = Random(System.currentTimeMillis())
    return List(rows) { r ->
        List(cols) { c ->
            val t = r / rows.toFloat()
            val s = c / cols.toFloat()
            (0.6f * t + 0.4f * s + rnd.nextFloat() * 0.2f).coerceIn(0f, 1f)
        }
    }
}
