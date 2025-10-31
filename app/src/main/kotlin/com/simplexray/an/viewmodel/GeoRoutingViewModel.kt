package com.simplexray.an.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.protocol.routing.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Geo-Routing
 */
class GeoRoutingViewModel : ViewModel() {

    private val router = GeoRouter()
    private val multiCdnManager = MultiCdnManager()

    private val _servers = MutableStateFlow<List<GeoRouter.ServerLocation>>(emptyList())
    val servers: StateFlow<List<GeoRouter.ServerLocation>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow<GeoRouter.ServerLocation?>(null)
    val selectedServer: StateFlow<GeoRouter.ServerLocation?> = _selectedServer.asStateFlow()

    private val _userLocation = MutableStateFlow<GeoRouter.UserLocation?>(null)
    val userLocation: StateFlow<GeoRouter.UserLocation?> = _userLocation.asStateFlow()

    private val _selectionResult = MutableStateFlow<GeoRouter.ServerSelectionResult?>(null)
    val selectionResult: StateFlow<GeoRouter.ServerSelectionResult?> = _selectionResult.asStateFlow()

    private val _preferences = MutableStateFlow(RoutingPreferences())
    val preferences: StateFlow<RoutingPreferences> = _preferences.asStateFlow()

    private val _loadBalanceAlgorithm = MutableStateFlow(LoadBalanceAlgorithm.LEAST_LOADED)
    val loadBalanceAlgorithm: StateFlow<LoadBalanceAlgorithm> = _loadBalanceAlgorithm.asStateFlow()

    private val _selectedContinent = MutableStateFlow<GeoRouter.Continent?>(null)
    val selectedContinent: StateFlow<GeoRouter.Continent?> = _selectedContinent.asStateFlow()

    init {
        loadSampleServers()
    }

    private fun loadSampleServers() {
        viewModelScope.launch {
            val sampleServers = listOf(
                GeoRouter.ServerLocation(
                    id = "us-ny-1",
                    name = "New York",
                    host = "ny.example.com",
                    port = 443,
                    latitude = 40.7128,
                    longitude = -74.0060,
                    country = "United States",
                    city = "New York",
                    continent = GeoRouter.Continent.NORTH_AMERICA,
                    provider = "CloudFlare",
                    capacity = GeoRouter.ServerCapacity.PREMIUM,
                    tags = listOf("low-latency", "gaming")
                ),
                GeoRouter.ServerLocation(
                    id = "eu-lon-1",
                    name = "London",
                    host = "lon.example.com",
                    port = 443,
                    latitude = 51.5074,
                    longitude = -0.1278,
                    country = "United Kingdom",
                    city = "London",
                    continent = GeoRouter.Continent.EUROPE,
                    provider = "AWS",
                    capacity = GeoRouter.ServerCapacity.HIGH,
                    tags = listOf("streaming")
                ),
                GeoRouter.ServerLocation(
                    id = "as-tok-1",
                    name = "Tokyo",
                    host = "tok.example.com",
                    port = 443,
                    latitude = 35.6762,
                    longitude = 139.6503,
                    country = "Japan",
                    city = "Tokyo",
                    continent = GeoRouter.Continent.ASIA,
                    provider = "Google Cloud",
                    capacity = GeoRouter.ServerCapacity.HIGH,
                    tags = listOf("low-latency", "gaming")
                ),
                GeoRouter.ServerLocation(
                    id = "eu-fra-1",
                    name = "Frankfurt",
                    host = "fra.example.com",
                    port = 443,
                    latitude = 50.1109,
                    longitude = 8.6821,
                    country = "Germany",
                    city = "Frankfurt",
                    continent = GeoRouter.Continent.EUROPE,
                    provider = "Hetzner",
                    capacity = GeoRouter.ServerCapacity.PREMIUM,
                    tags = listOf("streaming", "low-latency")
                ),
                GeoRouter.ServerLocation(
                    id = "as-sin-1",
                    name = "Singapore",
                    host = "sin.example.com",
                    port = 443,
                    latitude = 1.3521,
                    longitude = 103.8198,
                    country = "Singapore",
                    city = "Singapore",
                    continent = GeoRouter.Continent.ASIA,
                    provider = "DigitalOcean",
                    capacity = GeoRouter.ServerCapacity.MEDIUM,
                    tags = listOf("streaming")
                ),
                GeoRouter.ServerLocation(
                    id = "na-sf-1",
                    name = "San Francisco",
                    host = "sf.example.com",
                    port = 443,
                    latitude = 37.7749,
                    longitude = -122.4194,
                    country = "United States",
                    city = "San Francisco",
                    continent = GeoRouter.Continent.NORTH_AMERICA,
                    provider = "Linode",
                    capacity = GeoRouter.ServerCapacity.HIGH,
                    tags = listOf("gaming", "streaming")
                ),
                GeoRouter.ServerLocation(
                    id = "oc-syd-1",
                    name = "Sydney",
                    host = "syd.example.com",
                    port = 443,
                    latitude = -33.8688,
                    longitude = 151.2093,
                    country = "Australia",
                    city = "Sydney",
                    continent = GeoRouter.Continent.OCEANIA,
                    provider = "Vultr",
                    capacity = GeoRouter.ServerCapacity.MEDIUM,
                    tags = listOf("streaming")
                )
            )

            _servers.value = sampleServers

            // Set default user location (Istanbul, Turkey)
            _userLocation.value = GeoRouter.UserLocation(
                latitude = 41.0082,
                longitude = 28.9784,
                country = "Turkey",
                city = "Istanbul"
            )
        }
    }

