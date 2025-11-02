package com.simplexray.an.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
