package com.simplexray.an.ui.georouting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.protocol.routing.*
import com.simplexray.an.viewmodel.GeoRoutingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoRoutingScreen(
    onBackClick: () -> Unit = {},
    viewModel: GeoRoutingViewModel = viewModel()
) {
    val servers by viewModel.servers.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val selectionResult by viewModel.selectionResult.collectAsState()
    val selectedContinent by viewModel.selectedContinent.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showServerDetails by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geo-Routing") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.selectOptimalServer() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Auto Select"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Servers") },
                    icon = { Icon(Icons.Default.Place, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Map") },
                    icon = { Icon(Icons.Default.LocationOn, null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, null) }
                )
            }

            when (selectedTab) {
                0 -> ServersTab(
                    servers = viewModel.getFilteredServers(),
                    selectedServer = selectedServer,
                    userLocation = userLocation,
                    selectedContinent = selectedContinent,
                    onServerClick = {
                        viewModel.selectServer(it)
                        showServerDetails = true
                    },
                    onContinentFilter = { viewModel.filterByContinent(it) },
                    calculateDistance = { viewModel.calculateDistance(it) },
                    estimateLatency = { viewModel.estimateLatency(it) }
                )
                1 -> MapTab(
                    servers = servers,
                    selectedServer = selectedServer,
                    userLocation = userLocation,
                    selectionResult = selectionResult,
                    groupedServers = viewModel.groupServersByContinent()
                )
                2 -> SettingsTab(
                    userLocation = userLocation,
                    onLocationUpdate = { lat, lon, country, city ->
                        viewModel.updateUserLocation(lat, lon, country, city)
                    },
                    loadBalanceAlgorithm = viewModel.loadBalanceAlgorithm.collectAsState().value,
                    onAlgorithmChange = { viewModel.setLoadBalanceAlgorithm(it) }
                )
            }
        }

        // Server Details Dialog
        if (showServerDetails && selectedServer != null && selectionResult != null) {
            ServerDetailsDialog(
                server = selectedServer!!,
                result = selectionResult!!,
                onDismiss = { showServerDetails = false }
            )
        }
    }
}

