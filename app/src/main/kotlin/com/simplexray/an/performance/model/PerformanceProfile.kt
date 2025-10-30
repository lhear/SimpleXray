package com.simplexray.an.performance.model

/**
 * Performance profile defining optimization settings for different use cases
 */
sealed class PerformanceProfile(
    val id: String,
    val name: String,
    val description: String,
    val config: PerformanceConfig
) {
    /**
     * Maximum performance with high resource usage
     * Best for: Stable Wi-Fi, unlimited data
     */
    data object Turbo : PerformanceProfile(
        id = "turbo",
        name = "Turbo Mode",
        description = "Maximum performance, higher battery usage",
        config = PerformanceConfig(
            bufferSize = 256 * 1024, // 256KB
            connectionTimeout = 15000,
            handshakeTimeout = 10000,
            idleTimeout = 300000,
            tcpFastOpen = true,
            tcpNoDelay = true,
            keepAlive = true,
            keepAliveInterval = 30,
            dnsCacheTtl = 3600,
            dnsPrefetch = true,
            parallelConnections = 8,
            enableCompression = true,
            enableMultiplexing = true,
            statsUpdateInterval = 1000,
            logLevel = "warning"
        )
    )

    /**
     * Balanced performance and battery life
     * Best for: General use
     */
    data object Balanced : PerformanceProfile(
        id = "balanced",
        name = "Balanced Mode",
        description = "Optimal balance of performance and battery",
        config = PerformanceConfig(
            bufferSize = 128 * 1024, // 128KB
            connectionTimeout = 20000,
            handshakeTimeout = 15000,
            idleTimeout = 240000,
            tcpFastOpen = true,
            tcpNoDelay = false,
            keepAlive = true,
            keepAliveInterval = 60,
            dnsCacheTtl = 1800,
            dnsPrefetch = true,
            parallelConnections = 4,
            enableCompression = true,
            enableMultiplexing = true,
            statsUpdateInterval = 2000,
            logLevel = "warning"
        )
    )

    /**
     * Minimum battery consumption
     * Best for: Low battery, metered connections
     */
    data object BatterySaver : PerformanceProfile(
        id = "battery_saver",
        name = "Battery Saver Mode",
        description = "Minimum battery usage, reduced performance",
        config = PerformanceConfig(
            bufferSize = 64 * 1024, // 64KB
            connectionTimeout = 30000,
            handshakeTimeout = 20000,
            idleTimeout = 180000,
            tcpFastOpen = false,
            tcpNoDelay = false,
            keepAlive = true,
            keepAliveInterval = 120,
            dnsCacheTtl = 3600,
            dnsPrefetch = false,
            parallelConnections = 2,
            enableCompression = true,
            enableMultiplexing = false,
            statsUpdateInterval = 5000,
            logLevel = "error"
        )
    )

    /**
     * Low latency for gaming
     * Best for: Gaming, real-time applications
     */
    data object Gaming : PerformanceProfile(
        id = "gaming",
        name = "Gaming Mode",
        description = "Low latency, stable connection",
        config = PerformanceConfig(
            bufferSize = 128 * 1024, // 128KB
            connectionTimeout = 10000,
            handshakeTimeout = 8000,
            idleTimeout = 600000,
            tcpFastOpen = true,
            tcpNoDelay = true,
            keepAlive = true,
            keepAliveInterval = 15,
            dnsCacheTtl = 600,
            dnsPrefetch = true,
            parallelConnections = 2,
            enableCompression = false, // Lower latency
            enableMultiplexing = false, // More predictable
            statsUpdateInterval = 500,
            logLevel = "warning"
        )
    )

    /**
     * High throughput for streaming
     * Best for: Video streaming, large downloads
     */
    data object Streaming : PerformanceProfile(
        id = "streaming",
        name = "Streaming Mode",
        description = "High bandwidth, buffering optimization",
        config = PerformanceConfig(
            bufferSize = 512 * 1024, // 512KB
            connectionTimeout = 25000,
            handshakeTimeout = 15000,
            idleTimeout = 300000,
            tcpFastOpen = true,
            tcpNoDelay = false,
            keepAlive = true,
            keepAliveInterval = 45,
            dnsCacheTtl = 1800,
            dnsPrefetch = true,
            parallelConnections = 6,
            enableCompression = false, // Better for video
            enableMultiplexing = true,
            statsUpdateInterval = 3000,
            logLevel = "error"
        )
    )

    companion object {
        fun fromId(id: String): PerformanceProfile {
            return when (id) {
                "turbo" -> Turbo
                "balanced" -> Balanced
                "battery_saver" -> BatterySaver
                "gaming" -> Gaming
                "streaming" -> Streaming
                else -> Balanced
            }
        }

        fun getAll(): List<PerformanceProfile> {
            return listOf(Turbo, Balanced, BatterySaver, Gaming, Streaming)
        }
    }
}

/**
 * Performance configuration parameters
 */
data class PerformanceConfig(
    // Buffer settings
    val bufferSize: Int,

    // Timeout settings (milliseconds)
    val connectionTimeout: Int,
    val handshakeTimeout: Int,
    val idleTimeout: Int,

    // TCP optimization
    val tcpFastOpen: Boolean,
    val tcpNoDelay: Boolean,
    val keepAlive: Boolean,
    val keepAliveInterval: Int, // seconds

    // DNS settings
    val dnsCacheTtl: Int, // seconds
    val dnsPrefetch: Boolean,

    // Connection settings
    val parallelConnections: Int,
    val enableCompression: Boolean,
    val enableMultiplexing: Boolean,

    // Monitoring settings
    val statsUpdateInterval: Long, // milliseconds
    val logLevel: String // "debug", "info", "warning", "error", "none"
)
