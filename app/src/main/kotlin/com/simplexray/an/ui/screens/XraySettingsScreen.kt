package com.simplexray.an.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.R
import com.simplexray.an.ui.util.bracketMatcherTransformation
import com.simplexray.an.viewmodel.XraySettingsViewModel
import com.simplexray.an.viewmodel.XraySettingsViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XraySettingsScreen(
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: XraySettingsViewModel = viewModel(
        factory = XraySettingsViewModelFactory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val vlessSettings by viewModel.vlessSettings.collectAsStateWithLifecycle()
    val streamSettings by viewModel.streamSettings.collectAsStateWithLifecycle()
    val jsonContent by viewModel.jsonContent.collectAsStateWithLifecycle()
    val isJsonView by viewModel.isJsonView.collectAsStateWithLifecycle()
    val hasChanges by viewModel.hasChanges.collectAsStateWithLifecycle()
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle()
    val loadResult by viewModel.loadResult.collectAsStateWithLifecycle()
    val configFiles by viewModel.configFiles.collectAsStateWithLifecycle()
    val configFilename by viewModel.configFilename.collectAsStateWithLifecycle()

    var showLoadDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    var filenameError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(saveResult) {
        saveResult?.let { result ->
            result.fold(
                onSuccess = {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.config_save_success),
                        duration = SnackbarDuration.Short
                    )
                    viewModel.clearSaveResult()
                    viewModel.refreshConfigFileList()
                },
                onFailure = { error ->
                    snackbarHostState.showSnackbar(
                        error.message ?: context.getString(R.string.save_fail),
                        duration = SnackbarDuration.Short
                    )
                    viewModel.clearSaveResult()
                }
            )
        }
    }

    // Load result handling is done below in the delete dialog section

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.xray_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTemplateDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.templates)
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboardContent = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
                            if (clipboardContent != null && clipboardContent.isNotBlank()) {
                                viewModel.importFromClipboard(clipboardContent)
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.import_failed),
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.import_from_clipboard)
                        )
                    }
                    IconButton(
                        onClick = { showLoadDialog = true }
                    ) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = stringResource(R.string.load_config)
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.toggleView(!isJsonView)
                        }
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = if (isJsonView) stringResource(R.string.view_form) else stringResource(R.string.view_json)
                        )
                    }
                    IconButton(
                        onClick = {
                            val configJson = viewModel.exportToClipboard()
                            val clip = ClipData.newPlainText("JSON Config", configJson)
                            clipboardManager.setPrimaryClip(clip)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.json_copied),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy_json)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (viewModel.validateForm() || isJsonView) {
                                showSaveDialog = true
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.invalid_config_format),
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        },
                        enabled = hasChanges
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = stringResource(R.string.save)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(top = paddingValues.calculateTopPadding())
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                if (isJsonView) {
                    JsonView(
                        jsonContent = jsonContent,
                        viewModel = viewModel,
                        onJsonChange = { newTextFieldValue ->
                            val newText = newTextFieldValue.text
                            val oldText = jsonContent.text
                            val cursorPosition = newTextFieldValue.selection.start

                            if (newText.length == oldText.length + 1 &&
                                cursorPosition > 0 &&
                                newText[cursorPosition - 1] == '\n'
                            ) {
                                val pair = viewModel.handleAutoIndent(newText, cursorPosition - 1)
                                viewModel.updateJsonContent(
                                    TextFieldValue(
                                        text = pair.first,
                                        selection = TextRange(pair.second)
                                    )
                                )
                            } else {
                                viewModel.updateJsonContent(newTextFieldValue.copy(text = newText))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isKeyboardOpen) 0.dp else paddingValues.calculateBottomPadding())
                    )
                } else {
                    FormView(
                        viewModel = viewModel,
                        vlessSettings = vlessSettings,
                        streamSettings = streamSettings,
                        onServerAddressChange = { viewModel.updateServerAddress(it) },
                        onServerPortChange = { viewModel.updateServerPort(it) },
                        onIdChange = { viewModel.updateId(it) },
                        onGenerateId = { viewModel.generateNewId() },
                        onMuxToggle = { viewModel.toggleMux(it) },
                        onNetworkTypeChange = { viewModel.updateNetworkType(it) },
                        onTlsToggle = { viewModel.toggleTls(it) },
                        onInsecureToggle = { viewModel.toggleInsecure(it) },
                        onSniChange = { viewModel.updateSni(it) },
                        onWsPathChange = { viewModel.updateWsPath(it) },
                        onWsHostChange = { viewModel.updateWsHost(it) },
                        onGrpcServiceNameChange = { viewModel.updateGrpcServiceName(it) },
                        onHttpPathChange = { viewModel.updateHttpPath(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = paddingValues.calculateBottomPadding())
                    )
                }
            }
        }
    )

    if (showLoadDialog) {
        LoadConfigDialog(
            configFiles = configFiles,
            onDismiss = { showLoadDialog = false },
            onLoadConfig = { file ->
                showLoadDialog = false
                viewModel.loadFromConfigFile(file)
            },
            onDeleteConfig = { file ->
                showDeleteDialog = file
            }
        )
    }

    if (showSaveDialog) {
        SaveConfigDialog(
            filename = configFilename,
            onFilenameChange = { newFilename ->
                viewModel.updateConfigFilename(newFilename)
                filenameError = com.simplexray.an.common.FilenameValidator.validateFilename(
                    context,
                    newFilename
                )
            },
            onDismiss = { showSaveDialog = false },
            onSave = {
                if (filenameError == null) {
                    viewModel.saveConfigFile()
                    showSaveDialog = false
                }
            },
            filenameError = filenameError
        )
    }

    if (showTemplateDialog) {
        TemplateSelectionDialog(
            onDismiss = { showTemplateDialog = false },
            onTemplateSelected = { template ->
                viewModel.applyTemplate(template)
                showTemplateDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.config_load_success),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    showDeleteDialog?.let { fileToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_config)) },
            text = { Text(fileToDelete.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConfigFile(fileToDelete)
                        showDeleteDialog = null
                        // Snackbar will be shown via loadResult LaunchedEffect
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Handle load/delete result
    LaunchedEffect(loadResult) {
        loadResult?.let { result ->
            result.fold(
                onSuccess = {
                    // Check if this was a delete operation
                    val isDeleteOperation = showDeleteDialog == null && showLoadDialog == false
                    if (isDeleteOperation) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.config_deleted),
                            duration = SnackbarDuration.Short
                        )
                        viewModel.refreshConfigFileList()
                    } else {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.config_load_success),
                            duration = SnackbarDuration.Short
                        )
                        showLoadDialog = false
                    }
                    viewModel.clearLoadResult()
                },
                onFailure = { error ->
                    val isDeleteOperation = showDeleteDialog == null && showLoadDialog == false
                    val message = if (isDeleteOperation) {
                        context.getString(R.string.config_delete_failed)
                    } else {
                        error.message ?: context.getString(R.string.config_load_failed)
                    }
                    snackbarHostState.showSnackbar(
                        message,
                        duration = SnackbarDuration.Short
                    )
                    viewModel.clearLoadResult()
                }
            )
        }
    }
}

