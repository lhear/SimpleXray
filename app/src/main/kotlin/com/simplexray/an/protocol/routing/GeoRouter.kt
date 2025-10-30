package com.simplexray.an.protocol.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Geographic routing and server selection system
 */
class GeoRouter {

    /**
     * Server location information
     */
    data class ServerLocation(
        val id: String,
        val name: String,
        val host: String,
        val port: Int,
        val latitude: Double,
        val longitude: Double,
        val country: String,
        val city: String,
        val continent: Continent,
        val provider: String? = null,
        val capacity: ServerCapacity = ServerCapacity.MEDIUM,
        val tags: List<String> = emptyList()
    )

    /**
     * Server capacity classification
     */
    enum class ServerCapacity(val weight: Float) {
        LOW(0.5f),
        MEDIUM(1.0f),
        HIGH(1.5f),
        PREMIUM(2.0f)
    }

    /**
     * Continent classification
     */
    enum class Continent {
        ASIA,
        EUROPE,
        NORTH_AMERICA,
        SOUTH_AMERICA,
        AFRICA,
        OCEANIA,
        ANTARCTICA
    }

    /**
     * User location
     */
    data class UserLocation(
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val city: String? = null
    )

    /**
     * Server selection result
     */
    data class ServerSelectionResult(
        val server: ServerLocation,
        val score: Float,
        val distance: Double, // km
        val estimatedLatency: Int, // ms
        val reason: String
    )

    /**
     * Server with measured performance
     */
    data class ServerWithMetrics(
        val server: ServerLocation,
        val latency: Int,
        val jitter: Int,
        val packetLoss: Float,
        val bandwidth: Long,
        val load: Float, // 0.0-1.0
        val reliability: Float // 0.0-1.0 based on history
    )

    /**
     * Calculate distance between two coordinates (Haversine formula)
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val R = 6371.0 // Earth radius in km

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    /**
     * Estimate latency based on distance
     * Rough estimate: ~1ms per 100km + base latency
     */
    fun estimateLatency(distanceKm: Double): Int {
        val baseLatency = 10 // Base network latency
        val distanceLatency = (distanceKm / 100).toInt()
        return baseLatency + distanceLatency
    }

    /**
     * Find nearest server to user location
     */
    fun findNearestServer(
        userLocation: UserLocation,
        servers: List<ServerLocation>
    ): ServerLocation? {
        return servers.minByOrNull { server ->
            calculateDistance(
                userLocation.latitude,
                userLocation.longitude,
                server.latitude,
                server.longitude
            )
        }
    }

    /**
     * Select optimal server based on multiple factors
     */
    suspend fun selectOptimalServer(
        userLocation: UserLocation,
        servers: List<ServerLocation>,
        preferences: RoutingPreferences = RoutingPreferences()
    ): ServerSelectionResult? = withContext(Dispatchers.Default) {
        if (servers.isEmpty()) return@withContext null

        val scoredServers = servers.map { server ->
            val distance = calculateDistance(
                userLocation.latitude,
                userLocation.longitude,
                server.latitude,
                server.longitude
            )

            val estimatedLatency = estimateLatency(distance)

            // Calculate score (0-100, higher is better)
            var score = 100f

            // Distance penalty (up to -40 points)
            score -= when {
                distance < 500 -> 0f
                distance < 1000 -> 5f
                distance < 2000 -> 10f
                distance < 5000 -> 20f
                distance < 10000 -> 30f
                else -> 40f
            } * preferences.distanceWeight

            // Capacity bonus (up to +20 points)
            score += (server.capacity.weight - 1.0f) * 20f * preferences.capacityWeight

            // Same continent bonus (+10 points)
            if (preferences.continentAffinity && server.country == userLocation.country) {
                score += 15f
            } else if (preferences.continentAffinity) {
                // Detect continent from country - simplified
                score += 5f
            }

            ServerSelectionResult(
                server = server,
                score = score,
                distance = distance,
                estimatedLatency = estimatedLatency,
                reason = buildReason(distance, server.capacity, estimatedLatency)
            )
        }

        scoredServers.maxByOrNull { it.score }
    }

