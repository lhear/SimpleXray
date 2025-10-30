package com.simplexray.an.performance.optimizer

import com.simplexray.an.performance.model.AppQoSRule
import com.simplexray.an.performance.model.BandwidthLimit
import com.simplexray.an.performance.model.QoSPriority
import com.simplexray.an.performance.model.RateLimitStrategy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Traffic shaping and rate limiting implementation
 */
class TrafficShaper {

    private val appRules = mutableMapOf<String, AppQoSRule>()
    private val rateLimiters = mutableMapOf<String, RateLimiter>()
    private val mutex = Mutex()

    // Global limits
    private var globalDownloadLimit: Long? = null
    private var globalUploadLimit: Long? = null

    /**
     * Add or update QoS rule for an app
     */
    suspend fun setAppRule(rule: AppQoSRule) {
        mutex.withLock {
            appRules[rule.packageName] = rule

            // Create rate limiters if bandwidth limits are set
            rule.bandwidthLimit?.let { limit ->
                rateLimiters[rule.packageName] = RateLimiter(limit)
            }
        }
    }

    /**
     * Remove app rule
     */
    suspend fun removeAppRule(packageName: String) {
        mutex.withLock {
            appRules.remove(packageName)
            rateLimiters.remove(packageName)
        }
    }

    /**
     * Set global bandwidth limits
     */
    suspend fun setGlobalLimits(downloadLimit: Long?, uploadLimit: Long?) {
        mutex.withLock {
            globalDownloadLimit = downloadLimit
            globalUploadLimit = uploadLimit
        }
    }

    /**
     * Check if traffic should be allowed
     */
    suspend fun allowTraffic(
        packageName: String,
        bytes: Long,
        isUpload: Boolean
    ): TrafficDecision {
        return mutex.withLock {
            val rule = appRules[packageName]

            // Check if app is enabled
            if (rule != null && !rule.enabled) {
                return@withLock TrafficDecision.Block("App disabled in QoS")
            }

            // Check global limits
            val globalLimit = if (isUpload) globalUploadLimit else globalDownloadLimit
            if (globalLimit != null && bytes > globalLimit) {
                return@withLock TrafficDecision.Throttle(globalLimit, "Global limit")
            }

            // Check app-specific limits
            val rateLimiter = rateLimiters[packageName]
            if (rateLimiter != null) {
                val appLimit = if (isUpload) {
                    rule?.bandwidthLimit?.uploadLimit
                } else {
                    rule?.bandwidthLimit?.downloadLimit
                }

                if (appLimit != null) {
                    val allowed = rateLimiter.allowBytes(bytes, isUpload, appLimit)
                    if (!allowed) {
                        return@withLock TrafficDecision.Throttle(appLimit, "App limit exceeded")
                    }
                }
            }

            // Get priority
            val priority = rule?.priority?.value ?: QoSPriority.Normal.value

            TrafficDecision.Allow(priority)
        }
    }

    /**
     * Get QoS priority for package
     */
    suspend fun getPriority(packageName: String): Int {
        return mutex.withLock {
            appRules[packageName]?.priority?.value ?: QoSPriority.Normal.value
        }
    }

    /**
     * Get all app rules
     */
    suspend fun getAllRules(): List<AppQoSRule> {
        return mutex.withLock {
            appRules.values.toList()
        }
    }

    /**
     * Reset all rate limiters
     */
    suspend fun resetRateLimiters() {
        mutex.withLock {
            rateLimiters.values.forEach { it.reset() }
        }
    }

    /**
     * Traffic decision result
     */
    sealed class TrafficDecision {
        data class Allow(val priority: Int) : TrafficDecision()
        data class Throttle(val maxRate: Long, val reason: String) : TrafficDecision()
        data class Block(val reason: String) : TrafficDecision()
    }
}

/**
 * Rate limiter implementation using token bucket algorithm
 */
