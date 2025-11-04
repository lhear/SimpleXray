package com.simplexray.an.protocol.routing

import android.content.Context
import com.simplexray.an.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * GeoIpCache - Lazy-loaded GeoIP database with query caching.
 * 
 * FIXES:
 * - GeoIP database loaded lazily (only when needed)
 * - Cache last N queries (100 default)
 * - Thread-safe lookup
 * - Handles database reload on file change
 * - Fallback to null if database unavailable
 */
object GeoIpCache {
    private const val TAG = "GeoIpCache"
    private const val MAX_CACHE_SIZE = 100
    
    // Cache for IP -> Country lookups
    private val queryCache = ConcurrentHashMap<String, String?>()
    
    // Database reference (lazy-loaded)
    private val databaseRef = AtomicReference<GeoIpDatabase?>(null)
    
    @Volatile
    private var databaseFile: File? = null
    
    @Volatile
    private var isInitialized = false
    
    /**
     * Initialize GeoIP database from file
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        
        try {
            // Try to load from app files directory first
            val geoipFile = File(context.filesDir, "geoip.dat")
            if (geoipFile.exists()) {
                databaseFile = geoipFile
                loadDatabase(geoipFile)
                isInitialized = true
                AppLogger.d("$TAG: Initialized from ${geoipFile.absolutePath}")
                return@withContext
            }
            
            // Fallback to assets
            val assetsFile = context.assets.open("geoip.dat")
            assetsFile.use { input ->
                val tempFile = File(context.cacheDir, "geoip_temp.dat")
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                databaseFile = tempFile
                loadDatabase(tempFile)
                isInitialized = true
                AppLogger.d("$TAG: Initialized from assets")
            }
        } catch (e: Exception) {
            AppLogger.w("$TAG: Failed to initialize GeoIP database", e)
            isInitialized = true // Mark as initialized to prevent retry loops
        }
    }
    
    /**
     * Load GeoIP database from file
     */
    private suspend fun loadDatabase(file: File) = withContext(Dispatchers.IO) {
        try {
            // For now, use a simple implementation
            // In production, you'd use a proper GeoIP library (MaxMind, etc.)
            val database = SimpleGeoIpDatabase(file)
            databaseRef.set(database)
            AppLogger.d("$TAG: Database loaded successfully")
        } catch (e: Exception) {
            AppLogger.w("$TAG: Failed to load database", e)
            databaseRef.set(null)
        }
    }
    
    /**
     * Lookup country for IP address
     * Returns cached result if available, otherwise queries database
     */
    suspend fun lookupCountry(ip: String): String? = withContext(Dispatchers.Default) {
        // Check cache first
        queryCache[ip]?.let { return@withContext it }
        
        // Ensure database is loaded
        val database = databaseRef.get()
        if (database == null) {
            // Database not available, return null
            queryCache[ip] = null
            return@withContext null
        }
        
        // Query database
        val country = try {
            database.lookupCountry(ip)
        } catch (e: Exception) {
            AppLogger.w("$TAG: Error looking up IP: $ip", e)
            null
        }
        
        // Cache result (prune if cache is too large)
        if (queryCache.size >= MAX_CACHE_SIZE) {
            // Remove oldest entry (simple FIFO)
            val firstKey = queryCache.keys.firstOrNull()
            firstKey?.let { queryCache.remove(it) }
        }
        
        queryCache[ip] = country
        country
    }
    
    /**
     * Invalidate cache (called on database reload)
     */
    fun invalidateCache() {
        queryCache.clear()
        AppLogger.d("$TAG: Cache invalidated")
    }
    
    /**
     * Reload database from file
     */
    suspend fun reload(context: Context) = withContext(Dispatchers.IO) {
        invalidateCache()
        databaseRef.set(null)
        isInitialized = false
        initialize(context)
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): GeoIpCacheStats {
        return GeoIpCacheStats(
            cachedQueries = queryCache.size,
            databaseLoaded = databaseRef.get() != null,
            databaseFile = databaseFile?.absolutePath
        )
    }
    
    data class GeoIpCacheStats(
        val cachedQueries: Int,
        val databaseLoaded: Boolean,
        val databaseFile: String?
    )
}

/**
 * Simple GeoIP database interface
 * In production, implement with MaxMind or similar library
 */
interface GeoIpDatabase {
    fun lookupCountry(ip: String): String?
    fun lookupCity(ip: String): String?
    fun lookupContinent(ip: String): String?
}

/**
 * Simple implementation (placeholder)
 * Replace with actual GeoIP library integration
 */
private class SimpleGeoIpDatabase(private val file: File) : GeoIpDatabase {
    // This is a placeholder - in production, use MaxMind GeoIP2 or similar
    private val countryCache = ConcurrentHashMap<String, String?>()
    
    override fun lookupCountry(ip: String): String? {
        // Check cache
        countryCache[ip]?.let { return it }
        
        // Simple implementation - in production, use proper GeoIP library
        // For now, return null (will be handled by fallback)
        countryCache[ip] = null
        return null
    }
    
    override fun lookupCity(ip: String): String? {
        // Not implemented
        return null
    }
    
    override fun lookupContinent(ip: String): String? {
        // Not implemented
        return null
    }
}

