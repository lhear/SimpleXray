package com.simplexray.an.ui.performance

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.simplexray.an.common.ROUTE_CUSTOM_PROFILE_EDIT
import com.simplexray.an.performance.CustomProfileManager
import com.simplexray.an.viewmodel.CustomProfileViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomProfileListScreen(
    onBackClick: () -> Unit,
    navController: NavController,
    viewModel: CustomProfileViewModel = viewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    
    var showDeleteDialog by remember { mutableStateOf<CustomProfileManager.CustomProfile?>(null) }
    var showDuplicateDialog by remember { mutableStateOf<CustomProfileManager.CustomProfile?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var expandedMenuId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    
    // Clear error when dismissed
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // Auto-dismiss after 5 seconds
            kotlinx.coroutines.delay(5000)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Performance Profiles") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import"
                        )
                    }
                    IconButton(
                        onClick = {
                            navController.navigate("${ROUTE_CUSTOM_PROFILE_EDIT}?profileId=new")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Profile"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate("${ROUTE_CUSTOM_PROFILE_EDIT}?profileId=new")
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Profile"
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            
            if (isLoading && profiles.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (profiles.isEmpty()) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onCreateClick = {
                        navController.navigate("${ROUTE_CUSTOM_PROFILE_EDIT}?profileId=new")
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(profiles) { profile ->
                        ProfileItemCard(
                            profile = profile,
                            isExpanded = expandedMenuId == profile.id,
                            onExpandedChange = { 
                                expandedMenuId = if (expandedMenuId == profile.id) null else profile.id
                            },
                            onEditClick = {
                                navController.navigate("${ROUTE_CUSTOM_PROFILE_EDIT}?profileId=${profile.id}")
                            },
                            onDeleteClick = {
                                showDeleteDialog = profile
                            },
                            onDuplicateClick = {
                                showDuplicateDialog = profile
                            },
                            onExportClick = {
                                val json = viewModel.exportProfile(profile)
                                shareProfile(context, json, profile.name)
                            },
                            onShareClick = {
                                val json = viewModel.exportProfile(profile)
                                shareProfile(context, json, profile.name)
                            }
                        )
                    }
                }
            }
            
            // Error snackbar
            errorMessage?.let { error ->
                LaunchedEffect(error) {
                    // Error is shown via Toast in ViewModel
                }
            }
        }
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { profile ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Profile") },
            text = {
                Text("Are you sure you want to delete \"${profile.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(profile.id)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Duplicate dialog
    showDuplicateDialog?.let { profile ->
        DuplicateProfileDialog(
            profile = profile,
            onDismiss = { showDuplicateDialog = null },
            onConfirm = { newName ->
                viewModel.duplicateProfile(profile.id, newName)
                showDuplicateDialog = null
            }
        )
    }
    
    // Import dialog
    if (showImportDialog) {
        ImportProfileDialog(
            onDismiss = { showImportDialog = false },
            onImport = { json ->
                viewModel.importProfile(json)
                showImportDialog = false
            },
            onImportMultiple = { json ->
                viewModel.importProfiles(json)
                showImportDialog = false
            }
        )
    }
}

@Composable
fun ProfileItemCard(
    profile: CustomProfileManager.CustomProfile,
    isExpanded: Boolean,
    onExpandedChange: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDuplicateClick: () -> Unit,
    onExportClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Created: ${dateFormat.format(Date(profile.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Box {
                    IconButton(onClick = onExpandedChange) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    
                    DropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = onExpandedChange
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, null)
                            },
                            onClick = {
                                onEditClick()
                                onExpandedChange()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, null)
                            },
                            onClick = {
                                onDuplicateClick()
                                onExpandedChange()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export") },
                            leadingIcon = {
                                Icon(Icons.Default.Share, null)
                            },
                            onClick = {
                                onExportClick()
                                onExpandedChange()
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                onDeleteClick()
                                onExpandedChange()
                            }
                        )
                    }
                }
            }
            
            // Profile config preview
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ConfigChip("Buffer: ${profile.config.bufferSize / 1024}KB")
                ConfigChip("Connections: ${profile.config.parallelConnections}")
                ConfigChip(if (profile.config.tcpFastOpen) "TCP Fast Open" else "No Fast Open")
            }
        }
    }
}

@Composable
fun ConfigChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    onCreateClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "No Custom Profiles",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Create your first custom performance profile to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onCreateClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Profile")
        }
    }
}

@Composable
fun DuplicateProfileDialog(
    profile: CustomProfileManager.CustomProfile,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf("${profile.name} (Copy)") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate Profile") },
        text = {
            Column {
                Text("Enter a name for the duplicated profile:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotBlank()
            ) {
                Text("Duplicate")
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
fun ImportProfileDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onImportMultiple: (String) -> Unit
) {
    var importText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Profile") },
        text = {
            Column {
                Text("Paste the JSON profile data:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("JSON Data") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onImportMultiple(importText) },
                    enabled = importText.isNotBlank()
                ) {
                    Text("Import All")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { onImport(importText) },
                    enabled = importText.isNotBlank()
                ) {
                    Text("Import")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun shareProfile(context: Context, json: String, profileName: String) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, json)
        putExtra(Intent.EXTRA_SUBJECT, "Performance Profile: $profileName")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
}

fun copyToClipboard(context: Context, text: String, label: String = "Profile") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}


