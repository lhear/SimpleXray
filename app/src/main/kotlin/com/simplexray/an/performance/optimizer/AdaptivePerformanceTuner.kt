package com.simplexray.an.performance.optimizer

import android.content.Context
import com.simplexray.an.common.AppLogger
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.performance.model.NetworkType
import com.simplexray.an.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Advanced Adaptive Performance Tuner
 * Implements real-time tuning algorithm with learning-based recommendations
 */
class AdaptivePerformanceTuner(
    private val context: Context,
    private val optimizer: PerformanceOptimizer,
    private val scope: CoroutineScope
) {
    private val prefs = Preferences(context)
    
    // Tuning thresholds
    private val highLatencyThreshold = 200 // ms
    private val packetLossThreshold = 5f // percentage
    private val lowBandwidthThreshold = 1_000_000L // 1 Mbps
    private val highBandwidthThreshold = 50_000_000L // 50 Mbps
    
    // State tracking
    private val _tuningState = MutableStateFlow(TuningState())
    val tuningState: StateFlow<TuningState> = _tuningState.asStateFlow()
    
    private val _lastRecommendation = MutableStateFlow<ProfileRecommendation?>(null)
    val lastRecommendation: StateFlow<ProfileRecommendation?> = _lastRecommendation.asStateFlow()
    
    // Learning system - tracks user feedback and pattern recognition
    private val feedbackHistory = ConcurrentHashMap<String, FeedbackEntry>()
    private val patternHistory = mutableListOf<MetricPattern>()
    
    // Stability tracking
    private val metricHistory = mutableListOf<PerformanceMetrics>()
    private val maxHistorySize = 100
    
    init {
        // Note: Monitoring is now done via updateMetrics() calls from PerformanceMonitor
        // The monitorAndTune() loop is kept for backward compatibility but won't run
        // unless metrics are provided through updateMetrics()
    }
    
    /**
     * Continuous monitoring and tuning loop
     */
    private suspend fun monitorAndTune() {
        while (true) {
            try {
                delay(5000) // Check every 5 seconds
                
                if (!optimizer.autoTuneEnabled.value) {
                    continue
                }
                
                // Metrics are now provided via updateMetrics() from PerformanceMonitor
                // This loop is kept for compatibility but won't actively monitor
                delay(30000) // Check every 30 seconds as fallback
            } catch (e: Exception) {
                AppLogger.e("AdaptivePerformanceTuner: Monitoring error", e)
                delay(10000) // Wait longer on error
            }
        }
    }
    
    /**
     * Analyze metrics and generate recommendations
     */
    private suspend fun analyzeAndRecommend(metrics: PerformanceMetrics) {
        // Add to history
        metricHistory.add(metrics)
        if (metricHistory.size > maxHistorySize) {
            metricHistory.removeAt(0)
        }
        
        // Detect patterns
        detectPatterns(metrics)
        
        // Generate recommendation
        val recommendation = generateRecommendation(metrics)
        
        if (recommendation != null) {
            _lastRecommendation.value = recommendation
            
            // Apply recommendation if auto-apply is enabled
            if (prefs.adaptiveTuningAutoApply) {
                applyRecommendation(recommendation)
            } else {
                // Store for user approval
                _tuningState.value = _tuningState.value.copy(
                    pendingRecommendation = recommendation
                )
            }
        }
        
        // Update tuning state
        updateTuningState(metrics)
    }
    
    /**
     * Generate profile recommendation based on network conditions
     */
    private fun generateRecommendation(metrics: PerformanceMetrics): ProfileRecommendation? {
        val currentProfileId = optimizer.currentProfile.value.id
        val networkType = try {
            NetworkType.detect(context)
        } catch (e: Exception) {
            NetworkType.WiFi
        }
        
        // Rule 1: High Latency (> 200ms) → Low Latency profile (Gaming)
        if (metrics.latency > highLatencyThreshold) {
            if (currentProfileId != PerformanceProfile.Gaming.id) {
                return ProfileRecommendation(
                    profile = PerformanceProfile.Gaming,
                    reason = "High latency detected (${metrics.latency}ms). Gaming profile optimizes for low latency.",
                    confidence = calculateConfidence(metrics.latency.toFloat(), highLatencyThreshold.toFloat(), 500f),
                    trigger = RecommendationTrigger.HighLatency(metrics.latency)
                )
            }
        }
        
        // Rule 2: High Packet Loss (> 5%) → Reliability profile (Balanced with better stability)
        if (metrics.packetLoss > packetLossThreshold) {
            if (currentProfileId != PerformanceProfile.Balanced.id && 
                currentProfileId != PerformanceProfile.BatterySaver.id) {
                return ProfileRecommendation(
                    profile = PerformanceProfile.Balanced,
                    reason = "High packet loss detected (${String.format("%.1f", metrics.packetLoss)}%). Balanced profile provides better reliability.",
                    confidence = calculateConfidence(metrics.packetLoss, packetLossThreshold, 20f),
                    trigger = RecommendationTrigger.HighPacketLoss(metrics.packetLoss)
                )
            }
        }
        
        // Rule 3: Low Bandwidth (< 1 Mbps) → Battery Saver profile
        val bandwidthMbps = metrics.downloadSpeed / 1_000_000f
        if (metrics.downloadSpeed > 0 && metrics.downloadSpeed < lowBandwidthThreshold) {
            if (currentProfileId != PerformanceProfile.BatterySaver.id) {
                return ProfileRecommendation(
                    profile = PerformanceProfile.BatterySaver,
                    reason = "Low bandwidth detected (${String.format("%.2f", bandwidthMbps)} Mbps). Battery Saver mode reduces data usage.",
                    confidence = calculateConfidence(metrics.downloadSpeed.toFloat(), lowBandwidthThreshold.toFloat(), 0f),
                    trigger = RecommendationTrigger.LowBandwidth(metrics.downloadSpeed)
                )
            }
        }
        
        // Rule 4: High Bandwidth (> 50 Mbps) → High Throughput profile (Turbo or Ultimate)
        if (metrics.downloadSpeed > highBandwidthThreshold) {
            val recommendedProfile = if (currentProfileId == PerformanceProfile.Turbo.id) {
                PerformanceProfile.Ultimate
            } else if (currentProfileId != PerformanceProfile.Ultimate.id && 
                       currentProfileId != PerformanceProfile.Turbo.id) {
                PerformanceProfile.Turbo
            } else {
                null
            }
            
            if (recommendedProfile != null) {
                return ProfileRecommendation(
                    profile = recommendedProfile,
                    reason = "High bandwidth detected (${String.format("%.2f", bandwidthMbps)} Mbps). ${recommendedProfile.name} mode maximizes throughput.",
                    confidence = calculateConfidence(metrics.downloadSpeed.toFloat(), highBandwidthThreshold.toFloat(), 100_000_000f),
                    trigger = RecommendationTrigger.HighBandwidth(metrics.downloadSpeed)
                )
            }
        }
        
        // Learning-based recommendation (if patterns detected)
        val learnedRecommendation = generateLearnedRecommendation(metrics, currentProfileId)
        if (learnedRecommendation != null) {
            return learnedRecommendation
        }
        
        return null
    }
    
    /**
     * Calculate recommendation confidence (0-100)
     */
    private fun calculateConfidence(value: Float, threshold: Float, maxValue: Float): Int {
        val range = maxValue - threshold
        if (range <= 0) return 50
        
        val distance = if (value > threshold) {
            (value - threshold) / range
        } else {
            (threshold - value) / range
        }
        
        return (50 + (distance * 50)).toInt().coerceIn(0, 100)
    }
    
    /**
     * Detect patterns in metrics for learning
     */
    private fun detectPatterns(metrics: PerformanceMetrics) {
        if (metricHistory.size < 10) return
        
        // Detect latency spike pattern
        val recentLatencies = metricHistory.takeLast(10).map { it.latency }
        val avgLatency = recentLatencies.average()
        if (metrics.latency > avgLatency * 1.5 && avgLatency > highLatencyThreshold) {
            patternHistory.add(
                MetricPattern(
                    type = PatternType.LatencySpike,
                    timestamp = System.currentTimeMillis(),
                    networkType = try { NetworkType.detect(context) } catch (e: Exception) { NetworkType.WiFi }
                )
            )
        }
        
        // Detect bandwidth drop pattern
        val recentBandwidths = metricHistory.takeLast(10).map { it.downloadSpeed }
        val avgBandwidth = recentBandwidths.average()
        if (metrics.downloadSpeed < avgBandwidth * 0.5 && avgBandwidth > lowBandwidthThreshold) {
            patternHistory.add(
                MetricPattern(
                    type = PatternType.BandwidthDrop,
                    timestamp = System.currentTimeMillis(),
                    networkType = try { NetworkType.detect(context) } catch (e: Exception) { NetworkType.WiFi }
                )
            )
        }
        
        // Keep only recent patterns
        if (patternHistory.size > 50) {
            patternHistory.removeAt(0)
        }
    }
    
    /**
     * Generate learned recommendation based on patterns
     */
    private fun generateLearnedRecommendation(
        metrics: PerformanceMetrics,
        currentProfileId: String
    ): ProfileRecommendation? {
        // Check if we have learned patterns for this network type
        val networkType = try {
            NetworkType.detect(context)
        } catch (e: Exception) {
            NetworkType.WiFi
        }
        
        val relevantPatterns = patternHistory.filter { 
            it.networkType == networkType && 
            (System.currentTimeMillis() - it.timestamp) < 24 * 60 * 60 * 1000 // Last 24 hours
        }
        
        // If we see consistent latency spikes, recommend Gaming profile
        val latencySpikes = relevantPatterns.count { it.type == PatternType.LatencySpike }
        if (latencySpikes >= 3 && currentProfileId != PerformanceProfile.Gaming.id) {
            return ProfileRecommendation(
                profile = PerformanceProfile.Gaming,
                reason = "Detected latency spikes on this network. Gaming profile may help.",
                confidence = minOf(70, latencySpikes * 10),
                trigger = RecommendationTrigger.LearnedPattern(PatternType.LatencySpike)
            )
        }
        
        // If we see consistent bandwidth drops, recommend Balanced
        val bandwidthDrops = relevantPatterns.count { it.type == PatternType.BandwidthDrop }
        if (bandwidthDrops >= 3 && currentProfileId != PerformanceProfile.Balanced.id) {
            return ProfileRecommendation(
                profile = PerformanceProfile.Balanced,
                reason = "Detected bandwidth instability on this network. Balanced profile may help.",
                confidence = minOf(70, bandwidthDrops * 10),
                trigger = RecommendationTrigger.LearnedPattern(PatternType.BandwidthDrop)
            )
        }
        
        return null
    }
    
    /**
     * Apply recommendation
     */
    fun applyRecommendation(recommendation: ProfileRecommendation) {
        optimizer.setProfile(recommendation.profile)
        _tuningState.value = _tuningState.value.copy(
            pendingRecommendation = null,
            lastAppliedRecommendation = recommendation,
            lastAppliedAt = System.currentTimeMillis()
        )
        AppLogger.d("AdaptivePerformanceTuner: Applied recommendation: ${recommendation.profile.name}")
    }
    
    /**
     * User feedback on recommendation
     */
    fun provideFeedback(recommendation: ProfileRecommendation, accepted: Boolean) {
        val key = "${recommendation.trigger.type}_${recommendation.profile.id}"
        val entry = feedbackHistory.getOrPut(key) {
            FeedbackEntry(
                triggerType = recommendation.trigger.type,
                profileId = recommendation.profile.id,
                acceptedCount = 0,
                rejectedCount = 0
            )
        }
        
        if (accepted) {
            entry.acceptedCount++
        } else {
            entry.rejectedCount++
        }
        
        // Store in preferences for persistence
        prefs.setAdaptiveTuningFeedback(key, accepted)
        
        AppLogger.d("AdaptivePerformanceTuner: Feedback recorded: $key = $accepted")
    }
    
    /**
     * Update tuning state
     */
    private fun updateTuningState(metrics: PerformanceMetrics) {
        _tuningState.value = _tuningState.value.copy(
            isActive = optimizer.autoTuneEnabled.value,
            lastCheckTime = System.currentTimeMillis(),
            currentMetrics = metrics,
            patternCount = patternHistory.size
        )
    }
    
    /**
     * Get current metrics from PerformanceMonitor
     * Note: This should be called with metrics from PerformanceMonitor
     */
    fun updateMetrics(metrics: PerformanceMetrics) {
        scope.launch {
            analyzeAndRecommend(metrics)
        }
    }
    
    /**
     * Enable/disable adaptive tuning
     */
    fun setEnabled(enabled: Boolean) {
        optimizer.setAutoTuneEnabled(enabled)
    }
}

