package com.simplexray.an.performance.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/**
 * HTTP response cache for static content
 */
class HttpCache(
    private val maxCacheSize: Long = 50 * 1024 * 1024, // 50 MB
    private val defaultTtl: Long = 3600_000 // 1 hour
) {
    private val cache = mutableMapOf<String, HttpCacheEntry>()
    private val mutex = Mutex()
    private var currentSize: Long = 0

    /**
     * Get cached HTTP response
     */
    suspend fun get(url: String): HttpCacheEntry? {
        return mutex.withLock {
            val key = generateKey(url)
            val entry = cache[key]

            if (entry != null && !entry.isExpired()) {
                // Update access time for LRU
                entry.lastAccessed = System.currentTimeMillis()
                entry
            } else {
                cache.remove(key)?.let { currentSize -= it.size }
                null
            }
        }
    }

    /**
     * Put HTTP response in cache
     */
    suspend fun put(
        url: String,
        data: ByteArray,
        contentType: String,
        ttl: Long = defaultTtl,
        headers: Map<String, String> = emptyMap()
    ): Boolean {
        return mutex.withLock {
            val key = generateKey(url)
            val size = data.size.toLong()

            // Don't cache if too large
            if (size > maxCacheSize / 2) {
                return@withLock false
            }

            // Evict entries if needed
            while (currentSize + size > maxCacheSize && cache.isNotEmpty()) {
                evictLRU()
            }

            val entry = HttpCacheEntry(
                url = url,
                data = data,
                contentType = contentType,
                timestamp = System.currentTimeMillis(),
                ttl = ttl,
                size = size,
                headers = headers
            )

            cache[key] = entry
            currentSize += size
            true
        }
    }

    /**
     * Check if URL is cacheable
     */
    fun isCacheable(url: String, contentType: String): Boolean {
        // Only cache static content
        val cacheableTypes = listOf(
            "text/css",
            "text/javascript",
            "application/javascript",
            "image/",
            "font/",
            "application/font"
        )

        return cacheableTypes.any { contentType.startsWith(it) } &&
                !url.contains("nocache") &&
                !url.contains("?") // Don't cache URLs with query params
    }

    /**
     * Clear cache
     */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
            currentSize = 0
        }
    }

    /**
     * Get cache statistics
     */
    suspend fun getStats(): HttpCacheStats {
        return mutex.withLock {
            HttpCacheStats(
                totalEntries = cache.size,
                totalSize = currentSize,
                maxSize = maxCacheSize,
                hitRate = calculateHitRate()
            )
        }
    }

    /**
     * Evict least recently used entry
     */
    private fun evictLRU() {
        val lruEntry = cache.entries.minByOrNull { it.value.lastAccessed }
        lruEntry?.let {
            cache.remove(it.key)
            currentSize -= it.value.size
        }
    }

    /**
     * Generate cache key from URL
     */
    private fun generateKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(url.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calculate cache hit rate
     */
    private fun calculateHitRate(): Float {
        if (cache.isEmpty()) return 0f

        val activeEntries = cache.count { !it.value.isExpired() }
        return activeEntries.toFloat() / cache.size
    }
}

/**
 * HTTP cache entry
 */
data class HttpCacheEntry(
    val url: String,
    val data: ByteArray,
    val contentType: String,
    val timestamp: Long,
    val ttl: Long,
    val size: Long,
    val headers: Map<String, String> = emptyMap(),
    var lastAccessed: Long = timestamp,
    var hitCount: Int = 0
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - timestamp > ttl
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpCacheEntry

        if (url != other.url) return false
        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * HTTP cache statistics
 */
data class HttpCacheStats(
    val totalEntries: Int,
    val totalSize: Long,
    val maxSize: Long,
    val hitRate: Float
) {
    val usagePercentage: Float
        get() = if (maxSize > 0) (totalSize.toFloat() / maxSize) * 100 else 0f
}
