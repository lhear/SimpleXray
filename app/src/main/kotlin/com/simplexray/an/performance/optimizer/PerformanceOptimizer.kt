package com.simplexray.an.performance.optimizer

import android.content.Context
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.performance.model.PerformanceConfig
import com.simplexray.an.performance.model.NetworkType
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.monitor.ConnectionAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Adaptive performance optimizer
 */
class PerformanceOptimizer(
    private val context: Context
) {
    private val analyzer = ConnectionAnalyzer()

    private val _currentProfile = MutableStateFlow<PerformanceProfile>(PerformanceProfile.Balanced)
    val currentProfile: StateFlow<PerformanceProfile> = _currentProfile.asStateFlow()

    private val _adaptiveConfig = MutableStateFlow<PerformanceConfig>(PerformanceProfile.Balanced.config)
    val adaptiveConfig: StateFlow<PerformanceConfig> = _adaptiveConfig.asStateFlow()

    private val _autoTuneEnabled = MutableStateFlow(false)
    val autoTuneEnabled: StateFlow<Boolean> = _autoTuneEnabled.asStateFlow()

    /**
     * Set performance profile
     */
    fun setProfile(profile: PerformanceProfile) {
        _currentProfile.value = profile
        updateAdaptiveConfig()
    }

    /**
     * Enable/disable auto-tuning
     */
    fun setAutoTuneEnabled(enabled: Boolean) {
        _autoTuneEnabled.value = enabled
        if (enabled) {
            updateAdaptiveConfig()
        }
    }

    /**
     * Update configuration based on current conditions
     */
    fun updateAdaptiveConfig() {
        try {
            val baseConfig = _currentProfile.value.config

            val networkType = try {
                NetworkType.detect(context)
            } catch (e: Exception) {
                android.util.Log.w("PerformanceOptimizer", "Failed to detect network type, using default", e)
                NetworkType.WiFi  // Default fallback
            }

            val adjustment = networkType.configAdjustment

            // Apply network-specific adjustments with safety bounds
            val adaptedConfig = baseConfig.copy(
                bufferSize = ((baseConfig.bufferSize * adjustment.bufferMultiplier).toInt()).coerceAtLeast(32 * 1024),
                connectionTimeout = ((baseConfig.connectionTimeout * adjustment.timeoutMultiplier).toInt()).coerceAtMost(60000),
                handshakeTimeout = ((baseConfig.handshakeTimeout * adjustment.timeoutMultiplier).toInt()).coerceAtMost(30000),
                tcpFastOpen = baseConfig.tcpFastOpen && adjustment.aggressiveOptimization,
                dnsPrefetch = baseConfig.dnsPrefetch && adjustment.aggressiveOptimization
            )

            _adaptiveConfig.value = adaptedConfig
            android.util.Log.d("PerformanceOptimizer", "Adaptive config updated for ${networkType.name}")
        } catch (e: Exception) {
            android.util.Log.e("PerformanceOptimizer", "Error updating adaptive config", e)
            // Don't crash, just log the error
        }
    }

    /**
     * Auto-tune based on performance metrics
     */
    suspend fun autoTune(metrics: PerformanceMetrics) {
        try {
            if (!_autoTuneEnabled.value) return

            // Get recommendation from analyzer
            val recommendation = analyzer.recommendProfile(metrics, _currentProfile.value.id)

            if (recommendation != null && recommendation != _currentProfile.value.id) {
                val newProfile = PerformanceProfile.fromId(recommendation)
                setProfile(newProfile)
            }

            // Fine-tune current configuration
            val bottlenecks = analyzer.detectBottlenecks(metrics)
            if (bottlenecks.isNotEmpty()) {
                applyBottleneckFixes(bottlenecks)
            }
        } catch (e: Exception) {
            android.util.Log.e("PerformanceOptimizer", "Error during auto-tune", e)
        }
    }

    /**
     * Apply fixes for detected bottlenecks
     */
    private fun applyBottleneckFixes(bottlenecks: List<com.simplexray.an.performance.monitor.Bottleneck>) {
        try {
            var config = _adaptiveConfig.value

            bottlenecks.forEach { bottleneck ->
                config = when (bottleneck.type) {
                    com.simplexray.an.performance.monitor.BottleneckType.Memory -> {
                        // Reduce buffer size (minimum 32KB)
                        config.copy(
                            bufferSize = maxOf(32 * 1024, (config.bufferSize * 0.8).toInt()),
                            parallelConnections = maxOf(1, config.parallelConnections - 1)
                        )
                    }
                    com.simplexray.an.performance.monitor.BottleneckType.CPU -> {
                        // Disable compression
                        config.copy(
                            enableCompression = false,
                            parallelConnections = maxOf(1, config.parallelConnections - 1)
                        )
                    }
                    com.simplexray.an.performance.monitor.BottleneckType.HighLatency -> {
                        // Enable TCP optimizations
                        config.copy(
                            tcpFastOpen = true,
                            tcpNoDelay = true,
                            keepAliveInterval = minOf(config.keepAliveInterval, 30)
                        )
                    }
                    com.simplexray.an.performance.monitor.BottleneckType.PacketLoss -> {
                        // Increase timeouts (max 60 seconds)
                        config.copy(
                            connectionTimeout = minOf(60000, (config.connectionTimeout * 1.2).toInt()),
                            keepAlive = true
                        )
                    }
                    com.simplexray.an.performance.monitor.BottleneckType.LowBandwidth -> {
                        // Reduce buffer and parallel connections for low bandwidth
                        config.copy(
                            bufferSize = maxOf(32 * 1024, (config.bufferSize * 0.6).toInt()),
                            parallelConnections = maxOf(1, config.parallelConnections - 1),
                            enableMultiplexing = false
                        )
                    }
                    com.simplexray.an.performance.monitor.BottleneckType.Connection -> {
                        // Generic connection issues - reduce aggressive settings
                        config.copy(
                            tcpFastOpen = false,
                            parallelConnections = maxOf(1, config.parallelConnections - 1),
                            connectionTimeout = minOf(60000, (config.connectionTimeout * 1.5).toInt())
                        )
                    }
                }
            }

            _adaptiveConfig.value = config
        } catch (e: Exception) {
            android.util.Log.e("PerformanceOptimizer", "Error applying bottleneck fixes", e)
        }
    }

    /**
     * Generate Xray configuration JSON from performance config
     */
    fun generateXrayConfig(config: PerformanceConfig): Map<String, Any> {
        return mapOf(
            "log" to mapOf(
                "loglevel" to config.logLevel
            ),
            "policy" to mapOf(
                "levels" to mapOf(
                    "0" to mapOf(
                        "handshake" to config.handshakeTimeout,
                        "connIdle" to config.idleTimeout,
                        "uplinkOnly" to 2,
                        "downlinkOnly" to 4,
                        "bufferSize" to (config.bufferSize / 1024) // KB
                    )
                ),
                "system" to mapOf(
                    "statsInboundUplink" to true,
                    "statsInboundDownlink" to true,
                    "statsOutboundUplink" to true,
                    "statsOutboundDownlink" to true
                )
            ),
            "stats" to emptyMap<String, Any>()
        )
    }

    /**
     * Get optimization recommendations
     */
    fun getRecommendations(metrics: PerformanceMetrics): List<OptimizationRecommendation> {
        val recommendations = mutableListOf<OptimizationRecommendation>()

        // Network type recommendation
        val networkType = try {
            NetworkType.detect(context)
        } catch (e: Exception) {
            android.util.Log.w("PerformanceOptimizer", "Failed to detect network type for recommendations", e)
            NetworkType.WiFi
        }

        if (networkType.isMetered && _currentProfile.value != PerformanceProfile.BatterySaver) {
            recommendations.add(
                OptimizationRecommendation(
                    title = "Consider Battery Saver Mode",
                    description = "You're on a metered connection. Battery Saver mode can reduce data usage.",
                    impact = RecommendationImpact.Medium,
                    action = { setProfile(PerformanceProfile.BatterySaver) }
                )
            )
        }

        // Quality-based recommendations
        when (metrics.getConnectionQuality()) {
            com.simplexray.an.performance.model.ConnectionQuality.Poor,
            com.simplexray.an.performance.model.ConnectionQuality.VeryPoor -> {
                recommendations.add(
                    OptimizationRecommendation(
                        title = "Poor Connection Quality",
                        description = "Try switching to Balanced or Battery Saver mode for better stability.",
                        impact = RecommendationImpact.High,
                        action = { setProfile(PerformanceProfile.Balanced) }
                    )
                )
            }
            com.simplexray.an.performance.model.ConnectionQuality.Excellent -> {
                if (_currentProfile.value == PerformanceProfile.BatterySaver) {
                    recommendations.add(
                        OptimizationRecommendation(
                            title = "Excellent Connection",
                            description = "Your connection is excellent. Consider Turbo mode for better performance.",
                            impact = RecommendationImpact.Low,
                            action = { setProfile(PerformanceProfile.Turbo) }
                        )
                    )
                }
            }
            else -> {}
        }

        // Memory recommendations
        if (metrics.memoryUsage > 200 * 1024 * 1024) {
            recommendations.add(
                OptimizationRecommendation(
                    title = "High Memory Usage",
                    description = "Memory usage is high. Consider reducing buffer size or switching profiles.",
                    impact = RecommendationImpact.High,
                    action = {
                        _adaptiveConfig.value = _adaptiveConfig.value.copy(
                            bufferSize = (_adaptiveConfig.value.bufferSize * 0.7).toInt()
                        )
                    }
                )
            )
        }

        return recommendations
    }
}

/**
 * Optimization recommendation
 */
data class OptimizationRecommendation(
    val title: String,
    val description: String,
    val impact: RecommendationImpact,
    val action: () -> Unit
)

enum class RecommendationImpact {
    Low,
    Medium,
    High,
    Critical
}
