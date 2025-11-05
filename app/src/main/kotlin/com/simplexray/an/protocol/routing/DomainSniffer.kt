package com.simplexray.an.protocol.routing

import com.simplexray.an.logging.AppLogger
import com.simplexray.an.perf.PerformanceOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * DomainSniffer - Sniff-first strategy for HTTP/TLS traffic.
 * 
 * FIXES:
 * - Sniffing enabled for HTTP and TLS
 * - Fail open to domainLists if sniff fails
 * - Prevents DNS race by extracting host before DNS resolves
 * - Stores originalHost in routing lookup
 * 
 * Priority:
 * 1. Sniffed host (HTTP/TLS)
 * 2. Domain lists (geosite)
 * 3. Fallback to direct/proxy
 */
object DomainSniffer {
    private const val TAG = "DomainSniffer"
    
    // Sniff cache with TTL
    private const val SNIFF_CACHE_TTL_MS = 10_000L // 10 seconds
    private val sniffCache = ConcurrentHashMap<String, SniffCacheEntry>()
    
    /**
     * Sniff cache entry
     */
    private data class SniffCacheEntry(
        val host: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return (System.currentTimeMillis() - timestamp) > SNIFF_CACHE_TTL_MS
        }
    }
    
    /**
     * Sniff result from traffic inspection
     */
    data class SniffResult(
        val host: String?,              // Sniffed host (e.g., "example.com")
        val protocol: SniffProtocol,    // HTTP, TLS, or UNKNOWN
        val port: Int? = null,          // Port if detected
        val success: Boolean = false    // Whether sniffing succeeded
    )
    
    enum class SniffProtocol {
        HTTP,
        TLS,
        UNKNOWN
    }
    
    /**
     * Sniff host from traffic data (HTTP/TLS)
     * 
     * This is called BEFORE DNS resolution to prevent DNS race.
     * The sniffed host is stored in RouteCache before DNS resolves.
     * 
     * @param data Traffic data (first few bytes of connection)
     * @param port Destination port
     * @return SniffResult with host if detected
     */
    suspend fun sniffHost(data: ByteArray, port: Int): SniffResult = withContext(Dispatchers.Default) {
        // Check cache first
        val cacheKey = "${data.contentHashCode()}:$port"
        sniffCache[cacheKey]?.let { cached ->
            if (!cached.isExpired()) {
                PerformanceOptimizer.recordSniffThrottle()
                return@withContext SniffResult(
                    host = cached.host,
                    protocol = SniffProtocol.UNKNOWN,
                    success = true
                )
            } else {
                sniffCache.remove(cacheKey)
            }
        }
        
        if (data.isEmpty()) {
            return@withContext SniffResult(null, SniffProtocol.UNKNOWN, port, false)
        }
        
        // Try HTTP first (port 80 or common HTTP ports)
        if (port == 80 || port == 8080 || port == 8000 || port == 3128) {
            val httpHost = sniffHttpHost(data)
            if (httpHost != null) {
                AppLogger.d("$TAG: Sniffed HTTP host: $httpHost")
                // Cache result if successful
                sniffCache[cacheKey] = SniffCacheEntry(httpHost)
                return@withContext SniffResult(httpHost, SniffProtocol.HTTP, port, true)
            }
        }
        
        // Try TLS (port 443 or common TLS ports)
        if (port == 443 || port == 8443) {
            val tlsHost = sniffTlsHost(data)
            if (tlsHost != null) {
                AppLogger.d("$TAG: Sniffed TLS host: $tlsHost")
                // Cache result if successful
                sniffCache[cacheKey] = SniffCacheEntry(tlsHost)
                return@withContext SniffResult(tlsHost, SniffProtocol.TLS, port, true)
            }
        }
        
        // Try generic HTTP/TLS detection (any port)
        val httpHost = sniffHttpHost(data)
        if (httpHost != null) {
            // Cache result if successful
            sniffCache[cacheKey] = SniffCacheEntry(httpHost)
            return@withContext SniffResult(httpHost, SniffProtocol.HTTP, port, true)
        }
        
        val tlsHost = sniffTlsHost(data)
        if (tlsHost != null) {
            // Cache result if successful
            sniffCache[cacheKey] = SniffCacheEntry(tlsHost)
            return@withContext SniffResult(tlsHost, SniffProtocol.TLS, port, true)
        }
        
        return@withContext SniffResult(null, SniffProtocol.UNKNOWN, port, false)
    }
    
    /**
     * Sniff HTTP host from request data
     * Format: "GET / HTTP/1.1\r\nHost: example.com\r\n..."
     */
    private fun sniffHttpHost(data: ByteArray): String? {
        try {
            val dataStr = String(data, 0, minOf(data.size, 2048), Charsets.UTF_8)
            
            // Look for Host header
            val hostHeaderRegex = Regex("(?i)Host:\\s*([^\\r\\n]+)")
            val match = hostHeaderRegex.find(dataStr)
            if (match != null) {
                val host = match.groupValues[1].trim()
                if (host.isNotEmpty()) {
                    // Remove port if present
                    return host.split(":").first()
                }
            }
            
            // Alternative: Look for HTTP request line with host
            // "GET http://example.com/ HTTP/1.1"
            val httpUrlRegex = Regex("(?i)(?:GET|POST|PUT|DELETE|HEAD|OPTIONS)\\s+(?:https?://)?([^/\\s]+)")
            val urlMatch = httpUrlRegex.find(dataStr)
            if (urlMatch != null) {
                val host = urlMatch.groupValues[1].trim()
                if (host.isNotEmpty()) {
                    return host.split(":").first()
                }
            }
        } catch (e: Exception) {
            AppLogger.w("$TAG: Error sniffing HTTP host", e)
        }
        
        return null
    }
    
    /**
     * Sniff TLS host from SNI (Server Name Indication)
     * Format: TLS handshake with SNI extension
     */
    private fun sniffTlsHost(data: ByteArray): String? {
        try {
            if (data.size < 5) return null
            
            // Check TLS record type (0x16 = Handshake)
            if (data[0] != 0x16.toByte()) return null
            
            // Skip TLS record header (5 bytes)
            var offset = 5
            
            // Check for ClientHello (0x01)
            if (offset >= data.size || data[offset] != 0x01.toByte()) return null
            offset++
            
            // Skip ClientHello version and random (34 bytes)
            offset += 34
            
            // Skip session ID length and session ID
            if (offset >= data.size) return null
            val sessionIdLength = data[offset].toInt() and 0xFF
            offset += 1 + sessionIdLength
            
            // Skip cipher suites length and cipher suites
            if (offset >= data.size) return null
            val cipherSuitesLength = (data[offset].toInt() and 0xFF shl 8) or 
                                    (data[offset + 1].toInt() and 0xFF)
            offset += 2 + cipherSuitesLength
            
            // Skip compression methods length and compression methods
            if (offset >= data.size) return null
            val compressionMethodsLength = data[offset].toInt() and 0xFF
            offset += 1 + compressionMethodsLength
            
            // Look for extensions
            if (offset >= data.size) return null
            val extensionsLength = (data[offset].toInt() and 0xFF shl 8) or 
                                 (data[offset + 1].toInt() and 0xFF)
            offset += 2
            
            val extensionsEnd = offset + extensionsLength
            if (extensionsEnd > data.size) return null
            
            // Search for SNI extension (type 0x0000)
            while (offset < extensionsEnd - 4) {
                val extType = (data[offset].toInt() and 0xFF shl 8) or 
                             (data[offset + 1].toInt() and 0xFF)
                offset += 2
                
                val extLength = (data[offset].toInt() and 0xFF shl 8) or 
                               (data[offset + 1].toInt() and 0xFF)
                offset += 2
                
                if (extType == 0x0000) {
                    // SNI extension found
                    if (offset + 2 > data.size) break
                    
                    // Skip server name list length
                    offset += 2
                    
                    // Read server name type (should be 0 = hostname)
                    if (offset >= data.size || data[offset] != 0x00.toByte()) break
                    offset++
                    
                    // Read hostname length
                    if (offset + 2 > data.size) break
                    val hostnameLength = (data[offset].toInt() and 0xFF shl 8) or 
                                       (data[offset + 1].toInt() and 0xFF)
                    offset += 2
                    
                    // Read hostname
                    if (offset + hostnameLength > data.size) break
                    val hostnameBytes = data.sliceArray(offset until offset + hostnameLength)
                    val hostname = String(hostnameBytes, Charsets.UTF_8)
                    
                    if (hostname.isNotEmpty()) {
                        return hostname
                    }
                } else {
                    offset += extLength
                }
            }
        } catch (e: Exception) {
            AppLogger.w("$TAG: Error sniffing TLS host", e)
        }
        
        return null
    }
    
    /**
     * Check if sniffing is enabled for protocol
     */
    fun isSniffingEnabled(protocol: SniffProtocol): Boolean {
        return protocol == SniffProtocol.HTTP || protocol == SniffProtocol.TLS
    }
}

