package com.simplexray.an.ui.performance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.simplexray.an.performance.CustomProfileManager
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.viewmodel.CustomProfileViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomProfileEditScreen(
    profileId: String?,
    onBackClick: () -> Unit,
    navController: NavController,
    viewModel: CustomProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val isNewProfile = profileId == null || profileId == "new"
    
    var profile by remember {
        mutableStateOf<CustomProfileManager.CustomProfile?>(
            if (isNewProfile) null else viewModel.getProfile(profileId ?: "")
        )
    }
    
    // Load profile if editing
    LaunchedEffect(profileId) {
        if (!isNewProfile && profileId != null) {
            profile = viewModel.getProfile(profileId)
        }
    }
    
    // Form state
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var description by remember { mutableStateOf(profile?.description ?: "") }
    var selectedBaseProfile by remember { 
        mutableStateOf<PerformanceProfile?>(null) 
    }
    
    // Config state - initialize from profile or defaults
    val defaultConfig = PerformanceProfile.Balanced.config
    var bufferSize by remember { 
        mutableStateOf(profile?.config?.bufferSize ?: defaultConfig.bufferSize) 
    }
    var connectionTimeout by remember { 
        mutableStateOf(profile?.config?.connectionTimeout ?: defaultConfig.connectionTimeout) 
    }
    var handshakeTimeout by remember { 
        mutableStateOf(profile?.config?.handshakeTimeout ?: defaultConfig.handshakeTimeout) 
    }
    var idleTimeout by remember { 
        mutableStateOf(profile?.config?.idleTimeout ?: defaultConfig.idleTimeout) 
    }
    var tcpFastOpen by remember { 
        mutableStateOf(profile?.config?.tcpFastOpen ?: defaultConfig.tcpFastOpen) 
    }
    var tcpNoDelay by remember { 
        mutableStateOf(profile?.config?.tcpNoDelay ?: defaultConfig.tcpNoDelay) 
    }
    var keepAlive by remember { 
        mutableStateOf(profile?.config?.keepAlive ?: defaultConfig.keepAlive) 
    }
    var keepAliveInterval by remember { 
        mutableStateOf(profile?.config?.keepAliveInterval ?: defaultConfig.keepAliveInterval) 
    }
    var dnsCacheTtl by remember { 
        mutableStateOf(profile?.config?.dnsCacheTtl ?: defaultConfig.dnsCacheTtl) 
    }
    var dnsPrefetch by remember { 
        mutableStateOf(profile?.config?.dnsPrefetch ?: defaultConfig.dnsPrefetch) 
    }
    var parallelConnections by remember { 
        mutableStateOf(profile?.config?.parallelConnections ?: defaultConfig.parallelConnections) 
    }
    var enableCompression by remember { 
        mutableStateOf(profile?.config?.enableCompression ?: defaultConfig.enableCompression) 
    }
    var enableMultiplexing by remember { 
        mutableStateOf(profile?.config?.enableMultiplexing ?: defaultConfig.enableMultiplexing) 
    }
    var statsUpdateInterval by remember { 
        mutableStateOf(profile?.config?.statsUpdateInterval ?: defaultConfig.statsUpdateInterval) 
    }
    var logLevel by remember { 
        mutableStateOf(profile?.config?.logLevel ?: defaultConfig.logLevel) 
    }
    
    // Update form when base profile is selected
    LaunchedEffect(selectedBaseProfile) {
        selectedBaseProfile?.let { base ->
            bufferSize = base.config.bufferSize
            connectionTimeout = base.config.connectionTimeout
            handshakeTimeout = base.config.handshakeTimeout
            idleTimeout = base.config.idleTimeout
            tcpFastOpen = base.config.tcpFastOpen
            tcpNoDelay = base.config.tcpNoDelay
            keepAlive = base.config.keepAlive
            keepAliveInterval = base.config.keepAliveInterval
            dnsCacheTtl = base.config.dnsCacheTtl
            dnsPrefetch = base.config.dnsPrefetch
            parallelConnections = base.config.parallelConnections
            enableCompression = base.config.enableCompression
            enableMultiplexing = base.config.enableMultiplexing
            statsUpdateInterval = base.config.statsUpdateInterval
            logLevel = base.config.logLevel
        }
    }
    
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewProfile) "Create Profile" else "Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                val config = com.simplexray.an.performance.model.PerformanceConfig(
                                    bufferSize = bufferSize,
                                    connectionTimeout = connectionTimeout,
                                    handshakeTimeout = handshakeTimeout,
                                    idleTimeout = idleTimeout,
                                    tcpFastOpen = tcpFastOpen,
                                    tcpNoDelay = tcpNoDelay,
                                    keepAlive = keepAlive,
                                    keepAliveInterval = keepAliveInterval,
                                    dnsCacheTtl = dnsCacheTtl,
                                    dnsPrefetch = dnsPrefetch,
                                    parallelConnections = parallelConnections,
                                    enableCompression = enableCompression,
                                    enableMultiplexing = enableMultiplexing,
                                    statsUpdateInterval = statsUpdateInterval,
                                    logLevel = logLevel
                                )
                                
                                val newProfile = if (isNewProfile) {
                                    CustomProfileManager.CustomProfile(
                                        id = "custom_${System.currentTimeMillis()}",
                                        name = name,
                                        description = description,
                                        config = config,
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis(),
                                        category = "Custom"
                                    )
                                } else {
                                    profile!!.copy(
                                        name = name,
                                        description = description,
                                        config = config,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                                
                                viewModel.saveProfile(newProfile)
                                navController.popBackStack()
                            }
                        },
                        enabled = name.isNotBlank() && !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save"
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Info Section
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Basic Information",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Profile Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = name.isBlank()
                    )
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    
                    // Base Profile Selector
                    if (isNewProfile) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedTextField(
                                value = selectedBaseProfile?.name ?: "Select Base Profile",
                                onValueChange = {},
                                label = { Text("Start from Base Profile (Optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { expanded = true }) {
                                        Text("Select")
                                    }
                                }
                            )
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                PerformanceProfile.getAll().forEach { baseProfile ->
                                    DropdownMenuItem(
                                        text = { Text(baseProfile.name) },
                                        onClick = {
                                            selectedBaseProfile = baseProfile
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Buffer Settings
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Buffer Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    IntSliderField(
                        label = "Buffer Size (KB)",
                        value = bufferSize / 1024,
                        onValueChange = { bufferSize = it * 1024 },
                        min = 16,
                        max = 512,
                        step = 16
                    )
                }
            }
            
            // Timeout Settings
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Timeout Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    IntSliderField(
                        label = "Connection Timeout (ms)",
                        value = connectionTimeout,
                        onValueChange = { connectionTimeout = it },
                        min = 5000,
                        max = 60000,
                        step = 1000
                    )
                    
                    IntSliderField(
                        label = "Handshake Timeout (ms)",
                        value = handshakeTimeout,
                        onValueChange = { handshakeTimeout = it },
                        min = 3000,
                        max = 30000,
                        step = 1000
                    )
                    
                    IntSliderField(
                        label = "Idle Timeout (ms)",
                        value = idleTimeout,
                        onValueChange = { idleTimeout = it },
                        min = 60000,
                        max = 600000,
                        step = 30000
                    )
                }
            }
            
            // TCP Settings
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "TCP Optimization",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    SwitchField(
                        label = "TCP Fast Open",
                        checked = tcpFastOpen,
                        onCheckedChange = { tcpFastOpen = it }
                    )
                    
                    SwitchField(
                        label = "TCP No Delay",
                        checked = tcpNoDelay,
                        onCheckedChange = { tcpNoDelay = it }
                    )
                    
                    SwitchField(
                        label = "Keep Alive",
                        checked = keepAlive,
                        onCheckedChange = { keepAlive = it }
                    )
                    
                    if (keepAlive) {
                        IntSliderField(
                            label = "Keep Alive Interval (seconds)",
                            value = keepAliveInterval,
                            onValueChange = { keepAliveInterval = it },
                            min = 10,
                            max = 300,
                            step = 5
                        )
                    }
                }
            }
            
            // DNS Settings
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "DNS Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    IntSliderField(
                        label = "DNS Cache TTL (seconds)",
                        value = dnsCacheTtl,
                        onValueChange = { dnsCacheTtl = it },
                        min = 300,
                        max = 7200,
                        step = 300
                    )
                    
                    SwitchField(
                        label = "DNS Prefetch",
                        checked = dnsPrefetch,
                        onCheckedChange = { dnsPrefetch = it }
                    )
                }
            }
            
            // Connection Settings
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Connection Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    IntSliderField(
                        label = "Parallel Connections",
                        value = parallelConnections,
                        onValueChange = { parallelConnections = it },
                        min = 1,
                        max = 32,
                        step = 1
                    )
                    
                    SwitchField(
                        label = "Enable Compression",
                        checked = enableCompression,
                        onCheckedChange = { enableCompression = it }
                    )
                    
                    SwitchField(
                        label = "Enable Multiplexing",
                        checked = enableMultiplexing,
                        onCheckedChange = { enableMultiplexing = it }
                    )
                }
            }
            
            // Monitoring Settings
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Monitoring Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    LongSliderField(
                        label = "Stats Update Interval (ms)",
                        value = statsUpdateInterval,
                        onValueChange = { statsUpdateInterval = it },
                        min = 100,
                        max = 10000,
                        step = 100
                    )
                    
                    var logLevelExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedTextField(
                            value = logLevel,
                            onValueChange = {},
                            label = { Text("Log Level") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { logLevelExpanded = true }) {
                                    Text("▼")
                                }
                            }
                        )
                        
                        DropdownMenu(
                            expanded = logLevelExpanded,
                            onDismissRequest = { logLevelExpanded = false }
                        ) {
                            listOf("debug", "info", "warning", "error", "none").forEach { level ->
                                DropdownMenuItem(
                                    text = { Text(level.replaceFirstChar { it.uppercaseChar() }) },
                                    onClick = {
                                        logLevel = level
                                        logLevelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntSliderField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    step: Int
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$value",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / step) - 1
        )
    }
}

@Composable
fun LongSliderField(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    min: Long,
    max: Long,
    step: Long
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$value",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toLong()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / step).toInt() - 1
        )
    }
}

@Composable
fun SwitchField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

