package com.simplexray.an.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.grpc.GrpcChannelFactory
import com.simplexray.an.config.ApiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrafficViewModel(app: Application) : AndroidViewModel(app) {
    private val stub = GrpcChannelFactory.statsStub()
    private val repo = StatsRepository(app.applicationContext, stub, viewModelScope)

    private val _current = MutableStateFlow(BitrateHistory())
    val current: StateFlow<BitrateHistory> = _current.asStateFlow()

    init {
        val host = ApiConfig.getHost(app)
        val port = ApiConfig.getPort(app)
        val mock = ApiConfig.isMock(app)
        GrpcChannelFactory.setEndpoint(host, port)
        repo.start(mock)
        viewModelScope.launch {
            repo.flow().collect { p ->
                _current.value = _current.value.add(p)
            }
        }
    }

    fun applySettings(host: String, port: Int, mock: Boolean) {
        ApiConfig.set(getApplication(), host, port, mock)
        GrpcChannelFactory.setEndpoint(host, port)
        // Restart collection by creating a new repository observer path
        repo.start(mock)
    }
    
    override fun onCleared() {
        super.onCleared()
        repo.stop()
    }
}

data class BitrateHistory(
    val points: List<BitratePoint> = emptyList(),
    val maxPoints: Int = 300
) {
    fun add(p: BitratePoint): BitrateHistory {
        val list = (points + p).takeLast(maxPoints)
        return copy(points = list)
    }
}
