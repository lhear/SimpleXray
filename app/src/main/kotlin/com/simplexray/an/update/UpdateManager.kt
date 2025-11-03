package com.simplexray.an.update

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/**
 * Manages APK downloads and installations for app updates
 */
class UpdateManager(private val application: Application) {

    private val downloadManager: DownloadManager by lazy {
        application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    /**
     * Downloads APK from GitHub releases and returns download ID
     */
    fun downloadApk(versionTag: String, apkUrl: String): Long {
        val fileName = "simplexray-v$versionTag.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("SimpleXray Update")
            setDescription("Downloading version $versionTag")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
        }

        return downloadManager.enqueue(request)
    }

    /**
     * Monitors download progress and returns Flow of download status
     */
    fun observeDownloadProgress(downloadId: Long): Flow<DownloadProgress> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor: Cursor? = downloadManager.query(query)

                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                        when (status) {
                            DownloadManager.STATUS_RUNNING -> {
                                val progress = if (bytesTotal > 0) {
                                    (bytesDownloaded * 100 / bytesTotal).toInt()
                                } else 0
                                trySend(DownloadProgress.Downloading(progress, bytesDownloaded, bytesTotal))
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val uri = downloadManager.getUriForDownloadedFile(downloadId)
                                // Try to get file path directly from DownloadManager
                                val filePath = try {
                                    val localUriIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                                    val localUri = Uri.parse(cursor.getString(localUriIndex))
                                    if (localUri.scheme == "file") {
                                        localUri.path
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not get file path from COLUMN_LOCAL_URI", e)
                                    null
                                }
                                trySend(DownloadProgress.Completed(uri, filePath))
                                close()
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                trySend(DownloadProgress.Failed("Download failed: error code $reason"))
                                close()
                            }
                        }
                        cursor.close()
                    }
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        application.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

        // Query initial status
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val uri = downloadManager.getUriForDownloadedFile(downloadId)
                // Try to get file path directly from DownloadManager
                val filePath = try {
                    val localUriIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri = Uri.parse(cursor.getString(localUriIndex))
                    if (localUri.scheme == "file") {
                        localUri.path
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get file path from COLUMN_LOCAL_URI", e)
                    null
                }
                trySend(DownloadProgress.Completed(uri, filePath))
                close()
            }
            cursor.close()
        }

        awaitClose {
            try {
                application.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
    }

    /**
     * Installs downloaded APK
     * @param uri The URI from DownloadManager
     * @param filePath Optional file path. If provided, will be used directly for FileProvider.
     */
    fun installApk(uri: Uri, filePath: String? = null) {
        try {
            val fileProviderUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // For Android 7.0+ (API 24+), we need to use FileProvider
                val finalFilePath = filePath ?: getFilePathFromUri(uri)
                if (finalFilePath != null && File(finalFilePath).exists()) {
                    val file = File(finalFilePath)
                    FileProvider.getUriForFile(
                        application,
                        "com.simplexray.an.fileprovider",
                        file
                    )
                } else {
                    // If we can't get file path, try using the URI directly
                    // Note: This might not work on all Android versions
                    uri
                }
            } else {
                uri
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileProviderUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
            }
            application.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            // Show error to user via a toast
            android.widget.Toast.makeText(
                application,
                "Failed to install update: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Extracts file path from DownloadManager URI
     */
    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    // Try to get file path from content URI
                    var filePath: String? = null
                    val cursor = application.contentResolver.query(
                        uri,
                        null,
                        null,
                        null,
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            // Try different column names for file path
                            val columnNames = it.columnNames
                            val dataColumn = columnNames.indexOfFirst { name ->
                                name.equals(android.provider.MediaStore.MediaColumns.DATA, ignoreCase = true) ||
                                name.equals("_data", ignoreCase = true)
                            }
                            if (dataColumn >= 0) {
                                filePath = it.getString(dataColumn)
                            }
                        }
                    }
                    // If content resolver didn't work, try querying DownloadManager
                    if (filePath == null) {
                        filePath = getDownloadManagerFilePath(uri)
                    }
                    filePath
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file path from URI", e)
            null
        }
    }

    /**
     * Gets file path by querying DownloadManager
     */
    private fun getDownloadManagerFilePath(uri: Uri): String? {
        return try {
            val query = DownloadManager.Query()
            query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
            val cursor = downloadManager.query(query)
            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val uriIndex = it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                while (it.moveToNext()) {
                    val localUri = Uri.parse(it.getString(uriIndex))
                    if (localUri == uri || localUri.toString() == uri.toString()) {
                        // Get the file path from the local URI
                        if (localUri.scheme == "file") {
                            return localUri.path
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error querying DownloadManager for file path", e)
            null
        }
    }

    /**
     * Cancels ongoing download
     */
    fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
    }

    companion object {
        private const val TAG = "UpdateManager"
    }
}

/**
 * Represents download progress states
 */
sealed class DownloadProgress {
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val bytesTotal: Long) : DownloadProgress()
    data class Completed(val uri: Uri, val filePath: String? = null) : DownloadProgress()
    data class Failed(val error: String) : DownloadProgress()
}
