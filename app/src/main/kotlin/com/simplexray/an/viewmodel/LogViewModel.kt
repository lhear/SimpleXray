package com.simplexray.an.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.an.data.source.LogFileManager
import com.simplexray.an.service.TProxyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

private const val TAG = "LogViewModel"

@OptIn(FlowPreview::class)
class LogViewModel(application: Application) :
    AndroidViewModel(application) {

    private val logFileManager = LogFileManager(application)

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredEntries = MutableStateFlow<List<String>>(emptyList())
    val filteredEntries: StateFlow<List<String>> = _filteredEntries.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    private val _hasLogsToExport = MutableStateFlow(false)
    val hasLogsToExport: StateFlow<Boolean> = _hasLogsToExport.asStateFlow()

    // System Logcat
    private val _systemLogEntries = MutableStateFlow<List<String>>(emptyList())
    val systemLogEntries: StateFlow<List<String>> = _systemLogEntries.asStateFlow()

    private val _filteredSystemLogs = MutableStateFlow<List<String>>(emptyList())
    val filteredSystemLogs: StateFlow<List<String>> = _filteredSystemLogs.asStateFlow()

    private val _logType = MutableStateFlow(LogType.SERVICE)
    val logType: StateFlow<LogType> = _logType.asStateFlow()

    private val _logLevel = MutableStateFlow(LogLevel.ALL)
    val logLevel: StateFlow<LogLevel> = _logLevel.asStateFlow()

    private var logcatProcess: Process? = null

    enum class LogType {
        SERVICE, SYSTEM
    }

    enum class LogLevel(val tag: String) {
        ALL("*"),
        VERBOSE("V"),
        DEBUG("D"),
        INFO("I"),
        WARNING("W"),
        ERROR("E")
    }

    fun setLogType(type: LogType) {
        _logType.value = type
    }

    fun setLogLevel(level: LogLevel) {
        _logLevel.value = level
    }

    private val logEntrySet: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val logMutex = Mutex()

    private var logUpdateReceiver: BroadcastReceiver

    init {
        Log.d(TAG, "LogViewModel initialized.")
        logUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (TProxyService.ACTION_LOG_UPDATE == intent.action) {
                    val newLogs = intent.getStringArrayListExtra(TProxyService.EXTRA_LOG_DATA)
                    if (!newLogs.isNullOrEmpty()) {
                        Log.d(TAG, "Received log update broadcast with ${newLogs.size} entries.")
                        viewModelScope.launch {
                            processNewLogs(newLogs)
                        }
                    } else {
                        Log.w(
                            TAG,
                            "Received log update broadcast, but log data list is null or empty."
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            logEntries.collect { entries ->
                _hasLogsToExport.value = entries.isNotEmpty() && logFileManager.logFile.exists()
            }
        }
        viewModelScope.launch {
            combine(
                logEntries,
                searchQuery.debounce(200)
            ) { logs, query ->
                if (query.isBlank()) logs
                else logs.filter { it.contains(query, ignoreCase = true) }
            }
                .flowOn(Dispatchers.Default)
                .collect { _filteredEntries.value = it }
        }
        viewModelScope.launch {
            combine(
                systemLogEntries,
                searchQuery.debounce(200),
                logLevel
            ) { logs, query, level ->
                var filtered = logs
                if (level != LogLevel.ALL) {
                    filtered = filtered.filter { log ->
                        log.contains("/${level.tag}:", ignoreCase = false) ||
                        log.contains(" ${level.tag} ", ignoreCase = false)
                    }
                }
                if (query.isNotBlank()) {
                    filtered = filtered.filter { it.contains(query, ignoreCase = true) }
                }
                filtered
            }
                .flowOn(Dispatchers.Default)
                .collect { _filteredSystemLogs.value = it }
        }
    }

    fun registerLogReceiver(context: Context) {
        val filter = IntentFilter(TProxyService.ACTION_LOG_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(logUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(logUpdateReceiver, filter)
        }
        Log.d(TAG, "Log receiver registered.")
    }

    fun unregisterLogReceiver(context: Context) {
        try {
            context.unregisterReceiver(logUpdateReceiver)
            Log.d(TAG, "Log receiver unregistered.")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Log receiver was not registered", e)
        }
    }

    fun loadLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Loading logs.")
            val savedLogData = logFileManager.readLogs()
            val initialLogs = if (!savedLogData.isNullOrEmpty()) {
                savedLogData.split("\n").filter { it.trim().isNotEmpty() }
            } else {
                emptyList()
            }
            processInitialLogs(initialLogs)
        }
    }

    private suspend fun processInitialLogs(initialLogs: List<String>) {
        logMutex.withLock {
            logEntrySet.clear()
            _logEntries.value = initialLogs.filter { logEntrySet.add(it) }.reversed()
        }
        Log.d(TAG, "Processed initial logs: ${_logEntries.value.size} unique entries.")
    }

    private suspend fun processNewLogs(newLogs: ArrayList<String>) {
        val uniqueNewLogs = logMutex.withLock {
            newLogs.filter { it.trim().isNotEmpty() && logEntrySet.add(it) }
        }
        if (uniqueNewLogs.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                _logEntries.value = uniqueNewLogs + _logEntries.value
            }
            Log.d(TAG, "Added ${uniqueNewLogs.size} new unique log entries.")
        } else {
            Log.d(TAG, "No unique log entries from broadcast to add.")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logMutex.withLock {
                _logEntries.value = emptyList()
                logEntrySet.clear()
            }
            Log.d(TAG, "Logs cleared.")
        }
    }

    fun clearSystemLogs() {
        viewModelScope.launch {
            _systemLogEntries.value = emptyList()
            // Clear logcat buffer
            try {
                Runtime.getRuntime().exec("logcat -c")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing logcat buffer", e)
            }
        }
    }

    fun startLogcat() {
        if (logcatProcess != null) {
            Log.d(TAG, "Logcat already running")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Read logcat for current app package
                val packageName = getApplication<Application>().packageName
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time", "-s", "$packageName:V", "AndroidRuntime:E", "*:S")
                )

                val reader = logcatProcess?.inputStream?.bufferedReader()
                val systemLogsList = mutableListOf<String>()

                reader?.use {
                    var line: String?
                    while (it.readLine().also { line = it } != null) {
                        line?.let { logLine ->
                            systemLogsList.add(0, logLine)
                            // Keep only last 1000 lines
                            if (systemLogsList.size > 1000) {
                                systemLogsList.removeAt(systemLogsList.lastIndex)
                            }
                            withContext(Dispatchers.Main) {
                                _systemLogEntries.value = systemLogsList.toList()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading logcat", e)
            }
        }
    }

    fun stopLogcat() {
        logcatProcess?.destroy()
        logcatProcess = null
        Log.d(TAG, "Logcat stopped")
    }

    fun getLogFile(): File {
        return logFileManager.logFile
    }

    override fun onCleared() {
        super.onCleared()
        stopLogcat()
    }
}

class LogViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LogViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
