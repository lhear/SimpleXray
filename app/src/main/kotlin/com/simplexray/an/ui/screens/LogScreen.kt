package com.simplexray.an.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.simplexray.an.common.LocalTopAppBarScrollBehavior
import com.simplexray.an.viewmodel.LogViewModel
import com.simplexray.an.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    logViewModel: LogViewModel,
    listState: LazyListState
) {
    val context = LocalContext.current
    val logEntries by logViewModel.logEntries.collectAsStateWithLifecycle()

    val isInitialLoad = remember { mutableStateOf(true) }

    DisposableEffect(key1 = Unit) {
        logViewModel.registerLogReceiver(context)
        logViewModel.loadLogs()
        onDispose {
            logViewModel.unregisterLogReceiver(context)
        }
    }

    LaunchedEffect(logEntries) {
        if (logEntries.isNotEmpty() && isInitialLoad.value) {
            listState.animateScrollToItem(0)
            isInitialLoad.value = false
        }
    }

    val scrollBehavior = LocalTopAppBarScrollBehavior.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (logEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let {
                        if (scrollBehavior != null)
                            it.nestedScroll(scrollBehavior.nestedScrollConnection) else it
                    },
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
            LazyColumn(
                state = listState,
                modifier = Modifier.let {
                    if (scrollBehavior != null)
                        it.nestedScroll(scrollBehavior.nestedScrollConnection) else it
                },
                reverseLayout = true
            ) {
                items(logEntries) { logEntry ->
                    LogEntryItem(logEntry = logEntry)
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(logEntry: String) {
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val timestampColor = MaterialTheme.colorScheme.primary

    val annotatedString = remember(logEntry) {
        buildAnnotatedString {
            var endIndex = 0
            while (endIndex < logEntry.length) {
                val c = logEntry[endIndex]
                if (Character.isDigit(c) || c == '/' || c == ' ' || c == ':' || c == '.') {
                    endIndex++
                } else {
                    break
                }
            }
            if (endIndex > 0) {
                val potentialTimestamp = logEntry.substring(0, endIndex)
                if (potentialTimestamp.contains("/") && potentialTimestamp.contains(":")) {
                    withStyle(
                        style = SpanStyle(
                            color = timestampColor
                        )
                    ) {
                        append(logEntry.substring(0, endIndex))
                    }
                    append(logEntry.substring(endIndex))
                } else {
                    append(logEntry)
                }
            } else {
                append(logEntry)
            }
        }
    }

    Text(
        text = annotatedString,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        color = colorOnSurface,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
