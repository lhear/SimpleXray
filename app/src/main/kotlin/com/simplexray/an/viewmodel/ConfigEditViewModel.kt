package com.simplexray.an.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.an.R
import com.simplexray.an.common.ConfigFormatter
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
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

private const val TAG = "ConfigEditViewModel"

sealed class ConfigEditUiEvent {
    data class ShowSnackbar(val messageResId: Int) : ConfigEditUiEvent()
    data class ShareContent(val content: String) : ConfigEditUiEvent()
    data object FinishActivity : ConfigEditUiEvent()
}

class ConfigEditViewModel(
    application: Application,
    private val initialFilePath: String,
    prefs: Preferences
) :
    AndroidViewModel(application) {

    private var _configFile: File
    private var _originalFilePath: String = initialFilePath

    private val _configContent = MutableStateFlow("")
    val configContent: StateFlow<String> = _configContent.asStateFlow()

    private val _filename = MutableStateFlow("")
    val filename: StateFlow<String> = _filename.asStateFlow()

    private val _filenameErrorResId = MutableStateFlow<Int?>(null)
    val filenameErrorResId: StateFlow<Int?> = _filenameErrorResId.asStateFlow()

    private val _uiEvent = Channel<ConfigEditUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private val fileManager: FileManager = FileManager(application, prefs)

    init {
        _configFile = File(initialFilePath)
        _filename.value = _configFile.nameWithoutExtension

        viewModelScope.launch(Dispatchers.IO) {
            val content = readConfigFileContent()
            withContext(Dispatchers.Main) {
                _configContent.value = content
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

    fun onConfigContentChange(newValue: String) {
        _configContent.value = newValue
    }

    fun onFilenameChange(newFilename: String) {
        _filename.value = newFilename
        _filenameErrorResId.value = validateFilename(newFilename)
    }

    private fun validateFilename(name: String): Int? {
        val trimmedName = name.trim()
        return if (trimmedName.isEmpty()) {
            R.string.filename_empty
        } else if (!isValidFilenameChars(trimmedName)) {
            R.string.filename_invalid
        } else {
            null
        }
    }

    private fun isValidFilenameChars(filename: String): Boolean {
        val invalidChars = "[\\\\/:*?\"<>|]"
        return !Pattern.compile(invalidChars).matcher(filename).find()
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
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar(R.string.filename_already_exists))
                return@launch
            }

            val formattedContent: String
            try {
                formattedContent = ConfigFormatter.formatConfigContent(_configContent.value)
            } catch (e: JSONException) {
                Log.e(TAG, "Invalid JSON format", e)
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar(R.string.invalid_config_format))
                return@launch
            }

            val success = fileManager.renameConfigFile(_configFile, newFile, formattedContent)

            if (success) {
                if (newFile.absolutePath != oldFilePath) {
                    _configFile = newFile
                    _originalFilePath = newFile.absolutePath
                }

                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar(R.string.config_save_success))
                _configContent.value = formattedContent
                _filename.value = _configFile.nameWithoutExtension
            } else {
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar(R.string.save_fail))
            }
        }
    }

    fun shareConfigFile() {
        viewModelScope.launch {
            if (!_configFile.exists()) {
                Log.e(TAG, "Config file not found.")
                _uiEvent.trySend(ConfigEditUiEvent.ShowSnackbar(R.string.config_not_found))
                return@launch
            }
            val content = readConfigFileContent()
            _uiEvent.trySend(ConfigEditUiEvent.ShareContent(content))
        }
    }

    fun onBackClick() {
        viewModelScope.launch {
            _uiEvent.trySend(ConfigEditUiEvent.FinishActivity)
        }
    }
}

class ConfigEditViewModelFactory(
    private val application: Application,
    private val filePath: String,
    private val preferences: Preferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConfigEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConfigEditViewModel(application, filePath, preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
