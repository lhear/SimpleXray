package com.simplexray.an.ui.performance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    
    var cpuAffinityEnabled by remember { mutableStateOf(prefs.cpuAffinityEnabled) }
    var memoryPoolSize by remember { mutableStateOf(prefs.memoryPoolSize) }
    var connectionPoolSize by remember { mutableStateOf(prefs.connectionPoolSize) }
    var socketBufferMultiplier by remember { mutableStateOf(prefs.socketBufferMultiplier) }
    var threadPoolSize by remember { mutableStateOf(prefs.threadPoolSize) }
    var jitWarmupEnabled by remember { mutableStateOf(prefs.jitWarmupEnabled) }
    var tcpFastOpenEnabled by remember { mutableStateOf(prefs.tcpFastOpenEnabled) }
    
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
                Text(
                    "Advanced Settings",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Fine-tune performance optimizations. Changes require service restart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = threadPoolSize.toString(),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(100.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                (2..8).forEach { size ->
                                    DropdownMenuItem(
                                        text = { Text("$size") },
                                        onClick = {
                                            threadPoolSize = size
                                            prefs.threadPoolSize = size
                                            expanded = false
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
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = memoryPoolSize.toString(),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(100.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                listOf(8, 16, 24, 32).forEach { size ->
                                    DropdownMenuItem(
                                        text = { Text("$size") },
                                        onClick = {
                                            memoryPoolSize = size
                                            prefs.memoryPoolSize = size
                                            expanded = false
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
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = String.format(Locale.US, "%.1fx", socketBufferMultiplier),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(100.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                listOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f).forEach { multiplier ->
                                    DropdownMenuItem(
                                        text = { Text(String.format(Locale.US, "%.1fx", multiplier)) },
                                        onClick = {
                                            socketBufferMultiplier = multiplier
                                            prefs.socketBufferMultiplier = multiplier
                                            expanded = false
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
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = connectionPoolSize.toString(),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(100.dp),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                listOf(4, 6, 8, 10, 12, 14, 16).forEach { size ->
                                    DropdownMenuItem(
                                        text = { Text("$size") },
                                        onClick = {
                                            connectionPoolSize = size
                                            prefs.connectionPoolSize = size
                                            expanded = false
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
                        val isSupported = remember { perfManager.isTCPFastOpenSupported() }
                        Switch(
                            checked = tcpFastOpenEnabled && isSupported,
                            enabled = isSupported,
                            onCheckedChange = {
                                tcpFastOpenEnabled = it
                                prefs.tcpFastOpenEnabled = it
                            }
                        )
                    }
                )
            }
            
            if (!perfManager.isTCPFastOpenSupported()) {
                item {
                    Text(
                        "TCP Fast Open not supported on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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