    /**
     * Select best server based on actual measured latency
     */
    suspend fun selectByLatency(
        servers: List<ServerWithMetrics>,
        preferences: RoutingPreferences = RoutingPreferences()
    ): ServerWithMetrics? {
        if (servers.isEmpty()) return null

        return servers.minByOrNull { serverMetric ->
            // Weighted score (lower is better)
            val latencyScore = serverMetric.latency * preferences.latencyWeight
            val jitterScore = serverMetric.jitter * preferences.jitterWeight
            val lossScore = serverMetric.packetLoss * 100 * preferences.packetLossWeight
            val loadPenalty = serverMetric.load * 100 * preferences.loadWeight

            latencyScore + jitterScore + lossScore + loadPenalty
        }
    }

    /**
     * Load balance across multiple servers
     */
    fun loadBalance(
        servers: List<ServerWithMetrics>,
        algorithm: LoadBalanceAlgorithm = LoadBalanceAlgorithm.LEAST_LOADED
    ): ServerWithMetrics? {
        if (servers.isEmpty()) return null

        return when (algorithm) {
            LoadBalanceAlgorithm.ROUND_ROBIN -> {
                // Simple round-robin (would need state management)
                servers.firstOrNull()
            }
            LoadBalanceAlgorithm.LEAST_LOADED -> {
                servers.minByOrNull { it.load }
            }
            LoadBalanceAlgorithm.WEIGHTED_ROUND_ROBIN -> {
                // Weight by capacity
                servers.maxByOrNull { it.server.capacity.weight / (it.load + 0.1f) }
            }
            LoadBalanceAlgorithm.LEAST_LATENCY -> {
                servers.minByOrNull { it.latency }
            }
        }
    }

    /**
     * Build selection reason string
     */
    private fun buildReason(distance: Double, capacity: ServerCapacity, latency: Int): String {
        return buildString {
            append("Distance: ${distance.toInt()}km")
            append(", Est. Latency: ${latency}ms")
            append(", Capacity: ${capacity.name}")
        }
    }

    /**
     * Group servers by continent
     */
    fun groupByContinent(servers: List<ServerLocation>): Map<Continent, List<ServerLocation>> {
        return servers.groupBy { it.continent }
    }

    /**
     * Filter servers by criteria
     */
    fun filterServers(
        servers: List<ServerLocation>,
        maxDistance: Double? = null,
        continent: Continent? = null,
        country: String? = null,
        minCapacity: ServerCapacity? = null,
        tags: List<String>? = null
    ): List<ServerLocation> {
        return servers.filter { server ->
            (continent == null || server.continent == continent) &&
            (country == null || server.country == country) &&
            (minCapacity == null || server.capacity.weight >= minCapacity.weight) &&
            (tags == null || tags.any { it in server.tags })
        }
    }
}

/**
 * Routing preferences for server selection
 */
data class RoutingPreferences(
    val distanceWeight: Float = 1.0f,
    val latencyWeight: Float = 1.0f,
    val jitterWeight: Float = 0.5f,
    val packetLossWeight: Float = 2.0f,
    val loadWeight: Float = 0.7f,
    val capacityWeight: Float = 1.0f,
    val continentAffinity: Boolean = true
)

/**
 * Load balancing algorithms
 */
enum class LoadBalanceAlgorithm {
    ROUND_ROBIN,
    LEAST_LOADED,
    WEIGHTED_ROUND_ROBIN,
    LEAST_LATENCY
}

/**
 * CDN provider configuration
 */
data class CdnProvider(
    val name: String,
    val servers: List<GeoRouter.ServerLocation>,
    val priority: Int = 0,
    val enabled: Boolean = true
)

/**
 * Multi-CDN manager
 */
class MultiCdnManager {
    private val providers = mutableListOf<CdnProvider>()
    private val router = GeoRouter()

    fun addProvider(provider: CdnProvider) {
        providers.add(provider)
        providers.sortByDescending { it.priority }
    }

    fun removeProvider(name: String) {
        providers.removeAll { it.name == name }
    }

    /**
     * Select best server across all CDN providers
     */
    suspend fun selectOptimalServer(
        userLocation: GeoRouter.UserLocation,
        preferences: RoutingPreferences = RoutingPreferences()
    ): GeoRouter.ServerSelectionResult? {
        val allServers = providers
            .filter { it.enabled }
            .flatMap { it.servers }

        return router.selectOptimalServer(userLocation, allServers, preferences)
    }

    /**
     * Get all enabled servers
     */
    fun getAllServers(): List<GeoRouter.ServerLocation> {
        return providers
            .filter { it.enabled }
            .flatMap { it.servers }
    }
}
