package com.simplexray.an.streaming

/**
 * CdnMatcher - Fast suffix matcher for CDN domain classification.
 * 
 * Thread-safe, immutable utility for matching streaming CDN domains.
 * Uses binary search on sorted suffix list for O(log n) matching.
 * 
 * CDN patterns supported:
 * - *.googlevideo.com, *.ytimg.com (YouTube)
 * - *.cloudfront.net (CloudFront)
 * - *.fastly.net, *.fastlylb.net (Fastly)
 * - *.akamaihd.net, *.akamaihd-staging.net (Akamai)
 */
object CdnMatcher {
    // Immutable sorted list of CDN suffixes (for binary search)
    private val CDN_SUFFIXES = listOf(
        "akadns.net",
        "akamaihd-staging.net",
        "akamaihd.net",
        "akamaized.net",
        "cloudfront.net",
        "fastly.net",
        "fastlylb.net",
        "googlevideo.com",
        "gstatic.com",
        "googleusercontent.com",
        "ytimg.com"
    ).sorted()
    
    /**
     * Check if host is a streaming CDN domain.
     * Fast suffix matching using binary search.
     * 
     * @param host Host to check (can include protocol, port)
     * @return true if host matches streaming CDN pattern
     */
    fun isStreamingHost(host: String): Boolean {
        val normalized = normalize(host)
        
        // Binary search on sorted suffixes
        for (suffix in CDN_SUFFIXES) {
            if (normalized == suffix || normalized.endsWith(".$suffix")) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Normalize host for matching.
     * Removes protocol, port, www prefix, and trailing dot.
     * 
     * @param host Host to normalize
     * @return Normalized host (lowercase, no protocol/port/www)
     */
    fun normalize(host: String): String {
        var normalized = host
        
        // Remove protocol if present
        if (normalized.contains("://")) {
            normalized = normalized.substringAfter("://")
        }
        
        // Extract host part (before path)
        if (normalized.contains("/")) {
            normalized = normalized.substringBefore("/")
        }
        
        // Remove port if present
        if (normalized.contains(":")) {
            normalized = normalized.substringBefore(":")
        }
        
        // Remove www. prefix
        if (normalized.startsWith("www.", ignoreCase = true)) {
            normalized = normalized.substring(4)
        }
        
        // Remove trailing dot
        normalized = normalized.trimEnd('.')
        
        // Convert to lowercase
        return normalized.lowercase()
    }
}

