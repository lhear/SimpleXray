package com.simplexray.an.protocol.streaming

import com.simplexray.an.common.AppLogger
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * ThroughputSmoother - Sliding-window throughput smoothing with jitter damping.
 * 
 * FIXES IMPLEMENTED:
 * - Jitter damping using sliding-window algorithm
 * - Window size = 5 samples
 * - Drops highest + lowest sample (outlier removal)
 * - Prevents burst-then-stall pattern in bitrate reporting
 * - Thread-safe per-domain smoothing
 * 
 * Algorithm:
 * 1. Maintain sliding window of 5 throughput samples per domain
 * 2. On each update, add new sample and remove oldest if window full
 * 3. Sort samples, drop highest and lowest (outlier removal)
 * 4. Return average of remaining 3 samples
 * 5. This smooths out sudden spikes/drops that cause bitrate oscillation
 */
object ThroughputSmoother {
    private const val TAG = "ThroughputSmoother"
    private const val WINDOW_SIZE = 5
    private const val OUTLIER_REMOVAL_COUNT = 2 // Drop highest + lowest
    
    // Per-domain sliding windows
    private val throughputWindows = ConcurrentHashMap<String, ArrayDeque<ThroughputSample>>()
    
    /**
     * Throughput sample with timestamp
     */
    data class ThroughputSample(
        val throughputBps: Long, // bytes per second
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Smoothed throughput result
     */
    data class SmoothedThroughput(
        val smoothedBps: Long,
        val rawBps: Long,
        val sampleCount: Int
    )
    
    /**
     * Add throughput sample and get smoothed value.
     * 
     * @param domain Domain/endpoint identifier
     * @param throughputBps Raw throughput in bytes per second
     * @return Smoothed throughput with outlier removal
     */
    fun addSampleAndSmooth(domain: String, throughputBps: Long): SmoothedThroughput {
        val window = throughputWindows.getOrPut(domain) { ArrayDeque(WINDOW_SIZE) }
        
        synchronized(window) {
            // Add new sample
            window.addLast(ThroughputSample(throughputBps))
            
            // Remove oldest if window full
            if (window.size > WINDOW_SIZE) {
                window.removeFirst()
            }
            
            // Need at least 3 samples for outlier removal
            if (window.size < 3) {
                // Return average of available samples
                val avg = window.map { it.throughputBps }.average().toLong()
                return SmoothedThroughput(
                    smoothedBps = avg,
                    rawBps = throughputBps,
                    sampleCount = window.size
                )
            }
            
            // Sort samples by throughput value
            val sorted = window.sortedBy { it.throughputBps }
            
            // Remove highest and lowest (outlier removal)
            val trimmed = sorted.drop(1).dropLast(1) // Remove first (lowest) and last (highest)
            
            // Calculate average of remaining samples
            val smoothed = if (trimmed.isNotEmpty()) {
                trimmed.map { it.throughputBps }.average().toLong()
            } else {
                throughputBps // Fallback to raw if no samples after trimming
            }
            
            AppLogger.d("$TAG: Smoothed throughput for $domain: $rawBps -> $smoothed bps (window: ${window.size})")
            
            return SmoothedThroughput(
                smoothedBps = smoothed,
                rawBps = throughputBps,
                sampleCount = window.size
            )
        }
    }
    
    /**
     * Get current smoothed throughput for domain (without adding new sample)
     */
    fun getSmoothed(domain: String): Long? {
        val window = throughputWindows[domain] ?: return null
        
        synchronized(window) {
            if (window.size < 3) {
                // Return average of available samples
                return window.map { it.throughputBps }.average().toLong()
            }
            
            // Sort and remove outliers
            val sorted = window.sortedBy { it.throughputBps }
            val trimmed = sorted.drop(1).dropLast(1)
            
            return if (trimmed.isNotEmpty()) {
                trimmed.map { it.throughputBps }.average().toLong()
            } else {
                null
            }
        }
    }
    
    /**
     * Clear window for domain
     */
    fun clear(domain: String) {
        throughputWindows.remove(domain)
    }
    
    /**
     * Clear all windows
     */
    fun clearAll() {
        throughputWindows.clear()
    }
    
    /**
     * Prune stale windows (older than 5 minutes)
     */
    fun pruneStale() {
        val now = System.currentTimeMillis()
        val staleThreshold = 5 * 60 * 1000L // 5 minutes
        
        throughputWindows.entries.removeAll { (_, window) ->
            synchronized(window) {
                window.isEmpty() || (window.lastOrNull()?.let { now - it.timestamp > staleThreshold } == true)
            }
        }
    }
}

