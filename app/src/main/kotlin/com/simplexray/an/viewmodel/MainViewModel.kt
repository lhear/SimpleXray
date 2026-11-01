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
import com.simplexray.an.common.CoreStatsClient
import com.simplexray.an.common.ROUTE_APP_LIST
import com.simplexray.an.common.ROUTE_CONFIG_EDIT
import com.simplexray.an.common.ThemeMode
import com.simplexray.an.data.source.FileManager
import com.simplexray.an.data.source.LogFileManager
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.service.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "MainViewModel"

private const val EPHEMERAL_PORT_RANGE_START = 32768
private const val EPHEMERAL_PORT_RANGE_SIZE = 60999 - EPHEMERAL_PORT_RANGE_START + 1
private const val TEMP_SOCKS_PROBE_DELAY_MS = 500L
private const val TEMP_SOCKS_MAX_PROBES = 30
private const val TEMP_SOCKS_MIN_LIFETIME_MS = 10_000L
private const val TEMP_SOCKS_NO_TRAFFIC_TIMEOUT_MS = 10_000L

sealed class MainViewUiEvent {
    data class ShowSnackbar(val message: String) : MainViewUiEvent()
    data class ShareLauncher(val intent: Intent) : MainViewUiEvent()
    data class StartService(val intent: Intent) : MainViewUiEvent()
    data object RefreshConfigList : MainViewUiEvent()
    data class Navigate(val route: String) : MainViewUiEvent()
}

