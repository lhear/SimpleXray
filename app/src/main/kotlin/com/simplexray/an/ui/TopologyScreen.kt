package com.simplexray.an.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.simplexray.an.domain.DomainClassifier
import com.simplexray.an.topology.Edge
import com.simplexray.an.topology.Node
import com.simplexray.an.topology.TopologyViewModel
import com.simplexray.an.ui.components.NetworkGraphCanvas
import com.simplexray.an.asn.AsnLookup
import com.simplexray.an.asn.AsnCdnMap
import com.simplexray.an.perf.PerformanceOptimizer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.simplexray.an.ui.components.Legend
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    onBackClick: () -> Unit = {},
    vm: TopologyViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val pair = vm.graph.collectAsState().value
    
    // Recomposition guard - track hash for diagnostics
    // Note: Actual snapshot deduplication happens in TopologyRepository.emitGraph()
    // This tracks UI-level recomposition patterns for diagnostics
    val graphHash = remember(pair) {
        pair.first.map { it.id }.sorted().joinToString(",") + 
        "|" + pair.second.map { "${it.from}->${it.to}" }.sorted().joinToString(",")
    }
    
    // Track for diagnostics (Compose handles actual recomposition automatically)
    LaunchedEffect(graphHash) {
        PerformanceOptimizer.shouldRecompose("topology:$graphHash")
    }
    
    val nodes = pair.first
    val edges = pair.second
    val ctx = LocalContext.current
    val classifier = remember(ctx) { DomainClassifier(ctx) }
    val asn = remember(ctx) { AsnLookup(ctx) }
    
    // Persistent selection: load from store and restore when nodes are available
    val savedSelectedId = remember(ctx) { 
        com.simplexray.an.topology.TopologySelectionStore.loadSelectedNodeId(ctx) 
    }
    var selected by remember { 
        mutableStateOf<Node?>(
            savedSelectedId?.let { id -> nodes.firstOrNull { it.id == id } }
        )
    }
    
    // Restore selection when nodes change
    androidx.compose.runtime.LaunchedEffect(nodes) {
        savedSelectedId?.let { id ->
            nodes.firstOrNull { it.id == id }?.let { node ->
                selected = node
            }
        }
    }
    
    var showLabels by remember { mutableStateOf(com.simplexray.an.topology.TopologySettingsStore.loadShowLabels(ctx)) }
    var resetKey by remember { mutableStateOf(0) }
    var viewResetKey by remember { mutableStateOf(0) }
    var fitToGraphKey by remember { mutableStateOf(0) }
    
    // Ultimate features: Search, filters, stats
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var filterDomainOnly by remember { mutableStateOf(false) }
    var filterIPOnly by remember { mutableStateOf(false) }
    var filterCdnOnly by remember { mutableStateOf(false) }
    
    // Filtered nodes and edges
    val filteredNodes = remember(nodes, searchQuery, filterDomainOnly, filterIPOnly, filterCdnOnly) {
        nodes.filter { node ->
            val matchesSearch = searchQuery.isBlank() || 
                node.label.contains(searchQuery, ignoreCase = true) ||
                node.id.contains(searchQuery, ignoreCase = true)
            
            val matchesTypeFilter = when {
                filterDomainOnly -> node.type == Node.Type.Domain
                filterIPOnly -> node.type == Node.Type.IP
                else -> true
            }
            
            val matchesCdnFilter = if (filterCdnOnly) {
                when (node.type) {
                    Node.Type.Domain -> com.simplexray.an.domain.CdnPatterns(ctx).isCdn(node.label)
                    Node.Type.IP -> AsnCdnMap.isCdn(asn.lookup(node.label))
                }
            } else true
            
            matchesSearch && matchesTypeFilter && matchesCdnFilter
        }
    }
    
    val filteredEdges = remember(edges, filteredNodes) {
        val filteredIds = filteredNodes.map { it.id }.toSet()
        edges.filter { it.from in filteredIds && it.to in filteredIds }
    }
    
    // Statistics
    val stats = remember(nodes, edges, filteredNodes, filteredEdges) {
        val domainCount = filteredNodes.count { it.type == Node.Type.Domain }
        val ipCount = filteredNodes.count { it.type == Node.Type.IP }
        val totalWeight = filteredEdges.sumOf { it.weight.toDouble() }.toFloat()
        val maxWeight = filteredEdges.maxOfOrNull { it.weight } ?: 0f
        val avgWeight = if (filteredEdges.isNotEmpty()) totalWeight / filteredEdges.size else 0f
        val cdnCount = filteredNodes.count { node ->
            when (node.type) {
                Node.Type.Domain -> com.simplexray.an.domain.CdnPatterns(ctx).isCdn(node.label)
                Node.Type.IP -> AsnCdnMap.isCdn(asn.lookup(node.label))
            }
        }
        
        mapOf(
            "Total Nodes" to filteredNodes.size.toString(),
            "Domains" to domainCount.toString(),
            "IPs" to ipCount.toString(),
            "Total Edges" to filteredEdges.size.toString(),
            "Max Weight" to String.format("%.2f", maxWeight),
            "Avg Weight" to String.format("%.2f", avgWeight),
            "CDN Nodes" to cdnCount.toString()
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Topology") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showStats = !showStats }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Statistics",
                            tint = if (showStats) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = if (showFilters) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    }
                    IconButton(onClick = { 
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (searchQuery.isNotEmpty()) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        if (nodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("No topology data", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text(
                        if (com.simplexray.an.config.ApiConfig.isMock(ctx)) {
                            "Mock mode enabled - checking configuration..."
                        } else {
                            "Waiting for data...\nCheck Settings to configure Online IP Stat Name"
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }
        } else {
            // Search bar
            if (showSearchBar || searchQuery.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search nodes...") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    TextButton(onClick = { 
                                        searchQuery = ""
                                        showSearchBar = false
                                    }) {
                                        Text("Clear")
                                    }
                                } else {
                                    TextButton(onClick = { showSearchBar = false }) {
                                        Text("✕")
                                    }
                                }
                            }
                        )
                    }
                }
            }
            
            // Statistics panel
            if (showStats) {
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                        .width(200.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Statistics",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        stats.forEach { (key, value) ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    key,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    value,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${nodes.size} total / ${filteredNodes.size} shown",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            // Filters panel
            if (showFilters) {
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .width(220.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Filters",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Domains only")
                            Switch(checked = filterDomainOnly, onCheckedChange = { 
                                filterDomainOnly = it
                                if (it) filterIPOnly = false
                            })
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("IPs only")
                            Switch(checked = filterIPOnly, onCheckedChange = { 
                                filterIPOnly = it
                                if (it) filterDomainOnly = false
                            })
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CDN only")
                            Switch(checked = filterCdnOnly, onCheckedChange = { filterCdnOnly = it })
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                filterDomainOnly = false
                                filterIPOnly = false
                                filterCdnOnly = false
                                searchQuery = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset Filters")
                        }
                    }
                }
            }
            
            NetworkGraphCanvas(
                nodes = filteredNodes.ifEmpty { nodes }, // Show filtered or fallback to all
                edges = filteredEdges.ifEmpty { edges },
                modifier = Modifier.fillMaxSize(),
                showLabels = showLabels,
                cdnBadge = { n ->
                    when (n.type) {
                        Node.Type.Domain -> {
                            // Use CdnPatterns directly for synchronous check
                            val cdnPatterns = com.simplexray.an.domain.CdnPatterns(ctx)
                            cdnPatterns.isCdn(n.label)
                        }
                        Node.Type.IP -> AsnCdnMap.isCdn(asn.lookup(n.label))
                    }
                },
                onNodeClick = { n -> 
                    selected = n
                    // Persist selection
                    com.simplexray.an.topology.TopologySelectionStore.saveSelectedNodeId(ctx, n.id)
                },
                onBackgroundTap = { 
                    selected = null
                    // Clear persisted selection
                    com.simplexray.an.topology.TopologySelectionStore.clearSelection(ctx)
                },
                selectedNodeId = selected?.id,
                resetKey = resetKey,
                viewResetKey = viewResetKey,
                fitToGraphKey = fitToGraphKey,
                onFitToGraphRequest = { fitToGraphKey++ },
                highlightPaths = true // Ultimate: enable path highlighting
            )
            // Ultimate: Enhanced controls with zoom buttons
            Legend(modifier = Modifier.padding(12.dp))
            
            // Zoom controls (floating buttons)
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.BottomEnd),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { 
                        // Zoom in - handled by canvas transform gestures
                        // This is a visual indicator, actual zoom is via pinch
                    },
                    modifier = Modifier
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = { 
                        // Zoom out
                    },
                    modifier = Modifier
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = { 
                        fitToGraphKey++
                    },
                    modifier = Modifier
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Fit to Graph", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            }
            
            // Control buttons row
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.BottomStart),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = {
                        com.simplexray.an.topology.TopologyLayoutStore.clear(ctx)
                        resetKey++
                    },
                    modifier = Modifier
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) { 
                    Text("Reset layout", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { 
                        showLabels = !showLabels
                        com.simplexray.an.topology.TopologySettingsStore.saveShowLabels(ctx, showLabels)
                    },
                    modifier = Modifier
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) { 
                    Text(if (showLabels) "Hide labels" else "Show labels", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { viewResetKey++ },
                    modifier = Modifier
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) { 
                    Text("Reset view", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
            // Enhanced node details panel
            selected?.let { n ->
                val isCdn = n.type == Node.Type.Domain && com.simplexray.an.domain.CdnPatterns(ctx).isCdn(n.label)
                val asnInfo = if (n.type == Node.Type.IP) asn.lookup(n.label) else null
                val connectedEdges = edges.filter { it.from == n.id || it.to == n.id }
                val totalEdgeWeight = connectedEdges.sumOf { it.weight.toDouble() }.toFloat()
                val maxEdgeWeight = connectedEdges.maxOfOrNull { it.weight } ?: 0f
                
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.BottomStart)
                        .width(280.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${n.type}",
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (n.type) {
                                    Node.Type.Domain -> Color(0xFF42A5F5)
                                    Node.Type.IP -> Color(0xFF66BB6A)
                                }
                            )
                            TextButton(onClick = { 
                                selected = null
                                com.simplexray.an.topology.TopologySelectionStore.clearSelection(ctx)
                            }) {
                                Text("✕")
                            }
                        }
                        Text(
                            n.label,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        // Node statistics
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(
                                    "Weight",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Text(
                                    String.format("%.2f", n.weight),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Activity",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Text(
                                    String.format("%.2f", n.instantContribution),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            "Connections: ${connectedEdges.size}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        if (connectedEdges.isNotEmpty()) {
                            Text(
                                "Total Weight: ${String.format("%.2f", totalEdgeWeight)}",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Max Weight: ${String.format("%.2f", maxEdgeWeight)}",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        if (asnInfo != null) {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.Divider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "ASN: ${asnInfo.asn}",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                            Text(
                                asnInfo.org,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            val cdnName = AsnCdnMap.name(asnInfo)
                            if (cdnName != null) {
                                Text(
                                    "CDN: $cdnName",
                                    color = Color(0xFFFFA000),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        if (isCdn) {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.Divider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "✓ CDN Matched",
                                color = Color(0xFFFFA000),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
