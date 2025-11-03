package com.simplexray.an.performance

import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Burst Traffic Windowing
 * Manages maximum concurrency and window sizes for high throughput
 */
class BurstTrafficManager {
    
    data class BurstConfig(
        val initialWindowSize: Int = 6 * 1024 * 1024, // 6 MB
        val maxConcurrentStreams: Int = 384,
        val maxBurstSize: Int = 8 * 1024 * 1024, // 8 MB
        val adaptiveWindow: Boolean = true
    )
    
    private val _config = MutableStateFlow(BurstConfig())
    val config: StateFlow<BurstConfig> = _config.asStateFlow()
    
    private var currentWindowSize: Int = 0
    private var activeStreams: Int = 0
    
    /**
     * Update burst configuration
     */
    fun updateConfig(config: BurstConfig) {
        _config.value = config
        currentWindowSize = config.initialWindowSize
        AppLogger.d("$TAG: Burst config updated: window=${config.initialWindowSize}, streams=${config.maxConcurrentStreams}")
    }
    
    /**
     * Check if new stream can be opened
     */
    fun canOpenStream(): Boolean {
        return activeStreams < _config.value.maxConcurrentStreams
    }
    
    /**
     * Register stream opened
     */
    fun onStreamOpened() {
        activeStreams++
    }
    
    /**
     * Register stream closed
     */
    fun onStreamClosed() {
        activeStreams = maxOf(0, activeStreams - 1)
    }
    
    /**
     * Get current window size (adaptive)
     */
    fun getWindowSize(): Int {
        val baseConfig = _config.value
        if (!baseConfig.adaptiveWindow) {
            return baseConfig.initialWindowSize
        }
        
        // Adaptive: increase if few streams, decrease if many
        val ratio = if (baseConfig.maxConcurrentStreams > 0) {
            activeStreams.toFloat() / baseConfig.maxConcurrentStreams
        } else {
            0f
        }
        
        return when {
            ratio < 0.3f -> (baseConfig.initialWindowSize * 1.2f).toInt()
            ratio > 0.8f -> (baseConfig.initialWindowSize * 0.8f).toInt()
            else -> baseConfig.initialWindowSize
        }
    }
    
    /**
     * Get statistics
     */
    fun getStats(): BurstStats {
        return BurstStats(
            activeStreams = activeStreams,
            maxStreams = _config.value.maxConcurrentStreams,
            currentWindowSize = currentWindowSize,
            maxWindowSize = _config.value.maxBurstSize
        )
    }
    
    data class BurstStats(
        val activeStreams: Int,
        val maxStreams: Int,
        val currentWindowSize: Int,
        val maxWindowSize: Int
    )
    
    companion object {
        private const val TAG = "BurstTrafficManager"
    }
}

