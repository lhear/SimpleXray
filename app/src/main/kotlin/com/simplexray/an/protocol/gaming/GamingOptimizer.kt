package com.simplexray.an.protocol.gaming

import com.simplexray.an.protocol.optimization.ProtocolConfig

/**
 * Gaming-specific network optimization
 */
class GamingOptimizer {

    /**
     * Popular game profiles with optimized settings
     */
    enum class GameProfile(
        val displayName: String,
        val packagePatterns: List<String>,
        val serverPorts: List<Int>,
        val protocol: GameProtocol,
        val config: GameConfig
    ) {
        PUBG_MOBILE(
            "PUBG Mobile",
            listOf("com.tencent.ig", "com.pubg", "com.rekoo.pubgm"),
            listOf(10491, 17500, 20000, 20001, 20002),
            GameProtocol.UDP,
            GameConfig(
                priorityLevel = GamePriority.CRITICAL,
                maxLatency = 80,
                jitterTolerance = 20,
                bufferSize = 64 * 1024,
                enableFastPath = true,
                disableCompression = true,
                tcpNoDelay = true,
                udpBufferSize = 256 * 1024
            )
        ),

        FREE_FIRE(
            "Free Fire",
            listOf("com.dts.freefireth", "com.dts.freefiremax"),
            listOf(10001, 10002, 39003, 39006),
            GameProtocol.UDP,
            GameConfig(
                priorityLevel = GamePriority.CRITICAL,
                maxLatency = 100,
                jitterTolerance = 25,
                bufferSize = 64 * 1024,
                enableFastPath = true,
                disableCompression = true,
                udpBufferSize = 128 * 1024
            )
        ),

        COD_MOBILE(
            "Call of Duty Mobile",
            listOf("com.activision.callofduty", "com.garena.game.codm"),
            listOf(3074, 30000, 30001),
            GameProtocol.UDP,
            GameConfig(
                priorityLevel = GamePriority.CRITICAL,
                maxLatency = 70,
                jitterTolerance = 15,
                bufferSize = 128 * 1024,
                enableFastPath = true,
                disableCompression = true,
                tcpNoDelay = true,
                udpBufferSize = 256 * 1024
            )
        ),

        MOBILE_LEGENDS(
            "Mobile Legends",
            listOf("com.mobile.legends"),
            listOf(5000, 5001, 5500, 8001),
            GameProtocol.TCP_UDP,
            GameConfig(
                priorityLevel = GamePriority.CRITICAL,
                maxLatency = 90,
                jitterTolerance = 20,
                bufferSize = 96 * 1024,
                enableFastPath = true,
                disableCompression = false,
                tcpNoDelay = true,
                udpBufferSize = 192 * 1024
            )
        ),

        GENSHIN_IMPACT(
            "Genshin Impact",
            listOf("com.miHoYo.GenshinImpact", "com.miHoYo.Yuanshen"),
            listOf(22101, 22102),
            GameProtocol.TCP_UDP,
            GameConfig(
                priorityLevel = GamePriority.HIGH,
                maxLatency = 150,
                jitterTolerance = 30,
                bufferSize = 128 * 1024,
                enableFastPath = true,
                disableCompression = false,
                tcpNoDelay = true,
                udpBufferSize = 128 * 1024
            )
        ),

        CLASH_OF_CLANS(
            "Clash of Clans",
            listOf("com.supercell.clashofclans"),
            listOf(9330, 9339),
            GameProtocol.TCP,
            GameConfig(
                priorityLevel = GamePriority.HIGH,
                maxLatency = 200,
                jitterTolerance = 50,
                bufferSize = 64 * 1024,
                enableFastPath = false,
                disableCompression = false,
                tcpNoDelay = true,
                keepAliveInterval = 30
            )
        ),

        VALORANT_MOBILE(
            "Valorant Mobile",
            listOf("com.riotgames.valorant"),
            listOf(8393, 8394, 8395),
            GameProtocol.UDP,
            GameConfig(
                priorityLevel = GamePriority.CRITICAL,
                maxLatency = 50,
                jitterTolerance = 10,
                bufferSize = 128 * 1024,
                enableFastPath = true,
                disableCompression = true,
                tcpNoDelay = true,
                udpBufferSize = 256 * 1024
            )
        ),

        GENERIC_FPS(
            "Generic FPS",
            emptyList(),
            emptyList(),
            GameProtocol.UDP,
            GameConfig(
                priorityLevel = GamePriority.CRITICAL,
                maxLatency = 80,
                jitterTolerance = 20,
                bufferSize = 96 * 1024,
                enableFastPath = true,
                disableCompression = true,
                tcpNoDelay = true,
                udpBufferSize = 192 * 1024
            )
        ),

        GENERIC_MOBA(
            "Generic MOBA",
            emptyList(),
            emptyList(),
            GameProtocol.TCP_UDP,
            GameConfig(
                priorityLevel = GamePriority.CRITICAL,
                maxLatency = 100,
                jitterTolerance = 25,
                bufferSize = 96 * 1024,
                enableFastPath = true,
                disableCompression = false,
                tcpNoDelay = true,
                udpBufferSize = 128 * 1024
            )
        );

        companion object {
            fun detectGame(packageName: String): GameProfile? {
                return entries.find { profile ->
                    profile.packagePatterns.any { pattern ->
                        packageName.contains(pattern, ignoreCase = true)
                    }
                }
            }
        }
    }

    /**
     * Game protocol type
     */
    enum class GameProtocol {
        TCP,
        UDP,
        TCP_UDP
    }

    /**
     * Game priority level
     */
    enum class GamePriority(val qosValue: Int) {
        CRITICAL(4),
        HIGH(3),
        NORMAL(2)
    }