@Composable
private fun LoadConfigDialog(
    configFiles: List<File>,
    onDismiss: () -> Unit,
    onLoadConfig: (File) -> Unit,
    onDeleteConfig: (File) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_config_file)) },
        text = {
            if (configFiles.isEmpty()) {
                Text(stringResource(R.string.no_config_files))
            } else {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    configFiles.forEach { file ->
                        val fileName = file.name.replace(".json", "")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLoadConfig(file)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(fileName) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onDeleteConfig(file) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun TemplateSelectionDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (com.simplexray.an.viewmodel.ConfigTemplate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.templates)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                com.simplexray.an.viewmodel.ConfigTemplate.values().forEach { template ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(template.displayName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onTemplateSelected(template)
                            }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SaveConfigDialog(
    filename: String,
    onFilenameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    filenameError: String?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save)) },
        text = {
            Column {
                OutlinedTextField(
                    value = filename,
                    onValueChange = onFilenameChange,
                    label = { Text(stringResource(R.string.filename)) },
                    placeholder = { Text("config_name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = filenameError != null,
                    supportingText = {
                        filenameError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = filenameError == null
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormView(
    viewModel: XraySettingsViewModel,
    vlessSettings: com.simplexray.an.viewmodel.VlessSettings,
    streamSettings: com.simplexray.an.viewmodel.StreamSettings,
    onServerAddressChange: (String) -> Unit,
    onServerPortChange: (String) -> Unit,
    onIdChange: (String) -> Unit,
    onGenerateId: () -> Unit,
    onMuxToggle: (Boolean) -> Unit,
    onNetworkTypeChange: (com.simplexray.an.viewmodel.NetworkType) -> Unit,
    onTlsToggle: (Boolean) -> Unit,
    onInsecureToggle: (Boolean) -> Unit,
    onSniChange: (String) -> Unit,
    onWsPathChange: (String) -> Unit,
    onWsHostChange: (String) -> Unit,
    onGrpcServiceNameChange: (String) -> Unit,
    onHttpPathChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val serverAddressError = remember(vlessSettings.serverAddress) {
        viewModel.validateServerAddress(vlessSettings.serverAddress)
    }
    val portError = remember(vlessSettings.serverPort.toString()) {
        viewModel.validatePort(vlessSettings.serverPort.toString())
    }
    val idError = remember(vlessSettings.id) {
        viewModel.validateId(vlessSettings.id)
    }
    var networkTypeExpanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Vless Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.vless_settings),
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(
                onClick = {
                    viewModel.applyTemplate(com.simplexray.an.viewmodel.ConfigTemplate.VLESS_TCP_TLS)
                }
            ) {
                Text(
                    text = stringResource(R.string.reset_to_default),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = vlessSettings.serverAddress,
                    onValueChange = onServerAddressChange,
                    label = { Text(stringResource(R.string.server_address)) },
                    placeholder = { Text(stringResource(R.string.server_address_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = serverAddressError != null,
                    supportingText = {
                        if (serverAddressError != null) {
                            Text(
                                text = serverAddressError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (vlessSettings.serverAddress.isEmpty()) {
                            Text(
                                text = stringResource(R.string.server_address_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = vlessSettings.serverPort.toString(),
                    onValueChange = { onServerPortChange(it.filter { ch -> ch.isDigit() }.take(5)) },
                    label = { Text(stringResource(R.string.server_port)) },
                    placeholder = { Text(stringResource(R.string.server_port_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = portError != null,
                    supportingText = {
                        if (portError != null) {
                            Text(
                                text = portError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.server_port_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = vlessSettings.id,
                            onValueChange = onIdChange,
                            label = { Text(stringResource(R.string.vless_id)) },
                            placeholder = { Text(stringResource(R.string.vless_id_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = idError != null,
                            supportingText = {
                                if (idError != null) {
                                    Text(
                                        text = idError,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else if (vlessSettings.id.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.vless_id_helper),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = onGenerateId,
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(stringResource(R.string.generate_id))
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.mux),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.mux_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vlessSettings.muxEnabled,
                        onCheckedChange = onMuxToggle
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Stream Settings
        Text(
            text = stringResource(R.string.stream_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Network Type Selection
                ExposedDropdownMenuBox(
                    expanded = networkTypeExpanded,
                    onExpandedChange = { networkTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = streamSettings.networkType.displayName,
                        onValueChange = { },
                        label = { Text(stringResource(R.string.network_type)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                if (networkTypeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = networkTypeExpanded,
                        onDismissRequest = { networkTypeExpanded = false }
                    ) {
                        com.simplexray.an.viewmodel.NetworkType.values().forEach { networkType ->
                            DropdownMenuItem(
                                text = { Text(networkType.displayName) },
                                onClick = {
                                    onNetworkTypeChange(networkType)
                                    networkTypeExpanded = false
                                }
                            )
                        }
                    }
                }
                
                // Network-specific settings
                when (streamSettings.networkType) {
                    com.simplexray.an.viewmodel.NetworkType.WEBSOCKET -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = streamSettings.wsPath,
                            onValueChange = onWsPathChange,
                            label = { Text(stringResource(R.string.ws_path)) },
                            placeholder = { Text("/") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(
                                    text = stringResource(R.string.ws_path_helper),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = streamSettings.wsHost,
                            onValueChange = onWsHostChange,
                            label = { Text(stringResource(R.string.ws_host)) },
                            placeholder = { Text(stringResource(R.string.ws_host_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(
                                    text = stringResource(R.string.ws_host_helper),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                    com.simplexray.an.viewmodel.NetworkType.GRPC -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = streamSettings.grpcServiceName,
                            onValueChange = onGrpcServiceNameChange,
                            label = { Text(stringResource(R.string.grpc_service_name)) },
                            placeholder = { Text(stringResource(R.string.grpc_service_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(
                                    text = stringResource(R.string.grpc_service_name_helper),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                    com.simplexray.an.viewmodel.NetworkType.HTTP -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = streamSettings.httpPath,
                            onValueChange = onHttpPathChange,
                            label = { Text(stringResource(R.string.http_path)) },
                            placeholder = { Text("/") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(
                                    text = stringResource(R.string.http_path_helper),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                    else -> {
                        // TCP, KCP, QUIC don't need additional fields
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tls),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.tls_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = streamSettings.tlsEnabled,
                        onCheckedChange = onTlsToggle
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.insecure),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.insecure_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = streamSettings.insecure,
                        onCheckedChange = onInsecureToggle,
                        enabled = streamSettings.tlsEnabled
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = streamSettings.sni,
                    onValueChange = onSniChange,
                    label = { Text(stringResource(R.string.sni)) },
                    placeholder = { Text(stringResource(R.string.sni_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = streamSettings.tlsEnabled,
                    supportingText = {
                        if (streamSettings.tlsEnabled) {
                            Text(
                                text = stringResource(R.string.sni_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun JsonView(
    jsonContent: TextFieldValue,
    viewModel: XraySettingsViewModel,
    onJsonChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    var jsonError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(jsonContent.text) {
        if (jsonContent.text.isNotEmpty()) {
            val result = viewModel.validateAndFormatJson()
            jsonError = result.exceptionOrNull()?.message
        } else {
            jsonError = null
        }
    }
    
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        
        TextField(
            value = jsonContent,
            onValueChange = onJsonChange,
            label = { Text(stringResource(R.string.content)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text
            ),
            visualTransformation = bracketMatcherTransformation(jsonContent),
            isError = jsonError != null,
            supportingText = {
                jsonError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = MaterialTheme.colorScheme.error,
            ),
            minLines = 20
        )
    }
}
