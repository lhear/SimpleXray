package com.simplexray.an.performance.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress

/**
 * DNS query cache for improved performance
 */
class DnsCache(
    private val defaultTtl: Long = 1800_000, // 30 minutes in milliseconds
    private val maxEntries: Int = 1000
) {
    private val cache = mutableMapOf<String, DnsCacheEntry>()
    private val mutex = Mutex()

    /**
     * Get cached DNS result
     */
    suspend fun get(domain: String): List<InetAddress>? {
        return mutex.withLock {
            val entry = cache[domain]
            if (entry != null && !entry.isExpired()) {
                entry.addresses
            } else {
                cache.remove(domain)
                null
            }
        }
    }

    /**
     * Put DNS result in cache
     */
    suspend fun put(domain: String, addresses: List<InetAddress>, ttl: Long = defaultTtl) {
        mutex.withLock {
            // Enforce max entries (LRU)
            if (cache.size >= maxEntries) {
                val oldestEntry = cache.entries.minByOrNull { it.value.timestamp }
                oldestEntry?.let { cache.remove(it.key) }
            }

            cache[domain] = DnsCacheEntry(
                domain = domain,
                addresses = addresses,
                timestamp = System.currentTimeMillis(),
                ttl = ttl
            )
        }
    }

    /**
     * Clear cache
     */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
        }
    }

    /**
     * Remove expired entries
     */
    suspend fun cleanup() {
        mutex.withLock {
            val expiredKeys = cache.filter { it.value.isExpired() }.keys
            expiredKeys.forEach { cache.remove(it) }
        }
    }

    /**
     * Get cache statistics
     */
    suspend fun getStats(): CacheStats {
        return mutex.withLock {
            CacheStats(
                totalEntries = cache.size,
                maxEntries = maxEntries,
                expiredEntries = cache.count { it.value.isExpired() }
            )
        }
    }

    /**
     * Prefetch DNS for common domains
     */
    suspend fun prefetch(domains: List<String>) {
        domains.forEach { domain ->
            if (get(domain) == null) {
                try {
                    val addresses = InetAddress.getAllByName(domain).toList()
                    put(domain, addresses)
                } catch (e: Exception) {
                    // Ignore prefetch failures
                }
            }
        }
    }
}

/**
 * DNS cache entry
 */
data class DnsCacheEntry(
    val domain: String,
    val addresses: List<InetAddress>,
    val timestamp: Long,
    val ttl: Long
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - timestamp > ttl
    }

    fun getRemainingTtl(): Long {
        val remaining = ttl - (System.currentTimeMillis() - timestamp)
        return maxOf(0, remaining)
    }
}

/**
 * Cache statistics
 */
data class CacheStats(
    val totalEntries: Int,
    val maxEntries: Int,
    val expiredEntries: Int
) {
    val hitRate: Float
        get() = if (totalEntries > 0) {
            (totalEntries - expiredEntries).toFloat() / totalEntries
        } else 0f
}

/**
 * Common domains for prefetching
 */
object CommonDomains {
    val popular = listOf(
        "www.google.com",
        "www.youtube.com",
        "www.facebook.com",
        "www.twitter.com",
        "www.instagram.com",
        "www.whatsapp.com",
        "api.telegram.org",
        "www.cloudflare.com",
        "1.1.1.1",
        "8.8.8.8"
    )

    val cdn = listOf(
        "cdn.jsdelivr.net",
        "unpkg.com",
        "cdnjs.cloudflare.com",
        "stackpath.bootstrapcdn.com"
    )
}
