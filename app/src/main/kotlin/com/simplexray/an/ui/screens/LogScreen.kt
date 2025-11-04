package com.simplexray.an.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.an.R
import com.simplexray.an.ui.theme.ScrollbarDefaults
import com.simplexray.an.viewmodel.LogViewModel
import my.nanihadesuka.compose.LazyColumnScrollbar

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    logViewModel: LogViewModel,
    listState: LazyListState
) {
    val context = LocalContext.current
    val filteredEntries by logViewModel.filteredEntries.collectAsStateWithLifecycle()
    val filteredSystemLogs by logViewModel.filteredSystemLogs.collectAsStateWithLifecycle()
    val logType by logViewModel.logType.collectAsStateWithLifecycle()
    val logLevel by logViewModel.logLevel.collectAsStateWithLifecycle()
    val isInitialLoad = remember { mutableStateOf(true) }

    val selectedTabIndex = when (logType) {
        LogViewModel.LogType.SERVICE -> 0
        LogViewModel.LogType.SYSTEM -> 1
    }

    DisposableEffect(key1 = Unit) {
        // Load initial logs (LoggerRepository flow collection happens automatically in ViewModel)
        logViewModel.loadLogs()
        onDispose {
            // Only stop logcat, Flow collection is handled by ViewModel lifecycle
            logViewModel.stopLogcat()
        }
    }

    LaunchedEffect(logType) {
        if (logType == LogViewModel.LogType.SYSTEM) {
            logViewModel.startLogcat()
        } else {
            logViewModel.stopLogcat()
        }
    }

    LaunchedEffect(filteredEntries) {
        if (filteredEntries.isNotEmpty() && isInitialLoad.value && logType == LogViewModel.LogType.SERVICE) {
            listState.animateScrollToItem(0)
            isInitialLoad.value = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Tab Row
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { logViewModel.setLogType(LogViewModel.LogType.SERVICE) },
                text = { Text("Service Logs") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { logViewModel.setLogType(LogViewModel.LogType.SYSTEM) },
                text = { Text("System Logcat") }
            )
        }

        // Log Level Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogViewModel.LogLevel.values().forEach { level ->
                FilterChip(
                    selected = logLevel == level,
                    onClick = { logViewModel.setLogLevel(level) },
                    label = { Text(level.name) }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    if (logType == LogViewModel.LogType.SYSTEM) {
                        logViewModel.clearSystemLogs()
                    } else {
                        logViewModel.clearLogs()
                    }
                }
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
            }
        }

        // Log Content
        val currentLogs = if (logType == LogViewModel.LogType.SERVICE) filteredEntries else filteredSystemLogs

        if (currentLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_log_entries),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumnScrollbar(
                state = listState,
                settings = ScrollbarDefaults.defaultScrollbarSettings()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                    reverseLayout = true
                ) {
                    items(currentLogs) { logEntry ->
                        LogEntryItem(logEntry = logEntry)
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(logEntry: String) {
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val timestampColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = MaterialTheme.colorScheme.tertiary
    val infoColor = MaterialTheme.colorScheme.secondary
    val debugColor = MaterialTheme.colorScheme.outline
    val verboseColor = MaterialTheme.colorScheme.onSurfaceVariant

    val annotatedString = remember(logEntry) {
        buildAnnotatedString {
            // Parse threadtime format: MM-DD HH:MM:SS.mmm  PID   TID  LEVEL TAG: MESSAGE
            val parts = logEntry.trim().split(Regex("\\s+"), limit = 6)

            if (parts.size >= 5) {
                // Timestamp (Date + Time)
                withStyle(SpanStyle(color = timestampColor)) {
                    append(parts[0])
                    append(" ")
                    append(parts[1])
                }
                append(" ")

                // PID and TID
                withStyle(SpanStyle(color = debugColor)) {
                    append(parts[2])
                    append(" ")
                    append(parts[3])
                }
                append(" ")

                // Log Level with color coding
                val level = parts[4]
                val levelColor = when (level) {
                    "E" -> errorColor
                    "W" -> warningColor
                    "I" -> infoColor
                    "D" -> debugColor
                    "V" -> verboseColor
                    else -> colorOnSurface
                }
                withStyle(SpanStyle(color = levelColor)) {
                    append(level)
                }

                // Rest of the message (TAG: MESSAGE)
                if (parts.size >= 6) {
                    append(" ")
                    append(parts[5])
                }
            } else {
                // Fallback for non-standard format
                var endIndex = 0
                while (endIndex < logEntry.length) {
                    val c = logEntry[endIndex]
                    if (Character.isDigit(c) || c == '/' || c == '-' || c == ' ' || c == ':' || c == '.') {
                        endIndex++
                    } else {
                        break
                    }
                }
                if (endIndex > 0) {
                    withStyle(SpanStyle(color = timestampColor)) {
                        append(logEntry.substring(0, endIndex))
                    }
                    append(logEntry.substring(endIndex))
                } else {
                    append(logEntry)
                }
            }
        }
    }

    Text(
        text = annotatedString,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = colorOnSurface,
        modifier = Modifier.padding(vertical = 1.dp),
        lineHeight = 16.sp
    )
}
