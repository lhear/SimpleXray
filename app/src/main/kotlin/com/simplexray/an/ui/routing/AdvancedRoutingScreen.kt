package com.simplexray.an.ui.routing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.R
import com.simplexray.an.protocol.routing.AdvancedRouter.*
import com.simplexray.an.protocol.routing.RouteSnapshot
import com.simplexray.an.protocol.routing.RouteStatus
import com.simplexray.an.perf.PerformanceOptimizer
import com.simplexray.an.viewmodel.AdvancedRoutingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedRoutingScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: AdvancedRoutingViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AdvancedRoutingViewModel(context.applicationContext as android.app.Application) as T
            }
        }
    )
    val rules by viewModel.rules.collectAsState()
    val selectedRule by viewModel.selectedRule.collectAsState()
    val routeSnapshot by viewModel.routeSnapshot.collectAsState()
    
    // Recomposition guard for route snapshot
    val routeSnapshotHash = remember(routeSnapshot) {
        routeSnapshot?.let { 
            "${it.status}|${it.routeTable.rules.size}|${it.activeRoutes.size}"
        } ?: "null"
    }
    
    // Track recomposition skip for diagnostics
    PerformanceOptimizer.shouldRecompose("routing:$routeSnapshotHash")
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Routing") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { showTemplateDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Filled.Add, "Add Template")
                }
                FloatingActionButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Default.Add, "Add Rule")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (rules.isEmpty()) {
                EmptyState(
                    onAddTemplate = { showTemplateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary card
                    item {
                        SummaryCard(
                            totalRules = rules.size,
                            enabledRules = rules.count { it.enabled }
                        )
                    }
                    
                    // Route snapshot status card
                    item {
                        routeSnapshot?.let { snapshot ->
                            RouteStatusCard(snapshot = snapshot)
                        }
                    }

                    // Rule list
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            onToggle = { viewModel.toggleRuleEnabled(rule.id) },
                            onClick = { viewModel.selectRule(rule) },
                            onDelete = { viewModel.removeRule(rule.id) }
                        )
                    }
                }
            }
        }

        if (showTemplateDialog) {
            TemplatePickerDialog(
                onDismiss = { showTemplateDialog = false },
                onTemplateSelected = { template ->
                    viewModel.addTemplateRule(template)
                    showTemplateDialog = false
                }
            )
        }

        if (showAddDialog) {
            AddRuleDialog(
                onDismiss = { showAddDialog = false },
                onRuleCreated = { rule ->
                    viewModel.addRule(rule)
                    showAddDialog = false
                }
            )
        }

        selectedRule?.let { rule ->
            RuleDetailDialog(
                rule = rule,
                onDismiss = { viewModel.selectRule(null) },
                onUpdate = { updated ->
                    viewModel.updateRule(updated)
                    viewModel.selectRule(null)
                }
            )
        }
    }
}

