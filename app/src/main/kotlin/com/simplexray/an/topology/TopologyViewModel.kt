package com.simplexray.an.topology

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.grpc.GrpcChannelFactory
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.service.XrayProcessManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for topology graph screen.
 * Uses singleton TopologyRepository (Application-scoped).
 */
class TopologyViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Preferences(app)
    
    // Get singleton repository instance
    private val repo: TopologyRepository? = TopologyRepository.getInstanceOrNull()
    
    // Expose graph as StateFlow (converted from SharedFlow)
    val graph: StateFlow<Pair<List<Node>, List<Edge>>> = 
        (repo?.topologyFlow?.map { graph -> graph.nodes to graph.edges } 
            ?: kotlinx.coroutines.flow.flowOf(emptyList<Node>() to emptyList<Edge>()))
        .stateIn(
            viewModelScope, 
            SharingStarted.Eagerly, 
            emptyList<Node>() to emptyList()
        )
    
    init {
        // Ensure repository is initialized
        if (repo == null) {
            android.util.Log.w("TopologyViewModel", "TopologyRepository not initialized, ensure App.onCreate() calls it")
            
            // Try to initialize if not already done
            viewModelScope.launch {
                ensureGrpcEndpoint()
                val stub = GrpcChannelFactory.statsStub()
                val appScope = (getApplication() as? com.simplexray.an.App)?.let {
                    kotlinx.coroutines.CoroutineScope(
                        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
                    )
                }
                
                if (appScope != null) {
                    TopologyRepository.getInstance(getApplication(), stub, appScope)
                }
            }
        }
    }
    
    /**
     * Ensure gRPC endpoint is configured
     */
    private fun ensureGrpcEndpoint() {
        val port = prefs.apiPort.takeIf { it > 0 } ?: XrayProcessManager.statsPort
        val host = "127.0.0.1"
        val (currentHost, currentPort) = GrpcChannelFactory.currentEndpoint()
        
        if (currentPort != port || currentHost != host) {
            android.util.Log.d("TopologyViewModel", "Setting gRPC endpoint to $host:$port")
            GrpcChannelFactory.setEndpoint(host, port)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Repository is Application-scoped, don't stop it here
    }
}
