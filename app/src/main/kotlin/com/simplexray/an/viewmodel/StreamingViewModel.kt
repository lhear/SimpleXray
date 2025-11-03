package com.simplexray.an.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplexray.an.domain.DomainClassifier
import com.simplexray.an.grpc.GrpcChannelFactory
import com.simplexray.an.performance.optimizer.PerformanceOptimizer
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.protocol.streaming.StreamingOptimizer
import com.simplexray.an.protocol.streaming.StreamingOptimizer.*
import com.simplexray.an.protocol.streaming.StreamingOptimizationManager
import com.simplexray.an.topology.TopologyRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel for Streaming Optimization
 */
class StreamingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: Preferences = Preferences(application)
    private val gson: Gson = Gson()
    private val optimizer = StreamingOptimizer()
    
    // Initialize optimization manager
    private val classifier = DomainClassifier(application)
    private val performanceOptimizer = PerformanceOptimizer(application)
    private val optimizationManager = StreamingOptimizationManager(
        application,
        classifier,
        performanceOptimizer
    )
    
    // Topology repository for traffic monitoring
    private val topologyRepository = TopologyRepository(
        application,
        GrpcChannelFactory.statsStub(),
        viewModelScope
    )

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
        loadSavedSettings()
        startStreamingOptimization()
        
        // Observe optimization manager for real-time stats
        viewModelScope.launch {
            observeOptimizationManager()
        }
    }
    
    /**
     * Start streaming optimization monitoring
     */
    private fun startStreamingOptimization() {
        viewModelScope.launch {
            // Start topology repository
            topologyRepository.start()
            
            // Start optimization manager monitoring
            optimizationManager.startMonitoring(topologyRepository)
        }
    }
    
    /**
     * Observe optimization manager for stats and updates
     */
    private suspend fun observeOptimizationManager() {
        // Collect streaming stats periodically
        viewModelScope.launch {
            while (true) {
                val stats = optimizationManager.getStreamingStats()
                if (stats.isNotEmpty()) {
                    _streamingStats.value = stats
                }
                delay(2000) // Update stats every 2 seconds
            }
        }
        
        // Update current quality and buffer health from active sessions
        viewModelScope.launch {
            optimizationManager.activeStreamingSessions.collect { sessions ->
                if (sessions.isNotEmpty()) {
                    val activeSession = sessions.values.first()
                    _currentQuality.value = activeSession.config.preferredQuality
                    
                    // Update selected platform if changed
                    val currentPlatform = optimizationManager.getCurrentPlatform()
                    if (currentPlatform != null && _selectedPlatform.value != currentPlatform) {
                        _selectedPlatform.value = currentPlatform
                        prefs.selectedStreamingPlatform = currentPlatform.name
                    }
                }
            }
        }
        
        // Observe optimization enabled state
        viewModelScope.launch {
            optimizationManager.optimizationEnabled.collect { enabled ->
                _isOptimizationEnabled.value = enabled
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        optimizationManager.stopMonitoring()
        // TopologyRepository cleanup - scope is viewModelScope so it cancels automatically,
        // but we should still call stop() for consistency
        topologyRepository.stop()
    }

    private fun loadSavedSettings() {
        // Load optimization enabled state
        _isOptimizationEnabled.value = prefs.streamingOptimizationEnabled

        // Load selected platform
        val savedPlatformName = prefs.selectedStreamingPlatform
        if (savedPlatformName != null) {
            try {
                _selectedPlatform.value = StreamingPlatform.valueOf(savedPlatformName)
            } catch (e: Exception) {
                _selectedPlatform.value = null
            }
        }

        // Load platform configs
        val savedConfigsJson = prefs.streamingPlatformConfigs
        if (savedConfigsJson != null) {
            try {
                val type = object : TypeToken<Map<String, StreamingConfig>>() {}.type
                val savedConfigs: Map<String, StreamingConfig> = gson.fromJson(savedConfigsJson, type)
                val platformConfigs = mutableMapOf<StreamingPlatform, StreamingConfig>()
                savedConfigs.forEach { (platformName, config) ->
                    try {
                        val platform = StreamingPlatform.valueOf(platformName)
                        platformConfigs[platform] = config
                    } catch (e: Exception) {
                        // Skip invalid platform
                    }
                }
                if (platformConfigs.isNotEmpty()) {
                    _platformConfigs.value = platformConfigs
                }
            } catch (e: Exception) {
                // If deserialization fails, use default configs
            }
        }
    }

    private fun savePlatformConfigs() {
        try {
            val configsMap = _platformConfigs.value.mapKeys { it.key.name }
            val configsJson = gson.toJson(configsMap)
            prefs.streamingPlatformConfigs = configsJson
        } catch (e: Exception) {
            // Handle save error
        }
    }

    // Removed loadDefaultStats - now using real-time stats from optimization manager

    fun selectPlatform(platform: StreamingPlatform?) {
        _selectedPlatform.value = platform
        platform?.let {
            _currentQuality.value = it.config.preferredQuality
            prefs.selectedStreamingPlatform = platform.name
        } ?: run {
            prefs.selectedStreamingPlatform = null
        }
    }

    fun updatePlatformConfig(platform: StreamingPlatform, config: StreamingConfig) {
        viewModelScope.launch {
            val updated = _platformConfigs.value.toMutableMap()
            updated[platform] = config
            _platformConfigs.value = updated
            savePlatformConfigs()
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
        val newValue = !_isOptimizationEnabled.value
        _isOptimizationEnabled.value = newValue
        optimizationManager.setOptimizationEnabled(newValue)
        prefs.streamingOptimizationEnabled = newValue
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