class RateLimiter(
    private val bandwidthLimit: BandwidthLimit
) {
    private var downloadTokens: Long = 0
    private var uploadTokens: Long = 0
    private var lastRefillTime: Long = System.currentTimeMillis()

    private val refillInterval = 1000L // 1 second
    private val mutex = Mutex()

    /**
     * Check if bytes can be transmitted
     */
    suspend fun allowBytes(bytes: Long, isUpload: Boolean, rateLimit: Long): Boolean {
        return mutex.withLock {
            refillTokens()

            val tokens = if (isUpload) uploadTokens else downloadTokens
            val burst = bandwidthLimit.burstAllowance ?: rateLimit

            if (tokens + bytes <= burst) {
                if (isUpload) {
                    uploadTokens += bytes
                } else {
                    downloadTokens += bytes
                }
                true
            } else {
                false
            }
        }
    }

    /**
     * Refill tokens based on time elapsed
     */
    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefillTime

        if (elapsed >= refillInterval) {
            val intervals = elapsed / refillInterval

            // Refill download tokens
            bandwidthLimit.downloadLimit?.let { limit ->
                val refillAmount = (limit * intervals)
                downloadTokens = maxOf(0, downloadTokens - refillAmount)
            }

            // Refill upload tokens
            bandwidthLimit.uploadLimit?.let { limit ->
                val refillAmount = (limit * intervals)
                uploadTokens = maxOf(0, uploadTokens - refillAmount)
            }

            lastRefillTime = now
        }
    }

    /**
     * Reset token buckets
     */
    fun reset() {
        downloadTokens = 0
        uploadTokens = 0
        lastRefillTime = System.currentTimeMillis()
    }
}

/**
 * Fair queuing implementation for QoS
 */
class FairQueuing {
    private val queues = mutableMapOf<Int, MutableList<QueuedPacket>>()
    private val mutex = Mutex()

    /**
     * Queued packet
     */
    data class QueuedPacket(
        val packageName: String,
        val data: ByteArray,
        val priority: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as QueuedPacket

            if (packageName != other.packageName) return false
            if (!data.contentEquals(other.data)) return false
            if (priority != other.priority) return false
            if (timestamp != other.timestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = packageName.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + priority
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }

    /**
     * Enqueue packet
     */
    suspend fun enqueue(packet: QueuedPacket) {
        mutex.withLock {
            val queue = queues.getOrPut(packet.priority) { mutableListOf() }
            queue.add(packet)
        }
    }

    /**
     * Dequeue next packet (highest priority first)
     */
    suspend fun dequeue(): QueuedPacket? {
        return mutex.withLock {
            // Get highest priority queue that has packets
            val highestPriority = queues.keys.maxOrNull() ?: return@withLock null
            val queue = queues[highestPriority] ?: return@withLock null

            if (queue.isEmpty()) {
                queues.remove(highestPriority)
                return@withLock dequeue() // Try next priority
            }

            queue.removeAt(0)
        }
    }

    /**
     * Get total queued packets
     */
    suspend fun size(): Int {
        return mutex.withLock {
            queues.values.sumOf { it.size }
        }
    }

    /**
     * Clear all queues
     */
    suspend fun clear() {
        mutex.withLock {
            queues.clear()
        }
    }
}

/**
 * Bandwidth monitor for tracking usage
 */
class BandwidthMonitor {
    private val appBandwidth = mutableMapOf<String, AppBandwidthUsage>()
    private val mutex = Mutex()

    data class AppBandwidthUsage(
        val packageName: String,
        var uploadBytes: Long = 0,
        var downloadBytes: Long = 0,
        var lastUpdated: Long = System.currentTimeMillis()
    ) {
        fun getTotalBytes(): Long = uploadBytes + downloadBytes
    }

    /**
     * Record bandwidth usage
     */
    suspend fun recordUsage(packageName: String, bytes: Long, isUpload: Boolean) {
        mutex.withLock {
            val usage = appBandwidth.getOrPut(packageName) {
                AppBandwidthUsage(packageName)
            }

            if (isUpload) {
                usage.uploadBytes += bytes
            } else {
                usage.downloadBytes += bytes
            }

            usage.lastUpdated = System.currentTimeMillis()
        }
    }

    /**
     * Get usage for app
     */
    suspend fun getUsage(packageName: String): AppBandwidthUsage? {
        return mutex.withLock {
            appBandwidth[packageName]
        }
    }

    /**
     * Get all usage
     */
    suspend fun getAllUsage(): List<AppBandwidthUsage> {
        return mutex.withLock {
            appBandwidth.values.toList()
        }
    }

    /**
     * Get top apps by bandwidth
     */
    suspend fun getTopApps(limit: Int = 10): List<AppBandwidthUsage> {
        return mutex.withLock {
            appBandwidth.values
                .sortedByDescending { it.getTotalBytes() }
                .take(limit)
        }
    }

    /**
     * Reset all usage
     */
    suspend fun reset() {
        mutex.withLock {
            appBandwidth.clear()
        }
    }

    /**
     * Reset usage for specific app
     */
    suspend fun resetApp(packageName: String) {
        mutex.withLock {
            appBandwidth.remove(packageName)
        }
    }
}
