package com.simplexray.an.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.simplexray.an.common.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.an.data.source.LogFileManager
import com.simplexray.an.service.TProxyService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InterruptedIOException
import java.util.Collections

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
    private var logcatJob: Job? = null

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
        AppLogger.d("LogViewModel initialized.")
        logUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (TProxyService.ACTION_LOG_UPDATE == intent.action) {
                    val newLogs = intent.getStringArrayListExtra(TProxyService.EXTRA_LOG_DATA)
                    if (!newLogs.isNullOrEmpty()) {
                        AppLogger.d("Received log update broadcast with ${newLogs.size} entries.")
                        viewModelScope.launch {
                            processNewLogs(newLogs)
                        }
                    } else {
                        AppLogger.w("Received log update broadcast, but log data list is null or empty.")
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
                searchQuery.debounce(200),
                logLevel
            ) { logs, query, level ->
                var filtered = logs
                if (level != LogLevel.ALL) {
                    // Service logs format can vary, try to extract log level
                    // Common formats: [LEVEL] message, LEVEL: message, or threadtime format
                    filtered = filtered.filter { log ->
                        val upperLog = log.uppercase()
                        when (level) {
                            LogLevel.ERROR -> upperLog.contains("ERROR") || 
                                             upperLog.contains(" E ") || 
                                             upperLog.matches(Regex(".*\\sE\\s.*")) ||
                                             upperLog.startsWith("E/")
                            LogLevel.WARNING -> upperLog.contains("WARN") || 
                                               upperLog.contains(" W ") || 
                                               upperLog.matches(Regex(".*\\sW\\s.*")) ||
                                               upperLog.startsWith("W/")
                            LogLevel.INFO -> upperLog.contains("INFO") || 
                                           upperLog.contains(" I ") || 
                                           upperLog.matches(Regex(".*\\sI\\s.*")) ||
                                           upperLog.startsWith("I/")
                            LogLevel.DEBUG -> upperLog.contains("DEBUG") || 
                                             upperLog.contains(" D ") || 
                                             upperLog.matches(Regex(".*\\sD\\s.*")) ||
                                             upperLog.startsWith("D/")
                            LogLevel.VERBOSE -> upperLog.contains("VERBOSE") || 
                                               upperLog.contains(" V ") || 
                                               upperLog.matches(Regex(".*\\sV\\s.*")) ||
                                               upperLog.startsWith("V/")
                            LogLevel.ALL -> true
                        }
                    }
                }
                if (query.isNotBlank()) {
                    filtered = filtered.filter { it.contains(query, ignoreCase = true) }
                }
                filtered
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
                    // threadtime format: MM-DD HH:MM:SS.mmm  PID   TID  LEVEL TAG: MESSAGE
                    // Extract log level from threadtime format (5th field)
                    filtered = filtered.filter { log ->
                        val parts = log.trim().split(Regex("\\s+"))
                        if (parts.size >= 5) {
                            val logLevelChar = parts[4]
                            logLevelChar == level.tag
                        } else {
                            false
                        }
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
        AppLogger.d("Log receiver registered.")
    }

    fun unregisterLogReceiver(context: Context) {
        try {
            context.unregisterReceiver(logUpdateReceiver)
            AppLogger.d("Log receiver unregistered.")
        } catch (e: IllegalArgumentException) {
            AppLogger.w("Log receiver was not registered", e)
        }
    }

    fun loadLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.d("Loading logs.")
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
        AppLogger.d("Processed initial logs: ${_logEntries.value.size} unique entries.")
    }

    private suspend fun processNewLogs(newLogs: ArrayList<String>) {
        val uniqueNewLogs = logMutex.withLock {
            newLogs.filter { it.trim().isNotEmpty() && logEntrySet.add(it) }
        }
        if (uniqueNewLogs.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                _logEntries.value = uniqueNewLogs + _logEntries.value
            }
            AppLogger.d("Added ${uniqueNewLogs.size} new unique log entries.")
        } else {
            AppLogger.d("No unique log entries from broadcast to add.")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logMutex.withLock {
                _logEntries.value = emptyList()
                logEntrySet.clear()
            }
            AppLogger.d("Logs cleared.")
        }
    }

    fun clearSystemLogs() {
        viewModelScope.launch {
            _systemLogEntries.value = emptyList()
            // Clear logcat buffer
            try {
                Runtime.getRuntime().exec("logcat -c")
            } catch (e: Exception) {
                AppLogger.e("Error clearing logcat buffer", e)
            }
        }
    }

    fun startLogcat() {
        if (logcatProcess != null || logcatJob?.isActive == true) {
            AppLogger.d("Logcat already running")
            return
        }

        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear logcat buffer first to show only new logs
                try {
                    Runtime.getRuntime().exec("logcat -c").waitFor()
                } catch (e: Exception) {
                    AppLogger.w("Could not clear logcat buffer", e)
                }

                // Read logcat with threadtime format for better categorization
                val packageName = getApplication<Application>().packageName
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "threadtime", "*:V")
                )
                logcatProcess = process

                val reader = process.inputStream.bufferedReader()
                val systemLogsList = mutableListOf<String>()

                try {
                    reader.use { bufferedReader ->
                        while (isActive) {
                            val line = bufferedReader.readLine() ?: break
                            if (!isActive) break
                            
                            // Filter logs to show only app package and system errors
                            if (line.contains(packageName) ||
                                line.contains("AndroidRuntime") ||
                                line.contains("System.err") ||
                                line.contains("FATAL")) {

                                systemLogsList.add(0, line)
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
                } catch (e: CancellationException) {
                    // Normal cancellation, rethrow to maintain coroutine cancellation semantics
                    throw e
                } catch (e: InterruptedIOException) {
                    // Expected when process is destroyed from another thread
                    AppLogger.d("Logcat reading interrupted: ${e.message}")
                } catch (e: Exception) {
                    if (isActive) {
                        AppLogger.e("Error reading logcat", e)
                    }
                } finally {
                    // Ensure process is cleaned up
                    if (logcatProcess == process) {
                        logcatProcess = null
                    }
                }
            } catch (e: CancellationException) {
                // Re-throw cancellation to properly handle coroutine cancellation
                throw e
            } catch (e: Exception) {
                AppLogger.e("Error starting logcat", e)
                logcatProcess = null
            }
        }
    }

    fun stopLogcat() {
        logcatJob?.cancel()
        logcatJob = null
        logcatProcess?.destroy()
        logcatProcess = null
        AppLogger.d("Logcat stopped")
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