    /**
     * Game-specific configuration
     */
    data class GameConfig(
        val priorityLevel: GamePriority,
        val maxLatency: Int, // Maximum acceptable latency in ms
        val jitterTolerance: Int, // Maximum jitter in ms
        val bufferSize: Int,
        val enableFastPath: Boolean,
        val disableCompression: Boolean,
        val tcpNoDelay: Boolean,
        val keepAliveInterval: Int = 15, // seconds
        val udpBufferSize: Int = 128 * 1024,
        val enableJitterBuffer: Boolean = true
    )

    /**
     * Jitter buffer for UDP packets
     */
    class JitterBuffer(
        private val maxSize: Int = 64,
        private val targetDelay: Int = 20 // ms
    ) {
        private val buffer = mutableListOf<BufferedPacket>()

        data class BufferedPacket(
            val data: ByteArray,
            val sequence: Int,
            val timestamp: Long
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as BufferedPacket
                if (!data.contentEquals(other.data)) return false
                if (sequence != other.sequence) return false
                return true
            }

            override fun hashCode(): Int {
                var result = data.contentHashCode()
                result = 31 * result + sequence
                return result
            }
        }

        fun addPacket(packet: BufferedPacket) {
            synchronized(buffer) {
                if (buffer.size >= maxSize) {
                    buffer.removeAt(0)
                }
                buffer.add(packet)
                buffer.sortBy { it.sequence }
            }
        }

        fun getNextPacket(): BufferedPacket? {
            synchronized(buffer) {
                if (buffer.isEmpty()) return null

                val now = System.currentTimeMillis()
                val oldest = buffer.firstOrNull() ?: return null

                // Check if packet has been buffered long enough
                if (now - oldest.timestamp >= targetDelay) {
                    return buffer.removeAt(0)
                }

                return null
            }
        }

        fun clear() {
            synchronized(buffer) {
                buffer.clear()
            }
        }
    }

    /**
     * Ping stabilization
     */
    class PingStabilizer(
        private val windowSize: Int = 10
    ) {
        private val pingHistory = mutableListOf<Int>()

        fun addPing(ping: Int) {
            synchronized(pingHistory) {
                pingHistory.add(ping)
                if (pingHistory.size > windowSize) {
                    pingHistory.removeAt(0)
                }
            }
        }

        fun getStabilizedPing(): Int {
            synchronized(pingHistory) {
                if (pingHistory.isEmpty()) return 0

                // Remove outliers and calculate median
                val sorted = pingHistory.sorted()
                val size = sorted.size

                // Remove top and bottom 20%
                val trimSize = (size * 0.2).toInt()
                val trimmed = if (size > 5) {
                    sorted.subList(trimSize, size - trimSize)
                } else {
                    sorted
                }

                return if (trimmed.isNotEmpty()) {
                    trimmed[trimmed.size / 2]
                } else {
                    0
                }
            }
        }

        fun getJitter(): Int {
            synchronized(pingHistory) {
                if (pingHistory.size < 2) return 0

                var totalVariation = 0
                for (i in 1 until pingHistory.size) {
                    totalVariation += kotlin.math.abs(pingHistory[i] - pingHistory[i - 1])
                }

                return totalVariation / (pingHistory.size - 1)
            }
        }

        fun clear() {
            synchronized(pingHistory) {
                pingHistory.clear()
            }
        }
    }

    /**
     * Game server detection
     */
    object GameServerDetector {
        /**
         * Detect if connection is to a game server
         */
        fun isGameServer(host: String, port: Int, packageName: String?): GameProfile? {
            // Check by package name first
            packageName?.let { pkg ->
                GameProfile.detectGame(pkg)?.let { return it }
            }

            // Check by port
            GameProfile.entries.forEach { profile ->
                if (port in profile.serverPorts) {
                    return profile
                }
            }

            // Check by hostname patterns
            val gamingHostPatterns = listOf(
                "game", "gaming", "lobby", "match", "server",
                "pubg", "tencent", "garena", "mihoyo", "riot"
            )

            if (gamingHostPatterns.any { host.contains(it, ignoreCase = true) }) {
                // Return generic profile based on common port ranges
                return when {
                    port in 3074..3478 -> GameProfile.GENERIC_FPS
                    port in 5000..8001 -> GameProfile.GENERIC_MOBA
                    else -> null
                }
            }

            return null
        }
    }

    /**
     * Apply gaming optimizations to protocol config
     */
    fun applyGamingOptimizations(
        baseConfig: ProtocolConfig,
        gameProfile: GameProfile
    ): ProtocolConfig {
        val gameConfig = gameProfile.config

        return baseConfig.copy(
            tls13Enabled = true,
            tls13EarlyData = true, // 0-RTT for faster reconnection
            brotliEnabled = !gameConfig.disableCompression,
            brotliQuality = if (!gameConfig.disableCompression) 4 else 0,
            gzipEnabled = !gameConfig.disableCompression,
            multiplexingEnabled = gameConfig.enableFastPath,
            serverPushEnabled = false
        )
    }

    /**
     * Get recommended settings message for game
     */
    fun getRecommendedSettingsMessage(gameProfile: GameProfile): String {
        return buildString {
            appendLine("Recommended settings for ${gameProfile.displayName}:")
            appendLine("• Max acceptable latency: ${gameProfile.config.maxLatency}ms")
            appendLine("• Jitter tolerance: ${gameProfile.config.jitterTolerance}ms")
            appendLine("• Priority: ${gameProfile.config.priorityLevel.name}")
            appendLine("• Protocol: ${gameProfile.protocol.name}")
            if (gameProfile.config.disableCompression) {
                appendLine("• Compression: DISABLED (lower latency)")
            }
            if (gameProfile.config.tcpNoDelay) {
                appendLine("• TCP_NODELAY: ENABLED")
            }
        }
    }
}
