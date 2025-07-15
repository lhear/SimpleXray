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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.simplexray.an.BuildConfig
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
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.regex.Pattern
import javax.net.ssl.SSLSocketFactory

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

    private val _settingsState = MutableStateFlow(
        SettingsState(
            socksPort = InputFieldState(prefs.socksPort.toString()),
            dnsIpv4 = InputFieldState(prefs.dnsIpv4),
            dnsIpv6 = InputFieldState(prefs.dnsIpv6),
            switches = SwitchStates(
                ipv6Enabled = prefs.ipv6,
                useTemplateEnabled = prefs.useTemplate,
                httpProxyEnabled = prefs.httpProxyEnabled,
                bypassLanEnabled = prefs.bypassLan,
                disableVpn = prefs.disableVpn
            ),
            info = InfoStates(
                appVersion = BuildConfig.VERSION_NAME,
                kernelVersion = "N/A",
                geoipSummary = "",
                geositeSummary = ""
            ),
            files = FileStates(
                isGeoipCustom = prefs.customGeoipImported,
                isGeositeCustom = prefs.customGeositeImported
            ),
            connectivityTestTarget = InputFieldState(prefs.connectivityTestTarget),
            connectivityTestTimeout = InputFieldState(prefs.connectivityTestTimeout.toString())
        )
    )
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _controlMenuClickable = MutableStateFlow(true)
    val controlMenuClickable: StateFlow<Boolean> = _controlMenuClickable.asStateFlow()

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

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
        viewModelScope.launch(Dispatchers.IO) {
            _isServiceEnabled.value = isServiceRunning(application, TProxyService::class.java)

            updateSettingsState()
            loadKernelVersion()
            refreshConfigFileList()
        }
    }

    private fun updateSettingsState() {
        _settingsState.value = _settingsState.value.copy(
            socksPort = InputFieldState(prefs.socksPort.toString()),
            dnsIpv4 = InputFieldState(prefs.dnsIpv4),
            dnsIpv6 = InputFieldState(prefs.dnsIpv6),
            switches = SwitchStates(
                ipv6Enabled = prefs.ipv6,
                useTemplateEnabled = prefs.useTemplate,
                httpProxyEnabled = prefs.httpProxyEnabled,
                bypassLanEnabled = prefs.bypassLan,
                disableVpn = prefs.disableVpn
            ),
            info = _settingsState.value.info.copy(
                appVersion = BuildConfig.VERSION_NAME,
                geoipSummary = fileManager.getRuleFileSummary("geoip.dat"),
                geositeSummary = fileManager.getRuleFileSummary("geosite.dat")
            ),
            files = FileStates(
                isGeoipCustom = prefs.customGeoipImported,
                isGeositeCustom = prefs.customGeositeImported
            ),
            connectivityTestTarget = InputFieldState(prefs.connectivityTestTarget),
            connectivityTestTimeout = InputFieldState(prefs.connectivityTestTimeout.toString())
        )
    }

    private fun loadKernelVersion() {
        val libraryDir = TProxyService.getNativeLibraryDir(application)
        val xrayPath = "$libraryDir/libxray.so"
        try {
            val process = Runtime.getRuntime().exec("$xrayPath -version")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val firstLine = reader.readLine()
            process.destroy()
            _settingsState.value = _settingsState.value.copy(
                info = _settingsState.value.info.copy(
                    kernelVersion = firstLine ?: "N/A"
                )
            )
        } catch (e: IOException) {
            Log.e(TAG, "Failed to get xray version", e)
            _settingsState.value = _settingsState.value.copy(
                info = _settingsState.value.info.copy(
                    kernelVersion = "N/A"
                )
            )
        }
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
        withContext(Dispatchers.IO) {
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
    }

    suspend fun startRestoreTask(uri: Uri) {
        withContext(Dispatchers.IO) {
            val success = fileManager.decompressAndRestore(uri)
            if (success) {
                updateSettingsState()
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
                _settingsState.value = _settingsState.value.copy(
                    socksPort = InputFieldState(portString)
                )
                true
            } else {
                _settingsState.value = _settingsState.value.copy(
                    socksPort = InputFieldState(
                        value = portString,
                        error = application.getString(R.string.invalid_port_range),
                        isValid = false
                    )
                )
                false
            }
        } catch (e: NumberFormatException) {
            _settingsState.value = _settingsState.value.copy(
                socksPort = InputFieldState(
                    value = portString,
                    error = application.getString(R.string.invalid_port),
                    isValid = false
                )
            )
            false
        }
    }

    fun updateDnsIpv4(ipv4Addr: String): Boolean {
        val matcher = IPV4_PATTERN.matcher(ipv4Addr)
        return if (matcher.matches()) {
            prefs.dnsIpv4 = ipv4Addr
            _settingsState.value = _settingsState.value.copy(
                dnsIpv4 = InputFieldState(ipv4Addr)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                dnsIpv4 = InputFieldState(
                    value = ipv4Addr,
                    error = application.getString(R.string.invalid_ipv4),
                    isValid = false
                )
            )
            false
        }
    }

    fun updateDnsIpv6(ipv6Addr: String): Boolean {
        val matcher = IPV6_PATTERN.matcher(ipv6Addr)
        return if (matcher.matches()) {
            prefs.dnsIpv6 = ipv6Addr
            _settingsState.value = _settingsState.value.copy(
                dnsIpv6 = InputFieldState(ipv6Addr)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                dnsIpv6 = InputFieldState(
                    value = ipv6Addr,
                    error = application.getString(R.string.invalid_ipv6),
                    isValid = false
                )
            )
            false
        }
    }

    fun setIpv6Enabled(enabled: Boolean) {
        prefs.ipv6 = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(ipv6Enabled = enabled)
        )
    }

    fun setUseTemplateEnabled(enabled: Boolean) {
        prefs.useTemplate = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(useTemplateEnabled = enabled)
        )
    }

    fun setHttpProxyEnabled(enabled: Boolean) {
        prefs.httpProxyEnabled = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(httpProxyEnabled = enabled)
        )
    }

    fun setBypassLanEnabled(enabled: Boolean) {
        prefs.bypassLan = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(bypassLanEnabled = enabled)
        )
    }

    fun setDisableVpnEnabled(enabled: Boolean) {
        prefs.disableVpn = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(disableVpn = enabled)
        )
    }

    fun importRuleFile(uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = fileManager.importRuleFile(uri, fileName)
            if (success) {
                when (fileName) {
                    "geoip.dat" -> {
                        _settingsState.value = _settingsState.value.copy(
                            files = _settingsState.value.files.copy(
                                isGeoipCustom = prefs.customGeoipImported
                            ),
                            info = _settingsState.value.info.copy(
                                geoipSummary = fileManager.getRuleFileSummary("geoip.dat")
                            )
                        )
                    }

                    "geosite.dat" -> {
                        _settingsState.value = _settingsState.value.copy(
                            files = _settingsState.value.files.copy(
                                isGeositeCustom = prefs.customGeositeImported
                            ),
                            info = _settingsState.value.info.copy(
                                geositeSummary = fileManager.getRuleFileSummary("geosite.dat")
                            )
                        )
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
            val intent = Intent(application, TProxyService::class.java).setAction(action)
            _uiEvent.emit(UiEvent.StartService(intent))
        }
    }

    fun editConfig(filePath: String) {
        viewModelScope.launch {
            val intent = Intent(application, ConfigEditActivity::class.java)
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
                application,
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
            val vpnIntent = VpnService.prepare(application)
            if (vpnIntent != null) {
                vpnPrepareLauncher.launch(vpnIntent)
            } else {
                startTProxyService(TProxyService.ACTION_CONNECT)
            }
        }
    }

    fun navigateToAppList() {
        viewModelScope.launch {
            val intent = Intent(application, AppListActivity::class.java)
            _uiEvent.emit(UiEvent.StartActivity(intent))
        }
    }

    fun moveConfigFile(fromIndex: Int, toIndex: Int) {
        val currentList = _configFiles.value.toMutableList()
        val movedItem = currentList.removeAt(fromIndex)
        currentList.add(toIndex, movedItem)
        _configFiles.value = currentList
        prefs.configFilesOrder = currentList.map { it.name }
    }

    fun refreshConfigFileList() {
        viewModelScope.launch(Dispatchers.IO) {
            val filesDir = application.filesDir
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

            _configFiles.value = newOrder
            prefs.configFilesOrder = newOrder.map { it.name }

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

    fun updateConnectivityTestTarget(target: String) {
        val isValid = try {
            val url = URL(target)
            url.protocol == "http" || url.protocol == "https"
        } catch (e: Exception) {
            false
        }
        if (isValid) {
            prefs.connectivityTestTarget = target
            _settingsState.value = _settingsState.value.copy(
                connectivityTestTarget = InputFieldState(target)
            )
        } else {
            _settingsState.value = _settingsState.value.copy(
                connectivityTestTarget = InputFieldState(
                    value = target,
                    error = application.getString(R.string.connectivity_test_invalid_url),
                    isValid = false
                )
            )
        }
    }

    fun updateConnectivityTestTimeout(timeout: String) {
        val timeoutInt = timeout.toIntOrNull()
        if (timeoutInt != null && timeoutInt > 0) {
            prefs.connectivityTestTimeout = timeoutInt
            _settingsState.value = _settingsState.value.copy(
                connectivityTestTimeout = InputFieldState(timeout)
            )
        } else {
            _settingsState.value = _settingsState.value.copy(
                connectivityTestTimeout = InputFieldState(
                    value = timeout,
                    error = application.getString(R.string.invalid_timeout),
                    isValid = false
                )
            )
        }
    }

    fun testConnectivity() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = prefs
            val url: URL
            try {
                url = URL(prefs.connectivityTestTarget)
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.connectivity_test_invalid_url)))
                return@launch
            }
            val host = url.host
            val port = if (url.port > 0) url.port else url.defaultPort
            val path = if (url.path.isNullOrEmpty()) "/" else url.path
            val isHttps = url.protocol == "https"
            val proxy =
                Proxy(Proxy.Type.SOCKS, InetSocketAddress(prefs.socksAddress, prefs.socksPort))
            val timeout = prefs.connectivityTestTimeout
            val start = System.currentTimeMillis()
            try {
                Socket(proxy).use { socket ->
                    socket.soTimeout = timeout
                    socket.connect(InetSocketAddress(host, port), timeout)
                    val (writer, reader) = if (isHttps) {
                        val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                            .createSocket(socket, host, port, true) as javax.net.ssl.SSLSocket
                        sslSocket.startHandshake()
                        Pair(
                            sslSocket.outputStream.bufferedWriter(),
                            sslSocket.inputStream.bufferedReader()
                        )
                    } else {
                        Pair(
                            socket.getOutputStream().bufferedWriter(),
                            socket.getInputStream().bufferedReader()
                        )
                    }
                    writer.write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n")
                    writer.flush()
                    val firstLine = reader.readLine()
                    val latency = System.currentTimeMillis() - start
                    if (firstLine != null && firstLine.startsWith("HTTP/")) {
                        _uiEvent.emit(
                            UiEvent.ShowSnackbar(
                                application.getString(
                                    R.string.connectivity_test_latency,
                                    latency.toInt()
                                )
                            )
                        )
                    } else {
                        _uiEvent.emit(
                            UiEvent.ShowSnackbar(
                                application.getString(R.string.connectivity_test_failed)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiEvent.emit(
                    UiEvent.ShowSnackbar(
                        application.getString(R.string.connectivity_test_failed)
                    )
                )
            }
        }
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

    fun restoreDefaultGeoip(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultGeoip()
            _settingsState.value = _settingsState.value.copy(
                files = _settingsState.value.files.copy(
                    isGeoipCustom = prefs.customGeoipImported
                ),
                info = _settingsState.value.info.copy(
                    geoipSummary = fileManager.getRuleFileSummary("geoip.dat")
                )
            )
            _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geoip_success)))
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Restored default geoip.dat.")
                callback()
            }
        }
    }

    fun restoreDefaultGeosite(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            fileManager.restoreDefaultGeosite()
            _settingsState.value = _settingsState.value.copy(
                files = _settingsState.value.files.copy(
                    isGeositeCustom = prefs.customGeositeImported
                ),
                info = _settingsState.value.info.copy(
                    geositeSummary = fileManager.getRuleFileSummary("geosite.dat")
                )
            )
            _uiEvent.emit(UiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geosite_success)))
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Restored default geosite.dat.")
                callback()
            }
        }
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