@Composable
private fun SummaryCard(
    totalRules: Int,
    enabledRules: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = totalRules.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Total Rules",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = enabledRules.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: RoutingRule,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PriorityBadge(priority = rule.priority)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ActionChip(action = rule.action)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggle() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Matchers summary
            Text(
                text = getMatchersSummary(rule.matchers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PriorityBadge(priority: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when {
            priority >= 100 -> MaterialTheme.colorScheme.error
            priority >= 50 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = "P$priority",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionChip(action: RoutingAction) {
    val (text, color) = when (action) {
        is RoutingAction.Proxy -> "Proxy" to MaterialTheme.colorScheme.primary
        is RoutingAction.Direct -> "Direct" to MaterialTheme.colorScheme.tertiary
        is RoutingAction.Block -> "Block" to MaterialTheme.colorScheme.error
        is RoutingAction.CustomProxy -> "Custom" to MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyState(
    onAddTemplate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Routing Rules",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add routing rules to control traffic flow",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(onClick = onAddTemplate) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add from Template")
        }
    }
}

@Composable
private fun TemplatePickerDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (AdvancedRoutingViewModel.RuleTemplate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Template") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TemplateItem(
                        name = "Bypass LAN",
                        description = "Direct connection for local network",
                        icon = Icons.Default.Home,
                        onClick = { onTemplateSelected(AdvancedRoutingViewModel.RuleTemplate.BYPASS_LAN) }
                    )
                }
                item {
                    TemplateItem(
                        name = "Bypass China",
                        description = "Direct connection for China mainland IPs",
                        icon = Icons.Default.Language,
                        onClick = { onTemplateSelected(AdvancedRoutingViewModel.RuleTemplate.BYPASS_CHINA) }
                    )
                }
                item {
                    TemplateItem(
                        name = "Block Ads",
                        description = "Block advertising and tracking domains",
                        icon = Icons.Default.Block,
                        onClick = { onTemplateSelected(AdvancedRoutingViewModel.RuleTemplate.BLOCK_ADS) }
                    )
                }
                item {
                    TemplateItem(
                        name = "Streaming via Proxy",
                        description = "Route streaming platforms through proxy",
                        icon = Icons.Filled.PlayArrow,
                        onClick = { onTemplateSelected(AdvancedRoutingViewModel.RuleTemplate.STREAMING_PROXY) }
                    )
                }
                item {
                    TemplateItem(
                        name = "Work Hours Only",
                        description = "Use proxy during work hours (9-5, Mon-Fri)",
                        icon = Icons.Default.Schedule,
                        onClick = { onTemplateSelected(AdvancedRoutingViewModel.RuleTemplate.WORK_HOURS) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TemplateItem(
    name: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onRuleCreated: (RoutingRule) -> Unit
) {
    var ruleName by remember { mutableStateOf("") }
    var selectedAction: RoutingAction by remember { mutableStateOf(RoutingAction.Proxy) }
    var priority by remember { mutableStateOf(50) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Rule Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Action", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedAction is RoutingAction.Proxy,
                        onClick = { selectedAction = RoutingAction.Proxy },
                        label = { Text("Proxy") }
                    )
                    FilterChip(
                        selected = selectedAction is RoutingAction.Direct,
                        onClick = { selectedAction = RoutingAction.Direct },
                        label = { Text("Direct") }
                    )
                    FilterChip(
                        selected = selectedAction is RoutingAction.Block,
                        onClick = { selectedAction = RoutingAction.Block },
                        label = { Text("Block") }
                    )
                }

                Text("Priority: $priority", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = priority.toFloat(),
                    onValueChange = { priority = it.toInt() },
                    valueRange = 0f..100f
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    if (ruleName.isNotBlank()) {
                        val rule = RoutingRule(
                            id = java.util.UUID.randomUUID().toString(),
                            name = ruleName,
                            enabled = true,
                            priority = priority,
                            action = selectedAction,
                            matchers = emptyList() // User can add matchers in detail view
                        )
                        onRuleCreated(rule)
                    }
                },
                enabled = ruleName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RuleDetailDialog(
    rule: RoutingRule,
    onDismiss: () -> Unit,
    onUpdate: (RoutingRule) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rule.name) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    DetailRow("Priority", rule.priority.toString())
                }
                item {
                    DetailRow("Action", getActionName(rule.action))
                }
                item {
                    DetailRow("Status", if (rule.enabled) "Enabled" else "Disabled")
                }
                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Matchers (${rule.matchers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(rule.matchers.size) { index ->
                    val matcher = rule.matchers[index]
                    MatcherCard(matcher)
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
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MatcherCard(matcher: RoutingMatcher) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = getMatcherTypeName(matcher),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = getMatcherDescription(matcher),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// Helper functions
private fun getMatchersSummary(matchers: List<RoutingMatcher>): String {
    if (matchers.isEmpty()) return "No matchers configured"

    return matchers.joinToString(", ") { matcher ->
        when (matcher) {
            is RoutingMatcher.AppMatcher -> "${matcher.packages.size} apps"
            is RoutingMatcher.DomainMatcher -> "${matcher.domains.size} domains"
            is RoutingMatcher.IpMatcher -> "${matcher.ipRanges.size} IP ranges"
            is RoutingMatcher.PortMatcher -> "${matcher.ports.size} ports"
            is RoutingMatcher.ProtocolMatcher -> matcher.protocols.joinToString("/")
            is RoutingMatcher.GeoIpMatcher -> matcher.countries.joinToString(",")
            is RoutingMatcher.TimeMatcher -> "${matcher.startHour}:00-${matcher.endHour}:00"
            is RoutingMatcher.NetworkTypeMatcher -> matcher.networkTypes.joinToString(",")
        }
    }
}

private fun getActionName(action: RoutingAction): String {
    return when (action) {
        is RoutingAction.Proxy -> "Proxy"
        is RoutingAction.Direct -> "Direct Connection"
        is RoutingAction.Block -> "Block"
        is RoutingAction.CustomProxy -> "Custom Proxy: ${action.proxyId}"
    }
}

private fun getMatcherTypeName(matcher: RoutingMatcher): String {
    return when (matcher) {
        is RoutingMatcher.AppMatcher -> "App Matcher"
        is RoutingMatcher.DomainMatcher -> "Domain Matcher"
        is RoutingMatcher.IpMatcher -> "IP Matcher"
        is RoutingMatcher.PortMatcher -> "Port Matcher"
        is RoutingMatcher.ProtocolMatcher -> "Protocol Matcher"
        is RoutingMatcher.GeoIpMatcher -> "GeoIP Matcher"
        is RoutingMatcher.TimeMatcher -> "Time Matcher"
        is RoutingMatcher.NetworkTypeMatcher -> "Network Type Matcher"
    }
}

private fun getMatcherDescription(matcher: RoutingMatcher): String {
    return when (matcher) {
        is RoutingMatcher.AppMatcher -> matcher.packages.take(3).joinToString(", ") +
                if (matcher.packages.size > 3) " and ${matcher.packages.size - 3} more" else ""
        is RoutingMatcher.DomainMatcher -> matcher.domains.take(3).joinToString(", ") +
                if (matcher.domains.size > 3) " and ${matcher.domains.size - 3} more" else ""
        is RoutingMatcher.IpMatcher -> matcher.ipRanges.take(3).joinToString(", ") { it.cidr } +
                if (matcher.ipRanges.size > 3) " and ${matcher.ipRanges.size - 3} more" else ""
        is RoutingMatcher.PortMatcher -> matcher.ports.take(3).joinToString(", ") { "${it.start}-${it.end}" } +
                if (matcher.ports.size > 3) " and ${matcher.ports.size - 3} more" else ""
        is RoutingMatcher.ProtocolMatcher -> matcher.protocols.joinToString(", ")
        is RoutingMatcher.GeoIpMatcher -> matcher.countries.joinToString(", ")
        is RoutingMatcher.TimeMatcher -> "${matcher.startHour}:00 - ${matcher.endHour}:00" +
                (matcher.daysOfWeek?.let { " (${it.joinToString(",")})" } ?: "")
        is RoutingMatcher.NetworkTypeMatcher -> matcher.networkTypes.joinToString(", ")
    }
}

@Composable
private fun RouteStatusCard(snapshot: RouteSnapshot) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        colors = CardDefaults.cardColors(
            containerColor = when (snapshot.status) {
                RouteStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                RouteStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceContainer
                RouteStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                RouteStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Routing Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (snapshot.status) {
                        RouteStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                        RouteStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
                        RouteStatus.ERROR -> MaterialTheme.colorScheme.error
                        RouteStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = snapshot.status.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (snapshot.error != null) {
                Text(
                    text = "Error: ${snapshot.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Active Routes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${snapshot.activeRoutes.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = "Sniff Enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (snapshot.routeTable.sniffEnabled) "Yes" else "No",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
