package com.simplexray.an.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.protocol.streaming.StreamingOptimizer
import com.simplexray.an.protocol.streaming.StreamingOptimizer.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Streaming Optimization
 */
class StreamingViewModel : ViewModel() {

    private val optimizer = StreamingOptimizer()

    private val _selectedPlatform = MutableStateFlow<StreamingPlatform?>(null)
    val selectedPlatform: StateFlow<StreamingPlatform?> = _selectedPlatform.asStateFlow()

    private val _platformConfigs = MutableStateFlow<Map<StreamingPlatform, StreamingConfig>>(
        StreamingPlatform.entries.associateWith { it.config }
    )
    val platformConfigs: StateFlow<Map<StreamingPlatform, StreamingConfig>> = _platformConfigs.asStateFlow()

    private val _streamingStats = MutableStateFlow<Map<StreamingPlatform, StreamingStats>>(emptyMap())
    val streamingStats: StateFlow<Map<StreamingPlatform, StreamingStats>> = _streamingStats.asStateFlow()

    private val _currentQuality = MutableStateFlow(StreamQuality.AUTO)
    val currentQuality: StateFlow<StreamQuality> = _currentQuality.asStateFlow()

    private val _bufferHealth = MutableStateFlow(BufferHealth.GOOD)
    val bufferHealth: StateFlow<BufferHealth> = _bufferHealth.asStateFlow()

    private val _isOptimizationEnabled = MutableStateFlow(true)
    val isOptimizationEnabled: StateFlow<Boolean> = _isOptimizationEnabled.asStateFlow()

    init {
        loadDefaultStats()
    }

    private fun loadDefaultStats() {
        viewModelScope.launch {
            // Initialize with sample stats
            val stats = StreamingPlatform.entries.associateWith { platform ->
                StreamingStats(
                    platform = platform,
                    currentQuality = StreamQuality.HIGH,
                    bufferLevel = 15,
                    bufferHealth = BufferHealth.GOOD,
                    averageBitrate = 5_000_000,
                    rebufferCount = 0,
                    totalRebufferTime = 0,
                    segmentsDownloaded = 100,
                    segmentsFailed = 2
                )
            }
            _streamingStats.value = stats
        }
    }

    fun selectPlatform(platform: StreamingPlatform?) {
        _selectedPlatform.value = platform
        platform?.let {
            _currentQuality.value = it.config.preferredQuality
        }
    }

    fun updatePlatformConfig(platform: StreamingPlatform, config: StreamingConfig) {
        viewModelScope.launch {
            val updated = _platformConfigs.value.toMutableMap()
            updated[platform] = config
            _platformConfigs.value = updated
        }
    }

    fun setQuality(quality: StreamQuality) {
        _currentQuality.value = quality
        _selectedPlatform.value?.let { platform ->
            val config = _platformConfigs.value[platform]
            config?.let {
                updatePlatformConfig(platform, it.copy(preferredQuality = quality))
            }
        }
    }

    fun toggleOptimization() {
        _isOptimizationEnabled.value = !_isOptimizationEnabled.value
    }

    fun getRecommendations(platform: StreamingPlatform, bandwidth: Long): List<String> {
        return optimizer.getOptimizationRecommendations(platform, bandwidth)
    }

    fun resetPlatformConfig(platform: StreamingPlatform) {
        viewModelScope.launch {
            val updated = _platformConfigs.value.toMutableMap()
            updated[platform] = platform.config
            _platformConfigs.value = updated
        }
    }

    fun updateBufferHealth(health: BufferHealth) {
        _bufferHealth.value = health
    }

    fun getAllPlatforms(): List<StreamingPlatform> = StreamingPlatform.entries

    fun getPlatformsByType(type: PlatformType): List<StreamingPlatform> {
        return when (type) {
            PlatformType.VIDEO -> listOf(
                StreamingPlatform.YOUTUBE,
                StreamingPlatform.NETFLIX,
                StreamingPlatform.PRIME_VIDEO,
                StreamingPlatform.DISNEY_PLUS,
                StreamingPlatform.TWITCH
            )
            PlatformType.MUSIC -> listOf(
                StreamingPlatform.SPOTIFY,
                StreamingPlatform.YOUTUBE_MUSIC
            )
            PlatformType.ALL -> StreamingPlatform.entries
        }
    }

    enum class PlatformType {
        VIDEO,
        MUSIC,
        ALL
    }
}
