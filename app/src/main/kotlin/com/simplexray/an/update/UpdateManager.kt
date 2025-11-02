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
                                trySend(DownloadProgress.Completed(uri))
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
                trySend(DownloadProgress.Completed(uri))
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
     */
    fun installApk(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        application.startActivity(intent)
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
    data class Completed(val uri: Uri) : DownloadProgress()
    data class Failed(val error: String) : DownloadProgress()
}