@Composable
private fun ServersTab(
    servers: List<GeoRouter.ServerLocation>,
    selectedServer: GeoRouter.ServerLocation?,
    userLocation: GeoRouter.UserLocation?,
    selectedContinent: GeoRouter.Continent?,
    onServerClick: (GeoRouter.ServerLocation) -> Unit,
    onContinentFilter: (GeoRouter.Continent?) -> Unit,
    calculateDistance: (GeoRouter.ServerLocation) -> Double?,
    estimateLatency: (GeoRouter.ServerLocation) -> Int?
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Continent Filter
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedContinent == null,
                    onClick = { onContinentFilter(null) },
                    label = { Text("All") }
                )
            }
            items(GeoRouter.Continent.entries) { continent ->
                FilterChip(
                    selected = selectedContinent == continent,
                    onClick = { onContinentFilter(continent) },
                    label = { Text(continent.name.replace("_", " ")) }
                )
            }
        }

        // Server List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (userLocation != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Your Location",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "${userLocation.city}, ${userLocation.country}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            items(servers) { server ->
                ServerCard(
                    server = server,
                    isSelected = server == selectedServer,
                    distance = calculateDistance(server),
                    estimatedLatency = estimateLatency(server),
                    onClick = { onServerClick(server) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerCard(
    server: GeoRouter.ServerLocation,
    isSelected: Boolean,
    distance: Double?,
    estimatedLatency: Int?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (server.capacity) {
                                    GeoRouter.ServerCapacity.PREMIUM -> MaterialTheme.colorScheme.primary
                                    GeoRouter.ServerCapacity.HIGH -> MaterialTheme.colorScheme.tertiary
                                    GeoRouter.ServerCapacity.MEDIUM -> MaterialTheme.colorScheme.secondary
                                    GeoRouter.ServerCapacity.LOW -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        server.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${server.city}, ${server.country}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    if (distance != null) {
                        Text(
                            "${distance.toInt()} km",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (estimatedLatency != null) {
                            Text(
                                " • ${estimatedLatency}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (server.provider != null) {
                    Text(
                        server.provider,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    server.capacity.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapTab(
    servers: List<GeoRouter.ServerLocation>,
    selectedServer: GeoRouter.ServerLocation?,
    userLocation: GeoRouter.UserLocation?,
    selectionResult: GeoRouter.ServerSelectionResult?,
    groupedServers: Map<GeoRouter.Continent, List<GeoRouter.ServerLocation>>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "World Map View",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Total Servers: ${servers.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (selectedServer != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Selected: ${selectedServer.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (selectionResult != null) {
                            Text(
                                "Distance: ${selectionResult.distance.toInt()} km",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Est. Latency: ${selectionResult.estimatedLatency}ms",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Servers by Continent",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(groupedServers.entries.toList()) { (continent, continentServers) ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            continent.name.replace("_", " "),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "${continentServers.size} servers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    continentServers.forEach { server ->
                        Text(
                            "• ${server.name} (${server.city})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    userLocation: GeoRouter.UserLocation?,
    onLocationUpdate: (Double, Double, String?, String?) -> Unit,
    loadBalanceAlgorithm: LoadBalanceAlgorithm,
    onAlgorithmChange: (LoadBalanceAlgorithm) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "User Location",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (userLocation != null) {
                        InfoRow("Latitude", "%.4f".format(userLocation.latitude))
                        InfoRow("Longitude", "%.4f".format(userLocation.longitude))
                        InfoRow("Country", userLocation.country ?: "Unknown")
                        InfoRow("City", userLocation.city ?: "Unknown")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { /* TODO: Implement location picker */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocationOn, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update Location")
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Load Balance Algorithm",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LoadBalanceAlgorithm.entries.forEach { algorithm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = algorithm == loadBalanceAlgorithm,
                                onClick = { onAlgorithmChange(algorithm) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    algorithm.name.replace("_", " "),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    getAlgorithmDescription(algorithm),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Routing Preferences",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    InfoRow("Distance Weight", "1.0")
                    InfoRow("Latency Weight", "1.0")
                    InfoRow("Packet Loss Weight", "2.0")
                    InfoRow("Capacity Weight", "1.0")
                    InfoRow("Continent Affinity", "Enabled")
                }
            }
        }
    }
}

@Composable
private fun ServerDetailsDialog(
    server: GeoRouter.ServerLocation,
    result: GeoRouter.ServerSelectionResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(server.name) },
        text = {
            Column {
                InfoRow("Location", "${server.city}, ${server.country}")
                InfoRow("Continent", server.continent.name.replace("_", " "))
                InfoRow("Host", server.host)
                InfoRow("Port", server.port.toString())
                InfoRow("Capacity", server.capacity.name)
                if (server.provider != null) {
                    InfoRow("Provider", server.provider)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Selection Metrics",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                InfoRow("Score", "%.1f".format(result.score))
                InfoRow("Distance", "${result.distance.toInt()} km")
                InfoRow("Est. Latency", "${result.estimatedLatency}ms")
                InfoRow("Coordinates", "%.4f, %.4f".format(server.latitude, server.longitude))

                if (server.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tags: ${server.tags.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun getAlgorithmDescription(algorithm: LoadBalanceAlgorithm): String {
    return when (algorithm) {
        LoadBalanceAlgorithm.ROUND_ROBIN -> "Distribute requests evenly"
        LoadBalanceAlgorithm.LEAST_LOADED -> "Select server with lowest load"
        LoadBalanceAlgorithm.WEIGHTED_ROUND_ROBIN -> "Weighted by capacity"
        LoadBalanceAlgorithm.LEAST_LATENCY -> "Select fastest server"
    }
}
