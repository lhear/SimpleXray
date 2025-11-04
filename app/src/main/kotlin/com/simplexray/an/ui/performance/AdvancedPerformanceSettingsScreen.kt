package com.simplexray.an.ui.performance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplexray.an.performance.PerformanceManager
import com.simplexray.an.prefs.Preferences
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedPerformanceSettingsScreen(
    context: android.content.Context,
    onBackClick: () -> Unit = {}
) {
    val prefs = remember { Preferences(context) }
    val perfManager = remember { PerformanceManager.getInstance(context) }
    val isTCPFastOpenSupported = remember {
        try {
            perfManager.isTCPFastOpenSupported()
        } catch (e: UnsatisfiedLinkError) {
            // Native library not loaded, TCP Fast Open not available
            false
        } catch (e: Exception) {
            // Any other error, assume not supported
            false
        }
    }
    
    var cpuAffinityEnabled by remember { mutableStateOf(prefs.cpuAffinityEnabled) }
    var memoryPoolSize by remember { mutableStateOf(prefs.memoryPoolSize) }
    var connectionPoolSize by remember { mutableStateOf(prefs.connectionPoolSize) }
    var socketBufferMultiplier by remember { mutableStateOf(prefs.socketBufferMultiplier) }
    var threadPoolSize by remember { mutableStateOf(prefs.threadPoolSize) }
    var jitWarmupEnabled by remember { mutableStateOf(prefs.jitWarmupEnabled) }
    var tcpFastOpenEnabled by remember { mutableStateOf(prefs.tcpFastOpenEnabled) }
    
    // Dropdown expansion states
    var threadPoolExpanded by remember { mutableStateOf(false) }
    var memoryPoolExpanded by remember { mutableStateOf(false) }
    var socketBufferExpanded by remember { mutableStateOf(false) }
    var connectionPoolExpanded by remember { mutableStateOf(false) }
    
    // Refresh state from preferences when screen is displayed
    LaunchedEffect(Unit) {
        cpuAffinityEnabled = prefs.cpuAffinityEnabled
        jitWarmupEnabled = prefs.jitWarmupEnabled
        tcpFastOpenEnabled = prefs.tcpFastOpenEnabled
        
        // Validate and clamp values to valid ranges
        val originalMemoryPoolSize = prefs.memoryPoolSize
        val originalConnectionPoolSize = prefs.connectionPoolSize
        val originalSocketBufferMultiplier = prefs.socketBufferMultiplier
        val originalThreadPoolSize = prefs.threadPoolSize
        
        memoryPoolSize = originalMemoryPoolSize.coerceIn(8, 32)
        connectionPoolSize = originalConnectionPoolSize.coerceIn(4, 16)
        socketBufferMultiplier = originalSocketBufferMultiplier.coerceIn(1.0f, 4.0f)
        threadPoolSize = originalThreadPoolSize.coerceIn(2, 8)
        
        // If values were clamped, save the corrected values back
        if (originalMemoryPoolSize != memoryPoolSize) prefs.memoryPoolSize = memoryPoolSize
        if (originalConnectionPoolSize != connectionPoolSize) prefs.connectionPoolSize = connectionPoolSize
        if (originalSocketBufferMultiplier != socketBufferMultiplier) prefs.socketBufferMultiplier = socketBufferMultiplier
        if (originalThreadPoolSize != threadPoolSize) prefs.threadPoolSize = threadPoolSize
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Performance Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Advanced Settings",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Fine-tune performance optimizations. Changes require service restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "💡 Tip: These settings affect low-level network performance. Use default values unless you understand their impact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            
            item {
                PreferenceCategoryTitle("CPU & Threading")
            }
            
            item {
                SettingCard(
                    title = "CPU Affinity",
                    description = "Pin threads to big/little cores for optimal performance",
                    trailingContent = {
                        Switch(
                            checked = cpuAffinityEnabled,
                            onCheckedChange = {
                                cpuAffinityEnabled = it
                                prefs.cpuAffinityEnabled = it
                            }
                        )
                    }
                )
            }
            
            item {
                SettingCard(
                    title = "Thread Pool Size",
                    description = "Number of threads in I/O pool (2-8)",
                    trailingContent = {
                        ExposedDropdownMenuBox(
                            expanded = threadPoolExpanded,
                            onExpandedChange = { threadPoolExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = threadPoolSize.toString(),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(100.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = threadPoolExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = threadPoolExpanded,
                                onDismissRequest = { threadPoolExpanded = false }
                            ) {
                                (2..8).forEach { size ->
                                    DropdownMenuItem(
                                        text = { Text("$size") },
                                        onClick = {
                                            threadPoolSize = size
                                            prefs.threadPoolSize = size
                                            threadPoolExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
            
            item {
                PreferenceCategoryTitle("Memory & Buffers")
            }
            
            item {
                SettingCard(
                    title = "Memory Pool Size",
                    description = "Number of buffers in pool (8-32)",
                    trailingContent = {
                        ExposedDropdownMenuBox(
                            expanded = memoryPoolExpanded,
                            onExpandedChange = { memoryPoolExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = memoryPoolSize.toString(),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(100.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memoryPoolExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = memoryPoolExpanded,
                                onDismissRequest = { memoryPoolExpanded = false }
                            ) {
                                listOf(8, 16, 24, 32).forEach { size ->
                                    DropdownMenuItem(
                                        text = { Text("$size") },
                                        onClick = {
                                            memoryPoolSize = size
                                            prefs.memoryPoolSize = size
                                            memoryPoolExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
            
            item {
                SettingCard(
                    title = "Socket Buffer Multiplier",
                    description = "Buffer size multiplier (1.0x - 4.0x)",
                    trailingContent = {
                        ExposedDropdownMenuBox(
                            expanded = socketBufferExpanded,
                            onExpandedChange = { socketBufferExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = String.format(Locale.US, "%.1fx", socketBufferMultiplier),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(100.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = socketBufferExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = socketBufferExpanded,
                                onDismissRequest = { socketBufferExpanded = false }
                            ) {
                                listOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f).forEach { multiplier ->
                                    DropdownMenuItem(
                                        text = { Text(String.format(Locale.US, "%.1fx", multiplier)) },
                                        onClick = {
                                            socketBufferMultiplier = multiplier
                                            prefs.socketBufferMultiplier = multiplier
                                            socketBufferExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
            
            item {
                PreferenceCategoryTitle("Connection Pool")
            }
            
            item {
                SettingCard(
                    title = "Connection Pool Size",
                    description = "Sockets per pool type (4-16)",
                    trailingContent = {
                        ExposedDropdownMenuBox(
                            expanded = connectionPoolExpanded,
                            onExpandedChange = { connectionPoolExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = connectionPoolSize.toString(),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(100.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = connectionPoolExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = connectionPoolExpanded,
                                onDismissRequest = { connectionPoolExpanded = false }
                            ) {
                                listOf(4, 6, 8, 10, 12, 14, 16).forEach { size ->
                                    DropdownMenuItem(
                                        text = { Text("$size") },
                                        onClick = {
                                            connectionPoolSize = size
                                            prefs.connectionPoolSize = size
                                            connectionPoolExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
            
            item {
                PreferenceCategoryTitle("Optimizations")
            }
            
            item {
                SettingCard(
                    title = "JIT Warm-up",
                    description = "Pre-compile hot paths on startup",
                    trailingContent = {
                        Switch(
                            checked = jitWarmupEnabled,
                            onCheckedChange = {
                                jitWarmupEnabled = it
                                prefs.jitWarmupEnabled = it
                            }
                        )
                    }
                )
            }
            
            item {
                SettingCard(
                    title = "TCP Fast Open",
                    description = "Reduce first connection latency (if supported)",
                    trailingContent = {
                        Switch(
                            checked = tcpFastOpenEnabled && isTCPFastOpenSupported,
                            enabled = isTCPFastOpenSupported,
                            onCheckedChange = { enabled ->
                                if (isTCPFastOpenSupported) {
                                    tcpFastOpenEnabled = enabled
                                    prefs.tcpFastOpenEnabled = enabled
                                }
                            }
                        )
                    }
                )
            }
            
            if (!isTCPFastOpenSupported) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "⚠ TCP Fast Open not supported on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PreferenceCategoryTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingCard(
    title: String,
    description: String,
    trailingContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailingContent()
        }
    }
}

