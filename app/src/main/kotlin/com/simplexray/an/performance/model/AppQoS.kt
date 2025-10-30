package com.simplexray.an.performance.model

/**
 * Per-app Quality of Service settings
 */
data class AppQoSRule(
    val packageName: String,
    val appName: String,
    val priority: QoSPriority = QoSPriority.Normal,
    val bandwidthLimit: BandwidthLimit? = null,
    val latencySensitive: Boolean = false,
    val enabled: Boolean = true
)

/**
 * QoS priority levels
 */
enum class QoSPriority(val value: Int, val displayName: String) {
    Critical(4, "Critical"), // VoIP, Gaming
    High(3, "High"), // Video calls, messaging
    Normal(2, "Normal"), // Web browsing
    Low(1, "Low"), // Downloads, background sync
    Background(0, "Background"); // System updates

    companion object {
        fun fromValue(value: Int): QoSPriority {
            return entries.find { it.value == value } ?: Normal
        }
    }
}

/**
 * Bandwidth limit configuration
 */
data class BandwidthLimit(
    val downloadLimit: Long? = null, // bytes per second, null = unlimited
    val uploadLimit: Long? = null, // bytes per second, null = unlimited
    val burstAllowance: Long? = null // bytes
)

/**
 * Pre-configured app categories with recommended QoS settings
 */
enum class AppCategory(
    val displayName: String,
    val recommendedPriority: QoSPriority,
    val latencySensitive: Boolean,
    val packagePatterns: List<String>
) {
    VoIP(
        "Voice & Video Calls",
        QoSPriority.Critical,
        true,
        listOf("whatsapp", "telegram", "zoom", "teams", "skype", "discord", "viber")
    ),
    Gaming(
        "Gaming",
        QoSPriority.Critical,
        true,
        listOf("game", "pubg", "freefire", "callofduty", "mobilelegends", "clash")
    ),
    Streaming(
        "Video Streaming",
        QoSPriority.High,
        false,
        listOf("youtube", "netflix", "twitch", "spotify", "primevideo", "disney")
    ),
    Messaging(
        "Messaging",
        QoSPriority.High,
        false,
        listOf("messenger", "signal", "line", "wechat", "imessage")
    ),
    Browser(
        "Web Browser",
        QoSPriority.Normal,
        false,
        listOf("chrome", "firefox", "browser", "edge", "opera", "brave")
    ),
    Social(
        "Social Media",
        QoSPriority.Normal,
        false,
        listOf("facebook", "instagram", "twitter", "tiktok", "snapchat", "reddit")
    ),
    Download(
        "Downloads",
        QoSPriority.Low,
        false,
        listOf("torrent", "download", "fdm", "idm")
    ),
    Background(
        "Background Apps",
        QoSPriority.Background,
        false,
        listOf("backup", "sync", "update")
    );

    fun matches(packageName: String): Boolean {
        return packagePatterns.any { pattern ->
            packageName.lowercase().contains(pattern.lowercase())
        }
    }

    companion object {
        fun categorize(packageName: String): AppCategory? {
            return entries.find { it.matches(packageName) }
        }

        fun getRecommendedQoS(packageName: String): QoSPriority {
            return categorize(packageName)?.recommendedPriority ?: QoSPriority.Normal
        }
    }
}

/**
 * Traffic shaping configuration
 */
data class TrafficShapingConfig(
    val enabled: Boolean = false,
    val globalDownloadLimit: Long? = null, // bytes per second
    val globalUploadLimit: Long? = null, // bytes per second
    val appRules: List<AppQoSRule> = emptyList(),
    val enableQoS: Boolean = true,
    val enableFairQueuing: Boolean = true
)

/**
 * Rate limiting strategy
 */
sealed class RateLimitStrategy {
    /**
     * Token bucket algorithm
     */
    data class TokenBucket(
        val rate: Long, // tokens per second
        val bucketSize: Long, // maximum tokens
        val initialTokens: Long = bucketSize
    ) : RateLimitStrategy()

    /**
     * Leaky bucket algorithm
     */
    data class LeakyBucket(
        val rate: Long, // bytes per second
        val bucketSize: Long // maximum bytes
    ) : RateLimitStrategy()

    /**
     * Fixed window algorithm
     */
    data class FixedWindow(
        val limit: Long, // bytes per window
        val windowSize: Long // window duration in milliseconds
    ) : RateLimitStrategy()
}

/**
 * Per-domain traffic rules
 */
data class DomainRule(
    val domain: String,
    val priority: QoSPriority = QoSPriority.Normal,
    val bandwidthLimit: BandwidthLimit? = null,
    val block: Boolean = false,
    val directConnect: Boolean = false, // bypass VPN
    val enableCache: Boolean = true
)

/**
 * Traffic statistics per app
 */
data class AppTrafficStats(
    val packageName: String,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val connectionCount: Int = 0,
    val lastActive: Long = 0
) {
    fun getTotalBytes(): Long = uploadBytes + downloadBytes
}
