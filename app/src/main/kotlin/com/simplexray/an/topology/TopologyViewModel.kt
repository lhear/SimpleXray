package com.simplexray.an.topology

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.grpc.GrpcChannelFactory
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.service.XrayProcessManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TopologyViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Preferences(app)
    
    // Initialize with current port from preferences or XrayProcessManager
    private fun ensureGrpcEndpoint() {
        val port = prefs.apiPort.takeIf { it > 0 } ?: XrayProcessManager.statsPort
        val host = "127.0.0.1"
        val (currentHost, currentPort) = GrpcChannelFactory.currentEndpoint()
        
        // Update endpoint if port changed or not initialized
        if (currentPort != port || currentHost != host) {
            android.util.Log.d("TopologyViewModel", "Setting gRPC endpoint to $host:$port")
            GrpcChannelFactory.setEndpoint(host, port)
        }
    }
    
    // Ensure endpoint is set before creating repository
    private val repo: TopologyRepository = run {
        ensureGrpcEndpoint()
        TopologyRepository(app, GrpcChannelFactory.statsStub(), viewModelScope)
    }
    
    val graph: StateFlow<Pair<List<Node>, List<Edge>>> = repo.graph
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Node>() to emptyList())

    init { 
        // Periodically check and update endpoint in case port changes
        viewModelScope.launch {
            while (true) {
                ensureGrpcEndpoint()
                kotlinx.coroutines.delay(5000) // Check every 5 seconds
            }
        }
        repo.start() 
    }
    
    override fun onCleared() {
        super.onCleared()
        repo.stop()
    }
}

