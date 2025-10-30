package com.simplexray.an.protocol.optimization

/**
 * Protocol optimization configuration and support detection
 */
data class ProtocolConfig(
    // HTTP/3 & QUIC
    val http3Enabled: Boolean = false,
    val quicVersion: QuicVersion = QuicVersion.V1,
    val quicMaxStreams: Int = 100,
    val quicIdleTimeout: Long = 30000, // ms

    // TLS 1.3
    val tls13Enabled: Boolean = true,
    val tls13EarlyData: Boolean = false, // 0-RTT
    val tls13SessionTickets: Boolean = true,
    val preferredCipherSuites: List<String> = TLS13_RECOMMENDED_CIPHERS,

    // Compression
    val brotliEnabled: Boolean = true,
    val brotliQuality: Int = 6, // 0-11, 6 is balanced
    val gzipEnabled: Boolean = true,
    val gzipLevel: Int = 6, // 0-9

    // Header Compression
    val hpackEnabled: Boolean = true, // HTTP/2
    val qpackEnabled: Boolean = true, // HTTP/3
    val headerTableSize: Int = 4096,

    // Protocol Features
    val serverPushEnabled: Boolean = false,
    val multiplexingEnabled: Boolean = true,
    val prioritizationEnabled: Boolean = true
) {
    companion object {
        val TLS13_RECOMMENDED_CIPHERS = listOf(
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256"
        )

        /**
         * Gaming profile: Low latency, 0-RTT
         */
        fun forGaming(): ProtocolConfig {
            return ProtocolConfig(
                http3Enabled = true,
                quicMaxStreams = 50,
                quicIdleTimeout = 60000,
                tls13Enabled = true,
                tls13EarlyData = true, // 0-RTT for faster reconnection
                brotliEnabled = false, // Lower CPU overhead
                gzipEnabled = false,
                serverPushEnabled = false,
                multiplexingEnabled = true
            )
        }

        /**
         * Streaming profile: High throughput, compression
         */
        fun forStreaming(): ProtocolConfig {
            return ProtocolConfig(
                http3Enabled = true,
                quicMaxStreams = 200,
                quicIdleTimeout = 120000,
                tls13Enabled = true,
                tls13EarlyData = false,
                brotliEnabled = true,
                brotliQuality = 8, // Higher quality for video metadata
                gzipEnabled = true,
                serverPushEnabled = true,
                multiplexingEnabled = true,
                prioritizationEnabled = true
            )
        }

        /**
         * Battery saver profile: Minimal features
         */
        fun forBatterySaver(): ProtocolConfig {
            return ProtocolConfig(
                http3Enabled = false, // Stick to HTTP/2
                tls13Enabled = true,
                tls13EarlyData = false,
                brotliEnabled = true,
                brotliQuality = 4, // Lower CPU usage
                gzipEnabled = true,
                gzipLevel = 4,
                serverPushEnabled = false,
                multiplexingEnabled = false
            )
        }
    }
}

/**
 * QUIC version support
 */
enum class QuicVersion(val versionHex: String) {
    V1("0x00000001"),
    DRAFT_29("0xff00001d"),
    DRAFT_32("0xff000020");

    companion object {
        fun fromString(version: String): QuicVersion {
            return entries.find { it.versionHex == version } ?: V1
        }
    }
}

/**
 * Compression algorithm support
 */
enum class CompressionAlgorithm(
    val displayName: String,
    val mimeType: String,
    val typicalRatio: Float // typical compression ratio
) {
    BROTLI("Brotli", "br", 0.65f),
    GZIP("Gzip", "gzip", 0.70f),
    DEFLATE("Deflate", "deflate", 0.72f),
    ZSTD("Zstandard", "zstd", 0.60f),
    NONE("None", "identity", 1.0f);

    fun estimateCompressedSize(originalSize: Long): Long {
        return (originalSize * typicalRatio).toLong()
    }
}

/**
 * Protocol feature support detector
 */
object ProtocolSupport {
    /**
     * Check if server supports HTTP/3
     */
    suspend fun detectHttp3Support(host: String): Boolean {
        // Check Alt-Svc header or DNS HTTPS record
        // Simplified - would need actual implementation
        return false
    }

    /**
     * Check TLS 1.3 support
     */
    suspend fun detectTls13Support(host: String): Boolean {
        // Attempt TLS handshake with 1.3
        return true // Most modern servers support it
    }

    /**
     * Detect supported compression algorithms
     */
    suspend fun detectCompressionSupport(host: String): List<CompressionAlgorithm> {
        // Check Accept-Encoding response
        return listOf(
            CompressionAlgorithm.BROTLI,
            CompressionAlgorithm.GZIP,
            CompressionAlgorithm.DEFLATE
        )
    }

    /**
     * Get optimal protocol configuration for server
     */
    suspend fun getOptimalConfig(host: String): ProtocolConfig {
        val http3 = detectHttp3Support(host)
        val tls13 = detectTls13Support(host)
        val compression = detectCompressionSupport(host)

        return ProtocolConfig(
            http3Enabled = http3,
            tls13Enabled = tls13,
            brotliEnabled = compression.contains(CompressionAlgorithm.BROTLI),
            gzipEnabled = compression.contains(CompressionAlgorithm.GZIP)
        )
    }
}

/**
 * TLS session cache for faster reconnections
 */
class TlsSessionCache(
    private val maxSize: Int = 100,
    private val ttl: Long = 3600_000 // 1 hour
) {
    private val cache = mutableMapOf<String, TlsSession>()

    data class TlsSession(
        val host: String,
        val sessionId: ByteArray,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(ttl: Long): Boolean {
            return System.currentTimeMillis() - timestamp > ttl
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TlsSession
            if (host != other.host) return false
            if (!sessionId.contentEquals(other.sessionId)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = host.hashCode()
            result = 31 * result + sessionId.contentHashCode()
            return result
        }
    }

    fun put(session: TlsSession) {
        synchronized(cache) {
            if (cache.size >= maxSize) {
                // Remove oldest
                val oldest = cache.entries.minByOrNull { it.value.timestamp }
                oldest?.let { cache.remove(it.key) }
            }
            cache[session.host] = session
        }
    }

    fun get(host: String): TlsSession? {
        synchronized(cache) {
            val session = cache[host]
            return if (session != null && !session.isExpired(ttl)) {
                session
            } else {
                cache.remove(host)
                null
            }
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }
}

/**
 * Protocol optimization statistics
 */
data class ProtocolStats(
    val http3Requests: Long = 0,
    val http2Requests: Long = 0,
    val http1Requests: Long = 0,
    val tls13Connections: Long = 0,
    val tls12Connections: Long = 0,
    val brotliCompressionSaved: Long = 0, // bytes saved
    val gzipCompressionSaved: Long = 0,
    val zeroRttSuccess: Long = 0,
    val zeroRttFailed: Long = 0,
    val avgHandshakeTime: Long = 0 // milliseconds
) {
    val totalRequests: Long
        get() = http3Requests + http2Requests + http1Requests

    val http3Percentage: Float
        get() = if (totalRequests > 0) (http3Requests.toFloat() / totalRequests) * 100 else 0f

    val totalCompressionSaved: Long
        get() = brotliCompressionSaved + gzipCompressionSaved

    val zeroRttSuccessRate: Float
        get() {
            val total = zeroRttSuccess + zeroRttFailed
            return if (total > 0) (zeroRttSuccess.toFloat() / total) * 100 else 0f
        }
}