    fun selectOptimalServer() {
        viewModelScope.launch {
            val location = _userLocation.value ?: return@launch
            val result = router.selectOptimalServer(
                location,
                _servers.value,
                _preferences.value
            )
            _selectionResult.value = result
            _selectedServer.value = result?.server
        }
    }

    fun selectServer(server: GeoRouter.ServerLocation) {
        _selectedServer.value = server

        val location = _userLocation.value
        if (location != null) {
            val distance = router.calculateDistance(
                location.latitude,
                location.longitude,
                server.latitude,
                server.longitude
            )
            val estimatedLatency = router.estimateLatency(distance)

            _selectionResult.value = GeoRouter.ServerSelectionResult(
                server = server,
                score = 100f,
                distance = distance,
                estimatedLatency = estimatedLatency,
                reason = "Manual selection"
            )
        }
    }

    fun filterByContinent(continent: GeoRouter.Continent?) {
        _selectedContinent.value = continent
    }

    fun getFilteredServers(): List<GeoRouter.ServerLocation> {
        val continent = _selectedContinent.value
        return if (continent != null) {
            router.filterServers(_servers.value, continent = continent)
        } else {
            _servers.value
        }
    }

    fun groupServersByContinent(): Map<GeoRouter.Continent, List<GeoRouter.ServerLocation>> {
        return router.groupByContinent(_servers.value)
    }

    fun updateUserLocation(latitude: Double, longitude: Double, country: String?, city: String?) {
        viewModelScope.launch {
            _userLocation.value = GeoRouter.UserLocation(
                latitude = latitude,
                longitude = longitude,
                country = country,
                city = city
            )
        }
    }

    fun updatePreferences(preferences: RoutingPreferences) {
        _preferences.value = preferences
    }

    fun setLoadBalanceAlgorithm(algorithm: LoadBalanceAlgorithm) {
        _loadBalanceAlgorithm.value = algorithm
    }

    fun calculateDistance(server: GeoRouter.ServerLocation): Double? {
        val location = _userLocation.value ?: return null
        return router.calculateDistance(
            location.latitude,
            location.longitude,
            server.latitude,
            server.longitude
        )
    }

    fun estimateLatency(server: GeoRouter.ServerLocation): Int? {
        val distance = calculateDistance(server) ?: return null
        return router.estimateLatency(distance)
    }

    fun getAllContinents(): List<GeoRouter.Continent> = GeoRouter.Continent.entries

    fun getServerCount(): Int = _servers.value.size

    fun getServersByCapacity(capacity: GeoRouter.ServerCapacity): List<GeoRouter.ServerLocation> {
        return _servers.value.filter { it.capacity == capacity }
    }
}
