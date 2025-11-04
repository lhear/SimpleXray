package com.simplexray.an.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import com.simplexray.an.common.AppLogger
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
import com.simplexray.an.common.ServiceStateChecker
import com.simplexray.an.common.ThemeMode
import com.simplexray.an.common.error.AppError
import com.simplexray.an.common.error.ErrorHandler
import com.simplexray.an.common.error.runCatchingWithError
import com.simplexray.an.common.error.runSuspendCatchingWithError
import com.simplexray.an.common.error.toAppError
import com.simplexray.an.data.source.FileManager
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.service.TProxyService
import com.simplexray.an.update.UpdateManager
import com.simplexray.an.update.DownloadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "MainViewModel"

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
    private val coreStatsClientMutex = Mutex()

    private val fileManager: FileManager = FileManager(application, prefs)

    // StateFlow used for theme changes - callback removed for better reactivity
    private val _themeChanged = MutableStateFlow<Unit?>(null)
    val themeChanged: StateFlow<Unit?> = _themeChanged.asStateFlow()

    lateinit var appListViewModel: AppListViewModel
    
    // Specialized ViewModels
    lateinit var configViewModel: ConfigViewModel
    lateinit var connectionViewModel: ConnectionViewModel
    lateinit var downloadViewModel: DownloadViewModel
    lateinit var updateViewModel: UpdateViewModel
    
    // Keep for backward compatibility
    lateinit var configEditViewModel: ConfigEditViewModel

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
                disableVpn = prefs.disableVpn,
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
    private val updateManager = UpdateManager(application)
    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()
    private var currentDownloadId: Long? = null
    
    // Download completion state - holds APK URI and file path when download completes
    data class DownloadCompletion(val uri: Uri, val filePath: String?)
    private val _downloadCompletion = MutableStateFlow<DownloadCompletion?>(null)
    val downloadCompletion: StateFlow<DownloadCompletion?> = _downloadCompletion.asStateFlow()

    private val startReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppLogger.d("Service started")
            setServiceEnabled(true)
            setControlMenuClickable(true)
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppLogger.d("Service stopped")
            setServiceEnabled(false)
            setControlMenuClickable(true)
            _coreStatsState.value = CoreStatsState()
            coreStatsClient?.close()
            coreStatsClient = null
        }
    }

    init {
        AppLogger.d("MainViewModel initialized.")
        
        // Initialize specialized ViewModels
        val uiEventSender: (MainViewUiEvent) -> Unit = { event ->
            _uiEvent.trySend(event)
        }
        
        // Initialize service enabled state first
        viewModelScope.launch(Dispatchers.IO) {
            _isServiceEnabled.value = isServiceRunning(application, TProxyService::class.java)
        }
        
        // Initialize ConfigViewModel first (needs service enabled state)
        configViewModel = ConfigViewModel(
            application,
            prefs,
            _isServiceEnabled.asStateFlow(),
            uiEventSender
        )
        
        // Initialize ConnectionViewModel (needs config file state)
        connectionViewModel = ConnectionViewModel(
            application,
            prefs,
            configViewModel.selectedConfigFile,
            uiEventSender
        )
        
        // Sync service enabled state between ConnectionViewModel and MainViewModel
        // Also clear core stats when service stops
        var previousEnabled = _isServiceEnabled.value
        viewModelScope.launch {
            connectionViewModel.isServiceEnabled.collect { enabled ->
                _isServiceEnabled.value = enabled
                // Clear core stats when service stops
                if (previousEnabled && !enabled) {
                    _coreStatsState.value = CoreStatsState()
                    coreStatsClientMutex.withLock {
                        coreStatsClient?.close()
                        coreStatsClient = null
                    }
                }
                previousEnabled = enabled
            }
        }
        
        // Initialize DownloadViewModel (needs service enabled state)
        downloadViewModel = DownloadViewModel(
            application,
            prefs,
            connectionViewModel.isServiceEnabled,
            uiEventSender
        )
        
        // Initialize UpdateViewModel (needs service enabled state)
        updateViewModel = UpdateViewModel(
            application,
            prefs,
            connectionViewModel.isServiceEnabled,
            uiEventSender
        )
        
        viewModelScope.launch(Dispatchers.IO) {
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
                disableVpn = prefs.disableVpn,
                themeMode = prefs.theme,
                enablePerformanceMode = prefs.enablePerformanceMode
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

    // Cached kernel version to avoid repeated process execution
    private var cachedKernelVersion: String? = null
    private var kernelVersionCacheTime: Long = 0
    private val KERNEL_VERSION_CACHE_TTL_MS = 3600000L // 1 hour
    
    private fun loadKernelVersion() {
        viewModelScope.launch(Dispatchers.IO) {
            // Return cached version if still valid
            val now = System.currentTimeMillis()
            if (cachedKernelVersion != null && (now - kernelVersionCacheTime) < KERNEL_VERSION_CACHE_TTL_MS) {
                _settingsState.value = _settingsState.value.copy(
                    info = _settingsState.value.info.copy(
                        kernelVersion = cachedKernelVersion ?: "N/A"
                    )
                )
                return@launch
            }
            
            val result = runSuspendCatchingWithError {
                val libraryDir = TProxyService.getNativeLibraryDir(application)
                val xrayPath = "$libraryDir/libxray.so"
                // Use ProcessBuilder with timeout for better control
                val processBuilder = java.lang.ProcessBuilder(xrayPath, "-version")
                val process = processBuilder.start()
                
                val firstLine = withContext(Dispatchers.IO) {
                    InputStreamReader(process.inputStream).use { isr ->
                        BufferedReader(isr).use { reader ->
                            reader.readLine()
                        }
                    }
                }
                
                // Wait with timeout (5 seconds) to prevent indefinite blocking
                val exited = process.waitFor(5, TimeUnit.SECONDS)
                if (!exited) {
                    process.destroyForcibly()
                    throw IOException("Process timeout")
                }
                
                val version = firstLine ?: "N/A"
                // Cache the result
                cachedKernelVersion = version
                kernelVersionCacheTime = now
                version
            }
            
            result.fold(
                onSuccess = { version ->
                    _settingsState.value = _settingsState.value.copy(
                        info = _settingsState.value.info.copy(
                            kernelVersion = version
                        )
                    )
                },
                onFailure = { throwable ->
                    val appError = throwable.toAppError()
                    ErrorHandler.handleError(appError, TAG)
                    _settingsState.value = _settingsState.value.copy(
                        info = _settingsState.value.info.copy(
                            kernelVersion = "N/A"
                        )
                    )
                }
            )
        }
    }

    // Delegate to ConnectionViewModel
    fun setControlMenuClickable(isClickable: Boolean) = 
        connectionViewModel.setControlMenuClickable(isClickable)
    fun setServiceEnabled(enabled: Boolean) {
        // Update both ConnectionViewModel and MainViewModel state
        connectionViewModel.setServiceEnabled(enabled)
        // Also update local state if needed (it should sync via flow)
        // The state will be synced through the flow collector in init
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
                    // Check if output stream is null before using it
                    val outputStream = getApplication<Application>().contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        outputStream.use { os ->
                            os.write(dataToWrite)
                            AppLogger.d("Backup successful to: $uri")
                            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.backup_success)))
                        }
                    } else {
                        AppLogger.e( "Failed to open output stream for backup URI: $uri")
                        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.backup_failed)))
                    }
                } catch (e: IOException) {
                    AppLogger.e( "Error writing backup data to URI: $uri", e)
                    _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.backup_failed)))
                }
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.backup_failed)))
                AppLogger.e( "Compressed backup data is null in launcher callback.")
            }
        }
    }

    suspend fun startRestoreTask(uri: Uri) {
        withContext(Dispatchers.IO) {
            val success = fileManager.decompressAndRestore(uri)
            if (success) {
                updateSettingsState()
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.restore_success)))
                AppLogger.d("Restore successful.")
                refreshConfigFileList()
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.restore_failed)))
            }
        }
    }

    suspend fun createConfigFile(): String? {
        val filePath = fileManager.createConfigFile(getApplication<Application>().assets)
        if (filePath == null) {
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.create_config_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }

    // Rate limiting for core stats updates
    private var lastCoreStatsUpdate: Long = 0
    private val CORE_STATS_UPDATE_INTERVAL_MS = 500L // 500ms minimum interval
    
    suspend fun updateCoreStats() {
        // Rate limit updates to prevent excessive polling
        val now = System.currentTimeMillis()
        if (now - lastCoreStatsUpdate < CORE_STATS_UPDATE_INTERVAL_MS) {
            return
        }
        lastCoreStatsUpdate = now
        if (!_isServiceEnabled.value) return

        // Use Mutex instead of synchronized for suspend functions
        // Connection retry logic with exponential backoff
        var retryCount = 0
        val maxRetries = 3
        var connected = false
        while (retryCount < maxRetries && !connected) {
            val shouldRetry = coreStatsClientMutex.withLock {
                if (coreStatsClient == null) {
                    try {
                        coreStatsClient = CoreStatsClient.create("127.0.0.1", prefs.apiPort)
                        true
                    } catch (e: Exception) {
                        AppLogger.w("Failed to create CoreStatsClient (attempt ${retryCount + 1}/$maxRetries)", e)
                        false
                    }
                } else {
                    true
                }
            }
            if (shouldRetry) {
                connected = true
            } else {
                if (retryCount < maxRetries - 1) {
                    delay((1 shl retryCount) * 100L) // Exponential backoff: 100ms, 200ms, 400ms
                }
                retryCount++
            }
        }
        if (!connected) {
            return
        }

        val stats = coreStatsClientMutex.withLock { coreStatsClient?.getSystemStats() }
        val traffic = coreStatsClientMutex.withLock { coreStatsClient?.getTraffic() }

        if (stats == null && traffic == null) {
            coreStatsClientMutex.withLock {
                coreStatsClient?.close()
                coreStatsClient = null
            }
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
        AppLogger.d("Core stats updated")
    }

    suspend fun importConfigFromClipboard(): String? {
        val filePath = fileManager.importConfigFromClipboard()
        if (filePath == null) {
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.import_failed)))
        } else {
            refreshConfigFileList()
        }
        return filePath
    }

    suspend fun handleSharedContent(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!fileManager.importConfigFromContent(content).isNullOrEmpty()) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.import_success)))
                refreshConfigFileList()
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.invalid_config_format)))
            }
        }
    }

    suspend fun deleteConfigFile(file: File, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isServiceEnabled.value && _selectedConfigFile.value != null &&
                _selectedConfigFile.value == file
            ) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.config_in_use)))
                AppLogger.w( "Attempted to delete selected config file: ${file.name}")
                return@launch
            }

            val success = fileManager.deleteConfigFile(file)
            if (success) {
                withContext(Dispatchers.Main) {
                    refreshConfigFileList()
                }
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.delete_fail)))
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
                        error = getApplication<Application>().getString(R.string.invalid_port_range),
                        isValid = false
                    )
                )
                false
            }
        } catch (e: NumberFormatException) {
            _settingsState.value = _settingsState.value.copy(
                socksPort = InputFieldState(
                    value = portString,
                    error = getApplication<Application>().getString(R.string.invalid_port),
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
                    error = getApplication<Application>().getString(R.string.invalid_ipv4),
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
                    error = getApplication<Application>().getString(R.string.invalid_ipv6),
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

    fun setEnablePerformanceMode(enabled: Boolean) {
        prefs.enablePerformanceMode = enabled
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(enablePerformanceMode = enabled)
        )
        AppLogger.d("Performance mode ${if (enabled) "enabled" else "disabled"}")
    }

    fun setTheme(mode: ThemeMode) {
        prefs.theme = mode
        _settingsState.value = _settingsState.value.copy(
            switches = _settingsState.value.switches.copy(themeMode = mode)
        )
        _themeChanged.value = Unit
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
                        "$fileName ${getApplication<Application>().getString(R.string.import_success)}"
                    )
                )
            } else {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.import_failed)))
            }
        }
    }

    fun showExportFailedSnackbar() {
        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.export_failed)))
    }

    fun startTProxyService(action: String) {
        viewModelScope.launch {
            if (_selectedConfigFile.value == null) {
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.not_select_config)))
                AppLogger.w( "Cannot start service: no config file selected.")
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
                AppLogger.d("Export intent resolved and started.")
            } else {
                AppLogger.w( "No activity found to handle export intent.")
                _uiEvent.trySend(
                    MainViewUiEvent.ShowSnackbar(
                        getApplication<Application>().getString(R.string.no_app_for_export)
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
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.not_select_config)))
                AppLogger.w( "Cannot prepare VPN: no config file selected.")
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

    fun navigateToPerformance() {
        _uiEvent.trySend(MainViewUiEvent.Navigate(com.simplexray.an.common.ROUTE_PERFORMANCE))
    }
    
    fun navigateToAdvancedPerformanceSettings() {
        _uiEvent.trySend(MainViewUiEvent.Navigate(com.simplexray.an.common.ROUTE_ADVANCED_PERFORMANCE_SETTINGS))
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
            val filesDir = getApplication<Application>().filesDir
            // Use sequence for better memory efficiency with large directories
            val actualFiles = filesDir.listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.toList()
                ?: emptyList()
            
            // Use sequence for associateBy to avoid unnecessary allocations
            val actualFilesByName = actualFiles.asSequence().associateBy { it.name }
            val savedOrder = prefs.configFilesOrder

            val newOrder = mutableListOf<File>()
            val remainingActualFileNames = actualFilesByName.toMutableMap()

            // Use iterator for better performance
            savedOrder.iterator().forEach { filename ->
                actualFilesByName[filename]?.let { file ->
                    newOrder.add(file)
                    remainingActualFileNames.remove(filename)
                }
            }

            // Use sequence for remaining files
            newOrder.addAll(remainingActualFileNames.values.asSequence().filter { it !in newOrder })

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
                    error = getApplication<Application>().getString(R.string.connectivity_test_invalid_url),
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
                    error = getApplication<Application>().getString(R.string.invalid_timeout),
                    isValid = false
                )
            )
        }
    }

    // Connectivity test result caching
    private var cachedConnectivityResult: Pair<Long, Int>? = null // (timestamp, latency)
    private val CONNECTIVITY_CACHE_TTL_MS = 5000L // 5 seconds
    
    fun testConnectivity() {
        viewModelScope.launch(Dispatchers.IO) {
            ensureActive()
            
            // Return cached result if still valid
            val now = System.currentTimeMillis()
            cachedConnectivityResult?.let { (timestamp, latency) ->
                if (now - timestamp < CONNECTIVITY_CACHE_TTL_MS) {
                    _uiEvent.trySend(
                        MainViewUiEvent.ShowSnackbar(
                            getApplication<Application>().getString(
                                R.string.connectivity_test_latency,
                                latency
                            )
                        )
                    )
                    return@launch
                }
            }
            
            val prefs = prefs
            
            // Validate URL before parsing
            if (prefs.connectivityTestTarget.isEmpty() || 
                !prefs.connectivityTestTarget.startsWith("http://") && 
                !prefs.connectivityTestTarget.startsWith("https://")) {
                _uiEvent.trySend(
                    MainViewUiEvent.ShowSnackbar(
                        getApplication<Application>().getString(R.string.connectivity_test_invalid_url)
                    )
                )
                return@launch
            }
            
            // Parse URL with error handling
            val urlResult = runSuspendCatchingWithError {
                URL(prefs.connectivityTestTarget)
            }
            
            val url = urlResult.getOrElse { throwable ->
                val appError = throwable.toAppError()
                ErrorHandler.handleError(appError, TAG)
                _uiEvent.trySend(
                    MainViewUiEvent.ShowSnackbar(
                        getApplication<Application>().getString(R.string.connectivity_test_invalid_url)
                    )
                )
                return@launch
            }
            
            val host = url.host
            val port = if (url.port > 0) url.port else url.defaultPort
            val path = if (url.path.isNullOrEmpty()) "/" else url.path
            val isHttps = url.protocol == "https"
            // Cache InetSocketAddress to avoid repeated allocations
            val proxyAddress = InetSocketAddress(prefs.socksAddress, prefs.socksPort)
            val proxy = Proxy(Proxy.Type.SOCKS, proxyAddress)
            val timeout = prefs.connectivityTestTimeout.coerceAtLeast(1000).coerceAtMost(30000) // 1-30s
            
            val start = System.currentTimeMillis()
            
            // Execute connectivity test with error handling
            // Note: Connection pooling would require more complex state management
            val testResult = runSuspendCatchingWithError {
                // Cache target address
                val targetAddress = InetSocketAddress(host, port)
                Socket(proxy).use { socket ->
                    socket.soTimeout = timeout
                    socket.connect(targetAddress, timeout)

                    if (isHttps) {
                        // For HTTPS, properly manage SSL socket lifecycle
                        val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                            .createSocket(socket, host, port, true) as javax.net.ssl.SSLSocket
                        try {
                            // Set handshake timeout to prevent hanging
                            sslSocket.soTimeout = timeout
                            sslSocket.startHandshake()
                            
                            // Use StringBuilder for string concatenation
                            val request = StringBuilder()
                                .append("GET ").append(path).append(" HTTP/1.1\r\n")
                                .append("Host: ").append(host).append("\r\n")
                                .append("Connection: close\r\n\r\n")
                                .toString()
                            
                            sslSocket.outputStream.bufferedWriter().use { writer ->
                                sslSocket.inputStream.bufferedReader().use { reader ->
                                    writer.write(request)
                                    writer.flush()
                                    val firstLine = reader.readLine()
                                    val latency = System.currentTimeMillis() - start
                                    if (firstLine != null && firstLine.startsWith("HTTP/")) {
                                        latency.toInt()
                                    } else {
                                        null
                                    }
                                }
                            }
                        } finally {
                            // Ensure SSL socket is closed properly
                            try {
                                sslSocket.close()
                            } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                AppLogger.e("Error closing SSL socket", e)
                            }
                            }
                        }
                    } else {
                        // For HTTP, use plain socket streams
                        // Use StringBuilder for string concatenation
                        val request = StringBuilder()
                            .append("GET ").append(path).append(" HTTP/1.1\r\n")
                            .append("Host: ").append(host).append("\r\n")
                            .append("Connection: close\r\n\r\n")
                            .toString()
                        
                        socket.getOutputStream().bufferedWriter().use { writer ->
                            socket.getInputStream().bufferedReader().use { reader ->
                                writer.write(request)
                                writer.flush()
                                val firstLine = reader.readLine()
                                val latency = System.currentTimeMillis() - start
                                if (firstLine != null && firstLine.startsWith("HTTP/")) {
                                    latency.toInt()
                                } else {
                                    null
                                }
                            }
                        }
                    }
                }
            }
            
            testResult.fold(
                onSuccess = { latency ->
                    if (latency != null) {
                        // Cache successful result
                        cachedConnectivityResult = Pair(now, latency)
                        _uiEvent.trySend(
                            MainViewUiEvent.ShowSnackbar(
                                getApplication<Application>().getString(
                                    R.string.connectivity_test_latency,
                                    latency
                                )
                            )
                        )
                    } else {
                        _uiEvent.trySend(
                            MainViewUiEvent.ShowSnackbar(
                                getApplication<Application>().getString(R.string.connectivity_test_failed)
                            )
                        )
                    }
                },
                onFailure = { throwable ->
                    val appError = throwable.toAppError()
                    ErrorHandler.handleError(appError, TAG)
                    _uiEvent.trySend(
                        MainViewUiEvent.ShowSnackbar(
                            getApplication<Application>().getString(R.string.connectivity_test_failed)
                        )
                    )
                }
            )
        }
    }

    fun registerTProxyServiceReceivers() {
        val application = getApplication<Application>()
        val startSuccessFilter = IntentFilter(TProxyService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(
                startReceiver,
                startSuccessFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            getApplication<Application>().registerReceiver(startReceiver, startSuccessFilter)
        }

        val stopSuccessFilter = IntentFilter(TProxyService.ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(
                stopReceiver,
                stopSuccessFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            getApplication<Application>().registerReceiver(stopReceiver, stopSuccessFilter)
        }
        AppLogger.d("TProxyService receivers registered.")
    }

    fun unregisterTProxyServiceReceivers() {
        val application = getApplication<Application>()
        try {
            getApplication<Application>().unregisterReceiver(startReceiver)
        } catch (e: IllegalArgumentException) {
            AppLogger.w( "Start receiver was not registered", e)
        }
        try {
            getApplication<Application>().unregisterReceiver(stopReceiver)
        } catch (e: IllegalArgumentException) {
            AppLogger.w( "Stop receiver was not registered", e)
        }
        AppLogger.d("TProxyService receivers unregistered.")
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
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.rule_file_restore_geoip_success)))
            withContext(Dispatchers.Main) {
                AppLogger.d("Restored default geoip.dat.")
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
            _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.rule_file_restore_geosite_success)))
            withContext(Dispatchers.Main) {
                AppLogger.d("Restored default geosite.dat.")
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
            AppLogger.d("Download cancellation requested for $fileName")
        }
    }

    fun downloadRuleFile(url: String, fileName: String) {
        val currentJob = if (fileName == "geoip.dat") geoipDownloadJob else geositeDownloadJob
        if (currentJob?.isActive == true) {
            AppLogger.w( "Download already in progress for $fileName")
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
                if (_isServiceEnabled.value) {
                    proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", prefs.socksPort)))
                }
            }.build()

            try {
                progressFlow.value = getApplication<Application>().getString(R.string.connecting)

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
                                    getApplication<Application>().getString(R.string.downloading, progress)
                                lastProgress = progress
                            }
                        } else {
                            if (lastProgress == -1) {
                                progressFlow.value =
                                    getApplication<Application>().getString(R.string.downloading_no_size)
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
                        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.download_success)))
                    } else {
                        _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.download_failed)))
                    }
                }
            } catch (e: CancellationException) {
                AppLogger.d("Download cancelled for $fileName")
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.download_cancelled)))
            } catch (e: Exception) {
                AppLogger.e( "Failed to download rule file", e)
                _uiEvent.trySend(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.download_failed) + ": " + e.message))
            } finally {
                progressFlow.value = null
                updateSettingsState()
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

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingForUpdates.value = true
            val client = OkHttpClient.Builder().apply {
                if (_isServiceEnabled.value) {
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
                AppLogger.d("Latest version tag: $latestTag")
                val updateAvailable = compareVersions(latestTag) > 0
                if (updateAvailable) {
                    _newVersionAvailable.value = latestTag
                } else {
                    _uiEvent.trySend(
                        MainViewUiEvent.ShowSnackbar(
                            getApplication<Application>().getString(R.string.no_new_version_available)
                        )
                    )
                }
            } catch (e: CancellationException) {
                // Re-throw cancellation to properly handle coroutine cancellation
                throw e
            } catch (e: Exception) {
                AppLogger.e( "Failed to check for updates", e)
                _uiEvent.trySend(
                    MainViewUiEvent.ShowSnackbar(
                        getApplication<Application>().getString(R.string.failed_to_check_for_updates) + ": " + e.message
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

                // Detect device ABI and construct appropriate APK URL
                val deviceAbi = detectDeviceAbi()
                if (deviceAbi == null) {
                    throw IllegalStateException("No supported ABI found for this device")
                }

                // Construct APK download URL for detected ABI variant
                val apkUrl = BuildConfig.REPOSITORY_URL +
                    "/releases/download/v$versionTag/simplexray-$deviceAbi.apk"
                
                AppLogger.d("Detected ABI: $deviceAbi, Starting APK download from: $apkUrl")
                
                // Start download
                val downloadId = updateManager.downloadApk(versionTag, apkUrl)
                currentDownloadId = downloadId

                // Monitor download progress
                updateManager.observeDownloadProgress(downloadId).collect { progress ->
                    when (progress) {
                        is DownloadProgress.Downloading -> {
                            _downloadProgress.value = progress.progress
                            AppLogger.d("Download progress: ${progress.progress}%")
                        }
                        is DownloadProgress.Completed -> {
                            _downloadProgress.value = 100
                            _isDownloadingUpdate.value = false
                            AppLogger.d("Download completed, waiting for user to install")
                            
                            // Store completion info instead of installing immediately
                            withContext(Dispatchers.Main) {
                                _downloadCompletion.value = DownloadCompletion(progress.uri, progress.filePath)
                            }
                        }
                        is DownloadProgress.Failed -> {
                            _isDownloadingUpdate.value = false
                            _downloadProgress.value = 0
                            AppLogger.e( "Download failed: ${progress.error}")
                            
                            withContext(Dispatchers.Main) {
                                _uiEvent.trySend(
                                    MainViewUiEvent.ShowSnackbar(
                                        "Download failed: ${progress.error}"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Re-throw cancellation to properly handle coroutine cancellation
                _isDownloadingUpdate.value = false
                _downloadProgress.value = 0
                _downloadCompletion.value = null
                throw e
            } catch (e: Exception) {
                _isDownloadingUpdate.value = false
                _downloadProgress.value = 0
                _downloadCompletion.value = null
                AppLogger.e( "Error downloading update", e)
                
                withContext(Dispatchers.Main) {
                    _uiEvent.trySend(
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
    
    /**
     * Installs the downloaded APK
     */
    fun installDownloadedApk() {
        _downloadCompletion.value?.let { completion ->
            updateManager.installApk(completion.uri, completion.filePath)
            _downloadCompletion.value = null
            _newVersionAvailable.value = null
        }
    }
    
    /**
     * Clears download completion state without installing
     */
    fun clearDownloadCompletion() {
        _downloadCompletion.value = null
        _newVersionAvailable.value = null
    }
    fun clearNewVersionAvailable() {
        _newVersionAvailable.value = null
    }

    /**
     * Detects the appropriate ABI variant for the current device
     * @return The ABI string (e.g., "arm64-v8a", "x86_64") or null if no match found
     */
    private fun detectDeviceAbi(): String? {
        val supportedAbis = BuildConfig.SUPPORTED_ABIS
        val deviceAbis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS
        } else {
            arrayOf(Build.CPU_ABI ?: "", Build.CPU_ABI2 ?: "")
        }

        // Find first matching ABI from device's supported ABIs
        for (deviceAbi in deviceAbis) {
            if (deviceAbi in supportedAbis) {
                AppLogger.d("Detected device ABI: $deviceAbi")
                return deviceAbi
            }
        }

        // Fallback to first supported ABI if no match
        AppLogger.w( "No matching ABI found, using fallback: ${supportedAbis.firstOrNull()}")
        return supportedAbis.firstOrNull()
    }

    // Cached parsed version strings to avoid repeated parsing
    private var cachedVersion1: List<Int>? = null
    private var cachedVersion1Str: String? = null
    
    private fun compareVersions(version1: String): Int {
        // Use indexOf and substring instead of removePrefix and split for better performance
        val v1Start = if (version1.startsWith("v")) 1 else 0
        val v2Start = if (BuildConfig.VERSION_NAME.startsWith("v")) 1 else 0
        
        // Parse versions using iterator to avoid creating intermediate lists
        fun parseVersion(version: String, start: Int): List<Int> {
            val parts = mutableListOf<Int>()
            var begin = start
            while (begin < version.length) {
                val end = version.indexOf('.', begin)
                if (end == -1) {
                    parts.add(version.substring(begin).toIntOrNull() ?: 0)
                    break
                } else {
                    parts.add(version.substring(begin, end).toIntOrNull() ?: 0)
                    begin = end + 1
                }
            }
            return parts
        }
        
        // Use cached version if available
        val parts1 = if (cachedVersion1Str == version1 && cachedVersion1 != null) {
            cachedVersion1!!
        } else {
            val parsed = parseVersion(version1, v1Start)
            cachedVersion1 = parsed
            cachedVersion1Str = version1
            parsed
        }
        
        val parts2 = parseVersion(BuildConfig.VERSION_NAME, v2Start)

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            // Use direct array access with bounds check
            val p1 = if (i < parts1.size) parts1[i] else 0
            val p2 = if (i < parts2.size) parts2[i] else 0
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    override fun onCleared() {
        super.onCleared()
        AppLogger.d("MainViewModel cleared - cleaning up resources")
        
        // CRITICAL: Unregister receivers first to prevent memory leak
        // This must be done early to ensure cleanup even if exceptions occur
        try {
            unregisterTProxyServiceReceivers()
        } catch (e: Exception) {
            AppLogger.w("Error unregistering receivers", e)
        }
        
        geoipDownloadJob?.cancel()
        geositeDownloadJob?.cancel()
        activityScope.coroutineContext.cancelChildren()
        coreStatsClientMutex.let {
            // Close the client if exists
            coreStatsClient?.close()
            coreStatsClient = null
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
            // Use modern ServiceStateChecker utility instead of deprecated APIs
            return ServiceStateChecker.isServiceRunning(context, serviceClass)
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