/**
 * Tuning state
 */
data class TuningState(
    val isActive: Boolean = false,
    val lastCheckTime: Long = 0,
    val pendingRecommendation: ProfileRecommendation? = null,
    val lastAppliedRecommendation: ProfileRecommendation? = null,
    val lastAppliedAt: Long = 0,
    val currentMetrics: PerformanceMetrics? = null,
    val patternCount: Int = 0
)

/**
 * Profile recommendation
 */
data class ProfileRecommendation(
    val profile: PerformanceProfile,
    val reason: String,
    val confidence: Int, // 0-100
    val trigger: RecommendationTrigger
)

/**
 * Recommendation trigger
 */
sealed class RecommendationTrigger(val type: String) {
    data class HighLatency(val latency: Int) : RecommendationTrigger("high_latency")
    data class HighPacketLoss(val packetLoss: Float) : RecommendationTrigger("high_packet_loss")
    data class LowBandwidth(val bandwidth: Long) : RecommendationTrigger("low_bandwidth")
    data class HighBandwidth(val bandwidth: Long) : RecommendationTrigger("high_bandwidth")
    data class LearnedPattern(val pattern: PatternType) : RecommendationTrigger("learned_pattern")
}

/**
 * Pattern type
 */
enum class PatternType {
    LatencySpike,
    BandwidthDrop,
    PacketLoss,
    ConnectionInstability
}

/**
 * Metric pattern
 */
data class MetricPattern(
    val type: PatternType,
    val timestamp: Long,
    val networkType: NetworkType
)

/**
 * Feedback entry
 */
data class FeedbackEntry(
    val triggerType: String,
    val profileId: String,
    var acceptedCount: Int,
    var rejectedCount: Int
) {
    val acceptanceRate: Float
        get() = if (acceptedCount + rejectedCount == 0) 0f
                else acceptedCount.toFloat() / (acceptedCount + rejectedCount)
}

