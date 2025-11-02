package com.simplexray.an.domain

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.simplexray.an.config.ApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Automated IP→domain mapping using stats/mapping sources
 */
class IpDomainMapper(
    private val context: Context,
    private val scope: CoroutineScope,
    private val resolver: DomainResolver
) {
    private const val TAG = "IpDomainMapper"
    private val mappingCache = ConcurrentHashMap<String, String?>()
    private val reverseCache = ConcurrentHashMap<String, String>()
    private val lastSeenCache = ConcurrentHashMap<String, Long>()
    private var cacheFile: File? = null

    init {
        cacheFile = File(context.filesDir, "ip_domain_cache.json")
        loadCache()
    }

    /**
     * Get domain for IP, automatically resolving if needed
     */
    suspend fun getDomain(ip: String): String? {
        // Check cache first
        mappingCache[ip]?.let { return it }
        
        // Check manual mapping from settings
        val manualMap = parseManualMapping()
        manualMap[ip]?.let {
            cacheDomain(ip, it)
            return it
        }
        
        // Try reverse DNS if enabled
        if (ApiConfig.isAutoReverseDns(context)) {
            val domain = resolver.resolve(ip)
            if (!domain.isNullOrBlank()) {
                cacheDomain(ip, domain)
                return domain
            }
        }
        
        // Check stats-based mapping if available
        val statsDomain = tryGetFromStats(ip)
        statsDomain?.let {
            cacheDomain(ip, it)
            return it
        }
        
        return null
    }

    /**
     * Batch resolve multiple IPs
     */
    fun resolveBatch(ips: List<String>, onResult: (String, String?) -> Unit) {
        ips.forEach { ip ->
            scope.launch(Dispatchers.IO) {
                val domain = getDomain(ip)
                onResult(ip, domain)
            }
        }
    }

    /**
     * Try to get domain from stats API if available
     */
    private suspend fun tryGetFromStats(ip: String): String? {
        // This could integrate with stats API to get domain information
        // For now, return null - can be extended later
        return null
    }

    /**
     * Cache a domain mapping
     */
    private fun cacheDomain(ip: String, domain: String) {
        mappingCache[ip] = domain
        reverseCache[domain] = ip
        lastSeenCache[ip] = System.currentTimeMillis()
        
        // Save cache periodically
        scope.launch(Dispatchers.IO) {
            saveCache()
        }
    }

    /**
     * Invalidate cache entry
     */
    fun invalidate(ip: String? = null, domain: String? = null) {
        when {
            ip != null -> {
                mappingCache.remove(ip)
                lastSeenCache.remove(ip)
            }
            domain != null -> {
                reverseCache[domain]?.let { ipAddr ->
                    mappingCache.remove(ipAddr)
                    lastSeenCache.remove(ipAddr)
                }
                reverseCache.remove(domain)
            }
            else -> {
                // Invalidate all
                mappingCache.clear()
                reverseCache.clear()
                lastSeenCache.clear()
            }
        }
        scope.launch(Dispatchers.IO) {
            saveCache()
        }
    }

    /**
     * Clear stale entries (older than maxAgeMs)
     */
    fun pruneCache(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) { // 7 days default
        val now = System.currentTimeMillis()
        val toRemove = lastSeenCache.filter { (_, timestamp) ->
            now - timestamp > maxAgeMs
        }.keys
        
        toRemove.forEach { ip ->
            mappingCache.remove(ip)
            lastSeenCache.remove(ip)
        }
        
        Log.d(TAG, "Pruned ${toRemove.size} stale cache entries")
        
        if (toRemove.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                saveCache()
            }
        }
    }

    /**
     * Parse manual mapping from settings
     */
    private fun parseManualMapping(): Map<String, String> {
        val json = ApiConfig.getIpDomainJson(context)
        if (json.isBlank()) return emptyMap()
        
        return try {
            @Suppress("UNCHECKED_CAST")
            val raw = Gson().fromJson(json, Map::class.java) as? Map<*, *> ?: return emptyMap()
            raw.mapNotNull { (k, v) ->
                val key = k?.toString()?.trim()
                val value = v?.toString()?.trim()
                if (!key.isNullOrBlank() && !value.isNullOrBlank()) key to value else null
            }.toMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse manual mapping", e)
            emptyMap()
        }
    }

    /**
     * Load cache from file
     */
    private fun loadCache() {
        val file = cacheFile ?: return
        if (!file.exists()) return
        
        try {
            val json = file.readText()
            @Suppress("UNCHECKED_CAST")
            val data = Gson().fromJson(json, Map::class.java) as? Map<*, *> ?: return
            
            data.forEach { (k, v) ->
                val ip = k?.toString()
                val domain = v?.toString()
                if (!ip.isNullOrBlank() && !domain.isNullOrBlank()) {
                    mappingCache[ip] = domain
                    reverseCache[domain] = ip
                    lastSeenCache[ip] = System.currentTimeMillis()
                }
            }
            
            Log.d(TAG, "Loaded ${mappingCache.size} cached mappings")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cache", e)
        }
    }

    /**
     * Save cache to file
     */
    private suspend fun saveCache() {
        val file = cacheFile ?: return
        
        try {
            val data = mappingCache.filter { (_, domain) -> domain != null }
            val json = Gson().toJson(data)
            file.writeText(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save cache", e)
        }
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val now = System.currentTimeMillis()
        val staleCount = lastSeenCache.values.count { now - it > 7 * 24 * 60 * 60 * 1000L }
        
        return CacheStats(
            totalMappings = mappingCache.size,
            staleMappings = staleCount,
            oldestTimestamp = lastSeenCache.values.minOrNull() ?: 0L
        )
    }

    data class CacheStats(
        val totalMappings: Int,
        val staleMappings: Int,
        val oldestTimestamp: Long
    )
}

/**
 * Enhanced DomainResolver with caching
 */
suspend fun DomainResolver.resolve(ip: String): String? {
    val cache = ConcurrentHashMap<String, String?>()
    return cache.getOrPut(ip) {
        try {
            val addr = InetAddress.getByName(ip)
            val name = addr.canonicalHostName
            if (name != null && name != ip) name else null
        } catch (e: Exception) {
            null
        }
    }
}
