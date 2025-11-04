package com.simplexray.an.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.simplexray.an.common.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.an.data.source.LogFileManager
import com.simplexray.an.logging.LoggerRepository
import com.simplexray.an.logging.LogEvent
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InterruptedIOException

/**
 * ViewModel for log display that consumes from LoggerRepository.
 * 
 * Key improvements:
 * - Uses LoggerRepository SharedFlow (hot, survives Activity recreation)
 * - No BroadcastReceiver (replaced with Flow collection)
 * - Logs persist across screen rotations and process soft kills
 * - Real-time updates from global repository
 */
@OptIn(FlowPreview::class)
class LogViewModel(application: Application) :
    AndroidViewModel(application) {

    private val logFileManager = LogFileManager(application)

    // All logs from LoggerRepository (converted to strings)
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

    // System Logcat (kept for system log viewing)
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

    init {
        AppLogger.d("LogViewModel initialized.")
        
        // Collect logs from LoggerRepository (HOT flow - survives Activity recreation)
        viewModelScope.launch(Dispatchers.IO) {
            LoggerRepository.logEvents
                .map { event -> event.toFormattedString() }
                .flowOn(Dispatchers.Default)
                .collect { formattedLog ->
                    // Add new log to list (prepend for reverse chronological order)
                    _logEntries.value = listOf(formattedLog) + _logEntries.value
                    
                    // Keep last 5000 logs to prevent memory issues
                    if (_logEntries.value.size > 5000) {
                        _logEntries.value = _logEntries.value.take(5000)
                    }
                }
        }
        
        // Load initial logs from repository buffer
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val initialLogs = LoggerRepository.getAllLogs()
                    .map { it.toFormattedString() }
                    .reversed() // Reverse to show newest first
                _logEntries.value = initialLogs
                AppLogger.d("Loaded ${initialLogs.size} initial logs from repository")
            } catch (e: Exception) {
                AppLogger.e("Error loading initial logs", e)
            }
        }
        
        // Update hasLogsToExport
        viewModelScope.launch {
            logEntries.collect { entries ->
                _hasLogsToExport.value = entries.isNotEmpty() && logFileManager.logFile.exists()
            }
        }
        
        // Filter service logs
        viewModelScope.launch {
            combine(
                logEntries,
                searchQuery.debounce(200),
                logLevel
            ) { logs, query, level ->
                var filtered = logs
                
                // Filter by log level
                if (level != LogLevel.ALL) {
                    filtered = filtered.filter { log ->
                        val upperLog = log.uppercase()
                        when (level) {
                            LogLevel.ERROR -> upperLog.contains(" E ") || 
                                             upperLog.contains("FATAL") ||
                                             upperLog.contains("ERROR")
                            LogLevel.WARNING -> upperLog.contains(" W ") || 
                                               upperLog.contains("WARN")
                            LogLevel.INFO -> upperLog.contains(" I ") || 
                                           upperLog.contains("INFO")
                            LogLevel.DEBUG -> upperLog.contains(" D ") || 
                                             upperLog.contains("DEBUG")
                            LogLevel.VERBOSE -> upperLog.contains(" V ") || 
                                               upperLog.contains("VERBOSE")
                            LogLevel.ALL -> true
                        }
                    }
                }
                
                // Filter by search query
                if (query.isNotBlank()) {
                    filtered = filtered.filter { it.contains(query, ignoreCase = true) }
                }
                
                filtered
            }
                .flowOn(Dispatchers.Default)
                .collect { _filteredEntries.value = it }
        }
        
        // Filter system logs
        viewModelScope.launch {
            combine(
                systemLogEntries,
                searchQuery.debounce(200),
                logLevel
            ) { logs, query, level ->
                var filtered = logs
                if (level != LogLevel.ALL) {
                    // threadtime format: MM-DD HH:MM:SS.mmm  PID   TID  LEVEL TAG: MESSAGE
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

    /**
     * Load logs from file (for backward compatibility with file-based logs)
     */
    fun loadLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.d("Loading logs from file.")
            try {
                val savedLogData = logFileManager.readLogs()
                val fileLogs = if (!savedLogData.isNullOrEmpty()) {
                    savedLogData.split("\n").filter { it.trim().isNotEmpty() }
                } else {
                    emptyList()
                }
                
                // Merge with existing logs (file logs are older, append to end)
                if (fileLogs.isNotEmpty()) {
                    val currentLogs = _logEntries.value.toSet()
                    val newFileLogs = fileLogs.filter { it !in currentLogs }
                    if (newFileLogs.isNotEmpty()) {
                        _logEntries.value = _logEntries.value + newFileLogs.reversed()
                        AppLogger.d("Loaded ${newFileLogs.size} logs from file")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error loading logs from file", e)
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            _logEntries.value = emptyList()
            // Optionally clear repository buffer (commented out to preserve logs across clear)
            // LoggerRepository.clear()
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
                    throw e
                } catch (e: InterruptedIOException) {
                    AppLogger.d("Logcat reading interrupted: ${e.message}")
                } catch (e: Exception) {
                    if (isActive) {
                        AppLogger.e("Error reading logcat", e)
                    }
                } finally {
                    if (logcatProcess == process) {
                        logcatProcess = null
                    }
                }
            } catch (e: CancellationException) {
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
        // No need to unregister receiver - using Flow collection instead
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
