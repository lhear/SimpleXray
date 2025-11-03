package com.simplexray.an.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.an.BuildConfig
import com.simplexray.an.R
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.update.UpdateManager
import com.simplexray.an.update.DownloadProgress
import com.simplexray.an.viewmodel.MainViewUiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

private const val TAG = "UpdateViewModel"

data class DownloadCompletion(val uri: Uri, val filePath: String?)

class UpdateViewModel(
    application: Application,
    private val prefs: Preferences,
    private val isServiceEnabled: StateFlow<Boolean>,
    private val uiEventSender: (MainViewUiEvent) -> Unit
) : AndroidViewModel(application) {
    
    private val updateManager = UpdateManager(application)
    
    private val _isCheckingForUpdates = MutableStateFlow(false)
    val isCheckingForUpdates: StateFlow<Boolean> = _isCheckingForUpdates.asStateFlow()
    
    private val _newVersionAvailable = MutableStateFlow<String?>(null)
    val newVersionAvailable: StateFlow<String?> = _newVersionAvailable.asStateFlow()
    
    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()
    
    private var currentDownloadId: Long? = null
    
    private val _downloadCompletion = MutableStateFlow<DownloadCompletion?>(null)
    val downloadCompletion: StateFlow<DownloadCompletion?> = _downloadCompletion.asStateFlow()
    
    init {
        Log.d(TAG, "UpdateViewModel initialized")
    }
    
    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingForUpdates.value = true
            val client = OkHttpClient.Builder().apply {
                if (isServiceEnabled.value) {
                    proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort)))
                }
            }.build()
            
            val request = Request.Builder()
                .url(BuildConfig.REPOSITORY_URL + "/releases/latest")
                .head()
                .build()
            
            try {
                val response = client.newCall(request).await()
                val location = response.request.url.toString()
                val latestTag = location.substringAfterLast("/tag/v")
                Log.d(TAG, "Latest version tag: $latestTag")
                val updateAvailable = compareVersions(latestTag) > 0
                if (updateAvailable) {
                    _newVersionAvailable.value = latestTag
                } else {
                    uiEventSender(
                        MainViewUiEvent.ShowSnackbar(
                            application.getString(R.string.no_new_version_available)
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                uiEventSender(
                    MainViewUiEvent.ShowSnackbar(
                        application.getString(R.string.failed_to_check_for_updates) + ": " + e.message
                    )
                )
            } finally {
                _isCheckingForUpdates.value = false
            }
        }
    }
    
    fun downloadNewVersion(versionTag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isDownloadingUpdate.value = true
                _downloadProgress.value = 0
                
                val deviceAbi = detectDeviceAbi()
                if (deviceAbi == null) {
                    throw IllegalStateException("No supported ABI found for this device")
                }
                
                val apkUrl = BuildConfig.REPOSITORY_URL +
                    "/releases/download/v$versionTag/simplexray-$deviceAbi.apk"
                
                Log.d(TAG, "Detected ABI: $deviceAbi, Starting APK download from: $apkUrl")
                
                val downloadId = updateManager.downloadApk(versionTag, apkUrl)
                currentDownloadId = downloadId
                
                updateManager.observeDownloadProgress(downloadId).collect { progress ->
                    when (progress) {
                        is DownloadProgress.Downloading -> {
                            _downloadProgress.value = progress.progress
                            Log.d(TAG, "Download progress: ${progress.progress}%")
                        }
                        is DownloadProgress.Completed -> {
                            _downloadProgress.value = 100
                            _isDownloadingUpdate.value = false
                            Log.d(TAG, "Download completed, waiting for user to install")
                            
                            withContext(Dispatchers.Main) {
                                _downloadCompletion.value = DownloadCompletion(progress.uri, progress.filePath)
                            }
                        }
                        is DownloadProgress.Failed -> {
                            _isDownloadingUpdate.value = false
                            _downloadProgress.value = 0
                            Log.e(TAG, "Download failed: ${progress.error}")
                            
                            withContext(Dispatchers.Main) {
                                uiEventSender(
                                    MainViewUiEvent.ShowSnackbar(
                                        "Download failed: ${progress.error}"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _isDownloadingUpdate.value = false
                _downloadProgress.value = 0
                _downloadCompletion.value = null
                throw e
            } catch (e: Exception) {
                _isDownloadingUpdate.value = false
                _downloadProgress.value = 0
                _downloadCompletion.value = null
                Log.e(TAG, "Error downloading update", e)
                
                withContext(Dispatchers.Main) {
                    uiEventSender(
                        MainViewUiEvent.ShowSnackbar(
                            "Error downloading update: ${e.message}"
                        )
                    )
                }
            }
        }
    }
    
    fun cancelDownload() {
        currentDownloadId?.let { id ->
            updateManager.cancelDownload(id)
            _isDownloadingUpdate.value = false
            _downloadProgress.value = 0
            _newVersionAvailable.value = null
            _downloadCompletion.value = null
        }
    }
    
    fun installDownloadedApk() {
        _downloadCompletion.value?.let { completion ->
            updateManager.installApk(completion.uri, completion.filePath)
            _downloadCompletion.value = null
            _newVersionAvailable.value = null
        }
    }
    
    fun clearDownloadCompletion() {
        _downloadCompletion.value = null
        _newVersionAvailable.value = null
    }
    
    fun clearNewVersionAvailable() {
        _newVersionAvailable.value = null
    }
    
    private fun detectDeviceAbi(): String? {
        val supportedAbis = BuildConfig.SUPPORTED_ABIS
        val deviceAbis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS
        } else {
            arrayOf(Build.CPU_ABI ?: "", Build.CPU_ABI2 ?: "")
        }
        
        for (deviceAbi in deviceAbis) {
            if (deviceAbi in supportedAbis) {
                Log.d(TAG, "Detected device ABI: $deviceAbi")
                return deviceAbi
            }
        }
        
        Log.w(TAG, "No matching ABI found, using fallback: ${supportedAbis.firstOrNull()}")
        return supportedAbis.firstOrNull()
    }
    
    private fun compareVersions(version1: String): Int {
        val parts1 = version1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 =
            BuildConfig.VERSION_NAME.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }
    
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(Result.success(response))
            }
            
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWith(Result.failure(e))
            }
        })
        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Throwable) {
            }
        }
    }
}