class MainViewModel(application: Application) :
    AndroidViewModel(application) {
    val prefs: Preferences = Preferences(application)
    private val activityScope: CoroutineScope = viewModelScope
    private var compressedBackupData: ByteArray? = null

    private var coreStatsClient: CoreStatsClient? = null

    private val tempSocksMutex = Mutex()
    private var tempSocksAddress: String = ""
    private var tempSocksPort: Int = -1
    private var tempSocksTag: String = ""
    private var tempSocksUser: String = ""
    private var tempSocksPass: String = ""
    private var activeProxiedTaskCount: Int = 0
    private var cleanupJob: Job? = null

    private val globalSocksAuthenticator = object : java.net.Authenticator() {
        override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
            val user = prefs.socksUsername
            val pass = prefs.socksPassword
            return if (user.isNotEmpty() || pass.isNotEmpty()) {
                java.net.PasswordAuthentication(user, pass.toCharArray())
            } else {
                null
            }
        }
    }

    private val fileManager: FileManager = FileManager(application, prefs)

    var reloadView: (() -> Unit)? = null

    lateinit var appListViewModel: AppListViewModel
    lateinit var configEditViewModel: ConfigEditViewModel

    private val _settingsState = MutableStateFlow(
        SettingsState(
            socksAddress = InputFieldState(prefs.socksAddress),
            socksPort = InputFieldState(prefs.socksPort.toString()),
            socksUser = InputFieldState(prefs.socksUsername),
            socksPass = InputFieldState(prefs.socksPassword),
            dnsIpv4 = InputFieldState(prefs.dnsIpv4),
            dnsIpv6 = InputFieldState(prefs.dnsIpv6),
            switches = SwitchStates(
                ipv6Enabled = prefs.ipv6,
                useTemplateEnabled = prefs.useTemplate,
                httpProxyEnabled = prefs.httpProxyEnabled,
                bypassLanEnabled = prefs.bypassLan,
                disableVpn = prefs.disableVpn,
                useXrayTun = prefs.useXrayTun,
                themeMode = prefs.theme
            ),
            info = InfoStates(
                appVersion = BuildConfig.VERSION_NAME,
                kernelVersion = "N/A",
                geoipSummary = "",
                geositeSummary = "",
                geoipUrl = prefs.geoipUrl,
                geositeUrl = prefs.geositeUrl
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

    private val _coreStatsState = MutableStateFlow(CoreStatsState())
    val coreStatsState: StateFlow<CoreStatsState> = _coreStatsState.asStateFlow()

    private val _controlMenuClickable = MutableStateFlow(true)
    val controlMenuClickable: StateFlow<Boolean> = _controlMenuClickable.asStateFlow()

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _uiEvent = Channel<MainViewUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _configFiles = MutableStateFlow<List<File>>(emptyList())
    val configFiles: StateFlow<List<File>> = _configFiles.asStateFlow()

    private val _selectedConfigFile = MutableStateFlow<File?>(null)
    val selectedConfigFile: StateFlow<File?> = _selectedConfigFile.asStateFlow()

    private val _geoipDownloadProgress = MutableStateFlow<String?>(null)
    val geoipDownloadProgress: StateFlow<String?> = _geoipDownloadProgress.asStateFlow()
    private var geoipDownloadJob: Job? = null

    private val _geositeDownloadProgress = MutableStateFlow<String?>(null)
    val geositeDownloadProgress: StateFlow<String?> = _geositeDownloadProgress.asStateFlow()
    private var geositeDownloadJob: Job? = null

    private val _isCheckingForUpdates = MutableStateFlow(false)
    val isCheckingForUpdates: StateFlow<Boolean> = _isCheckingForUpdates.asStateFlow()

    private val _newVersionAvailable = MutableStateFlow<String?>(null)
    val newVersionAvailable: StateFlow<String?> = _newVersionAvailable.asStateFlow()

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
            _coreStatsState.value = CoreStatsState()
            coreStatsClient?.close()
            coreStatsClient = null
            viewModelScope.launch(Dispatchers.IO) { cleanupTempSocksIfActive() }
        }
    }

    init {
        Log.d(TAG, "MainViewModel initialized.")

        setupGlobalSocksAuthenticator()

        viewModelScope.launch(Dispatchers.IO) {
            _isServiceEnabled.value = isServiceRunning(application, TProxyService::class.java)

            updateSettingsState()
            loadKernelVersion()
            refreshConfigFileList()
        }
    }

    private fun updateSettingsState() {
        _settingsState.value = _settingsState.value.copy(
            socksAddress = InputFieldState(prefs.socksAddress),
            socksPort = InputFieldState(prefs.socksPort.toString()),
            socksUser = InputFieldState(prefs.socksUsername),
            socksPass = InputFieldState(prefs.socksPassword),
            dnsIpv4 = InputFieldState(prefs.dnsIpv4),
            dnsIpv6 = InputFieldState(prefs.dnsIpv6),
            switches = SwitchStates(
                ipv6Enabled = prefs.ipv6,
                useTemplateEnabled = prefs.useTemplate,
                httpProxyEnabled = prefs.httpProxyEnabled,
                bypassLanEnabled = prefs.bypassLan,
                disableVpn = prefs.disableVpn,
                useXrayTun = prefs.useXrayTun,
                themeMode = prefs.theme
            ),
            info = _settingsState.value.info.copy(
                appVersion = BuildConfig.VERSION_NAME,
                geoipSummary = fileManager.getRuleFileSummary("geoip.dat"),
                geositeSummary = fileManager.getRuleFileSummary("geosite.dat"),
                geoipUrl = prefs.geoipUrl,
                geositeUrl = prefs.geositeUrl
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

    private fun setupGlobalSocksAuthenticator() {
        java.net.Authenticator.setDefault(globalSocksAuthenticator)
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
                            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.backup_success)))
                        } else {
                            Log.e(TAG, "Failed to open output stream for backup URI: $uri")
                            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.backup_failed)))
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error writing backup data to URI: $uri", e)
                    _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.backup_failed)))
                }
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.backup_failed)))
                Log.e(TAG, "Compressed backup data is null in launcher callback.")
            }
        }
    }

    suspend fun startRestoreTask(uri: Uri) {
        withContext(Dispatchers.IO) {
            val success = fileManager.decompressAndRestore(uri)
            if (success) {
                updateSettingsState()
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.restore_success)))
                Log.d(TAG, "Restore successful.")
                refreshConfigFileList()
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.restore_failed)))
            }
        }
    }

    suspend fun createConfigFile(): String? {
        val filePath = fileManager.createConfigFile(application.assets)
        if (filePath == null) {
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.create_config_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }

    suspend fun updateCoreStats() {
        if (!_isServiceEnabled.value) return
        if (coreStatsClient == null)
            coreStatsClient = CoreStatsClient.create(prefs.apiAddress, prefs.apiPort)

        val stats = coreStatsClient?.getSystemStats()
        val traffic = coreStatsClient?.getTraffic()

        if (stats == null && traffic == null) {
            coreStatsClient?.close()
            coreStatsClient = null
            return
        }

        _coreStatsState.value = CoreStatsState(
            uplink = traffic?.uplink ?: 0,
            downlink = traffic?.downlink ?: 0,
            numGoroutine = stats?.numGoroutine ?: 0,
            numGC = stats?.numGC ?: 0,
            alloc = stats?.alloc ?: 0,
            totalAlloc = stats?.totalAlloc ?: 0,
            sys = stats?.sys ?: 0,
            mallocs = stats?.mallocs ?: 0,
            frees = stats?.frees ?: 0,
            liveObjects = stats?.liveObjects ?: 0,
            pauseTotalNs = stats?.pauseTotalNs ?: 0,
            uptime = stats?.uptime ?: 0
        )
        Log.d(TAG, "Core stats updated")
    }

    suspend fun importConfigFromClipboard(): String? {
        val filePath = fileManager.importConfigFromClipboard()
        if (filePath == null) {
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.import_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }

    suspend fun handleSharedContent(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!fileManager.importConfigFromContent(content).isNullOrEmpty()) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.import_success)))
                refreshConfigFileList()
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.invalid_config_format)))
            }
        }
    }

    suspend fun deleteConfigFile(file: File, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isServiceEnabled.value && _selectedConfigFile.value != null &&
                _selectedConfigFile.value == file
            ) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.config_in_use)))
                Log.w(TAG, "Attempted to delete selected config file: ${file.name}")
                return@launch
            }

            val success = fileManager.deleteConfigFile(file)
            if (success) {
                withContext(Dispatchers.Main) {
                    refreshConfigFileList()
                }
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.delete_fail)))
            }
            callback()
        }
    }

    fun extractAssetsIfNeeded() {
        fileManager.extractAssetsIfNeeded()
    }

    fun updateSocksAddress(addressString: String): Boolean {
        val matcherIpv4 = IPV4_PATTERN.matcher(addressString)
        val matcherIpv6 = IPV6_PATTERN.matcher(addressString)
        return if (matcherIpv4.matches()) {
            prefs.socksAddress = addressString
            _settingsState.value = _settingsState.value.copy(
                socksAddress = InputFieldState(addressString)
            )
            true
        } else if (matcherIpv6.matches()) {
            prefs.socksAddress = addressString
            _settingsState.value = _settingsState.value.copy(
                socksAddress = InputFieldState(addressString)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                socksAddress = InputFieldState(
                    value = addressString,
                    error = application.getString(R.string.invalid_ipv4_or_ipv6),
                    isValid = false
                )
            )
            false
        }
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

    fun updateSocksUser(userString: String): Boolean {
        val byteCount = userString.toByteArray(Charsets.UTF_8).size
        return if (byteCount <= 255) {
            prefs.socksUsername = userString
            _settingsState.value = _settingsState.value.copy(
                socksUser = InputFieldState(userString)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                socksUser = InputFieldState(
                    value = userString,
                    error = "Username length must not exceed 255 bytes",
                    isValid = false
                )
            )
            false
        }
    }

    fun updateSocksPass(passString: String): Boolean {
        val byteCount = passString.toByteArray(Charsets.UTF_8).size
        return if (byteCount <= 255) {
            prefs.socksPassword = passString
            _settingsState.value = _settingsState.value.copy(
                socksPass = InputFieldState(passString)
            )
            true
        } else {
            _settingsState.value = _settingsState.value.copy(
                socksPass = InputFieldState(
                    value = passString,
                    error = "Password length must not exceed 255 bytes",
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

    fun setUseXrayTun(enabled: Boolean) {
        prefs.useXrayTun = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(useXrayTun = enabled)
        )
    }

    fun setTheme(mode: ThemeMode) {
        prefs.theme = mode
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(themeMode = mode)
        )
        reloadView?.invoke()
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
                _uiEvent.trySend(
                    MainViewUiEvent.ShowSnackbar(
                        "$fileName ${application.getString(R.string.import_success)}"
                    )
                )
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.import_failed)))
            }
        }
    }

    fun showExportFailedSnackbar() {
        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.export_failed)))
    }

    fun startTProxyService(action: String) {
        viewModelScope.launch {
            if (_selectedConfigFile.value == null) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.not_select_config)))
                Log.w(TAG, "Cannot start service: no config file selected.")
                setControlMenuClickable(true)
                return@launch
            }
            val intent = Intent(application, TProxyService::class.java).setAction(action)
            _uiEvent.trySend(MainViewUiEvent.StartService(intent))
        }
    }

    fun editConfig(filePath: String) {
        viewModelScope.launch {
            configEditViewModel = ConfigEditViewModel(application, filePath, prefs)
            _uiEvent.trySend(MainViewUiEvent.Navigate(ROUTE_CONFIG_EDIT))
        }
    }

    fun shareIntent(chooserIntent: Intent, packageManager: PackageManager) {
        viewModelScope.launch {
            if (chooserIntent.resolveActivity(packageManager) != null) {
                _uiEvent.trySend(MainViewUiEvent.ShareLauncher(chooserIntent))
                Log.d(TAG, "Export intent resolved and started.")
            } else {
                Log.w(TAG, "No activity found to handle export intent.")
                _uiEvent.trySend(
                    MainViewUiEvent.ShowSnackbar(
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
            _uiEvent.trySend(MainViewUiEvent.StartService(intent))
        }
    }

    fun prepareAndStartVpn(vpnPrepareLauncher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            if (_selectedConfigFile.value == null) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.not_select_config)))
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
            appListViewModel = AppListViewModel(application)
            _uiEvent.trySend(MainViewUiEvent.Navigate(ROUTE_APP_LIST))
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
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.connectivity_test_invalid_url)))
                return@launch
            }

            val host = url.host
            val urlPort = if (url.port > 0) url.port else url.defaultPort
            val path = if (url.path.isNullOrEmpty()) "/" else url.path
            val isHttps = url.protocol == "https"
            val timeout = prefs.connectivityTestTimeout

            fun doTest(proxy: Proxy) {
                val start = System.currentTimeMillis()
                try {
                    Socket(proxy).use { socket ->
                        socket.soTimeout = timeout
                        socket.connect(InetSocketAddress(host, urlPort), timeout)
                        val (writer, reader) = if (isHttps) {
                            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                                .createSocket(socket, host, urlPort, true) as javax.net.ssl.SSLSocket
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
                            _uiEvent.trySend(
                                MainViewUiEvent.ShowSnackbar(
                                    application.getString(R.string.connectivity_test_latency, latency.toInt())
                                )
                            )
                        } else {
                            _uiEvent.trySend(
                                MainViewUiEvent.ShowSnackbar(application.getString(R.string.connectivity_test_failed))
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connectivity test failed for ${prefs.connectivityTestTarget}", e)
                    _uiEvent.trySend(
                        MainViewUiEvent.ShowSnackbar(application.getString(R.string.connectivity_test_failed))
                    )
                }
            }

            if (_isServiceEnabled.value && prefs.isXrayTunActive) {
                try {
                    val (address, socksPort) = ensureTempSocksReady()
                    val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(address, socksPort))
                    try {
                        doTest(proxy)
                    } finally {
                        decrementAndCleanupIfNeeded()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set up temporary proxy for connectivity test", e)
                    _uiEvent.trySend(
                        MainViewUiEvent.ShowSnackbar(application.getString(R.string.connectivity_test_failed))
                    )
                }
            } else {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(prefs.socksAddress, prefs.socksPort))
                doTest(proxy)
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
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geoip_success)))
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
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.rule_file_restore_geosite_success)))
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Restored default geosite.dat.")
                callback()
            }
        }
    }

    fun cancelDownload(fileName: String) {
        viewModelScope.launch {
            if (fileName == "geoip.dat") {
                geoipDownloadJob?.cancel()
            } else {
                geositeDownloadJob?.cancel()
            }
            Log.d(TAG, "Download cancellation requested for $fileName")
        }
    }

    private fun buildHttpClient(): OkHttpClient {
        val serviceActive = _isServiceEnabled.value
        return OkHttpClient.Builder().apply {
            if (serviceActive && !prefs.isXrayTunActive) {
                proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort)))
            }
            readTimeout(TEMP_SOCKS_NO_TRAFFIC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }.build()
    }

    private fun appendToAppLog(message: String) {
        try {
            LogFileManager(application).appendLog(message)
            val intent = Intent(TProxyService.ACTION_LOG_UPDATE)
            intent.setPackage(application.packageName)
            intent.putStringArrayListExtra(TProxyService.EXTRA_LOG_DATA, arrayListOf(message))
            application.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to append message to app log", e)
        }
    }

    private suspend fun waitForSocksProxy(address: String, port: Int) {
        repeat(TEMP_SOCKS_MAX_PROBES) {
            delay(TEMP_SOCKS_PROBE_DELAY_MS)
            if (runCatching {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(address, port), TEMP_SOCKS_PROBE_DELAY_MS.toInt())
                    }
                }.isSuccess
            ) return
        }
        throw IOException("Temporary SOCKS5 inbound did not bind on $address:$port within the expected time")
    }

    private fun cleanupTempSocksLocked(restartProxy: Boolean) {
        activeProxiedTaskCount = 0
        tempSocksAddress = ""
        tempSocksPort = -1
        tempSocksTag = ""
        tempSocksUser = ""
        tempSocksPass = ""
        cleanupJob?.cancel()
        cleanupJob = null
        java.net.Authenticator.setDefault(globalSocksAuthenticator)
        if (restartProxy && _isServiceEnabled.value) {
            appendToAppLog("[SimpleXray] Removing temporary SOCKS5 inbound, reloading proxy.")
            application.startService(
                Intent(application, TProxyService::class.java)
                    .setAction(TProxyService.ACTION_RELOAD_CONFIG)
            )
        }
    }

    private suspend fun ensureTempSocksReady(): Pair<String, Int> = tempSocksMutex.withLock {
        if (tempSocksPort > 0) {
            cleanupJob?.cancel()
            cleanupJob = null
            activeProxiedTaskCount++
            return@withLock Pair(tempSocksAddress, tempSocksPort)
        }

        val rng = java.security.SecureRandom()
        val randomAddr = "127.${rng.nextInt(254) + 1}.${rng.nextInt(254) + 1}.${rng.nextInt(254) + 1}"
        val randomUser = ByteArray(8).also(rng::nextBytes).joinToString("") { "%02x".format(it) }
        val randomPass = ByteArray(8).also(rng::nextBytes).joinToString("") { "%02x".format(it) }
        val tag = "temp-socks-${ByteArray(4).also(rng::nextBytes).joinToString("") { "%02x".format(it) }}"

        val port = run {
            var p: Int? = null
            repeat(20) {
                val candidate = EPHEMERAL_PORT_RANGE_START + rng.nextInt(EPHEMERAL_PORT_RANGE_SIZE)
                if (runCatching { java.net.ServerSocket(candidate).close() }.isSuccess) {
                    p = candidate
                    return@repeat
                }
            }
            p
        } ?: throw IOException("No free local port available for temporary SOCKS5 inbound")

        val tempConfigJson = com.simplexray.an.common.ConfigUtils
            .buildTempSocksConfigJson(randomAddr, port, tag, randomUser, randomPass)

        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication() =
                java.net.PasswordAuthentication(randomUser, randomPass.toCharArray())
        })

        tempSocksAddress = randomAddr
        tempSocksPort = port
        tempSocksTag = tag
        tempSocksUser = randomUser
        tempSocksPass = randomPass
        activeProxiedTaskCount = 1

        appendToAppLog("[SimpleXray] Injecting temporary SOCKS5 inbound at $randomAddr:$port, reloading proxy.")
        application.startService(
            Intent(application, TProxyService::class.java)
                .setAction(TProxyService.ACTION_RELOAD_CONFIG)
                .putExtra(TProxyService.EXTRA_TEMP_SOCKS_CONFIG, tempConfigJson)
        )

        try {
            waitForSocksProxy(randomAddr, port)
        } catch (e: IOException) {
            cleanupTempSocksLocked(restartProxy = true)
            throw e
        }

        Pair(tempSocksAddress, tempSocksPort)
    }

    private suspend fun decrementAndCleanupIfNeeded() {
        tempSocksMutex.withLock {
            activeProxiedTaskCount = (activeProxiedTaskCount - 1).coerceAtLeast(0)
            if (activeProxiedTaskCount == 0 && tempSocksPort > 0) {
                cleanupJob?.cancel()
                cleanupJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(TEMP_SOCKS_MIN_LIFETIME_MS)
                    tempSocksMutex.withLock {
                        if (activeProxiedTaskCount == 0 && tempSocksPort > 0) {
                            cleanupTempSocksLocked(restartProxy = true)
                        }
                        cleanupJob = null
                    }
                }
            } else if (activeProxiedTaskCount > 0 && tempSocksPort <= 0) {
                Log.e(TAG, "Inconsistent temp SOCKS state: count=$activeProxiedTaskCount but port=$tempSocksPort; forcing cleanup")
                cleanupTempSocksLocked(restartProxy = true)
            }
        }
    }

    private suspend fun cleanupTempSocksIfActive() {
        tempSocksMutex.withLock {
            if (activeProxiedTaskCount > 0 || tempSocksPort > 0 || cleanupJob != null) {
                cleanupTempSocksLocked(restartProxy = false)
            }
        }
    }

    private suspend fun <T> withTempSocksProxiedClient(
        connectTimeoutMs: Long = 30_000L,
        readTimeoutMs: Long = TEMP_SOCKS_NO_TRAFFIC_TIMEOUT_MS,
        block: suspend (OkHttpClient) -> T
    ): T {
        val (address, port) = ensureTempSocksReady()
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(address, port))
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
        try {
            return block(client)
        } finally {
            decrementAndCleanupIfNeeded()
        }
    }

    fun downloadRuleFile(url: String, fileName: String) {
        val currentJob = if (fileName == "geoip.dat") geoipDownloadJob else geositeDownloadJob
        if (currentJob?.isActive == true) {
            Log.w(TAG, "Download already in progress for $fileName")
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

            suspend fun doDownload(client: OkHttpClient) {
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
                            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_success)))
                        } else {
                            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_failed)))
                        }
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "Download cancelled for $fileName")
                    _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_cancelled)))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download rule file", e)
                    _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_failed) + ": " + e.message))
                } finally {
                    progressFlow.value = null
                    updateSettingsState()
                }
            }

            if (_isServiceEnabled.value && prefs.isXrayTunActive) {
                try {
                    withTempSocksProxiedClient { client -> doDownload(client) }
                } catch (e: CancellationException) {
                    Log.d(TAG, "Download cancelled while setting up proxy for $fileName")
                    _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_cancelled)))
                    progressFlow.value = null
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set up temporary proxy for download of $fileName", e)
                    _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(application.getString(R.string.download_failed) + ": " + e.message))
                    progressFlow.value = null
                }
            } else {
                doDownload(buildHttpClient())
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response, null)
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

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingForUpdates.value = true

            val request = Request.Builder()
                .url(application.getString(R.string.source_url) + "/releases/latest")
                .head()
                .build()

            suspend fun doCheck(client: OkHttpClient) {
                try {
                    val response = client.newCall(request).await()
                    val location = response.request.url.toString()
                    val latestTag = location.substringAfterLast("/tag/v")
                    Log.d(TAG, "Latest version tag: $latestTag")
                    val updateAvailable = compareVersions(latestTag) > 0
                    if (updateAvailable) {
                        _newVersionAvailable.value = latestTag
                    } else {
                        _uiEvent.trySend(
                            MainViewUiEvent.ShowSnackbar(
                                application.getString(R.string.no_new_version_available)
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check for updates", e)
                    _uiEvent.trySend(
                        MainViewUiEvent.ShowSnackbar(
                            application.getString(R.string.failed_to_check_for_updates) + ": " + e.message
                        )
                    )
                } finally {
                    _isCheckingForUpdates.value = false
                }
            }

            if (_isServiceEnabled.value && prefs.isXrayTunActive) {
                try {
                    withTempSocksProxiedClient { client -> doCheck(client) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set up temporary proxy for update check", e)
                    _uiEvent.trySend(
                        MainViewUiEvent.ShowSnackbar(
                            application.getString(R.string.failed_to_check_for_updates) + ": " + e.message
                        )
                    )
                    _isCheckingForUpdates.value = false
                }
            } else {
                doCheck(buildHttpClient())
            }
        }
    }

    fun downloadNewVersion(versionTag: String) {
        val url = application.getString(R.string.source_url) + "/releases/tag/v$versionTag"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
        _newVersionAvailable.value = null
    }

    fun clearNewVersionAvailable() {
        _newVersionAvailable.value = null
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

    companion object {
        private const val IPV4_REGEX =
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        private val IPV4_PATTERN: Pattern = Pattern.compile(IPV4_REGEX)
        private const val IPV6_REGEX =
            "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80::(fe80(:[0-9a-fA-F]{0,4})?){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d)|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d))$"
        private val IPV6_PATTERN: Pattern = Pattern.compile(IPV6_REGEX)

        @Suppress("DEPRECATION")
        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return activityManager.getRunningServices(Int.MAX_VALUE).any { service ->
                serviceClass.name == service.service.className
            }
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

