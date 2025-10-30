package com.simplexray.an.protocol.streaming

/**
 * Streaming platform optimization
 */
class StreamingOptimizer {

    /**
     * Streaming platform profiles
     */
    enum class StreamingPlatform(
        val displayName: String,
        val domains: List<String>,
        val config: StreamingConfig
    ) {
        YOUTUBE(
            "YouTube",
            listOf("youtube.com", "googlevideo.com", "ytimg.com"),
            StreamingConfig(
                adaptiveBitrate = true,
                preferredQuality = StreamQuality.AUTO,
                bufferAhead = 30, // seconds
                initialBuffer = 5,
                rebufferThreshold = 2,
                maxBitrate = 50_000_000, // 50 Mbps for 4K
                enablePrefetch = true,
                segmentSize = 5 // seconds per segment
            )
        ),

        NETFLIX(
            "Netflix",
            listOf("netflix.com", "nflxvideo.net", "nflxso.net"),
            StreamingConfig(
                adaptiveBitrate = true,
                preferredQuality = StreamQuality.HIGH,
                bufferAhead = 60,
                initialBuffer = 10,
                rebufferThreshold = 3,
                maxBitrate = 25_000_000, // 25 Mbps
                enablePrefetch = true,
                segmentSize = 4
            )
        ),

        PRIME_VIDEO(
            "Prime Video",
            listOf("primevideo.com", "amazon.com", "amazonvideo.com"),
            StreamingConfig(
                adaptiveBitrate = true,
                preferredQuality = StreamQuality.HIGH,
                bufferAhead = 45,
                initialBuffer = 8,
                rebufferThreshold = 3,
                maxBitrate = 25_000_000,
                enablePrefetch = true,
                segmentSize = 6
            )
        ),

        TWITCH(
            "Twitch",
            listOf("twitch.tv", "ttvnw.net"),
            StreamingConfig(
                adaptiveBitrate = true,
                preferredQuality = StreamQuality.AUTO,
                bufferAhead = 20, // Lower for live streaming
                initialBuffer = 3,
                rebufferThreshold = 1,
                maxBitrate = 8_000_000, // 8 Mbps
                enablePrefetch = false, // Live content
                segmentSize = 2,
                lowLatencyMode = true
            )
        ),

        DISNEY_PLUS(
            "Disney+",
            listOf("disneyplus.com", "disney.com"),
            StreamingConfig(
                adaptiveBitrate = true,
                preferredQuality = StreamQuality.HIGH,
                bufferAhead = 45,
                initialBuffer = 8,
                rebufferThreshold = 3,
                maxBitrate = 25_000_000,
                enablePrefetch = true,
                segmentSize = 6
            )
        ),

        SPOTIFY(
            "Spotify",
            listOf("spotify.com", "scdn.co"),
            StreamingConfig(
                adaptiveBitrate = false,
                preferredQuality = StreamQuality.HIGH,
                bufferAhead = 60,
                initialBuffer = 5,
                rebufferThreshold = 2,
                maxBitrate = 320_000, // 320 kbps for music
                enablePrefetch = true,
                segmentSize = 10
            )
        ),

        YOUTUBE_MUSIC(
            "YouTube Music",
            listOf("music.youtube.com", "googlevideo.com"),
            StreamingConfig(
                adaptiveBitrate = false,
                preferredQuality = StreamQuality.HIGH,
                bufferAhead = 60,
                initialBuffer = 5,
                rebufferThreshold = 2,
                maxBitrate = 256_000,
                enablePrefetch = true,
                segmentSize = 10
            )
        );

        companion object {
            fun detectPlatform(url: String): StreamingPlatform? {
                return entries.find { platform ->
                    platform.domains.any { domain ->
                        url.contains(domain, ignoreCase = true)
                    }
                }
            }
        }
    }

    /**
     * Stream quality levels
     */
    enum class StreamQuality(
        val displayName: String,
        val videoBitrate: Long, // bps
        val resolution: String
    ) {
        AUTO("Auto", 0, "Auto"),
        LOW("Low (360p)", 500_000, "640x360"),
        MEDIUM("Medium (480p)", 1_000_000, "854x480"),
        HIGH("High (720p)", 2_500_000, "1280x720"),
        FULL_HD("Full HD (1080p)", 5_000_000, "1920x1080"),
        QHD("QHD (1440p)", 10_000_000, "2560x1440"),
        UHD_4K("4K (2160p)", 25_000_000, "3840x2160")
    }

    /**
     * Streaming configuration
     */
    data class StreamingConfig(
        val adaptiveBitrate: Boolean,
        val preferredQuality: StreamQuality,
        val bufferAhead: Int, // seconds
        val initialBuffer: Int, // seconds
        val rebufferThreshold: Int, // seconds
        val maxBitrate: Long, // bits per second
        val enablePrefetch: Boolean,
        val segmentSize: Int, // seconds
        val lowLatencyMode: Boolean = false
    )

