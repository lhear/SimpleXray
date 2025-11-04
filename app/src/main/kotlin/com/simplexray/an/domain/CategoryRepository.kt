package com.simplexray.an.domain

import android.content.Context
import com.simplexray.an.config.ApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CategoryRepository(
    private val context: Context,
    externalScope: CoroutineScope? = null,
    private val ipDomainMapper: IpDomainMapper? = null
) {
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _distribution = MutableStateFlow<Map<Category, Float>>(emptyMap())
    val distribution: Flow<Map<Category, Float>> = _distribution.asStateFlow()
    
    // Domain categorization patterns
    private val categoryPatterns = mapOf(
        Category.CDN to listOf(
            "cdn", "cloudfront", "akamai", "fastly", "cloudflare",
            "amazonaws", "azureedge", "googleusercontent"
        ),
        Category.Social to listOf(
            "facebook", "instagram", "twitter", "tiktok", "snapchat",
            "linkedin", "reddit", "telegram", "whatsapp", "discord"
        ),
        Category.Gaming to listOf(
            "steam", "epicgames", "riot", "battle.net", "blizzard",
            "ea.com", "ubisoft", "xbox", "playstation", "nintendo",
            "pubg", "garena", "mobile-legends"
        ),
        Category.Video to listOf(
            "youtube", "netflix", "twitch", "hulu", "disney",
            "primevideo", "vimeo", "dailymotion", "spotify",
            "soundcloud", "deezer"
        )
    )
    
    // Track domain traffic weights
    private val domainWeights = mutableMapOf<String, Long>()

    fun start() {
        if (ApiConfig.isMock(context)) startMock() else startLive()
    }

    private fun startMock() {
        scope.launch {
            _distribution.emit(
                linkedMapOf(
                    Category.CDN to 0.35f,
                    Category.Social to 0.15f,
                    Category.Gaming to 0.10f,
                    Category.Video to 0.25f,
                    Category.Other to 0.15f
                )
            )
        }
    }

    private fun startLive() {
        scope.launch {
            // Continuously monitor and categorize traffic
            while (isActive) {
                updateDistribution()
                delay(5000) // Update every 5 seconds
            }
        }
    }
    
    /**
     * Update traffic distribution based on domain activity
     */
    private suspend fun updateDistribution() {
        if (domainWeights.isEmpty()) {
            // No data yet, emit empty distribution
            _distribution.emit(emptyMap())
            return
        }
        
        val categoryWeights = mutableMapOf<Category, Long>()
        
        // Categorize each domain and sum weights
        domainWeights.forEach { (domain, weight) ->
            val category = categorizeDomain(domain)
            categoryWeights[category] = (categoryWeights[category] ?: 0L) + weight
        }
        
        // Calculate percentages
        val total = categoryWeights.values.sum().toFloat()
        if (total > 0) {
            val distribution = categoryWeights.mapValues { (_, weight) ->
                weight.toFloat() / total
            }
            _distribution.emit(distribution)
        }
    }
    
    /**
     * Categorize a domain based on patterns
     */
    private fun categorizeDomain(domain: String): Category {
        val lowerDomain = domain.lowercase()
        
        for ((category, patterns) in categoryPatterns) {
            if (patterns.any { pattern -> lowerDomain.contains(pattern) }) {
                return category
            }
        }
        
        return Category.Other
    }
    
    /**
     * Record traffic for a domain
     */
    fun recordDomainTraffic(domain: String, bytes: Long) {
        domainWeights[domain] = (domainWeights[domain] ?: 0L) + bytes
        
        // Cleanup old domains with very low weights (< 1% of max)
        val maxWeight = domainWeights.values.maxOrNull() ?: 0L
        if (maxWeight > 10000) { // Only cleanup if we have significant traffic
            val threshold = maxWeight / 100
            domainWeights.entries.removeIf { it.value < threshold }
        }
    }
    
    /**
     * Record traffic for an IP (will be resolved to domain if possible)
     */
    suspend fun recordIpTraffic(ip: String, bytes: Long) {
        val domain = ipDomainMapper?.getDomain(ip) ?: ip
        recordDomainTraffic(domain, bytes)
    }
    
    /**
     * Clear all traffic data
     */
    fun clearTraffic() {
        domainWeights.clear()
        scope.launch {
            _distribution.emit(emptyMap())
        }
    }
}

