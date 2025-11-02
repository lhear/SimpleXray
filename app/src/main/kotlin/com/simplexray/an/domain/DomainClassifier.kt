package com.simplexray.an.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class DomainClassifier(private val context: Context) {
    private val cache = ConcurrentHashMap<String, Category>()
    private val cacheTimestamps = ConcurrentHashMap<String, Long>()
    private val social = listOf(
        Regex(".*\\b(twitter|x\\.com|facebook|instagram|tiktok|snapchat|linkedin|pinterest|reddit|discord|telegram|whatsapp|wechat|line|viber|signal)\\b.*", RegexOption.IGNORE_CASE)
    )
    private val gaming = listOf(
        Regex(".*\\b(steam|epicgames|riotgames|playstation|xbox|blizzard|battle\\.net|origin|uplay|gog|twitch|mixer)\\b.*", RegexOption.IGNORE_CASE)
    )
    private val video = listOf(
        Regex(".*\\b(youtube|ytimg|netflix|hulu|primevideo|disney|disneyplus|hbo|hbonow|paramount|peacock|crunchyroll|vimeo|dailymotion)\\b.*", RegexOption.IGNORE_CASE)
    )
    private val cdn = CdnPatterns(context)
    
    // Cache TTL: 1 hour by default
    private var cacheTtlMs: Long = 60 * 60 * 1000L

    suspend fun classify(domain: String): Category = withContext(Dispatchers.Default) {
        // Check cache with TTL
        val cached = cache[domain]
        val timestamp = cacheTimestamps[domain]
        if (cached != null && timestamp != null) {
            val age = System.currentTimeMillis() - timestamp
            if (age < cacheTtlMs) {
                return@withContext cached
            } else {
                // Cache expired, remove
                cache.remove(domain)
                cacheTimestamps.remove(domain)
            }
        }
        
        val cat = when {
            cdn.isCdn(domain) -> Category.CDN
            social.any { it.matches(domain) } -> Category.Social
            gaming.any { it.matches(domain) } -> Category.Gaming
            video.any { it.matches(domain) } -> Category.Video
            else -> Category.Other
        }
        
        cache[domain] = cat
        cacheTimestamps[domain] = System.currentTimeMillis()
        cat
    }

    /**
     * Invalidate cache entry or all cache
     */
    fun invalidateCache(domain: String? = null) {
        if (domain != null) {
            cache.remove(domain)
            cacheTimestamps.remove(domain)
        } else {
            cache.clear()
            cacheTimestamps.clear()
        }
    }

    /**
     * Set cache TTL
     */
    fun setCacheTtl(ttlMs: Long) {
        cacheTtlMs = ttlMs.coerceAtLeast(0)
    }

    /**
     * Prune stale cache entries
     */
    fun pruneCache() {
        val now = System.currentTimeMillis()
        val toRemove = cacheTimestamps.filter { (_, timestamp) ->
            now - timestamp > cacheTtlMs
        }.keys
        
        toRemove.forEach { domain ->
            cache.remove(domain)
            cacheTimestamps.remove(domain)
        }
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val now = System.currentTimeMillis()
        val staleCount = cacheTimestamps.values.count { now - it > cacheTtlMs }
        
        return CacheStats(
            totalEntries = cache.size,
            staleEntries = staleCount
        )
    }

    data class CacheStats(
        val totalEntries: Int,
        val staleEntries: Int
    )
}

class CdnPatterns(private val context: Context) {
    private val builtIn = listOf(
        Regex(".*\\b(cloudflare|cdn-cgi|cf-ipfs)\\b.*", RegexOption.IGNORE_CASE),
        Regex(".*\\b(akamai|akadns|akamaized)\\b.*", RegexOption.IGNORE_CASE),
        Regex(".*\\b(fastly|fastlylb)\\b.*", RegexOption.IGNORE_CASE),
        Regex(".*\\b(azureedge|msecnd|trafficmanager)\\b.*", RegexOption.IGNORE_CASE),
        Regex(".*\\b(amazonaws|cloudfront)\\b.*", RegexOption.IGNORE_CASE)
    )
    @Volatile private var loaded: List<Regex>? = null
    private val loadLock = Any()

    private fun loadFromAssets(): List<Regex> {
        // Double-check locking pattern for thread safety
        loaded?.let { return it }
        synchronized(loadLock) {
            // Check again after acquiring lock
            loaded?.let { return it }
            return try {
                val am = context.assets
                val regexes = am.open("cdn_patterns.json").use { ins ->
                    val text = ins.bufferedReader().readText()
                    val list = com.google.gson.Gson().fromJson(text, Array<String>::class.java)?.toList() ?: emptyList()
                    list.mapNotNull { pattern ->
                        try { Regex(pattern, RegexOption.IGNORE_CASE) } catch (_: Throwable) { null }
                    }
                }
                loaded = regexes
                regexes
            } catch (_: Throwable) {
                val empty = emptyList<Regex>()
                loaded = empty
                empty
            }
        }
    }

    fun isCdn(domain: String): Boolean {
        val assets = loadFromAssets()
        return assets.any { it.matches(domain) } || builtIn.any { it.matches(domain) }
    }
}