    /**
     * Adaptive bitrate controller
     */
    class AdaptiveBitrateController(
        private val config: StreamingConfig
    ) {
        private var currentQuality = config.preferredQuality
        private val bandwidthHistory = mutableListOf<Long>()
        private val maxHistorySize = 10

        /**
         * Bandwidth measurement
         */
        data class BandwidthMeasurement(
            val timestamp: Long,
            val bytesTransferred: Long,
            val duration: Long, // milliseconds
            val bandwidth: Long // bytes per second
        )

        /**
         * Update quality based on available bandwidth
         */
        fun updateQuality(availableBandwidth: Long): StreamQuality {
            // Add to history
            bandwidthHistory.add(availableBandwidth)
            if (bandwidthHistory.size > maxHistorySize) {
                bandwidthHistory.removeAt(0)
            }

            // Calculate average bandwidth
            val avgBandwidth = bandwidthHistory.average().toLong()

            // Select appropriate quality with safety margin
            val targetBandwidth = (avgBandwidth * 0.85).toLong() // 85% of available

            currentQuality = when {
                targetBandwidth >= StreamQuality.UHD_4K.videoBitrate -> StreamQuality.UHD_4K
                targetBandwidth >= StreamQuality.QHD.videoBitrate -> StreamQuality.QHD
                targetBandwidth >= StreamQuality.FULL_HD.videoBitrate -> StreamQuality.FULL_HD
                targetBandwidth >= StreamQuality.HIGH.videoBitrate -> StreamQuality.HIGH
                targetBandwidth >= StreamQuality.MEDIUM.videoBitrate -> StreamQuality.MEDIUM
                else -> StreamQuality.LOW
            }

            return currentQuality
        }

        /**
         * Predict next segment quality
         */
        fun predictQuality(bufferLevel: Int): StreamQuality {
            return when {
                bufferLevel < config.rebufferThreshold -> {
                    // Buffer low, downgrade quality
                    downgradeQuality(currentQuality)
                }
                bufferLevel > config.bufferAhead * 0.8 -> {
                    // Buffer high, can try upgrade
                    upgradeQuality(currentQuality)
                }
                else -> currentQuality
            }
        }

        private fun upgradeQuality(current: StreamQuality): StreamQuality {
            val qualities = StreamQuality.entries
            val currentIndex = qualities.indexOf(current)
            return if (currentIndex < qualities.size - 1) {
                qualities[currentIndex + 1]
            } else {
                current
            }
        }

        private fun downgradeQuality(current: StreamQuality): StreamQuality {
            val qualities = StreamQuality.entries
            val currentIndex = qualities.indexOf(current)
            return if (currentIndex > 1) { // Skip AUTO
                qualities[currentIndex - 1]
            } else {
                current
            }
        }

        fun getCurrentQuality(): StreamQuality = currentQuality

        fun reset() {
            bandwidthHistory.clear()
            currentQuality = config.preferredQuality
        }
    }

    /**
     * Buffer manager for smooth playback
     */
    class BufferManager(
        private val config: StreamingConfig
    ) {
        private var currentBufferLevel = 0 // seconds
        private var isBuffering = false

        fun addToBuffer(segmentDuration: Int) {
            currentBufferLevel += segmentDuration
        }

        fun consumeBuffer(playbackTime: Int) {
            currentBufferLevel = maxOf(0, currentBufferLevel - playbackTime)
        }

        fun shouldBuffer(): Boolean {
            return currentBufferLevel < config.rebufferThreshold
        }

        fun shouldPrefetch(): Boolean {
            return config.enablePrefetch &&
                    currentBufferLevel < config.bufferAhead
        }

        fun getBufferLevel(): Int = currentBufferLevel

        fun setBuffering(buffering: Boolean) {
            isBuffering = buffering
        }

        fun isBuffering(): Boolean = isBuffering

        fun getBufferHealth(): BufferHealth {
            return when {
                currentBufferLevel < config.rebufferThreshold -> BufferHealth.CRITICAL
                currentBufferLevel < config.initialBuffer -> BufferHealth.LOW
                currentBufferLevel < config.bufferAhead * 0.5 -> BufferHealth.MEDIUM
                currentBufferLevel < config.bufferAhead -> BufferHealth.GOOD
                else -> BufferHealth.EXCELLENT
            }
        }
    }

    enum class BufferHealth {
        CRITICAL,
        LOW,
        MEDIUM,
        GOOD,
        EXCELLENT
    }

    /**
     * Streaming statistics
     */
    data class StreamingStats(
        val platform: StreamingPlatform,
        val currentQuality: StreamQuality,
        val bufferLevel: Int,
        val bufferHealth: BufferHealth,
        val averageBitrate: Long,
        val rebufferCount: Int,
        val totalRebufferTime: Long, // milliseconds
        val segmentsDownloaded: Int,
        val segmentsFailed: Int
    ) {
        val successRate: Float
            get() = if (segmentsDownloaded + segmentsFailed > 0) {
                (segmentsDownloaded.toFloat() / (segmentsDownloaded + segmentsFailed)) * 100
            } else 100f
    }

    /**
     * Platform-specific optimization recommendations
     */
    fun getOptimizationRecommendations(
        platform: StreamingPlatform,
        currentBandwidth: Long
    ): List<String> {
        val recommendations = mutableListOf<String>()
        val config = platform.config

        // Bandwidth recommendations
        val recommendedBandwidth = config.maxBitrate * 1.5 // 50% overhead
        if (currentBandwidth < recommendedBandwidth) {
            recommendations.add(
                "Recommended bandwidth: ${recommendedBandwidth / 1_000_000}Mbps, " +
                "Current: ${currentBandwidth / 1_000_000}Mbps"
            )
        }

        // Quality recommendations
        if (currentBandwidth < config.preferredQuality.videoBitrate) {
            recommendations.add("Consider lowering video quality for smoother playback")
        }

        // Buffer recommendations
        if (config.lowLatencyMode) {
            recommendations.add("Low latency mode enabled - minimal buffering")
        } else {
            recommendations.add("Pre-buffer ${config.initialBuffer}s before playback")
        }

        return recommendations
    }
}
