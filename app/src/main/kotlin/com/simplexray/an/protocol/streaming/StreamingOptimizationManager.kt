package com.simplexray.an.protocol.streaming

import android.content.Context
import android.util.Log
import com.simplexray.an.domain.DomainClassifier
import com.simplexray.an.performance.optimizer.PerformanceOptimizer
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.topology.TopologyRepository
import com.simplexray.an.xray.XrayConfigPatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.coroutineContext
import java.util.concurrent.ConcurrentHashMap

// Type aliases for cleaner code
typealias StreamingPlatform = StreamingOptimizer.StreamingPlatform
typealias StreamingConfig = StreamingOptimizer.StreamingConfig
typealias StreamQuality = StreamingOptimizer.StreamQuality
typealias BufferHealth = StreamingOptimizer.BufferHealth
typealias StreamingStats = StreamingOptimizer.StreamingStats

/**
 * Manages streaming optimization by monitoring traffic and applying platform-specific optimizations
 */
class StreamingOptimizationManager(
    private val context: Context,
    private val classifier: DomainClassifier,
    private val performanceOptimizer: PerformanceOptimizer
) {
    private val prefs = Preferences(context)
    private val optimizer = StreamingOptimizer()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _activeStreamingSessions = MutableStateFlow<Map<String, StreamingSession>>(emptyMap())
    val activeStreamingSessions: StateFlow<Map<String, StreamingSession>> = _activeStreamingSessions.asStateFlow()
    
    private val _optimizationEnabled = MutableStateFlow(prefs.streamingOptimizationEnabled)
    val optimizationEnabled: StateFlow<Boolean> = _optimizationEnabled.asStateFlow()
    
    private val sessionControllers = ConcurrentHashMap<String, StreamingOptimizer.AdaptiveBitrateController>()
    private val sessionBuffers = ConcurrentHashMap<String, StreamingOptimizer.BufferManager>()
    private val sessionStats = ConcurrentHashMap<String, StreamingStatsBuilder>()
    
    private var monitoringJob: Job? = null
    private var currentPlatform: StreamingPlatform? = null
    
    data class StreamingSession(
        val domain: String,
        val platform: StreamingPlatform,
        val startTime: Long,
        val config: StreamingConfig,
        var totalBytes: Long = 0,
        var peakBitrate: Long = 0
    )
    
    private class StreamingStatsBuilder(
        val platform: StreamingPlatform,
        var segmentsDownloaded: Int = 0,
        var segmentsFailed: Int = 0,
        var rebufferCount: Int = 0,
        var totalRebufferTime: Long = 0,
        var totalBytes: Long = 0,
        var lastUpdate: Long = System.currentTimeMillis()
    ) {
        fun build(
            currentQuality: StreamQuality,
            bufferLevel: Int,
            bufferHealth: BufferHealth,
            averageBitrate: Long
        ): StreamingStats {
            return StreamingStats(
                platform = platform,
                currentQuality = currentQuality,
                bufferLevel = bufferLevel,
                bufferHealth = bufferHealth,
                averageBitrate = averageBitrate,
                rebufferCount = rebufferCount,
                totalRebufferTime = totalRebufferTime,
                segmentsDownloaded = segmentsDownloaded,
                segmentsFailed = segmentsFailed
            )
        }
    }
    
    /**
     * Start monitoring traffic for streaming optimization
     */
    fun startMonitoring(topologyRepository: TopologyRepository? = null) {
        if (monitoringJob?.isActive == true) {
            Log.d(TAG, "Monitoring already active")
            return
        }
        
        monitoringJob = scope.launch {
            if (topologyRepository != null) {
                // Monitor via topology graph
                monitorViaTopology(topologyRepository)
            } else {
                // Fallback: periodic check
                periodicCheck()
            }
        }
        
        Log.d(TAG, "Streaming optimization monitoring started")
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        Log.d(TAG, "Streaming optimization monitoring stopped")
    }
    
    /**
     * Monitor via topology graph (real-time domain detection)
     */
    private suspend fun monitorViaTopology(repository: TopologyRepository) {
        try {
            repository.graph.collect { (nodes, edges) ->
                if (!_optimizationEnabled.value) {
                    return@collect
                }
                
                // Find streaming domains
                nodes.forEach { node ->
                    if (node.type == com.simplexray.an.topology.Node.Type.Domain) {
                        val domain = node.label
                        val platform = classifier.detectStreamingPlatform(domain)
                        
                        if (platform != null) {
                            handleStreamingDomain(domain, platform, node.weight)
                        }
                    }
                }
                
                // Clean up inactive sessions
                cleanupInactiveSessions()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in topology monitoring", e)
        }
    }
    
    /**
     * Periodic check fallback
     */
    private suspend fun periodicCheck() {
        while (coroutineContext.isActive) {
            try {
                // Check if optimization is enabled
                if (!_optimizationEnabled.value) {
                    delay(5000)
                    continue
                }
                
                // In a real implementation, this would check active connections
                // For now, we'll rely on topology monitoring
                delay(10000)
            } catch (e: Exception) {
                Log.e(TAG, "Error in periodic check", e)
                delay(5000)
            }
        }
    }
    
    /**
     * Handle detected streaming domain
     */
    private suspend fun handleStreamingDomain(
        domain: String,
        platform: StreamingPlatform,
        trafficWeight: Float
    ) {
        val sessionKey = "$platform-$domain"
        val existingSession = _activeStreamingSessions.value[sessionKey]
        
        if (existingSession == null && trafficWeight > 0.1f) {
            // New streaming session detected
            createStreamingSession(domain, platform)
            applyStreamingOptimizations(platform)
        } else if (existingSession != null) {
            // Update existing session
            updateStreamingSession(sessionKey, existingSession, trafficWeight)
        }
    }
    
    /**
     * Create new streaming session
     */
    private suspend fun createStreamingSession(domain: String, platform: StreamingPlatform) {
        val sessionKey = "$platform-$domain"
        val config = getOptimizedConfig(platform)
        
        val session = StreamingSession(
            domain = domain,
            platform = platform,
            startTime = System.currentTimeMillis(),
            config = config
        )
        
        // Initialize controllers
        if (config.adaptiveBitrate) {
            sessionControllers[sessionKey] = StreamingOptimizer.AdaptiveBitrateController(config)
        }
        sessionBuffers[sessionKey] = StreamingOptimizer.BufferManager(config)
        sessionStats[sessionKey] = StreamingStatsBuilder(platform)
        
        // Update active sessions
        val updated = _activeStreamingSessions.value.toMutableMap()
        updated[sessionKey] = session
        _activeStreamingSessions.value = updated
        
        Log.d(TAG, "Created streaming session for $platform on $domain")
    }
    
    /**
     * Update existing streaming session
     */
    private fun updateStreamingSession(
        sessionKey: String,
        session: StreamingSession,
        trafficWeight: Float
    ) {
        // Estimate bitrate from traffic weight (simplified)
        val estimatedBitrate = (trafficWeight * 10_000_000).toLong() // Rough estimate
        
        val updated = session.copy(
            totalBytes = session.totalBytes + (estimatedBitrate / 8 * 1000), // Rough byte count
            peakBitrate = maxOf(session.peakBitrate, estimatedBitrate)
        )
        
        // Update buffer manager if exists
        sessionBuffers[sessionKey]?.let { buffer ->
            // Simulate buffer updates (in real implementation, this would come from actual playback)
            if (buffer.getBufferLevel() < session.config.bufferAhead) {
                buffer.addToBuffer(session.config.segmentSize)
            }
        }
        
        // Update stats
        sessionStats[sessionKey]?.let { stats ->
            stats.totalBytes += (estimatedBitrate / 8 * 1000).toLong()
            stats.lastUpdate = System.currentTimeMillis()
            stats.segmentsDownloaded++
        }
        
        val updatedSessions = _activeStreamingSessions.value.toMutableMap()
        updatedSessions[sessionKey] = updated
        _activeStreamingSessions.value = updatedSessions
    }
    
    /**
     * Apply streaming-specific optimizations
     */
    private suspend fun applyStreamingOptimizations(platform: StreamingPlatform) {
        try {
            // Set performance profile to Streaming mode
            if (performanceOptimizer.currentProfile.value != PerformanceProfile.Streaming) {
                performanceOptimizer.setProfile(PerformanceProfile.Streaming)
                Log.d(TAG, "Switched to Streaming performance profile for $platform")
            }
            
            // Apply protocol optimizations via Xray config
            val config = getOptimizedConfig(platform)
            updateXrayConfigForStreaming(config)
            
            currentPlatform = platform
            Log.d(TAG, "Applied optimizations for $platform")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying streaming optimizations", e)
        }
    }
    
    /**
     * Update Xray config for streaming
     */
    private suspend fun updateXrayConfigForStreaming(config: StreamingConfig) {
        try {
            // Reload Xray config to apply streaming profile settings
            withContext(Dispatchers.IO) {
                XrayConfigPatcher.patchConfig(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Xray config", e)
        }
    }
    
    /**
     * Get optimized config for platform (from preferences or default)
     */
    private fun getOptimizedConfig(platform: StreamingPlatform): StreamingConfig {
        // In a real implementation, load from preferences
        // For now, use platform default
        return platform.config
    }
    
    /**
     * Clean up inactive sessions
     */
    private suspend fun cleanupInactiveSessions() {
        val now = System.currentTimeMillis()
        val inactiveThreshold = 30_000L // 30 seconds
        
        val active = _activeStreamingSessions.value.filter { (key, session) ->
            val stats = sessionStats[key]
            val isActive = stats != null && (now - stats.lastUpdate) < inactiveThreshold
            
            if (!isActive) {
                // Clean up controllers
                sessionControllers.remove(key)
                sessionBuffers.remove(key)
                sessionStats.remove(key)
                Log.d(TAG, "Cleaned up inactive session: $key")
            }
            
            isActive
        }
        
        if (active.size != _activeStreamingSessions.value.size) {
            _activeStreamingSessions.value = active
        }
    }
    
    /**
     * Get streaming stats for all active platforms
     */
    suspend fun getStreamingStats(): Map<StreamingPlatform, StreamingStats> {
        return _activeStreamingSessions.value.mapNotNull { (key, session) ->
            val controller = sessionControllers[key]
            val buffer = sessionBuffers[key]
            val statsBuilder = sessionStats[key]
            
            if (controller == null || buffer == null || statsBuilder == null) {
                null
            } else {
                val quality = controller.getCurrentQuality()
                val bufferLevel = buffer.getBufferLevel()
                val bufferHealth = buffer.getBufferHealth()
                
                // Calculate average bitrate
                val duration = (System.currentTimeMillis() - statsBuilder.lastUpdate) / 1000
                val avgBitrate = if (duration > 0) {
                    (statsBuilder.totalBytes * 8) / duration
                } else {
                    0L
                }
                
                session.platform to statsBuilder.build(quality, bufferLevel, bufferHealth, avgBitrate)
            }
        }.toMap()
    }
    
    /**
     * Enable/disable optimization
     */
    fun setOptimizationEnabled(enabled: Boolean) {
        _optimizationEnabled.value = enabled
        prefs.streamingOptimizationEnabled = enabled
        
        if (!enabled) {
            // Revert to previous profile
            val previousProfile = prefs.performanceProfile ?: "balanced"
            performanceOptimizer.setProfile(PerformanceProfile.fromId(previousProfile))
            currentPlatform = null
        }
    }
    
    /**
     * Report segment download (for stats)
     */
    fun reportSegmentDownload(platform: StreamingPlatform, success: Boolean) {
        val session = _activeStreamingSessions.value.values.find { it.platform == platform }
        if (session != null) {
            val key = "${platform}-${session.domain}"
            sessionStats[key]?.let { stats ->
                if (success) {
                    stats.segmentsDownloaded++
                } else {
                    stats.segmentsFailed++
                }
            }
        }
    }
    
    /**
     * Report rebuffering event
     */
    fun reportRebuffering(platform: StreamingPlatform, durationMs: Long) {
        val session = _activeStreamingSessions.value.values.find { it.platform == platform }
        if (session != null) {
            val key = "${platform}-${session.domain}"
            sessionStats[key]?.let { stats ->
                stats.rebufferCount++
                stats.totalRebufferTime += durationMs
            }
            
            // Trigger buffer management
            sessionBuffers[key]?.let { buffer ->
                buffer.setBuffering(true)
                // Attempt to recover by increasing buffer target
            }
        }
    }
    
    /**
     * Get current active platform
     */
    fun getCurrentPlatform(): StreamingPlatform? = currentPlatform
    
    companion object {
        private const val TAG = "StreamingOptManager"
    }
}

