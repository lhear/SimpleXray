package com.simplexray.an.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.application
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.simplexray.an.R
import com.simplexray.an.TProxyService
import com.simplexray.an.activity.AppListActivity
import com.simplexray.an.activity.ConfigEditActivity
import com.simplexray.an.data.source.FileManager
import com.simplexray.an.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

private const val TAG = "MainViewModel"

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
    data class StartActivity(val intent: Intent) : UiEvent()
    data class StartService(val intent: Intent) : UiEvent()
    data object RefreshConfigList : UiEvent()
}

class MainViewModel(application: Application) :
    AndroidViewModel(application) {
    val prefs: Preferences = Preferences(application)
    private val activityScope: CoroutineScope = viewModelScope
    private var compressedBackupData: ByteArray? = null

    private val fileManager: FileManager = FileManager(application, prefs)

    private val _controlMenuClickable = MutableStateFlow(true)
    val controlMenuClickable: StateFlow<Boolean> = _controlMenuClickable.asStateFlow()

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _customGeoipImported = MutableStateFlow(prefs.customGeoipImported)
    val customGeoipImported: StateFlow<Boolean> = _customGeoipImported.asStateFlow()

    private val _customGeositeImported = MutableStateFlow(prefs.customGeositeImported)
    val customGeositeImported: StateFlow<Boolean> = _customGeositeImported.asStateFlow()

    private val _geoipSummary = MutableStateFlow("")
    val geoipSummary: StateFlow<String> = _geoipSummary.asStateFlow()

    private val _geositeSummary = MutableStateFlow("")
    val geositeSummary: StateFlow<String> = _geositeSummary.asStateFlow()

    private val _socksPortError = MutableStateFlow(false)
    val socksPortError: StateFlow<Boolean> = _socksPortError.asStateFlow()
    private val _socksPortErrorMessage = MutableStateFlow("")
    val socksPortErrorMessage: StateFlow<String> = _socksPortErrorMessage.asStateFlow()

    private val _dnsIpv4Error = MutableStateFlow(false)
    val dnsIpv4Error: StateFlow<Boolean> = _dnsIpv4Error.asStateFlow()
    private val _dnsIpv4ErrorMessage = MutableStateFlow("")
    val dnsIpv4ErrorMessage: StateFlow<String> = _dnsIpv4ErrorMessage.asStateFlow()

    private val _dnsIpv6Error = MutableStateFlow(false)
    val dnsIpv6Error: StateFlow<Boolean> = _dnsIpv6Error.asStateFlow()
    private val _dnsIpv6ErrorMessage = MutableStateFlow("")
    val dnsIpv6ErrorMessage: StateFlow<String> = _dnsIpv6ErrorMessage.asStateFlow()

    private val _socksPort = MutableStateFlow(prefs.socksPort)
    val socksPort: StateFlow<Int> = _socksPort.asStateFlow()

    private val _dnsIpv4 = MutableStateFlow(prefs.dnsIpv4)
    val dnsIpv4: StateFlow<String> = _dnsIpv4.asStateFlow()

    private val _dnsIpv6 = MutableStateFlow(prefs.dnsIpv6)
    val dnsIpv6: StateFlow<String> = _dnsIpv6.asStateFlow()

    private val _ipv6Enabled = MutableStateFlow(prefs.ipv6)
    val ipv6Enabled: StateFlow<Boolean> = _ipv6Enabled.asStateFlow()

    private val _useTemplateEnabled = MutableStateFlow(prefs.useTemplate)
    val useTemplateEnabled: StateFlow<Boolean> = _useTemplateEnabled.asStateFlow()

    private val _httpProxyEnabled = MutableStateFlow(prefs.httpProxyEnabled)
    val httpProxyEnabled: StateFlow<Boolean> = _httpProxyEnabled.asStateFlow()

    private val _bypassLanEnabled = MutableStateFlow(prefs.bypassLan)
    val bypassLanEnabled: StateFlow<Boolean> = _bypassLanEnabled.asStateFlow()

    val appVersion: LiveData<String> = liveData {
        val packageManager = application.packageManager
        val packageName = application.packageName
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName?.let { emit(it) }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Error getting app version", e)
            emit("N/A")
        }
    }

    val kernelVersion: LiveData<String> = liveData {
        val libraryDir = TProxyService.getNativeLibraryDir(application)
        val xrayPath = "$libraryDir/libxray.so"
        try {
            val process = Runtime.getRuntime().exec("$xrayPath -version")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val firstLine = reader.readLine()
            process.destroy()
            emit(firstLine ?: "N/A")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to get xray version", e)
            emit("N/A")
        }
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _configFiles = MutableStateFlow<List<File>>(emptyList())
    val configFiles: StateFlow<List<File>> = _configFiles.asStateFlow()

    private val _selectedConfigFile = MutableStateFlow<File?>(null)
    val selectedConfigFile: StateFlow<File?> = _selectedConfigFile.asStateFlow()

    private val startReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Service started")
            setServiceEnabled(true)
            setControlMenuClickable(true)
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Service stopped")
            setServiceEnabled(false)
            setControlMenuClickable(true)
        }
    }

    init {
        Log.d(TAG, "MainViewModel initialized.")
        _isServiceEnabled.value = isServiceRunning(getApplication(), TProxyService::class.java)
        _socksPort.value = prefs.socksPort
        _dnsIpv4.value = prefs.dnsIpv4
        _dnsIpv6.value = prefs.dnsIpv6
        _ipv6Enabled.value = prefs.ipv6
        _useTemplateEnabled.value = prefs.useTemplate
        _httpProxyEnabled.value = prefs.httpProxyEnabled
        _bypassLanEnabled.value = prefs.bypassLan
        _customGeoipImported.value = prefs.customGeoipImported
        _customGeositeImported.value = prefs.customGeositeImported
        _geoipSummary.value = fileManager.getRuleFileSummary("geoip.dat")
        _geositeSummary.value = fileManager.getRuleFileSummary("geosite.dat")

        refreshConfigFileList()
    }

    fun setControlMenuClickable(isClickable: Boolean) {
        _controlMenuClickable.value = isClickable
    }

    fun setServiceEnabled(enabled: Boolean) {
        _isServiceEnabled.value = enabled
        prefs.enable = enabled
    }

    fun clearCompressedBackupData() {
        compressedBackupData = null
    }

    fun performBackup(createFileLauncher: ActivityResultLauncher<String>) {
        activityScope.launch {
            compressedBackupData = fileManager.compressBackupData()
            val filename = "simplexray_backup_" + System.currentTimeMillis() + ".dat"
            withContext(Dispatchers.Main) {
                createFileLauncher.launch(filename)
            }
        }
    }

    suspend fun handleBackupFileCreationResult(uri: Uri) {
        if (compressedBackupData != null) {
            val dataToWrite: ByteArray = compressedBackupData as ByteArray
            compressedBackupData = null
            try {
                application.contentResolver.openOutputStream(uri).use { os ->
                    if (os != null) {
                        os.write(dataToWrite)
                        Log.d(TAG, "Backup successful to: $uri")
                        _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.backup_success)))
                    } else {
                        Log.e(TAG, "Failed to open output stream for backup URI: $uri")
                        _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.backup_failed)))
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing backup data to URI: $uri", e)
                _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.backup_failed)))
            }
        } else {
            _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.backup_failed)))
            Log.e(TAG, "Compressed backup data is null in launcher callback.")
        }
    }

    suspend fun startRestoreTask(uri: Uri) {
        withContext(Dispatchers.IO) {
            val success = fileManager.decompressAndRestore(uri)
            if (success) {
                _socksPort.value = prefs.socksPort
                _dnsIpv4.value = prefs.dnsIpv4
                _dnsIpv6.value = prefs.dnsIpv6
                _ipv6Enabled.value = prefs.ipv6
                _bypassLanEnabled.value = prefs.bypassLan
                _useTemplateEnabled.value = prefs.useTemplate
                _httpProxyEnabled.value = prefs.httpProxyEnabled
                _customGeoipImported.value = prefs.customGeoipImported
                _customGeositeImported.value = prefs.customGeositeImported
                _geoipSummary.value = fileManager.getRuleFileSummary("geoip.dat")
                _geositeSummary.value = fileManager.getRuleFileSummary("geosite.dat")
                _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.restore_success)))
                Log.d(TAG, "Restore successful.")
                refreshConfigFileList()
            } else {
                _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.restore_failed)))
            }
        }
    }

    suspend fun createConfigFile(): String? {
        val filePath = fileManager.createConfigFile(application.assets)
        if (filePath == null) {
            _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.create_config_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }

    suspend fun importConfigFromClipboard(): String? {
        val filePath = fileManager.importConfigFromClipboard()
        if (filePath == null) {
            _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.import_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }

    suspend fun deleteConfigFile(file: File, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isServiceEnabled.value && _selectedConfigFile.value != null &&
                _selectedConfigFile.value == file
            ) {
                withContext(Dispatchers.Main) {
                    _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.config_in_use)))
                    Log.w(TAG, "Attempted to delete selected config file: ${file.name}")
                }
                return@launch
            }

            val success = fileManager.deleteConfigFile(file)
            if (success) {
                withContext(Dispatchers.Main) {
                    refreshConfigFileList()
                }
            } else {
                withContext(Dispatchers.Main) {
                    _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.delete_fail)))
                }
            }
            callback()
        }
    }

    fun extractAssetsIfNeeded() {
        fileManager.extractAssetsIfNeeded()
    }

    fun updateSocksPort(portString: String): Boolean {
        return try {
            val port = portString.toInt()
            if (port in 1025..65535) {
                prefs.socksPort = port
                _socksPort.value = port
                _socksPortError.value = false
                _socksPortErrorMessage.value = ""
                true
            } else {
                _socksPortError.value = true
                _socksPortErrorMessage.value =
                    application.getString(R.string.invalid_port_range)
                false
            }
        } catch (e: NumberFormatException) {
            _socksPortError.value = true
            _socksPortErrorMessage.value =
                application.getString(R.string.invalid_port)
            false
        }
    }

    fun updateDnsIpv4(ipv4Addr: String): Boolean {
        val matcher = IPV4_PATTERN.matcher(ipv4Addr)
        return if (matcher.matches()) {
            prefs.dnsIpv4 = ipv4Addr
            _dnsIpv4.value = ipv4Addr
            _dnsIpv4Error.value = false
            _dnsIpv4ErrorMessage.value = ""
            true
        } else {
            _dnsIpv4Error.value = true
            _dnsIpv4ErrorMessage.value =
                application.getString(R.string.invalid_ipv4)
            false
        }
    }

    fun updateDnsIpv6(ipv6Addr: String): Boolean {
        val matcher = IPV6_PATTERN.matcher(ipv6Addr)
        return if (matcher.matches()) {
            prefs.dnsIpv6 = ipv6Addr
            _dnsIpv6.value = ipv6Addr
            _dnsIpv6Error.value = false
            _dnsIpv6ErrorMessage.value = ""
            true
        } else {
            _dnsIpv6Error.value = true
            _dnsIpv6ErrorMessage.value =
                application.getString(R.string.invalid_ipv6)
            false
        }
    }

    fun setIpv6Enabled(enabled: Boolean) {
        prefs.ipv6 = enabled
        _ipv6Enabled.value = enabled
    }

    fun setUseTemplateEnabled(enabled: Boolean) {
        prefs.useTemplate = enabled
        _useTemplateEnabled.value = enabled
    }

    fun setHttpProxyEnabled(enabled: Boolean) {
        prefs.httpProxyEnabled = enabled
        _httpProxyEnabled.value = enabled
    }

    fun setBypassLanEnabled(enabled: Boolean) {
        prefs.bypassLan = enabled
        _bypassLanEnabled.value = enabled
    }

    fun importRuleFile(uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = fileManager.importRuleFile(uri, fileName)
            if (success) {
                when (fileName) {
                    "geoip.dat" -> {
                        _customGeoipImported.value = prefs.customGeoipImported
                        _geoipSummary.value = fileManager.getRuleFileSummary("geoip.dat")
                    }

                    "geosite.dat" -> {
                        _customGeositeImported.value = prefs.customGeositeImported
                        _geositeSummary.value = fileManager.getRuleFileSummary("geosite.dat")
                    }
                }
                _uiEvent.emit(
                    UiEvent.ShowSnackbar(
                        "$fileName ${application.getString(R.string.import_success)}"
                    )
                )
            } else {
                _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.import_failed)))
            }
        }
    }

    fun restoreDefaultRuleFile(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultRuleFile()
            _customGeoipImported.value = prefs.customGeoipImported
            _customGeositeImported.value = prefs.customGeositeImported
            _geoipSummary.value = fileManager.getRuleFileSummary("geoip.dat")
            _geositeSummary.value = fileManager.getRuleFileSummary("geosite.dat")

            _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_default_success)))
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Restored default rule files.")
                callback()
            }
        }
    }

    suspend fun showExportFailedSnackbar() {
        _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.export_failed)))
    }

    fun startTProxyService(action: String) {
        viewModelScope.launch {
            if (_selectedConfigFile.value == null) {
                _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.not_select_config)))
                Log.w(TAG, "Cannot start service: no config file selected.")
                setControlMenuClickable(true)
                return@launch
            }
            val intent = Intent(getApplication(), TProxyService::class.java).setAction(action)
            _uiEvent.emit(UiEvent.StartService(intent))
        }
    }

    fun editConfig(filePath: String) {
        viewModelScope.launch {
            val intent = Intent(getApplication(), ConfigEditActivity::class.java)
            intent.putExtra("filePath", filePath)
            _uiEvent.emit(UiEvent.StartActivity(intent))
        }
    }

    fun shareIntent(chooserIntent: Intent, packageManager: PackageManager) {
        viewModelScope.launch {
            if (chooserIntent.resolveActivity(packageManager) != null) {
                _uiEvent.emit(UiEvent.StartActivity(chooserIntent))
                Log.d(TAG, "Export intent resolved and started.")
            } else {
                Log.w(TAG, "No activity found to handle export intent.")
                _uiEvent.emit(
                    UiEvent.ShowSnackbar(
                        application.getString(R.string.no_app_for_export)
                    )
                )
            }
        }
    }

    fun stopTProxyService() {
        viewModelScope.launch {
            val intent = Intent(
                getApplication(),
                TProxyService::class.java
            ).setAction(TProxyService.ACTION_DISCONNECT)
            _uiEvent.emit(UiEvent.StartService(intent))
        }
    }

    fun prepareAndStartVpn(vpnPrepareLauncher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            if (_selectedConfigFile.value == null) {
                _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.not_select_config)))
                Log.w(TAG, "Cannot prepare VPN: no config file selected.")
                setControlMenuClickable(true)
                return@launch
            }
            val vpnIntent = VpnService.prepare(getApplication())
            if (vpnIntent != null) {
                vpnPrepareLauncher.launch(vpnIntent)
            } else {
                startTProxyService(TProxyService.ACTION_CONNECT)
            }
        }
    }

    fun navigateToAppList() {
        viewModelScope.launch {
            val intent = Intent(getApplication(), AppListActivity::class.java)
            _uiEvent.emit(UiEvent.StartActivity(intent))
        }
    }

    fun refreshConfigFileList() {
        viewModelScope.launch(Dispatchers.IO) {
            val filesDir = application.filesDir
            val newFiles =
                filesDir.listFiles { _, name -> name.endsWith(".json") }?.toList()
                    ?.sortedBy { it.lastModified() } ?: emptyList()
            withContext(Dispatchers.Main) {
                _configFiles.value = newFiles

                val currentSelected = newFiles.find { it.absolutePath == prefs.selectedConfigPath }
                if (currentSelected != null) {
                    updateSelectedConfigFile(currentSelected)
                } else if (newFiles.isNotEmpty()) {
                    updateSelectedConfigFile(newFiles.first())
                } else {
                    updateSelectedConfigFile(null)
                }
            }
        }
    }

    fun updateSelectedConfigFile(file: File?) {
        _selectedConfigFile.value = file
        prefs.selectedConfigPath = file?.absolutePath ?: ""
    }

    fun registerTProxyServiceReceivers() {
        val application = application
        val startSuccessFilter = IntentFilter(TProxyService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(
                startReceiver,
                startSuccessFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(startReceiver, startSuccessFilter)
        }

        val stopSuccessFilter = IntentFilter(TProxyService.ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(
                stopReceiver,
                stopSuccessFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(stopReceiver, stopSuccessFilter)
        }
        Log.d(TAG, "TProxyService receivers registered.")
    }

    fun unregisterTProxyServiceReceivers() {
        val application = application
        application.unregisterReceiver(startReceiver)
        application.unregisterReceiver(stopReceiver)
        Log.d(TAG, "TProxyService receivers unregistered.")
    }

    companion object {
        private const val IPV4_REGEX =
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        private val IPV4_PATTERN: Pattern = Pattern.compile(IPV4_REGEX)
        private const val IPV6_REGEX =
            "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80::(fe80(:[0-9a-fA-F]{0,4})?){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d)|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d))$"
        private val IPV6_PATTERN: Pattern = Pattern.compile(IPV6_REGEX)

        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
}

class MainViewModelFactory(
    private val application: Application
) : ViewModelProvider.AndroidViewModelFactory(application) {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
