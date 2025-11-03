package com.simplexray.an.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.common.ConfigUtils
import com.simplexray.an.data.source.FileManager
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.xray.XrayConfigBuilder
import com.simplexray.an.xray.XrayConfigPatcher
import com.simplexray.an.xray.XrayCoreLauncher
import com.simplexray.an.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

data class VlessSettings(
    val serverAddress: String = "",
    val serverPort: Int = 8443,
    val id: String = "",
    val muxEnabled: Boolean = false
)

enum class NetworkType(val value: String, val displayName: String) {
    TCP("tcp", "TCP"),
    WEBSOCKET("ws", "WebSocket"),
    GRPC("grpc", "gRPC"),
    HTTP("http", "HTTP/2"),
    KCP("kcp", "mKCP"),
    QUIC("quic", "QUIC")
}

data class StreamSettings(
    val networkType: NetworkType = NetworkType.TCP,
    val tlsEnabled: Boolean = true,
    val insecure: Boolean = true,
    val sni: String = "",
    // WebSocket settings
    val wsPath: String = "/",
    val wsHost: String = "",
    // gRPC settings
    val grpcServiceName: String = "",
    // HTTP/2 settings
    val httpHost: List<String> = emptyList(),
    val httpPath: String = "/"
)

class XraySettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _vlessSettings = MutableStateFlow(VlessSettings())
    val vlessSettings: StateFlow<VlessSettings> = _vlessSettings.asStateFlow()

    private val _streamSettings = MutableStateFlow(StreamSettings())
    val streamSettings: StateFlow<StreamSettings> = _streamSettings.asStateFlow()

    private val _jsonContent = MutableStateFlow(TextFieldValue(""))
    val jsonContent: StateFlow<TextFieldValue> = _jsonContent.asStateFlow()

    private val _isJsonView = MutableStateFlow(false)
    val isJsonView: StateFlow<Boolean> = _isJsonView.asStateFlow()

    private val _hasChanges = MutableStateFlow(false)
    val hasChanges: StateFlow<Boolean> = _hasChanges.asStateFlow()

    private val _saveResult = MutableStateFlow<Result<Unit>?>(null)
    val saveResult: StateFlow<Result<Unit>?> = _saveResult.asStateFlow()

    private val _loadResult = MutableStateFlow<Result<Unit>?>(null)
    val loadResult: StateFlow<Result<Unit>?> = _loadResult.asStateFlow()

    private val _configFiles = MutableStateFlow<List<File>>(emptyList())
    val configFiles: StateFlow<List<File>> = _configFiles.asStateFlow()

    private val _configFilename = MutableStateFlow("")
    val configFilename: StateFlow<String> = _configFilename.asStateFlow()

    // Configuration management state
    private val _configOperationResult = MutableStateFlow<Result<String>?>(null)
    val configOperationResult: StateFlow<Result<String>?> = _configOperationResult.asStateFlow()

    private val _isXrayRunning = MutableStateFlow(false)
    val isXrayRunning: StateFlow<Boolean> = _isXrayRunning.asStateFlow()

    private val _mergeInbounds = MutableStateFlow(true)
    val mergeInbounds: StateFlow<Boolean> = _mergeInbounds.asStateFlow()

    private val _mergeOutbounds = MutableStateFlow(true)
    val mergeOutbounds: StateFlow<Boolean> = _mergeOutbounds.asStateFlow()

    private val _mergeTransport = MutableStateFlow(true)
    val mergeTransport: StateFlow<Boolean> = _mergeTransport.asStateFlow()

    private val _autoReload = MutableStateFlow(false)
    val autoReload: StateFlow<Boolean> = _autoReload.asStateFlow()

    private val _currentConfigFile = MutableStateFlow<String?>(null)
    val currentConfigFile: StateFlow<String?> = _currentConfigFile.asStateFlow()

    private val _configPreview = MutableStateFlow<String?>(null)
    val configPreview: StateFlow<String?> = _configPreview.asStateFlow()

    private val fileManager: FileManager = FileManager(application, Preferences(application))

    init {
        generateDefaultConfig()
        refreshConfigFileList()
        checkXrayStatus()
        loadConfigPreferences()
    }

    private fun loadConfigPreferences() {
        val prefs = Preferences(getApplication())
        _mergeInbounds.value = prefs.getMergeInbounds(true)
        _mergeOutbounds.value = prefs.getMergeOutbounds(true)
        _mergeTransport.value = prefs.getMergeTransport(true)
        _autoReload.value = prefs.getAutoReloadConfig(false)
        _currentConfigFile.value = prefs.getCurrentConfigFile("xray.json")
    }

    fun checkXrayStatus() {
        viewModelScope.launch {
            _isXrayRunning.value = XrayCoreLauncher.isRunning()
        }
    }

    fun setMergeInbounds(value: Boolean) {
        _mergeInbounds.value = value
        Preferences(getApplication()).setMergeInbounds(value)
    }

    fun setMergeOutbounds(value: Boolean) {
        _mergeOutbounds.value = value
        Preferences(getApplication()).setMergeOutbounds(value)
    }

    fun setMergeTransport(value: Boolean) {
        _mergeTransport.value = value
        Preferences(getApplication()).setMergeTransport(value)
    }

    fun setAutoReload(value: Boolean) {
        _autoReload.value = value
        Preferences(getApplication()).setAutoReloadConfig(value)
    }

    fun setCurrentConfigFile(filename: String) {
        _currentConfigFile.value = filename
        Preferences(getApplication()).setCurrentConfigFile(filename)
    }

    fun writeDefaultConfig(): Result<File> {
        return try {
            val context = getApplication<Application>()
            val host = ApiConfig.getHost(context)
            val port = ApiConfig.getPort(context)
            val cfg = XrayConfigBuilder.defaultConfig(host, port)
            val file = XrayConfigBuilder.writeConfig(context, cfg, _currentConfigFile.value ?: "xray.json")
            _configOperationResult.value = Result.success("Default config written to ${file.name}")
            if (_autoReload.value && _isXrayRunning.value) {
                reloadXrayConfig()
            }
            Result.success(file)
        } catch (e: Exception) {
            val error = "Failed to write default config: ${e.message}"
            _configOperationResult.value = Result.failure(Exception(error))
            Result.failure(e)
        }
    }

    fun patchConfig(): Result<File> {
        return try {
            val context = getApplication<Application>()
            val filename = _currentConfigFile.value ?: "xray.json"
            val file = XrayConfigPatcher.patchConfig(
                context,
                filename,
                mergeInbounds = _mergeInbounds.value,
                mergeOutbounds = _mergeOutbounds.value,
                mergeTransport = _mergeTransport.value
            )
            _configOperationResult.value = Result.success("Config patched: ${file.name}")
            if (_autoReload.value && _isXrayRunning.value) {
                reloadXrayConfig()
            }
            Result.success(file)
        } catch (e: Exception) {
            val error = "Failed to patch config: ${e.message}"
            _configOperationResult.value = Result.failure(Exception(error))
            Result.failure(e)
        }
    }

    fun patchApiStatsOnly(): Result<File> {
        return try {
            val context = getApplication<Application>()
            val filename = _currentConfigFile.value ?: "xray.json"
            val file = XrayConfigPatcher.ensureApiStatsPolicy(context, filename)
            _configOperationResult.value = Result.success("API/Stats patched: ${file.name}")
            if (_autoReload.value && _isXrayRunning.value) {
                reloadXrayConfig()
            }
            Result.success(file)
        } catch (e: Exception) {
            val error = "Failed to patch API/Stats: ${e.message}"
            _configOperationResult.value = Result.failure(Exception(error))
            Result.failure(e)
        }
    }

    fun startXrayCore(): Result<Boolean> {
        return try {
            val context = getApplication<Application>()
            val configFile = _currentConfigFile.value?.let {
                File(context.filesDir, it)
            }
            val success = XrayCoreLauncher.start(context, configFile)
            _isXrayRunning.value = success
            if (success) {
                _configOperationResult.value = Result.success("Xray core started successfully")
            } else {
                _configOperationResult.value = Result.failure(Exception("Failed to start Xray core"))
            }
            Result.success(success)
        } catch (e: Exception) {
            val error = "Error starting Xray core: ${e.message}"
            _configOperationResult.value = Result.failure(Exception(error))
            Result.failure(e)
        }
    }

    fun stopXrayCore(): Result<Boolean> {
        return try {
            val success = XrayCoreLauncher.stop()
            _isXrayRunning.value = false
            if (success) {
                _configOperationResult.value = Result.success("Xray core stopped successfully")
            } else {
                _configOperationResult.value = Result.failure(Exception("Failed to stop Xray core"))
            }
            Result.success(success)
        } catch (e: Exception) {
            val error = "Error stopping Xray core: ${e.message}"
            _configOperationResult.value = Result.failure(Exception(error))
            Result.failure(e)
        }
    }

    fun restartXrayCore(): Result<Boolean> {
        return try {
            stopXrayCore()
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                startXrayCore()
            }
            Result.success(true)
        } catch (e: Exception) {
            val error = "Error restarting Xray core: ${e.message}"
            _configOperationResult.value = Result.failure(Exception(error))
            Result.failure(e)
        }
    }

    private fun reloadXrayConfig() {
        viewModelScope.launch {
            try {
                if (_isXrayRunning.value) {
                    stopXrayCore()
                    kotlinx.coroutines.delay(300)
                    startXrayCore()
                }
            } catch (e: Exception) {
                Log.e("XraySettingsViewModel", "Failed to reload config", e)
            }
        }
    }

    fun validateConfigFile(filename: String? = null): Result<JSONObject> {
        return try {
            val context = getApplication<Application>()
            val file = File(context.filesDir, filename ?: _currentConfigFile.value ?: "xray.json")
            if (!file.exists()) {
                return Result.failure(FileNotFoundException("Config file not found: ${file.name}"))
            }
            val content = file.readText()
            val json = JSONObject(content)
            
            // Validate required sections
            val errors = mutableListOf<String>()
            if (!json.has("inbounds")) errors.add("Missing 'inbounds' section")
            if (!json.has("outbounds")) errors.add("Missing 'outbounds' section")
            
            if (errors.isNotEmpty()) {
                return Result.failure(IllegalArgumentException("Invalid config: ${errors.joinToString(", ")}"))
            }
            
            Result.success(json)
        } catch (e: JSONException) {
            Result.failure(Exception("Invalid JSON format: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun previewConfigFile(filename: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val file = File(context.filesDir, filename ?: _currentConfigFile.value ?: "xray.json")
                if (file.exists()) {
                    val content = file.readText()
                    val json = JSONObject(content)
                    val formatted = json.toString(2)
                    withContext(Dispatchers.Main) {
                        _configPreview.value = formatted
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _configPreview.value = null
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _configPreview.value = "Error: ${e.message}"
                }
            }
        }
    }

    fun clearConfigPreview() {
        _configPreview.value = null
    }

    fun clearOperationResult() {
        _configOperationResult.value = null
    }

    fun backupConfigFile(filename: String? = null): Result<File> {
        return try {
            val context = getApplication<Application>()
            val sourceFile = File(context.filesDir, filename ?: _currentConfigFile.value ?: "xray.json")
            if (!sourceFile.exists()) {
                return Result.failure(FileNotFoundException("Source file not found"))
            }
            val backupDir = File(context.filesDir, "backups")
            backupDir.mkdirs()
            val timestamp = System.currentTimeMillis()
            val backupFile = File(backupDir, "${sourceFile.nameWithoutExtension}_${timestamp}.json")
            sourceFile.copyTo(backupFile, overwrite = true)
            _configOperationResult.value = Result.success("Backup created: ${backupFile.name}")
            Result.success(backupFile)
        } catch (e: Exception) {
            val error = "Failed to backup config: ${e.message}"
            _configOperationResult.value = Result.failure(Exception(error))
            Result.failure(e)
        }
    }

    fun refreshConfigFileList() {
        viewModelScope.launch(Dispatchers.IO) {
            val filesDir = getApplication<Application>().filesDir
            val actualFiles = filesDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            }?.toList() ?: emptyList()
            
            withContext(Dispatchers.Main) {
                _configFiles.value = actualFiles.sortedBy { it.name }
            }
        }
    }

    fun updateServerAddress(address: String) {
        _vlessSettings.value = _vlessSettings.value.copy(serverAddress = address)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun updateServerPort(port: String) {
        val portInt = port.toIntOrNull() ?: 8443
        _vlessSettings.value = _vlessSettings.value.copy(serverPort = portInt)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun updateId(id: String) {
        _vlessSettings.value = _vlessSettings.value.copy(id = id)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun toggleMux(enabled: Boolean) {
        _vlessSettings.value = _vlessSettings.value.copy(muxEnabled = enabled)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun updateNetworkType(networkType: NetworkType) {
        _streamSettings.value = _streamSettings.value.copy(networkType = networkType)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun toggleTls(enabled: Boolean) {
        _streamSettings.value = _streamSettings.value.copy(tlsEnabled = enabled)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun toggleInsecure(enabled: Boolean) {
        _streamSettings.value = _streamSettings.value.copy(insecure = enabled)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun updateSni(sni: String) {
        _streamSettings.value = _streamSettings.value.copy(sni = sni)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun updateWsPath(path: String) {
        _streamSettings.value = _streamSettings.value.copy(wsPath = path)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun updateWsHost(host: String) {
        _streamSettings.value = _streamSettings.value.copy(wsHost = host)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun updateGrpcServiceName(serviceName: String) {
        _streamSettings.value = _streamSettings.value.copy(grpcServiceName = serviceName)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun updateHttpPath(path: String) {
        _streamSettings.value = _streamSettings.value.copy(httpPath = path)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun updateHttpHost(hostText: String) {
        val hosts = hostText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        _streamSettings.value = _streamSettings.value.copy(httpHost = hosts)
        _hasChanges.value = true
        if (!_isJsonView.value) {
            updateJsonFromForm()
        }
    }

    fun toggleView(isJsonView: Boolean) {
        _isJsonView.value = isJsonView
        if (isJsonView) {
            updateJsonFromForm()
        } else {
            updateFormFromJson()
        }
    }

    fun updateJsonContent(textFieldValue: TextFieldValue) {
        _jsonContent.value = textFieldValue
        _hasChanges.value = true
        if (_isJsonView.value) {
            // Debounce JSON parsing to avoid excessive updates
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                updateFormFromJson()
            }
        }
    }

    fun handleAutoIndent(text: String, newlinePosition: Int): Pair<String, Int> {
        val prevLineStart = text.lastIndexOf('\n', newlinePosition - 1).let {
            if (it == -1) 0 else it + 1
        }
        val prevLine = text.substring(prevLineStart, newlinePosition)
        val leadingSpaces = prevLine.takeWhile { it.isWhitespace() }.length
        val additionalIndent = if (prevLine.trimEnd().let {
                it.endsWith('{') || it.endsWith('[')
            }) 2 else 0
        val shouldDedent = run {
            val nextLineStart = newlinePosition + 1
            nextLineStart < text.length &&
                    text.substring(nextLineStart).substringBefore('\n').trimStart().let {
                        it.startsWith('}') || it.startsWith(']')
                    }
        }
        val finalIndent = (
                leadingSpaces + additionalIndent - if (shouldDedent) 2 else 0
                ).coerceAtLeast(0)
        val indent = " ".repeat(finalIndent)
        val indentedText = StringBuilder(text).insert(newlinePosition + 1, indent).toString()
        return indentedText to (newlinePosition + 1 + finalIndent)
    }

    fun validateAndFormatJson(): Result<String> {
        return try {
            val text = _jsonContent.value.text.trim()
            if (text.isEmpty()) {
                return Result.failure(IllegalArgumentException("JSON content is empty"))
            }
            val json = JSONObject(text)
            val formatted = json.toString(2)
            Result.success(formatted)
        } catch (e: JSONException) {
            val message = getApplication<Application>().getString(
                com.simplexray.an.R.string.invalid_config_format
            ) + ": ${e.message}"
            Result.failure(IllegalArgumentException(message, e))
        } catch (e: Exception) {
            val message = getApplication<Application>().getString(
                com.simplexray.an.R.string.invalid_config_format
            ) + ": ${e.message}"
            Result.failure(IllegalArgumentException(message, e))
        }
    }

    private fun updateJsonFromForm() {
        viewModelScope.launch {
            try {
                val config = buildConfigJson()
                val formatted = config.toString(2)
                _jsonContent.value = TextFieldValue(formatted)
            } catch (e: Exception) {
                // Handle error silently or show message
            }
        }
    }

    private fun updateFormFromJson() {
        viewModelScope.launch {
            try {
                val json = JSONObject(_jsonContent.value.text)
                parseConfigJson(json)
            } catch (e: Exception) {
                // Handle error - invalid JSON
            }
        }
    }

    private fun buildConfigJson(): JSONObject {
        val vless = _vlessSettings.value
        val stream = _streamSettings.value

        val config = JSONObject().apply {
            put("inbounds", JSONArray())
            put("outbounds", JSONArray().apply {
                put(JSONObject().apply {
                    if (vless.muxEnabled) {
                        put("mux", JSONObject().apply {
                            put("enabled", true)
                        })
                    } else {
                        put("mux", JSONObject().apply {
                            put("enabled", false)
                        })
                    }
                    put("protocol", "vless")
                    put("settings", JSONObject().apply {
                        put("vnext", JSONArray().apply {
                            put(JSONObject().apply {
                                put("address", vless.serverAddress)
                                put("port", vless.serverPort)
                                put("users", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("encryption", "none")
                                        put("id", vless.id)
                                        put("level", 8)
                                    })
                                })
                            })
                        })
                    })
                    put("streamSettings", JSONObject().apply {
                        put("network", stream.networkType.value)
                        
                        // Network-specific settings
                        when (stream.networkType) {
                            NetworkType.WEBSOCKET -> {
                                put("wsSettings", JSONObject().apply {
                                    put("path", stream.wsPath)
                                    if (stream.wsHost.isNotEmpty()) {
                                        put("headers", JSONObject().apply {
                                            put("Host", stream.wsHost)
                                        })
                                    }
                                })
                            }
                            NetworkType.GRPC -> {
                                put("grpcSettings", JSONObject().apply {
                                    if (stream.grpcServiceName.isNotEmpty()) {
                                        put("serviceName", stream.grpcServiceName)
                                    }
                                })
                            }
                            NetworkType.HTTP -> {
                                put("httpSettings", JSONObject().apply {
                                    put("path", stream.httpPath)
                                    if (stream.httpHost.isNotEmpty()) {
                                        put("host", JSONArray().apply {
                                            stream.httpHost.forEach { host ->
                                                put(host)
                                            }
                                        })
                                    }
                                })
                            }
                            else -> {
                                // TCP, KCP, QUIC don't need additional settings
                            }
                        }
                        
                        // TLS settings
                        if (stream.tlsEnabled) {
                            put("security", "tls")
                            put("tlsSettings", JSONObject().apply {
                                put("allowInsecure", stream.insecure)
                                if (stream.sni.isNotEmpty()) {
                                    put("serverName", stream.sni)
                                }
                            })
                        }
                    })
                    put("tag", "VLESS")
                })
            })
            put("policy", JSONObject())
        }

        return config
    }

    private fun parseConfigJson(json: JSONObject) {
        try {
            val outbounds = json.getJSONArray("outbounds")
            if (outbounds.length() > 0) {
                val outbound = outbounds.getJSONObject(0)
                
                // Parse mux
                val muxEnabled = outbound.optJSONObject("mux")?.optBoolean("enabled") ?: false
                _vlessSettings.value = _vlessSettings.value.copy(muxEnabled = muxEnabled)

                // Parse vless settings
                val settings = outbound.getJSONObject("settings")
                val vnext = settings.getJSONArray("vnext")
                if (vnext.length() > 0) {
                    val server = vnext.getJSONObject(0)
                    val address = server.getString("address")
                    val port = server.getInt("port")
                    val users = server.getJSONArray("users")
                    if (users.length() > 0) {
                        val user = users.getJSONObject(0)
                        val id = user.getString("id")
                        _vlessSettings.value = _vlessSettings.value.copy(
                            serverAddress = address,
                            serverPort = port,
                            id = id
                        )
                    }
                }

                // Parse stream settings
                val streamSettingsObj = outbound.optJSONObject("streamSettings")
                if (streamSettingsObj != null) {
                    val network = streamSettingsObj.optString("network", "tcp")
                    val networkType = NetworkType.values().find { it.value == network } ?: NetworkType.TCP
                    
                    val security = streamSettingsObj.optString("security", "")
                    val tlsEnabled = security == "tls"
                    
                    val tlsSettings = streamSettingsObj.optJSONObject("tlsSettings")
                    val insecure = tlsSettings?.optBoolean("allowInsecure") ?: false
                    val sni = tlsSettings?.optString("serverName") ?: ""
                    
                    // Parse network-specific settings
                    var wsPath = "/"
                    var wsHost = ""
                    var grpcServiceName = ""
                    var httpPath = "/"
                    var httpHost = emptyList<String>()
                    
                    when (networkType) {
                        NetworkType.WEBSOCKET -> {
                            val wsSettings = streamSettingsObj.optJSONObject("wsSettings")
                            wsPath = wsSettings?.optString("path") ?: "/"
                            val headers = wsSettings?.optJSONObject("headers")
                            wsHost = headers?.optString("Host") ?: ""
                        }
                        NetworkType.GRPC -> {
                            val grpcSettings = streamSettingsObj.optJSONObject("grpcSettings")
                            grpcServiceName = grpcSettings?.optString("serviceName") ?: ""
                        }
                        NetworkType.HTTP -> {
                            val httpSettings = streamSettingsObj.optJSONObject("httpSettings")
                            httpPath = httpSettings?.optString("path") ?: "/"
                            val hostArray = httpSettings?.optJSONArray("host")
                            httpHost = if (hostArray != null) {
                                (0 until hostArray.length()).map { hostArray.getString(it) }
                            } else {
                                emptyList()
                            }
                        }
                        else -> {
                            // TCP, KCP, QUIC don't have additional settings
                        }
                    }
                    
                    _streamSettings.value = StreamSettings(
                        networkType = networkType,
                        tlsEnabled = tlsEnabled,
                        insecure = insecure,
                        sni = sni,
                        wsPath = wsPath,
                        wsHost = wsHost,
                        grpcServiceName = grpcServiceName,
                        httpHost = httpHost,
                        httpPath = httpPath
                    )
                }
            }
        } catch (e: Exception) {
            // Handle parsing error
        }
    }

    private fun generateDefaultConfig() {
        _vlessSettings.value = VlessSettings(
            serverAddress = "halibiram.online",
            serverPort = 8443,
            id = "1cf65334-d06c-4437-93c3-a671c8ca9c90",
            muxEnabled = false
        )
        _streamSettings.value = StreamSettings(
            networkType = NetworkType.TCP,
            tlsEnabled = true,
            insecure = true,
            sni = "*.googlevideo.com"
        )
        updateJsonFromForm()
    }

    fun generateNewId() {
        val newId = UUID.randomUUID().toString()
        updateId(newId)
    }

    fun getConfigJson(): String {
        return buildConfigJson().toString(2)
    }

    fun updateConfigFilename(filename: String) {
        _configFilename.value = filename
    }

    fun saveConfigFile() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = _configFilename.value.ifBlank { 
                    "xray_config_${System.currentTimeMillis()}" 
                }
                
                // Validate filename
                val validationError = com.simplexray.an.common.FilenameValidator.validateFilename(
                    getApplication<Application>(), 
                    filename
                )
                if (validationError != null) {
                    withContext(Dispatchers.Main) {
                        _saveResult.value = Result.failure(
                            IllegalArgumentException(validationError)
                        )
                    }
                    return@launch
                }

                val configJson = if (_isJsonView.value) {
                    val result = validateAndFormatJson()
                    result.getOrThrow()
                } else {
                    buildConfigJson().toString(2)
                }

                val formattedContent = ConfigUtils.formatConfigContent(configJson)
                val finalFilename = if (filename.endsWith(".json")) filename else "$filename.json"
                val configFile = File(getApplication<Application>().filesDir, finalFilename)
                
                configFile.writeText(formattedContent)
                
                withContext(Dispatchers.Main) {
                    _saveResult.value = Result.success(Unit)
                    _hasChanges.value = false
                    _configFilename.value = ""
                }
            } catch (e: JSONException) {
                Log.e("XraySettingsViewModel", "Invalid JSON format", e)
                withContext(Dispatchers.Main) {
                    _saveResult.value = Result.failure(e)
                }
            } catch (e: Exception) {
                Log.e("XraySettingsViewModel", "Failed to save config", e)
                withContext(Dispatchers.Main) {
                    _saveResult.value = Result.failure(e)
                }
            }
        }
    }

    fun exportToClipboard(): String {
        val configJson = if (_isJsonView.value) {
            val result = validateAndFormatJson()
            result.getOrNull() ?: _jsonContent.value.text
        } else {
            buildConfigJson().toString(2)
        }
        return configJson
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun validateForm(): Boolean {
        val vless = _vlessSettings.value
        return vless.serverAddress.isNotBlank() && 
               vless.serverPort > 0 && 
               vless.serverPort <= 65535 &&
               vless.id.isNotBlank()
    }

    fun validateServerAddress(address: String): String? {
        val context = getApplication<Application>().applicationContext
        return when {
            address.isBlank() -> context.getString(com.simplexray.an.R.string.server_address_empty)
            address.length > 255 -> context.getString(com.simplexray.an.R.string.server_address_too_long)
            else -> null
        }
    }

    fun validatePort(port: String): String? {
        val context = getApplication<Application>().applicationContext
        val portInt = port.toIntOrNull()
        return when {
            port.isBlank() -> context.getString(com.simplexray.an.R.string.port_empty)
            portInt == null -> context.getString(com.simplexray.an.R.string.port_not_number)
            portInt < 1 -> context.getString(com.simplexray.an.R.string.port_too_small)
            portInt > 65535 -> context.getString(com.simplexray.an.R.string.port_too_large)
            else -> null
        }
    }

    fun validateId(id: String): String? {
        val context = getApplication<Application>().applicationContext
        return when {
            id.isBlank() -> context.getString(com.simplexray.an.R.string.id_empty)
            !isValidUUID(id) && id.length < 8 -> context.getString(com.simplexray.an.R.string.id_invalid_format)
            else -> null
        }
    }

    private fun isValidUUID(uuid: String): Boolean {
        return try {
            UUID.fromString(uuid)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun loadFromConfigFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = file.readText()
                val json = JSONObject(content)
                parseConfigJson(json)
                updateJsonFromForm()
                withContext(Dispatchers.Main) {
                    _hasChanges.value = false
                    _loadResult.value = Result.success(Unit)
                    _configFilename.value = file.name.replace(".json", "")
                }
            } catch (e: JSONException) {
                Log.e("XraySettingsViewModel", "Invalid JSON in config file", e)
                withContext(Dispatchers.Main) {
                    _loadResult.value = Result.failure(e)
                }
            } catch (e: Exception) {
                Log.e("XraySettingsViewModel", "Failed to load config file", e)
                withContext(Dispatchers.Main) {
                    _loadResult.value = Result.failure(e)
                }
            }
        }
    }

    fun importFromClipboard(clipboardContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(clipboardContent)
                parseConfigJson(json)
                updateJsonFromForm()
                withContext(Dispatchers.Main) {
                    _hasChanges.value = true
                    _loadResult.value = Result.success(Unit)
                }
            } catch (e: JSONException) {
                Log.e("XraySettingsViewModel", "Invalid JSON from clipboard", e)
                withContext(Dispatchers.Main) {
                    _loadResult.value = Result.failure(e)
                }
            } catch (e: Exception) {
                Log.e("XraySettingsViewModel", "Failed to import from clipboard", e)
                withContext(Dispatchers.Main) {
                    _loadResult.value = Result.failure(e)
                }
            }
        }
    }

    fun clearLoadResult() {
        _loadResult.value = null
    }

    fun deleteConfigFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = file.delete()
                if (success) {
                    refreshConfigFileList()
                    withContext(Dispatchers.Main) {
                        _loadResult.value = Result.success(Unit)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _loadResult.value = Result.failure(
                            IOException("Failed to delete config file")
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("XraySettingsViewModel", "Failed to delete config file", e)
                withContext(Dispatchers.Main) {
                    _loadResult.value = Result.failure(e)
                }
            }
        }
    }

    fun applyTemplate(template: ConfigTemplate) {
        when (template) {
            ConfigTemplate.VLESS_TCP_TLS -> {
                _vlessSettings.value = VlessSettings(
                    serverAddress = "",
                    serverPort = 443,
                    id = UUID.randomUUID().toString(),
                    muxEnabled = false
                )
                _streamSettings.value = StreamSettings(
                    networkType = NetworkType.TCP,
                    tlsEnabled = true,
                    insecure = false,
                    sni = ""
                )
            }
            ConfigTemplate.VLESS_WS_TLS -> {
                _vlessSettings.value = VlessSettings(
                    serverAddress = "",
                    serverPort = 443,
                    id = UUID.randomUUID().toString(),
                    muxEnabled = false
                )
                _streamSettings.value = StreamSettings(
                    networkType = NetworkType.WEBSOCKET,
                    tlsEnabled = true,
                    insecure = false,
                    sni = "",
                    wsPath = "/",
                    wsHost = ""
                )
            }
            ConfigTemplate.VLESS_GRPC_TLS -> {
                _vlessSettings.value = VlessSettings(
                    serverAddress = "",
                    serverPort = 443,
                    id = UUID.randomUUID().toString(),
                    muxEnabled = false
                )
                _streamSettings.value = StreamSettings(
                    networkType = NetworkType.GRPC,
                    tlsEnabled = true,
                    insecure = false,
                    sni = "",
                    grpcServiceName = ""
                )
            }
            ConfigTemplate.VLESS_HTTP_TLS -> {
                _vlessSettings.value = VlessSettings(
                    serverAddress = "",
                    serverPort = 443,
                    id = UUID.randomUUID().toString(),
                    muxEnabled = false
                )
                _streamSettings.value = StreamSettings(
                    networkType = NetworkType.HTTP,
                    tlsEnabled = true,
                    insecure = false,
                    sni = "",
                    httpPath = "/",
                    httpHost = emptyList()
                )
            }
            ConfigTemplate.VLESS_QUIC_TLS -> {
                _vlessSettings.value = VlessSettings(
                    serverAddress = "",
                    serverPort = 443,
                    id = UUID.randomUUID().toString(),
                    muxEnabled = false
                )
                _streamSettings.value = StreamSettings(
                    networkType = NetworkType.QUIC,
                    tlsEnabled = true,
                    insecure = false,
                    sni = ""
                )
            }
            ConfigTemplate.VLESS_KCP_TLS -> {
                _vlessSettings.value = VlessSettings(
                    serverAddress = "",
                    serverPort = 443,
                    id = UUID.randomUUID().toString(),
                    muxEnabled = false
                )
                _streamSettings.value = StreamSettings(
                    networkType = NetworkType.KCP,
                    tlsEnabled = true,
                    insecure = false,
                    sni = ""
                )
            }
        }
        updateJsonFromForm()
        _hasChanges.value = true
    }
}

enum class ConfigTemplate(val displayName: String) {
    VLESS_TCP_TLS("VLESS + TCP + TLS"),
    VLESS_WS_TLS("VLESS + WebSocket + TLS"),
    VLESS_GRPC_TLS("VLESS + gRPC + TLS"),
    VLESS_HTTP_TLS("VLESS + HTTP/2 + TLS"),
    VLESS_QUIC_TLS("VLESS + QUIC + TLS"),
    VLESS_KCP_TLS("VLESS + mKCP + TLS")
}
