package com.simplexray.an.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simplexray.an.R

/**
 * Dialog shown when a new app version is available
 */
@Composable
fun UpdateDialog(
    currentVersion: String,
    newVersion: String,
    isDownloading: Boolean = false,
    downloadProgress: Int = 0,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Text(text = stringResource(R.string.new_version_available_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.new_version_available_message,
                        newVersion
                    )
                )

                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.downloading, downloadProgress),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.download))
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Simple update available dialog that opens browser to release page
 */
@Composable
fun SimpleUpdateDialog(
    newVersion: String,
    onVisitRelease: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.new_version_available_title))
        },
        text = {
            Text(
                text = stringResource(
                    R.string.new_version_available_message,
                    newVersion
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onVisitRelease) {
                Text(stringResource(R.string.download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = modifier
    )
}

/**
 * Bottom sheet modal shown during app update download
 * Shows download progress, cancel button, and install button when complete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDownloadBottomSheet(
    isDownloading: Boolean,
    downloadProgress: Int,
    isDownloadComplete: Boolean,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    LaunchedEffect(isDownloading || isDownloadComplete) {
        if (isDownloading || isDownloadComplete) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    if (isDownloading || isDownloadComplete) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!isDownloading) {
                    onDismiss()
                }
            },
            sheetState = sheetState,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = stringResource(R.string.new_version_available_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )

                // Download progress section
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.downloading, downloadProgress),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        
                        Text(
                            text = "$downloadProgress%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Download complete section
                if (isDownloadComplete && !isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.download_completed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = stringResource(R.string.ready_to_install),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button (only during download)
                    if (isDownloading) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }

                    // Install button (only when complete)
                    if (isDownloadComplete && !isDownloading) {
                        Button(
                            onClick = {
                                onInstall()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.install))
                        }
                        
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }

                // Extra padding at bottom for better UX
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
