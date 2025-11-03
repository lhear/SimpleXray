package com.simplexray.an.viewmodel

import android.app.Application
import android.net.Uri
import com.simplexray.an.common.AppLogger
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.an.R
import com.simplexray.an.data.source.FileManager
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.viewmodel.MainViewUiEvent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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

private const val TAG = "DownloadViewModel"

class DownloadViewModel(
    application: Application,
    private val prefs: Preferences,
    private val isServiceEnabled: StateFlow<Boolean>,
    private val uiEventSender: (MainViewUiEvent) -> Unit
) : AndroidViewModel(application) {
    
    private val fileManager: FileManager = FileManager(application, prefs)
    
    private val _geoipDownloadProgress = MutableStateFlow<String?>(null)
    val geoipDownloadProgress: StateFlow<String?> = _geoipDownloadProgress.asStateFlow()
    private var geoipDownloadJob: Job? = null
    
    private val _geositeDownloadProgress = MutableStateFlow<String?>(null)
    val geositeDownloadProgress: StateFlow<String?> = _geositeDownloadProgress.asStateFlow()
    private var geositeDownloadJob: Job? = null
    
    init {
        AppLogger.d("DownloadViewModel initialized")
    }
    
    fun cancelDownload(fileName: String) {
        viewModelScope.launch {
            if (fileName == "geoip.dat") {
                geoipDownloadJob?.cancel()
            } else {
                geositeDownloadJob?.cancel()
            }
            AppLogger.d("Download cancellation requested for $fileName")
        }
    }
    
    fun downloadRuleFile(url: String, fileName: String, updateSettingsCallback: () -> Unit) {
        val currentJob = if (fileName == "geoip.dat") geoipDownloadJob else geositeDownloadJob
        if (currentJob?.isActive == true) {
            AppLogger.w("Download already in progress for $fileName")
            return
        }
        
        val job = viewModelScope.launch(Dispatchers.IO) {
            val progressFlow = if (fileName == "geoip.dat") {
                prefs.geoipUrl = url
                _geoipDownloadProgress
            } else {
                prefs.geositeUrl = url
                _geositeDownloadProgress
            }
            
            val client = OkHttpClient.Builder().apply {
                if (isServiceEnabled.value) {
                    proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort)))
                }
            }.build()
            
            try {
                progressFlow.value = application.getString(R.string.connecting)
                
                val request = Request.Builder().url(url).build()
                val call = client.newCall(request)
                val response = call.await()
                
                if (!response.isSuccessful) {
                    throw IOException("Failed to download file: ${response.code}")
                }
                
                val body = response.body ?: throw IOException("Response body is null")
                val totalBytes = body.contentLength()
                var bytesRead = 0L
                var lastProgress = -1
                
                body.byteStream().use { inputStream ->
                    val success = fileManager.saveRuleFile(inputStream, fileName) { read ->
                        ensureActive()
                        bytesRead += read
                        if (totalBytes > 0) {
                            val progress = (bytesRead * 100 / totalBytes).toInt()
                            if (progress != lastProgress) {
                                progressFlow.value =
                                    application.getString(R.string.downloading, progress)
                                lastProgress = progress
                            }
                        } else {
                            if (lastProgress == -1) {
                                progressFlow.value =
                                    application.getString(R.string.downloading_no_size)
                                lastProgress = 0
                            }
                        }
                    }
                    if (success) {
                        updateSettingsCallback()
                        uiEventSender(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_success)))
                    } else {
                        uiEventSender(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_failed)))
                    }
                }
            } catch (e: CancellationException) {
                AppLogger.d("Download cancelled for $fileName")
                uiEventSender(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_cancelled)))
            } catch (e: Exception) {
                AppLogger.e("Failed to download rule file", e)
                uiEventSender(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_failed) + ": " + e.message))
            } finally {
                progressFlow.value = null
            }
        }
        
        if (fileName == "geoip.dat") {
            geoipDownloadJob = job
        } else {
            geositeDownloadJob = job
        }
        
        job.invokeOnCompletion {
            if (fileName == "geoip.dat") {
                geoipDownloadJob = null
            } else {
                geositeDownloadJob = null
            }
        }
    }
    
    fun restoreDefaultGeoip(callback: () -> Unit, updateSettingsCallback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultGeoip()
            updateSettingsCallback()
            uiEventSender(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geoip_success)))
            withContext(Dispatchers.Main) {
                AppLogger.d("Restored default geoip.dat.")
                callback()
            }
        }
    }
    
    fun restoreDefaultGeosite(callback: () -> Unit, updateSettingsCallback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultGeosite()
            updateSettingsCallback()
            uiEventSender(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geosite_success)))
            withContext(Dispatchers.Main) {
                AppLogger.d("Restored default geosite.dat.")
                callback()
            }
        }
    }
    
    fun importRuleFile(uri: Uri, fileName: String, updateSettingsCallback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = fileManager.importRuleFile(uri, fileName)
            if (success) {
                updateSettingsCallback()
                uiEventSender(
                    MainViewUiEvent.ShowSnackbar(
                        "$fileName ${application.getString(R.string.import_success)}"
                    )
                )
            } else {
                uiEventSender(MainViewUiEvent.ShowSnackbar(application.getString(R.string.import_failed)))
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        AppLogger.d("DownloadViewModel cleared - cleaning up downloads")
        geoipDownloadJob?.cancel()
        geositeDownloadJob?.cancel()
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

