package com.simplexray.an.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.simplexray.an.R
import com.simplexray.an.common.ConfigUtils
import com.simplexray.an.common.FilenameValidator
import com.simplexray.an.data.source.FileManager
import com.simplexray.an.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.Deflater

private const val TAG = "ConfigEditViewModel"

sealed class ConfigEditUiEvent {
    data class ShowSnackbar(val message: String) : ConfigEditUiEvent()
    data class ShareContent(val content: String) : ConfigEditUiEvent()
    data object NavigateBack : ConfigEditUiEvent()
}

class ConfigEditViewModel(
    application: Application,
    private val initialFilePath: String,
    prefs: Preferences
) :
    AndroidViewModel(application) {

    private var _configFile: File
    private var _originalFilePath: String = initialFilePath

    private val _configTextFieldValue = MutableStateFlow(TextFieldValue())
    val configTextFieldValue: StateFlow<TextFieldValue> = _configTextFieldValue.asStateFlow()

    private val _filename = MutableStateFlow("")
    val filename: StateFlow<String> = _filename.asStateFlow()

    private val _filenameErrorMessage = MutableStateFlow<String?>(null)
    val filenameErrorMessage: StateFlow<String?> = _filenameErrorMessage.asStateFlow()

    private val _uiEvent = Channel<ConfigEditUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _hasConfigChanged = MutableStateFlow(false)
    val hasConfigChanged: StateFlow<Boolean> = _hasConfigChanged.asStateFlow()

    // Advanced editor features
    private val _jsonValidationError = MutableStateFlow<String?>(null)
    val jsonValidationError: StateFlow<String?> = _jsonValidationError.asStateFlow()

    private val _lineCount = MutableStateFlow(0)
    val lineCount: StateFlow<Int> = _lineCount.asStateFlow()

    private val _charCount = MutableStateFlow(0)
    val charCount: StateFlow<Int> = _charCount.asStateFlow()

    private val _wordCount = MutableStateFlow(0)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    private val _cursorLine = MutableStateFlow(1)
    val cursorLine: StateFlow<Int> = _cursorLine.asStateFlow()

    private val _cursorColumn = MutableStateFlow(1)
    val cursorColumn: StateFlow<Int> = _cursorColumn.asStateFlow()

    // Undo/Redo
    private val undoStack = mutableListOf<TextFieldValue>()
    private val redoStack = mutableListOf<TextFieldValue>()
    private val maxHistorySize = 50

    private val fileManager: FileManager = FileManager(application, prefs)

    init {
        _configFile = File(initialFilePath)
        _filename.value = _configFile.nameWithoutExtension

        viewModelScope.launch(Dispatchers.IO) {
            val content = readConfigFileContent()
            withContext(Dispatchers.Main) {
                _configTextFieldValue.value = _configTextFieldValue.value.copy(text = content)
                updateStatistics(content, 0)
                validateJsonAsync(content)
            }
        }
    }

    private val File.nameWithoutExtension: String
        get() {
            var name = this.name
            if (name.endsWith(".json")) {
                name = name.substring(0, name.length - ".json".length)
            }
            return name
        }

    private suspend fun readConfigFileContent(): String = withContext(Dispatchers.IO) {
        if (!_configFile.exists()) {
            Log.e(TAG, "Config not found at path: $initialFilePath")
            return@withContext ""
        }
        try {
            _configFile.readText()
        } catch (e: IOException) {
            Log.e(TAG, "Error reading config file", e)
            ""
        }
    }

    fun onConfigContentChange(newValue: TextFieldValue) {
        // Save to undo stack
        if (undoStack.isEmpty() || undoStack.last() != _configTextFieldValue.value) {
            undoStack.add(_configTextFieldValue.value.copy())
            if (undoStack.size > maxHistorySize) {
                undoStack.removeAt(0)
            }
            redoStack.clear() // Clear redo when new change
        }

        _configTextFieldValue.value = newValue
        _hasConfigChanged.value = true
        
        // Update statistics
        updateStatistics(newValue.text, newValue.selection.start)
        
        // Validate JSON in background
        validateJsonAsync(newValue.text)
    }

    private fun updateStatistics(text: String, cursorPosition: Int) {
        _charCount.value = text.length
        _wordCount.value = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        _lineCount.value = text.lines().size
        
        // Calculate cursor position
        val lines = text.substring(0, cursorPosition.coerceAtMost(text.length))
        val lineNumber = lines.lines().size
        val columnNumber = lines.lines().lastOrNull()?.length ?: 0
        _cursorLine.value = lineNumber
        _cursorColumn.value = columnNumber + 1
    }

    private fun validateJsonAsync(text: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (text.isBlank()) {
                    withContext(Dispatchers.Main) {
                        _jsonValidationError.value = null
                    }
                    return@launch
                }
                JSONObject(text)
                withContext(Dispatchers.Main) {
                    _jsonValidationError.value = null
                }
            } catch (e: JSONException) {
                withContext(Dispatchers.Main) {
                    _jsonValidationError.value = e.message ?: "Invalid JSON"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _jsonValidationError.value = "Error: ${e.message}"
                }
            }
        }
    }

    fun onFilenameChange(newFilename: String) {
        _filename.value = newFilename
        _filenameErrorMessage.value = validateFilename(newFilename)
        _hasConfigChanged.value = true
    }

    private fun validateFilename(name: String): String? {
        return FilenameValidator.validateFilename(application, name)
    }

    fun saveConfigFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFilePath = _configFile.absolutePath

            var newFilename = _filename.value.trim { it <= ' ' }

            val validationError = validateFilename(newFilename)
            if (validationError != null) {
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar(validationError))
                return@launch
            }

            if (!newFilename.endsWith(".json")) {
                newFilename += ".json"
            }

            val parentDir = _configFile.parentFile
            if (parentDir == null) {
                Log.e(TAG, "Could not determine parent directory.")
                return@launch
            }
            val newFile = File(parentDir, newFilename)

            if (newFile.exists() && newFile.absolutePath != _configFile.absolutePath) {
                _uiEvent.trySend(
                    ConfigEditUiEvent.ShowSnackbar(
                        application.getString(R.string.filename_already_exists)
                    )
                )
                return@launch
            }

            val formattedContent: String
            try {
                formattedContent =
                    ConfigUtils.formatConfigContent(_configTextFieldValue.value.text)
            } catch (e: JSONException) {
                Log.e(TAG, "Invalid JSON format", e)
                _uiEvent.trySend(
                    ConfigEditUiEvent.ShowSnackbar(
                        application.getString(R.string.invalid_config_format)
                    )
                )
                return@launch
            }

            val success = fileManager.renameConfigFile(_configFile, newFile, formattedContent)

            if (success) {
                if (newFile.absolutePath != oldFilePath) {
                    _configFile = newFile
                    _originalFilePath = newFile.absolutePath
                }

                _uiEvent.trySend(
                    ConfigEditUiEvent.ShowSnackbar(
                        application.getString(R.string.config_save_success)
                    )
                )
                _configTextFieldValue.value =
                    _configTextFieldValue.value.copy(text = formattedContent)
                _filename.value = _configFile.nameWithoutExtension
                _hasConfigChanged.value = false
            } else {
                _uiEvent.trySend(
                    ConfigEditUiEvent.ShowSnackbar(
                        application.getString(R.string.save_fail)
                    )
                )
            }
        }
    }

    fun shareConfigFile() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_configFile.exists()) {
                Log.e(TAG, "Config file not found.")
                _uiEvent.trySend(
                    ConfigEditUiEvent.ShowSnackbar(
                        application.getString(R.string.config_not_found)
                    )
                )
                return@launch
            }
            val content = readConfigFileContent()
            val name = _filename.value

            val input = content.toByteArray(Charsets.UTF_8)
            val outputStream = ByteArrayOutputStream()
            val deflater = Deflater()
            val buffer = ByteArray(1024)
            deflater.setInput(input)
            deflater.finish()
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                outputStream.write(buffer, 0, count)
            }
            deflater.end()
            val compressed = outputStream.toByteArray()
            val encodedContent = Base64.getUrlEncoder().encodeToString(compressed)
            val encodedName = URLEncoder.encode(name, "UTF-8")
            val shareableLink = "simplexray://config/$encodedName/$encodedContent"
            _uiEvent.trySend(ConfigEditUiEvent.ShareContent(shareableLink))
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

    // Format JSON (beautify)
    fun formatJson() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = _configTextFieldValue.value.text
                if (text.isBlank()) {
                    _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("No content to format"))
                    return@launch
                }
                val json = JSONObject(text)
                val formatted = json.toString(2)
                withContext(Dispatchers.Main) {
                    _configTextFieldValue.value = TextFieldValue(
                        text = formatted,
                        selection = TextRange(formatted.length)
                    )
                    _hasConfigChanged.value = true
                    _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("JSON formatted"))
                }
            } catch (e: JSONException) {
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Invalid JSON: ${e.message}"))
            } catch (e: Exception) {
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Error: ${e.message}"))
            }
        }
    }

    // Minify JSON
    fun minifyJson() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = _configTextFieldValue.value.text
                if (text.isBlank()) {
                    _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("No content to minify"))
                    return@launch
                }
                val json = JSONObject(text)
                val minified = json.toString(0).replace("\\s+".toRegex(), " ")
                withContext(Dispatchers.Main) {
                    val cursorPos = _configTextFieldValue.value.selection.start
                    _configTextFieldValue.value = TextFieldValue(
                        text = minified,
                        selection = TextRange(cursorPos.coerceAtMost(minified.length))
                    )
                    _hasConfigChanged.value = true
                    _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("JSON minified"))
                }
            } catch (e: JSONException) {
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Invalid JSON: ${e.message}"))
            } catch (e: Exception) {
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Error: ${e.message}"))
            }
        }
    }

    // Validate JSON
    fun validateJson(): Boolean {
        return try {
            val text = _configTextFieldValue.value.text
            if (text.isBlank()) {
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Content is empty"))
                return false
            }
            JSONObject(text)
            _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("JSON is valid ✓"))
            true
        } catch (e: JSONException) {
            _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Invalid JSON: ${e.message}"))
            false
        } catch (e: Exception) {
            _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Error: ${e.message}"))
            false
        }
    }

    // Undo
    fun undo(): Boolean {
        if (undoStack.isEmpty()) {
            _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Nothing to undo"))
            return false
        }
        val current = _configTextFieldValue.value.copy()
        redoStack.add(current)
        if (redoStack.size > maxHistorySize) {
            redoStack.removeAt(0)
        }
        _configTextFieldValue.value = undoStack.removeAt(undoStack.size - 1)
        _hasConfigChanged.value = true
        updateStatistics(_configTextFieldValue.value.text, _configTextFieldValue.value.selection.start)
        return true
    }

    // Redo
    fun redo(): Boolean {
        if (redoStack.isEmpty()) {
            _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Nothing to redo"))
            return false
        }
        val current = _configTextFieldValue.value.copy()
        undoStack.add(current)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
        _configTextFieldValue.value = redoStack.removeAt(redoStack.size - 1)
        _hasConfigChanged.value = true
        updateStatistics(_configTextFieldValue.value.text, _configTextFieldValue.value.selection.start)
        return true
    }

    // Insert snippet
    fun insertSnippet(snippet: String) {
        val current = _configTextFieldValue.value
        val cursorPos = current.selection.start
        val newText = current.text.substring(0, cursorPos) + 
                     snippet + 
                     current.text.substring(cursorPos)
        val newCursorPos = cursorPos + snippet.length
        _configTextFieldValue.value = TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
        _hasConfigChanged.value = true
        updateStatistics(newText, newCursorPos)
    }

    // Find and replace (basic)
    fun findText(query: String, startFrom: Int = 0): Int {
        val text = _configTextFieldValue.value.text
        val index = text.indexOf(query, startFrom)
        if (index != -1) {
            _configTextFieldValue.value = TextFieldValue(
                text = text,
                selection = TextRange(index, index + query.length)
            )
        }
        return index
    }

    // Get JSON statistics
    fun getJsonStatistics(): Map<String, Any> {
        return try {
            val text = _configTextFieldValue.value.text
            if (text.isBlank()) {
                return emptyMap()
            }
            val json = JSONObject(text)
            val keys = json.keys().asSequence().toList()
            mapOf(
                "keys" to keys.size,
                "topLevelKeys" to keys,
                "isValid" to true,
                "size" to text.length,
                "lines" to text.lines().size
            )
        } catch (e: Exception) {
            mapOf("isValid" to false, "error" to (e.message ?: "Unknown error"))
        }
    }

    // Insert template
    fun insertTemplate(templateName: String) {
        val templates = mapOf(
            "vless_tcp_tls" to """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "example.com",
                        "port": 443,
                        "users": [
                          {
                            "id": "uuid-here",
                            "encryption": "none"
                          }
                        ]
                      }
                    ]
                  },
                  "streamSettings": {
                    "network": "tcp",
                    "security": "tls"
                  }
                }
              ]
            }
            """.trimIndent(),
            "vless_ws_tls" to """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "example.com",
                        "port": 443,
                        "users": [
                          {
                            "id": "uuid-here",
                            "encryption": "none"
                          }
                        ]
                      }
                    ]
                  },
                  "streamSettings": {
                    "network": "ws",
                    "security": "tls",
                    "wsSettings": {
                      "path": "/path"
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
            "vless_grpc_tls" to """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "example.com",
                        "port": 443,
                        "users": [
                          {
                            "id": "uuid-here",
                            "encryption": "none"
                          }
                        ]
                      }
                    ]
                  },
                  "streamSettings": {
                    "network": "grpc",
                    "security": "tls",
                    "grpcSettings": {
                      "serviceName": "grpc-service"
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
            "vless_http_tls" to """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "example.com",
                        "port": 443,
                        "users": [
                          {
                            "id": "uuid-here",
                            "encryption": "none"
                          }
                        ]
                      }
                    ]
                  },
                  "streamSettings": {
                    "network": "http",
                    "security": "tls",
                    "httpSettings": {
                      "path": "/path",
                      "host": ["example.com"]
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
            "vless_quic_tls" to """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "example.com",
                        "port": 443,
                        "users": [
                          {
                            "id": "uuid-here",
                            "encryption": "none"
                          }
                        ]
                      }
                    ]
                  },
                  "streamSettings": {
                    "network": "quic",
                    "security": "tls",
                    "quicSettings": {
                      "security": "none",
                      "key": "",
                      "header": {
                        "type": "none"
                      }
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
            "vless_kcp_tls" to """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "example.com",
                        "port": 443,
                        "users": [
                          {
                            "id": "uuid-here",
                            "encryption": "none"
                          }
                        ]
                      }
                    ]
                  },
                  "streamSettings": {
                    "network": "kcp",
                    "security": "tls",
                    "kcpSettings": {
                      "mtu": 1350,
                      "tti": 20,
                      "uplinkCapacity": 5,
                      "downlinkCapacity": 20,
                      "congestion": false,
                      "readBufferSize": 1,
                      "writeBufferSize": 1,
                      "header": {
                        "type": "none"
                      }
                    }
                  }
                }
              ]
            }
            """.trimIndent()
        )

        templates[templateName]?.let { template ->
            _configTextFieldValue.value = TextFieldValue(
                text = template,
                selection = TextRange(template.length)
            )
            _hasConfigChanged.value = true
            updateStatistics(template, template.length)
            _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Template inserted: $templateName"))
        } ?: run {
            _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar("Template not found: $templateName"))
        }
    }

    // Clear all content
    fun clearContent() {
        _configTextFieldValue.value = TextFieldValue("")
        _hasConfigChanged.value = true
        updateStatistics("", 0)
    }

    // Select all
    fun selectAll() {
        val text = _configTextFieldValue.value.text
        _configTextFieldValue.value = TextFieldValue(
            text = text,
            selection = TextRange(0, text.length)
        )
    }
}
