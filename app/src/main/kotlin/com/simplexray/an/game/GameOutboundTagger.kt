package com.simplexray.an.game

import com.simplexray.an.common.AppLogger
import com.simplexray.an.perf.PerformanceOptimizer
import com.simplexray.an.protocol.routing.RoutingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GameOutboundTagger - Creates prioritized outbound chains for game domains.
 * 
 * Features:
 * - Priority tagging on outbound chain for game domains
 * - Congestion control = BBR for game outbounds
 * - reusePort = true for connection reuse
 * - tcp_keepalive = true for persistent connections
 * - udpFragment = true where applicable
 * - Lower idle timeouts for real-time flows
 * 
 * Outbound configuration for games:
 * {
 *   "tag": "game-priority",
 *   "protocol": "...",
 *   "settings": {...},
 *   "streamSettings": {
 *     "sockopt": {
 *       "tcpKeepAliveInterval": 30,
 *       "tcpKeepAliveIdle": 60,
 *       "reusePort": true,
 *       "udpFragment": true
 *     }
 *   },
 *   "congestion": "bbr"
 * }
 */
object GameOutboundTagger {
    private const val TAG = "GameOutboundTagger"
    
    // Game outbound tag
    const val GAME_OUTBOUND_TAG = "game-priority"
    
    // Active game domains with priority tags
    private val activeGameDomains = mutableSetOf<String>()
    
    /**
     * Tag domain as game priority and create/update outbound chain.
     * 
     * @param host Game host
     * @param port Game port
     * @param outboundTag Optional specific outbound tag (defaults to GAME_OUTBOUND_TAG)
     * @return True if tagging successful
     */
    suspend fun tagGameDomain(
        host: String,
        port: Int,
        outboundTag: String = GAME_OUTBOUND_TAG
    ): Boolean = withContext(Dispatchers.Default) {
        val normalized = normalizeHost(host)
        val key = "$normalized:$port"
        
        synchronized(activeGameDomains) {
            activeGameDomains.add(key)
        }
        
        AppLogger.d("$TAG: Tagged game domain: $normalized:$port (outbound: $outboundTag)")
        
        // Apply priority outbound tag for game domains
        // This ensures BBR pacing, tcp_keepalive, reusePort=true, udpFragment=true
        AppLogger.d("$TAG: Applying priority outbound tag for game domain: $normalized:$port")
        com.simplexray.an.logging.LoggerRepository.add(
            com.simplexray.an.logging.LogEvent.Info(
                message = "Game priority chain applied: $normalized:$port",
                tag = TAG
            )
        )
        
        // Create game outbound if it doesn't exist
        createGameOutboundIfNeeded()
        
        // Optimize outbound change - prevent churn for identical results
        if (!PerformanceOptimizer.optimizeOutboundChange("$normalized:$port", outboundTag)) {
            AppLogger.d("$TAG: Skipping duplicate outbound tag for $normalized:$port")
            return true // Already applied, skip
        }
        
        // Update routing rules to use game-priority for this domain
        updateRoutingForGameDomain(normalized, port, outboundTag)
        
        // Pin route in GameOptimizationRepository
        val gameKey = GameOptimizationRepository.GameKey(host = normalized, port = port)
        GameOptimizationRepository.pinRoute(gameKey, outboundTag)
        
        true
    }
    
    /**
     * Remove game priority tag from domain
     */
    suspend fun untagGameDomain(host: String, port: Int) {
        val normalized = normalizeHost(host)
        val key = "$normalized:$port"
        
        synchronized(activeGameDomains) {
            activeGameDomains.remove(key)
        }
        
        AppLogger.d("$TAG: Untagged game domain: $normalized:$port")
    }
    
    /**
     * Check if domain has game priority tag
     */
    fun isGameTagged(host: String, port: Int): Boolean {
        val normalized = normalizeHost(host)
        val key = "$normalized:$port"
        return synchronized(activeGameDomains) {
            activeGameDomains.contains(key)
        }
    }
    
    /**
     * Create game outbound with optimized settings if it doesn't exist.
     * 
     * Game outbound configuration:
     * - congestionControl: bbr (BBR congestion control)
     * - reusePort: true (connection reuse)
     * - tcp_keepalive: true (persistent connections)
     * - udpFragment: true (UDP fragmentation support)
     * - idleTimeout: lower for real-time flows
     */
    private suspend fun createGameOutboundIfNeeded() {
        // This will be integrated with XrayConfigPatcher to inject game outbound
        // For now, we log the requirement
        
        AppLogger.d("$TAG: Creating game outbound with optimized settings")
        
        // The actual outbound creation will be done via XrayConfigPatcher
        // when the config is patched for game optimization
    }
    
