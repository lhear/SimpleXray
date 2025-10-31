package com.simplexray.an.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.performance.monitor.PerformanceMonitor
import com.simplexray.an.performance.optimizer.PerformanceOptimizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Performance Optimization screen
 */
class PerformanceViewModel(application: Application) : AndroidViewModel(application) {

    private val performanceMonitor = PerformanceMonitor(application)
    private val performanceOptimizer = PerformanceOptimizer(application)

    private val _currentProfile: MutableStateFlow<PerformanceProfile> = MutableStateFlow(PerformanceProfile.Balanced)
    val currentProfile: StateFlow<PerformanceProfile> = _currentProfile.asStateFlow()

    private val _currentMetrics = MutableStateFlow(
        PerformanceMetrics(
            cpuUsage = 0.0f,
            memoryUsage = 0L,
            latency = 0,
            packetLoss = 0.0f
        )
    )
    val currentMetrics: StateFlow<PerformanceMetrics> = _currentMetrics.asStateFlow()

    private val _autoTuneEnabled = MutableStateFlow(false)
    val autoTuneEnabled: StateFlow<Boolean> = _autoTuneEnabled.asStateFlow()

    init {
        // Start monitoring
        viewModelScope.launch {
            performanceMonitor.start()
            performanceMonitor.currentMetrics.collect { metrics ->
                _currentMetrics.value = metrics

                // Auto-tune if enabled
                if (_autoTuneEnabled.value) {
                    performanceOptimizer.autoTune(metrics)
                }
            }
        }

        // Observe profile changes from optimizer
        viewModelScope.launch {
            performanceOptimizer.currentProfile.collect { profile ->
                _currentProfile.value = profile
            }
        }

        // Observe auto-tune state changes
        viewModelScope.launch {
            performanceOptimizer.autoTuneEnabled.collect { enabled ->
                _autoTuneEnabled.value = enabled
            }
        }
    }

    fun selectProfile(profile: PerformanceProfile) {
        viewModelScope.launch {
            performanceOptimizer.setProfile(profile)
            _currentProfile.value = profile
        }
    }

    fun toggleAutoTune() {
        val newState = !_autoTuneEnabled.value
        viewModelScope.launch {
            performanceOptimizer.setAutoTuneEnabled(newState)
            _autoTuneEnabled.value = newState

            if (newState) {
                // Immediately run auto-tune
                performanceOptimizer.autoTune(_currentMetrics.value)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        performanceMonitor.stop()
    }
}
