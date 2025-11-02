package com.simplexray.an.topology

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.grpc.GrpcChannelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TopologyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TopologyRepository(app, GrpcChannelFactory.statsStub(), viewModelScope)
    val graph: StateFlow<Pair<List<Node>, List<Edge>>> = repo.graph
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Node>() to emptyList())

    init { repo.start() }
}