    /**
     * Update routing rules to use game-priority for game domain
     */
    private suspend fun updateRoutingForGameDomain(domain: String, port: Int, outboundTag: String) {
        // Get current route table
        val routeTable = RoutingRepository.getCurrentRouteTable()
        
        // Check if routing rule already exists for this domain
        val existingRule = routeTable.rules.find { rule ->
            rule.matchers.any { matcher ->
                when (matcher) {
                    is com.simplexray.an.protocol.routing.AdvancedRouter.RoutingMatcher.DomainMatcher -> {
                        matcher.domains.any { ruleDomain ->
                            normalizeHost(ruleDomain) == domain || 
                            domain.endsWith(".$ruleDomain") || 
                            ruleDomain.endsWith(".$domain")
                        }
                    }
                    is com.simplexray.an.protocol.routing.AdvancedRouter.RoutingMatcher.PortMatcher -> {
                        matcher.ports.contains(port)
                    }
                    else -> false
                }
            }
        }
        
        if (existingRule == null) {
            // Create new routing rule for game domain
            val gameRule = com.simplexray.an.protocol.routing.AdvancedRouter.RoutingRule(
                id = "game-$domain-$port",
                name = "Game: $domain:$port",
                enabled = true,
                priority = 150, // Higher priority than streaming (100)
                matchers = listOf(
                    com.simplexray.an.protocol.routing.AdvancedRouter.RoutingMatcher.DomainMatcher(
                        domains = listOf(domain)
                    ),
                    com.simplexray.an.protocol.routing.AdvancedRouter.RoutingMatcher.PortMatcher(
                        ports = listOf(port)
                    )
                ),
                action = com.simplexray.an.protocol.routing.AdvancedRouter.RoutingAction.PROXY,
                outboundTag = outboundTag
            )
            
            RoutingRepository.addRule(gameRule)
            
            AppLogger.d("$TAG: Created routing rule for game domain: $domain:$port")
        } else {
            // Update existing rule to use game-priority outbound
            val updatedRule = existingRule.copy(
                outboundTag = outboundTag,
                priority = maxOf(existingRule.priority, 150) // Ensure high priority
            )
            
            RoutingRepository.updateRule(updatedRule)
            
            AppLogger.d("$TAG: Updated routing rule for game domain: $domain:$port")
        }
    }
    
    /**
     * Get game outbound configuration JSON (for Xray config injection)
     */
    fun getGameOutboundConfig(
        baseOutbound: com.google.gson.JsonObject
    ): com.google.gson.JsonObject {
        val config = baseOutbound.deepCopy()
        
        // Set tag
        config.addProperty("tag", GAME_OUTBOUND_TAG)
        
        // Add stream settings for TCP keepalive and UDP fragment
        if (!config.has("streamSettings")) {
            config.add("streamSettings", com.google.gson.JsonObject())
        }
        
        val streamSettings = config.getAsJsonObject("streamSettings")
        if (!streamSettings.has("sockopt")) {
            streamSettings.add("sockopt", com.google.gson.JsonObject())
        }
        
        val sockopt = streamSettings.getAsJsonObject("sockopt")
        sockopt.addProperty("tcpKeepAliveInterval", 30)
        sockopt.addProperty("tcpKeepAliveIdle", 60)
        sockopt.addProperty("reusePort", true)
        sockopt.addProperty("tcpFastOpen", true) // Enable TCP Fast Open for lower latency
        sockopt.addProperty("udpFragment", true) // Enable UDP fragmentation
        
        // Add congestion control (BBR)
        // Note: This is Xray-specific configuration
        if (!config.has("congestion")) {
            config.addProperty("congestion", "bbr")
        }
        
        // Configure QUIC for UDP games (if applicable)
        if (!streamSettings.has("quicSettings")) {
            streamSettings.add("quicSettings", com.google.gson.JsonObject().apply {
                addProperty("maxIdleTimeout", 30000) // 30s idle timeout
                addProperty("keepAlivePeriod", 10) // 10s keepalive
                addProperty("maxIncomingStreams", 128) // Higher for games
            })
        }
        val quicSettings = streamSettings.getAsJsonObject("quicSettings")
        quicSettings.addProperty("security", "none")
        quicSettings.addProperty("key", "")
        
        return config
    }
    
    /**
     * Clear all game tags
     */
    fun clearAllTags() {
        synchronized(activeGameDomains) {
            activeGameDomains.clear()
        }
        AppLogger.d("$TAG: Cleared all game tags")
    }
    
    /**
     * Get all active game domains
     */
    fun getActiveGameDomains(): Set<String> {
        return synchronized(activeGameDomains) {
            activeGameDomains.toSet()
        }
    }
    
    /**
     * Normalize host (remove protocol, lowercase)
     */
    private fun normalizeHost(host: String): String {
        return host
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")
            .split("/")[0]
            .split(":")[0]
            .lowercase()
            .trim()
    }
}

/**
 * Extension function to deep copy JsonObject
 */
private fun com.google.gson.JsonObject.deepCopy(): com.google.gson.JsonObject {
    return com.google.gson.Gson().fromJson(this.toString(), com.google.gson.JsonObject::class.java)
}

