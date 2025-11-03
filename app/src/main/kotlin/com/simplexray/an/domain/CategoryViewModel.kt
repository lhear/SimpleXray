package com.simplexray.an.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.config.ApiConfig
import com.simplexray.an.grpc.GrpcChannelFactory
import com.simplexray.an.topology.TopologyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CategoryViewModel(app: Application) : AndroidViewModel(app) {
    private val classifier = DomainClassifier(app)
    private val topRepo = TopologyRepository(app, GrpcChannelFactory.statsStub(), viewModelScope)
    private val _dist = MutableStateFlow<Map<Category, Float>>(emptyMap())
    val dist: StateFlow<Map<Category, Float>> = _dist

    init {
        topRepo.start()
        viewModelScope.launch {
            topRepo.graph.collect { (nodes, edges) ->
                if (nodes.isEmpty()) {
                    _dist.value = emptyMap()
                } else {
                    // Sum edge weights per domain node
                    val weightByNode = mutableMapOf<String, Float>()
                    edges.forEach { e -> weightByNode[e.from] = (weightByNode[e.from] ?: 0f) + e.weight }
                    val totals = mutableMapOf<Category, Float>()
                    for (n in nodes) {
                        if (n.type == com.simplexray.an.topology.Node.Type.Domain) {
                            val cat = classifier.classify(n.label)
                            val w = weightByNode[n.id] ?: n.weight
                            totals[cat] = (totals[cat] ?: 0f) + w
                        }
                    }
                    val sum = totals.values.sum().takeIf { it > 0f } ?: 1f
                    val normalized = totals.mapValues { it.value / sum }
                    _dist.value = normalized
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        topRepo.stop()
    }
}
