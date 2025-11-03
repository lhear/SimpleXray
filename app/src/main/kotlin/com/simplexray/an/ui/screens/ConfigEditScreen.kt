package com.simplexray.an.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.an.R
import com.simplexray.an.ui.util.bracketMatcherTransformation
import com.simplexray.an.viewmodel.ConfigEditUiEvent
import com.simplexray.an.viewmodel.ConfigEditViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: ConfigEditViewModel
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filename by viewModel.filename.collectAsStateWithLifecycle()
    val configTextFieldValue by viewModel.configTextFieldValue.collectAsStateWithLifecycle()
    val filenameErrorMessage by viewModel.filenameErrorMessage.collectAsStateWithLifecycle()
    val hasConfigChanged by viewModel.hasConfigChanged.collectAsStateWithLifecycle()
    val jsonValidationError by viewModel.jsonValidationError.collectAsStateWithLifecycle()
    val lineCount by viewModel.lineCount.collectAsStateWithLifecycle()
    val charCount by viewModel.charCount.collectAsStateWithLifecycle()
    val cursorLine by viewModel.cursorLine.collectAsStateWithLifecycle()
    val cursorColumn by viewModel.cursorColumn.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is ConfigEditUiEvent.NavigateBack -> {
                    onBackClick()
                }

                is ConfigEditUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        event.message,
                        duration = SnackbarDuration.Short
                    )
                }

                is ConfigEditUiEvent.ShareContent -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.content)
                    }
                    shareLauncher.launch(Intent.createChooser(shareIntent, null))
                }
            }
        }
    }

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(title = { Text(stringResource(id = R.string.config)) }, navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(
                        R.string.back
                    )
                )
            }
        }, actions = {
            // Undo
            IconButton(onClick = { viewModel.undo() }) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Undo"
                )
            }
            // Redo
            IconButton(onClick = { viewModel.redo() }) {
                Icon(
                    Icons.Default.Redo,
                    contentDescription = "Redo"
                )
            }
            // Format
            IconButton(onClick = { viewModel.formatJson() }) {
                Icon(
                    Icons.Default.FormatAlignLeft,
                    contentDescription = "Format JSON"
                )
            }
            // Validate
            IconButton(onClick = { viewModel.validateJson() }) {
                Icon(
                    if (jsonValidationError == null) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = "Validate JSON",
                    tint = if (jsonValidationError == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            // Save
            IconButton(onClick = {
                viewModel.saveConfigFile()
                focusManager.clearFocus()
            }, enabled = hasConfigChanged) {
                Icon(
                    painter = painterResource(id = R.drawable.save),
                    contentDescription = stringResource(id = R.string.save)
                )
            }
            // More menu
            IconButton(onClick = { showMenu = !showMenu }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more)
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Minify JSON") },
                    onClick = {
                        viewModel.minifyJson()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Compress, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Search") },
                    onClick = {
                        showSearchDialog = true
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Statistics") },
                    onClick = {
                        showStatsDialog = true
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Templates") },
                    onClick = {
                        showTemplateDialog = true
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Code, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.share)) },
                    onClick = {
                        viewModel.shareConfigFile()
                        showMenu = false
                    })
            }
        }, scrollBehavior = scrollBehavior
        )
    }, snackbarHost = { SnackbarHost(snackbarHostState) }, content = { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(scrollState)
        ) {
            TextField(value = filename,
                onValueChange = { v ->
                    viewModel.onFilenameChange(v)
                },
                label = { Text(stringResource(id = R.string.filename)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                ),
                isError = filenameErrorMessage != null,
                supportingText = {
                    filenameErrorMessage?.let { Text(it) }
                })

            TextField(
                value = configTextFieldValue,
                onValueChange = { newTextFieldValue ->
                    val newText = newTextFieldValue.text
                    val oldText = configTextFieldValue.text
                    val cursorPosition = newTextFieldValue.selection.start

                    if (newText.length == oldText.length + 1 &&
                        cursorPosition > 0 &&
                        newText[cursorPosition - 1] == '\n'
                    ) {
                        val pair = viewModel.handleAutoIndent(newText, cursorPosition - 1)
                        viewModel.onConfigContentChange(
                            TextFieldValue(
                                text = pair.first,
                                selection = TextRange(pair.second)
                            )
                        )
                    } else {
                        viewModel.onConfigContentChange(newTextFieldValue.copy(text = newText))
                    }
                },
                visualTransformation = bracketMatcherTransformation(configTextFieldValue),
                label = { Text(stringResource(R.string.content)) },
                modifier = Modifier
                    .padding(bottom = if (isKeyboardOpen) 0.dp else paddingValues.calculateBottomPadding())
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                )
            )

            // Statistics Bar
            EditorStatisticsBar(
                lineCount = lineCount,
                charCount = charCount,
                cursorLine = cursorLine,
                cursorColumn = cursorColumn,
                hasError = jsonValidationError != null
            )

            // Error indicator
            jsonValidationError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "✗ $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    })

    // Search Dialog
    if (showSearchDialog) {
        SearchDialog(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onDismiss = { showSearchDialog = false },
            onSearch = {
                val index = viewModel.findText(searchQuery)
                if (index == -1) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Text not found", duration = SnackbarDuration.Short)
                    }
                }
            }
        )
    }

    // Statistics Dialog
    if (showStatsDialog) {
        val stats = viewModel.getJsonStatistics()
        StatisticsDialog(
            stats = stats,
            lineCount = lineCount,
            charCount = charCount,
            onDismiss = { showStatsDialog = false }
        )
    }

    // Template Dialog
    if (showTemplateDialog) {
        TemplateSelectionDialog(
            onDismiss = { showTemplateDialog = false },
            onTemplateSelected = { templateName ->
                viewModel.insertTemplate(templateName)
            }
        )
    }
}

@Composable
private fun EditorStatisticsBar(
    lineCount: Int,
    charCount: Int,
    cursorLine: Int,
    cursorColumn: Int,
    hasError: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Ln $cursorLine, Col $cursorColumn",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$lineCount lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$charCount chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasError) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            } else {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Valid",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSearch: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search") },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search text") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onSearch) {
                Text("Search")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StatisticsDialog(
    stats: Map<String, Any>,
    lineCount: Int,
    charCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Statistics") },
        text = {
            Column {
                Text("Lines: $lineCount", style = MaterialTheme.typography.bodyMedium)
                Text("Characters: $charCount", style = MaterialTheme.typography.bodyMedium)
                if (stats.isNotEmpty()) {
                    Text("Valid JSON: ${stats["isValid"]}", style = MaterialTheme.typography.bodyMedium)
                    stats["keys"]?.let {
                        Text("Top-level keys: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    stats["topLevelKeys"]?.let { keys ->
                        if (keys is List<*>) {
                            Text("Keys: ${keys.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun TemplateSelectionDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (String) -> Unit
) {
    val templates = listOf(
        "vless_tcp_tls",
        "vless_ws_tls",
        "vless_grpc_tls",
        "vless_http_tls",
        "vless_quic_tls",
        "vless_kcp_tls"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Template") },
        text = {
            Column {
                templates.forEach { template ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { 
                            Text(template.replace("_", " ").replaceFirstChar { it.uppercase() })
                        },
                        modifier = Modifier.clickable {
                            onTemplateSelected(template)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
