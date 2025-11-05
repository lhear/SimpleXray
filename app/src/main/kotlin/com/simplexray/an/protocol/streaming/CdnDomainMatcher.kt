package com.simplexray.an.protocol.streaming

import com.simplexray.an.common.AppLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * CdnDomainMatcher - CDN domain classification with geosite normalization.
 * 
 * FIXES IMPLEMENTED:
 * - CDN domain normalization by geosite patterns
 * - Suffix matching: *.googlevideo.com, *.ytimg.com, *.fastly.net, etc.
 * - Streaming priority tagging for CDN domains
 * - Thread-safe domain matching
 * - Cache with TTL for performance
 * 
 * Supported CDN patterns:
 * - YouTube: *.googlevideo.com, *.ytimg.com
 * - CloudFront: *.cloudfront.net
 * - Fastly: *.fastly.net
 * - Akamai: *.akamaihd.net
 * - Google CDN: *.gstatic.com, *.googleusercontent.com
 */
object CdnDomainMatcher {
    private const val TAG = "CdnDomainMatcher"
    private const val CACHE_TTL_MS = 3_600_000L // 1 hour
    
    // CDN suffix patterns (normalized without wildcards)
    private val cdnSuffixPatterns = mapOf(
        // YouTube/Google Video CDN
        "googlevideo.com" to StreamingRepository.CdnProvider.GOOGLE_VIDEO,
        "ytimg.com" to StreamingRepository.CdnProvider.GOOGLE_VIDEO,
        "gstatic.com" to StreamingRepository.CdnProvider.GOOGLE_VIDEO,
        "googleusercontent.com" to StreamingRepository.CdnProvider.GOOGLE_VIDEO,
        
        // CloudFront
        "cloudfront.net" to StreamingRepository.CdnProvider.CLOUDFRONT,
        
        // Fastly
        "fastly.net" to StreamingRepository.CdnProvider.FASTLY,
        "fastlylb.net" to StreamingRepository.CdnProvider.FASTLY,
        
        // Akamai
        "akamaihd.net" to StreamingRepository.CdnProvider.AKAMAI,
        "akamaized.net" to StreamingRepository.CdnProvider.AKAMAI,
        "akadns.net" to StreamingRepository.CdnProvider.AKAMAI,
        
        // Other streaming CDNs
        "netflix.com" to StreamingRepository.CdnProvider.OTHER,
        "nflximg.com" to StreamingRepository.CdnProvider.OTHER,
        "nflxvideo.net" to StreamingRepository.CdnProvider.OTHER,
        "twitch.tv" to StreamingRepository.CdnProvider.OTHER,
        "ttvnw.net" to StreamingRepository.CdnProvider.OTHER // Twitch CDN
    )
    
    // Cache for classification results
    private val classificationCache = ConcurrentHashMap<String, CachedClassification>()
    
    /**
     * Cached classification with timestamp
     */
    private data class CachedClassification(
        val isStreamingDomain: Boolean,
        val cdnProvider: StreamingRepository.CdnProvider?,
        val normalizedDomain: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS
        }
    }
    
    /**
     * Match domain against CDN patterns with normalization.
     * 
     * @param domain Domain to match (can include protocol, port, path)
     * @return CdnMatchResult with classification
     */
    fun matchDomain(domain: String): CdnMatchResult {
        val normalized = normalizeDomain(domain)
        
        // Check cache first
        classificationCache[normalized]?.let { cached ->
            if (!cached.isExpired()) {
                return CdnMatchResult(
                    isStreamingDomain = cached.isStreamingDomain,
                    cdnProvider = cached.cdnProvider,
                    normalizedDomain = cached.normalizedDomain,
                    matchType = MatchType.CACHED
                )
            } else {
                // Remove expired entry
                classificationCache.remove(normalized)
            }
        }
        
        // Match against CDN patterns
        val matchResult = performMatch(normalized)
        
        // Cache result
        classificationCache[normalized] = CachedClassification(
            isStreamingDomain = matchResult.isStreamingDomain,
            cdnProvider = matchResult.cdnProvider,
            normalizedDomain = matchResult.normalizedDomain
        )
        
        AppLogger.d("$TAG: Matched domain $domain -> $matchResult")
        
        return matchResult
    }
    
    /**
     * Normalize domain for matching (handles geosite patterns)
     */
    private fun normalizeDomain(domain: String): String {
        // Remove protocol if present
        var normalized = domain
        if (normalized.contains("://")) {
            normalized = normalized.substringAfter("://").substringBefore("/")
        }
        
        // Remove port if present
        if (normalized.contains(":")) {
            normalized = normalized.substringBefore(":")
        }
        
        // Remove www. prefix
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4)
        }
        
        return normalized.lowercase()
    }
    
    /**
     * Perform domain matching against CDN patterns
     */
    private fun performMatch(normalized: String): CdnMatchResult {
        // Try exact match first
        cdnSuffixPatterns[normalized]?.let { provider ->
            return CdnMatchResult(
                isStreamingDomain = true,
                cdnProvider = provider,
                normalizedDomain = normalized,
                matchType = MatchType.EXACT
            )
        }
        
        // Try suffix matching (e.g., r1---sn-xxx.googlevideo.com matches googlevideo.com)
        for ((suffix, provider) in cdnSuffixPatterns) {
            if (normalized.endsWith(".$suffix") || normalized.endsWith(suffix)) {
                return CdnMatchResult(
                    isStreamingDomain = true,
                    cdnProvider = provider,
                    normalizedDomain = normalized,
                    matchType = MatchType.SUFFIX
                )
            }
        }
        
        // Check if it's a known streaming domain (not necessarily CDN)
        val isStreaming = isStreamingDomain(normalized)
        
        return CdnMatchResult(
            isStreamingDomain = isStreaming,
            cdnProvider = null,
            normalizedDomain = normalized,
            matchType = if (isStreaming) MatchType.STREAMING_DOMAIN else MatchType.NO_MATCH
        )
    }
    
    /**
     * Check if domain is a streaming domain (not necessarily CDN)
     */
    private fun isStreamingDomain(domain: String): Boolean {
        val streamingPatterns = listOf(
            "youtube.com",
            "youtu.be",
            "twitch.tv",
            "netflix.com",
            "hulu.com",
            "disney.com",
            "hbonow.com",
            "primevideo.com"
        )
        
        return streamingPatterns.any { pattern ->
            domain.contains(pattern) || domain.endsWith(".$pattern")
        }
    }
    
    /**
     * Invalidate cache for domain or all
     */
    fun invalidateCache(domain: String? = null) {
        if (domain != null) {
            val normalized = normalizeDomain(domain)
            classificationCache.remove(normalized)
        } else {
            classificationCache.clear()
        }
    }
    
    /**
     * Prune expired cache entries
     */
    fun pruneExpired() {
        classificationCache.entries.removeAll { (_, cached) ->
            cached.isExpired()
        }
    }
    
    /**
     * CDN match result
     */
    data class CdnMatchResult(
        val isStreamingDomain: Boolean,
        val cdnProvider: StreamingRepository.CdnProvider?,
        val normalizedDomain: String,
        val matchType: MatchType
    )
    
    /**
     * Match type
     */
    enum class MatchType {
        EXACT,              // Exact domain match
        SUFFIX,             // Suffix pattern match (*.googlevideo.com)
        STREAMING_DOMAIN,   // Known streaming domain (not CDN)
        CACHED,             // Cached result
        NO_MATCH            // No match
    }
}

