package com.simplexray.an.protocol.streaming

import com.simplexray.an.common.AppLogger
import com.simplexray.an.logging.LoggerRepository
import com.simplexray.an.logging.LogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * AdaptiveBitrateStabilizer - Stabilizes adaptive bitrate by detecting drops and triggering route fallback.
 * 
 * FIXES IMPLEMENTED:
 * - Adaptive bitrate stabilization with event hooks
 * - onBitrateDrop: Detects consecutive bitrate drops
 * - onRebuffer: Detects rebuffering events
 * - If >2 consecutive drops: Force route chain to "streaming-proxy"
 * - Thread-safe per-domain tracking
 * 
 * Usage:
 * - Call onBitrateDrop() when bitrate decreases
 * - Call onRebuffer() when rebuffering occurs
 * - Route chain will be automatically updated if instability detected
 */
object AdaptiveBitrateStabilizer {
    private const val TAG = "AdaptiveBitrateStabilizer"
    private const val CONSECUTIVE_DROP_THRESHOLD = 2
    
    // Per-domain bitrate tracking
    private val bitrateHistory = ConcurrentHashMap<String, BitrateTracker>()
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Bitrate tracker for a domain
     */
    private data class BitrateTracker(
        val domain: String,
        val platform: StreamingRepository.StreamingPlatform,
        var consecutiveDrops: Int = 0,
        var lastBitrate: Long = 0L,
        var lastRebufferTime: Long = 0L,
        var currentRouteChain: String? = null
    )
    
    /**
     * Handle bitrate drop event.
     * 
     * @param domain Domain experiencing bitrate drop
     * @param platform Streaming platform
     * @param currentBitrate Current bitrate in bps
     * @param previousBitrate Previous bitrate in bps
     */
    fun onBitrateDrop(
        domain: String,
        platform: StreamingRepository.StreamingPlatform,
        currentBitrate: Long,
        previousBitrate: Long
    ) {
        val tracker = bitrateHistory.getOrPut("${platform.name}-$domain") {
            BitrateTracker(domain, platform, lastBitrate = previousBitrate)
        }
        
        // Check if this is a drop (current < previous)
        if (currentBitrate < previousBitrate) {
            tracker.consecutiveDrops++
            tracker.lastBitrate = currentBitrate
            
            AppLogger.d("$TAG: Bitrate drop for $domain: $previousBitrate -> $currentBitrate bps (consecutive: ${tracker.consecutiveDrops})")
            
            // If >2 consecutive drops, force route chain to streaming-proxy
            if (tracker.consecutiveDrops > CONSECUTIVE_DROP_THRESHOLD) {
                scope.launch {
                    forceStreamingRouteChain(domain, platform, tracker)
                }
            }
            
            // Also notify StreamingRepository
            StreamingRepository.reportBitrateDrop(domain, platform)
        } else {
            // Bitrate increased, reset consecutive drops
            tracker.consecutiveDrops = 0
            tracker.lastBitrate = currentBitrate
        }
        
        // Log bitrate drop event
        LoggerRepository.add(
            LogEvent.Info(
                message = "Streaming bitrate drop: $domain ${previousBitrate}bps -> ${currentBitrate}bps (consecutive: ${tracker.consecutiveDrops})",
                tag = TAG
            )
        )
    }
    
    /**
     * Handle rebuffering event.
     * 
     * @param domain Domain experiencing rebuffering
     * @param platform Streaming platform
     * @param durationMs Rebuffering duration in milliseconds
     */
    fun onRebuffer(
        domain: String,
        platform: StreamingRepository.StreamingPlatform,
        durationMs: Long
    ) {
        val tracker = bitrateHistory.getOrPut("${platform.name}-$domain") {
            BitrateTracker(domain, platform)
        }
        
        tracker.lastRebufferTime = System.currentTimeMillis()
        
        AppLogger.w("$TAG: Rebuffering for $domain: ${durationMs}ms")
        
        // Notify StreamingRepository
        StreamingRepository.reportRebuffering(domain, platform, durationMs)
        
        // If rebuffering is severe (>2 seconds), consider route chain update
        if (durationMs > 2000) {
            scope.launch {
                forceStreamingRouteChain(domain, platform, tracker)
            }
        }
        
        // Log rebuffering event
        LoggerRepository.add(
            LogEvent.Info(
                message = "Streaming rebuffer: $domain (${durationMs}ms)",
                tag = TAG
            )
        )
    }
    
    /**
     * Force route chain to streaming-proxy for unstable domain
     */
    private suspend fun forceStreamingRouteChain(
        domain: String,
        platform: StreamingRepository.StreamingPlatform,
        tracker: BitrateTracker
    ) {
        val routeChain = StreamingOutboundTagger.STREAMING_OUTBOUND_TAG
        
        if (tracker.currentRouteChain != routeChain) {
            AppLogger.w("$TAG: Forcing route chain to $routeChain for unstable domain: $domain")
            
            tracker.currentRouteChain = routeChain
            
            // Update route chain in StreamingRepository
            // This will trigger routing rule update
            StreamingOutboundTagger.tagStreamingDomain(
                domain,
                StreamingRepository.getTransportPreference(domain)
            )
            
            // Log route chain update
            LoggerRepository.add(
                LogEvent.Info(
                    message = "Streaming route chain updated: $domain -> $routeChain (instability detected)",
                    tag = TAG
                )
            )
        }
    }
    
    /**
     * Reset bitrate tracking for domain
     */
    fun reset(domain: String, platform: StreamingRepository.StreamingPlatform) {
        bitrateHistory.remove("${platform.name}-$domain")
        AppLogger.d("$TAG: Reset bitrate tracking for $domain")
    }
    
    /**
     * Clear all tracking
     */
    fun clearAll() {
        bitrateHistory.clear()
        AppLogger.d("$TAG: Cleared all bitrate tracking")
    }
    
    /**
     * Get current tracker for domain
     */
    fun getTracker(domain: String, platform: StreamingRepository.StreamingPlatform): BitrateTracker? {
        return bitrateHistory["${platform.name}-$domain"]
    }
}

