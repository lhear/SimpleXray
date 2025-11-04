package com.simplexray.an.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.simplexray.an.common.AppLogger
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.an.R
import com.simplexray.an.common.ROUTE_CONFIG_EDIT
import com.simplexray.an.common.error.ErrorHandler
import com.simplexray.an.common.error.runSuspendCatchingWithError
import com.simplexray.an.common.error.toAppError
import com.simplexray.an.data.source.FileManager
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.security.SecureCredentialStorage
import com.simplexray.an.service.TProxyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

private const val TAG = "ConfigViewModel"

sealed class ConfigUiEvent {
    data class ShowSnackbar(val message: String) : ConfigUiEvent()
    data class ShareLauncher(val intent: Intent) : ConfigUiEvent()
    data object RefreshConfigList : ConfigUiEvent()
    data class Navigate(val route: String) : ConfigUiEvent()
}

/**
 * ViewModel for managing configuration files
 * TODO: Add config file validation before saving
 * TODO: Implement config file versioning
 * TODO: Add config file encryption option for sensitive data
 */
class ConfigViewModel(
    application: Application,
    private val prefs: Preferences,
    private val isServiceEnabled: StateFlow<Boolean>,
    private val uiEventSender: (MainViewUiEvent) -> Unit
) : AndroidViewModel(application) {
    
    private val fileManager: FileManager = FileManager(application, prefs)
    private val secureStorage: SecureCredentialStorage = SecureCredentialStorage.getInstance(application)
    
    private var compressedBackupData: ByteArray? = null
    private var encryptedBackupData: String? = null
    
    lateinit var configEditViewModel: ConfigEditViewModel
    
    private val _configFiles = MutableStateFlow<List<File>>(emptyList())
    val configFiles: StateFlow<List<File>> = _configFiles.asStateFlow()
    
    private val _selectedConfigFile = MutableStateFlow<File?>(null)
    val selectedConfigFile: StateFlow<File?> = _selectedConfigFile.asStateFlow()
    
    private val _uiEvent = channelFlow<Nothing> {
        // This will be used to emit events that need to be handled by MainViewModel
    }
    
    init {
        AppLogger.d("ConfigViewModel initialized")
        viewModelScope.launch(Dispatchers.IO) {
            refreshConfigFileList()
        }
    }
    
    fun clearCompressedBackupData() {
        compressedBackupData = null
        encryptedBackupData = null
    }
    
    fun performBackup(createFileLauncher: ActivityResultLauncher<String>) {
        viewModelScope.launch {
            compressedBackupData = fileManager.compressBackupData()
            // Encrypt backup data for security
            compressedBackupData?.let { data ->
                encryptedBackupData = secureStorage.encryptData(data)
            }
            val filename = "simplexray_backup_" + System.currentTimeMillis() + ".dat"
            withContext(Dispatchers.Main) {
                createFileLauncher.launch(filename)
            }
        }
    }
    
    suspend fun handleBackupFileCreationResult(uri: Uri) {
        withContext(Dispatchers.IO) {
            // Prefer encrypted backup if available, fallback to compressed
            val dataToWrite: ByteArray? = when {
                encryptedBackupData != null -> {
                    // Write encrypted Base64 string directly (backup file is encrypted)
                    val encrypted = encryptedBackupData
                    encryptedBackupData = null
                    encrypted?.toByteArray(StandardCharsets.UTF_8)
                }
                compressedBackupData != null -> {
                    compressedBackupData?.also { compressedBackupData = null }
                }
                else -> null
            }
            
            if (dataToWrite == null) {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.backup_failed)))
                AppLogger.e("Backup data is null in launcher callback.")
                return@withContext
            }
            
            val result = runSuspendCatchingWithError {
                val outputStream = getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: throw IOException("Failed to open output stream for backup URI: $uri")
                
                outputStream.use { os ->
                    os.write(dataToWrite)
                }
                AppLogger.d("Backup successful to: $uri")
            }
            
            result.fold(
                onSuccess = {
                    uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.backup_success)))
                },
                onFailure = { throwable ->
                    val appError = throwable.toAppError()
                    ErrorHandler.handleError(appError, TAG)
                    uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.backup_failed)))
                }
            )
        }
    }
    
    suspend fun startRestoreTask(uri: Uri) {
        withContext(Dispatchers.IO) {
            val success = fileManager.decompressAndRestore(uri)
            if (success) {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.restore_success)))
                AppLogger.d("Restore successful.")
                refreshConfigFileList()
            } else {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.restore_failed)))
            }
        }
    }
    
    suspend fun createConfigFile(): String? {
        val filePath = fileManager.createConfigFile(getApplication<Application>().assets)
        if (filePath == null) {
            uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.create_config_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }
    
    suspend fun importConfigFromClipboard(): String? {
        val filePath = fileManager.importConfigFromClipboard()
        if (filePath == null) {
            uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.import_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }
    
    suspend fun handleSharedContent(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!fileManager.importConfigFromContent(content).isNullOrEmpty()) {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.import_success)))
                refreshConfigFileList()
            } else {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.invalid_config_format)))
            }
        }
    }
    
    suspend fun deleteConfigFile(file: File, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isServiceEnabled.value && _selectedConfigFile.value != null &&
                _selectedConfigFile.value == file
            ) {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.config_in_use)))
                AppLogger.w("Attempted to delete selected config file: ${file.name}")
                return@launch
            }
            
            val success = fileManager.deleteConfigFile(file)
            if (success) {
                withContext(Dispatchers.Main) {
                    refreshConfigFileList()
                }
            } else {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.delete_fail)))
            }
            callback()
        }
    }
    
    fun extractAssetsIfNeeded() {
        fileManager.extractAssetsIfNeeded()
    }
    
    fun editConfig(filePath: String) {
        viewModelScope.launch {
            configEditViewModel = ConfigEditViewModel(getApplication(), filePath, prefs)
            uiEventSender(MainViewUiEvent.Navigate(ROUTE_CONFIG_EDIT))
        }
    }
    
    fun shareIntent(chooserIntent: Intent, packageManager: android.content.pm.PackageManager) {
        viewModelScope.launch {
            if (chooserIntent.resolveActivity(packageManager) != null) {
                uiEventSender(MainViewUiEvent.ShareLauncher(chooserIntent))
                AppLogger.d("Export intent resolved and started.")
            } else {
                AppLogger.w("No activity found to handle export intent.")
                uiEventSender(
                    MainViewUiEvent.ShowSnackbar(
                        getApplication<Application>().getString(R.string.no_app_for_export)
                    )
                )
            }
        }
    }
    
    fun showExportFailedSnackbar() {
        uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.export_failed)))
    }
    
    fun moveConfigFile(fromIndex: Int, toIndex: Int) {
        val currentList = _configFiles.value.toMutableList()
        val movedItem = currentList.removeAt(fromIndex)
        currentList.add(toIndex, movedItem)
        _configFiles.value = currentList
        prefs.configFilesOrder = currentList.map { it.name }
    }
    
    private var lastRefreshTime = 0L
    private val REFRESH_DEBOUNCE_MS = 1000L // 1 second
    private var cachedFileList: List<File>? = null
    private var cacheTime = 0L
    private val CACHE_TTL_MS = 5000L // 5 seconds
    
    fun refreshConfigFileList() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            
            // Debounce check
            if (now - lastRefreshTime < REFRESH_DEBOUNCE_MS) {
                return@launch
            }
            lastRefreshTime = now
            
            // Check cache
            if (cachedFileList != null && now - cacheTime < CACHE_TTL_MS) {
                _configFiles.value = cachedFileList!!
                return@launch
            }
            
            val filesDir = getApplication<Application>().filesDir
            // Validate files to ensure only valid config files are listed
            val actualFiles =
                filesDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }?.toList()
                    ?: emptyList()
            val actualFilesByName = actualFiles.associateBy { it.name }
            val savedOrder = prefs.configFilesOrder
            
            val newOrder = mutableListOf<File>()
            val remainingActualFileNames = actualFilesByName.toMutableMap()
            
            savedOrder.forEach { filename ->
                actualFilesByName[filename]?.let { file ->
                    newOrder.add(file)
                    remainingActualFileNames.remove(filename)
                }
            }
            
            newOrder.addAll(remainingActualFileNames.values.filter { it !in newOrder })
            
            // Validate files (only include .json files that can be read)
            val validFiles = newOrder.filter { file ->
                file.isFile && file.name.endsWith(".json") && file.canRead()
            }
            
            cachedFileList = validFiles
            cacheTime = now
            _configFiles.value = validFiles
            prefs.configFilesOrder = validFiles.map { it.name }
            
            val currentSelectedPath = prefs.selectedConfigPath
            var fileToSelect: File? = null
            
            if (currentSelectedPath != null) {
                val foundSelected = newOrder.find { it.absolutePath == currentSelectedPath }
                if (foundSelected != null) {
                    fileToSelect = foundSelected
                }
            }
            
            if (fileToSelect == null) {
                fileToSelect = newOrder.firstOrNull()
            }
            
            _selectedConfigFile.value = fileToSelect
            prefs.selectedConfigPath = fileToSelect?.absolutePath
        }
    }
    
    fun updateSelectedConfigFile(file: File?) {
        _selectedConfigFile.value = file
        prefs.selectedConfigPath = file?.absolutePath
    }
}

