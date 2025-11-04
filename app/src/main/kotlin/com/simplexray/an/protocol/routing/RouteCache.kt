package com.simplexray.an.protocol.routing

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * RouteCache - LRU sliding window cache for route decisions.
 * 
 * FIXES:
 * - Prevents DNS race by caching domain before DNS resolves
 * - TTL-based invalidation (30s default)
 * - Invalidate on background→resume, config reload, binder reconnect
 * - Thread-safe with ConcurrentHashMap
 * - Handles up to 500 lookups/sec
 * 
 * Cache entry structure:
 * - Key: host/domain string
 * - Value: RouteDecision with timestamp
 * - TTL: 30 seconds (configurable)
 */
object RouteCache {
    private const val DEFAULT_TTL_MS = 30_000L // 30 seconds
    private const val MAX_CACHE_SIZE = 5000
    
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val accessOrder = ConcurrentHashMap<String, AtomicLong>()
    private val accessCounter = AtomicLong(0)
    
    @Volatile
    private var ttlMs: Long = DEFAULT_TTL_MS
    
    /**
     * Cache entry with timestamp
     */
    private data class CacheEntry(
        val decision: RouteDecision,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(ttl: Long): Boolean {
            return (System.currentTimeMillis() - timestamp) > ttl
        }
    }
    
    /**
     * Get cached route decision for host/domain
     * Returns null if not cached or expired
     */
    fun get(host: String): RouteDecision? {
        val entry = cache[host] ?: return null
        
        // Check expiration
        if (entry.isExpired(ttlMs)) {
            cache.remove(host)
            accessOrder.remove(host)
            return null
        }
        
        // Update access order
        accessOrder[host] = AtomicLong(accessCounter.incrementAndGet())
        
        return entry.decision
    }
    
    /**
     * Put route decision in cache
     * Stores originalHost before DNS resolves (prevents DNS race)
     */
    fun put(originalHost: String, decision: RouteDecision) {
        // Prune if cache is too large
        if (cache.size >= MAX_CACHE_SIZE) {
            pruneOldest()
        }
        
        cache[originalHost] = CacheEntry(decision)
        accessOrder[originalHost] = AtomicLong(accessCounter.incrementAndGet())
    }
    
    /**
     * Invalidate specific host entry
     */
    fun invalidate(host: String) {
        cache.remove(host)
        accessOrder.remove(host)
    }
    
    /**
     * Invalidate all cache entries
     * Called on:
     * - Background → resume
     * - Route config reload
     * - Binder reconnect
     */
    fun invalidateAll() {
        cache.clear()
        accessOrder.clear()
        accessCounter.set(0)
    }
    
    /**
     * Prune expired entries
     */
    fun pruneExpired() {
        val now = System.currentTimeMillis()
        val expired = cache.entries.filter { (_, entry) ->
            entry.isExpired(ttlMs)
        }.map { it.key }
        
        expired.forEach { host ->
            cache.remove(host)
            accessOrder.remove(host)
        }
    }
    
    /**
     * Prune oldest entries (LRU eviction)
     */
    private fun pruneOldest() {
        val toRemove = cache.size - (MAX_CACHE_SIZE * 0.8).toInt() // Remove 20%
        
        if (toRemove <= 0) return
        
        // Sort by access order and remove oldest
        val sorted = accessOrder.entries.sortedBy { it.value.get() }
        sorted.take(toRemove).forEach { (host, _) ->
            cache.remove(host)
            accessOrder.remove(host)
        }
    }
    
    /**
     * Set cache TTL
     */
    fun setTtl(ttlMs: Long) {
        this.ttlMs = ttlMs.coerceAtLeast(0)
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        val now = System.currentTimeMillis()
        val expiredCount = cache.values.count { it.isExpired(ttlMs) }
        
        return CacheStats(
            totalEntries = cache.size,
            expiredEntries = expiredCount,
            ttlMs = ttlMs
        )
    }
    
    data class CacheStats(
        val totalEntries: Int,
        val expiredEntries: Int,
        val ttlMs: Long
    )
}

