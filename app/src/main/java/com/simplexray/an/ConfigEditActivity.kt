package com.simplexray.an

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

class ConfigEditActivity : ComponentActivity() {
    private lateinit var originalFilePath: String
    private lateinit var configFile: File
    private var configContentState by mutableStateOf("")
    private var filenameState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)

        originalFilePath = intent.getStringExtra("filePath").toString()
        configFile = File(originalFilePath)

        filenameState = fileNameWithoutExtension

        lifecycleScope.launch(Dispatchers.IO) {
            val content = readConfigFileContent()
            withContext(Dispatchers.Main) {
                configContentState = content
                filenameState = fileNameWithoutExtension
            }
        }

        setContent {
            val context = LocalContext.current
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                dynamicColor && isDark -> dynamicDarkColorScheme(context)
                dynamicColor && !isDark -> dynamicLightColorScheme(context)
                isDark -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                ConfigEditScreen(
                    onSave = { content, filename ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val newContent = saveConfigFile(content, filename)
                            withContext(Dispatchers.Main) {
                                newContent?.let {
                                    configContentState = it
                                }
                                filenameState = fileNameWithoutExtension
                            }
                        }
                        null
                    },
                    onShare = { shareConfigFile() },
                    filename = filenameState,
                    configContent = configContentState,
                    onBackClick = { finish() },
                    onValidateFilename = { name ->
                        val trimmedName = name.trim()
                        if (trimmedName.isEmpty()) {
                            R.string.filename_empty
                        } else if (!isValidFilename(trimmedName)) {
                            R.string.filename_invalid
                        } else {
                            null
                        }
                    },
                    onConfigContentChange = { newValue ->
                        configContentState = newValue
                    },
                    onFilenameChange = { newFilename ->
                        filenameState = newFilename
                    }
                )
            }
        }
    }

    private suspend fun readConfigFileContent(): String = withContext(Dispatchers.IO) {
        configFile.let { file ->
            if (!file.exists()) {
                Log.e(TAG, "config not found.")
                return@withContext ""
            }

            try {
                return@withContext file.readText()
            } catch (e: IOException) {
                Log.e(TAG, "Error reading config file", e)
                return@withContext ""
            }
        }
    }

    private suspend fun saveConfigFile(configContent: String, filename: String): String? =
        withContext(Dispatchers.IO) {
            var newFilename = filename.trim { it <= ' ' }

            if (newFilename.isEmpty()) {
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@ConfigEditActivity)
                        .setMessage(R.string.filename_empty)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                return@withContext null
            }

            if (!isValidFilename(newFilename)) {
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@ConfigEditActivity)
                        .setMessage(R.string.filename_invalid)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                return@withContext null
            }

            if (!newFilename.endsWith(".json")) {
                newFilename += ".json"
            }

            val originalFile = File(originalFilePath)
            val parentDir = originalFile.parentFile
            if (parentDir == null) {
                Log.e(TAG, "Could not determine parent directory.")
                return@withContext null
            }
            val newFile = File(parentDir, newFilename)

            if (newFile.exists() && newFile != configFile) {
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@ConfigEditActivity)
                        .setMessage(R.string.filename_already_exists)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                return@withContext null
            }

            var formattedContent: String
            try {
                val jsonObject = JSONObject(configContent)
                (jsonObject["log"] as? JSONObject)?.apply {
                    remove("access")?.also { Log.d(TAG, "Removed log.access") }
                    remove("error")?.also { Log.d(TAG, "Removed log.error") }
                }

                formattedContent = jsonObject.toString(2)
                formattedContent = formattedContent.replace("\\\\/".toRegex(), "/")
            } catch (e: JSONException) {
                Log.e(TAG, "Invalid JSON format", e)
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@ConfigEditActivity)
                        .setMessage(R.string.invalid_json_format)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                return@withContext null
            }

            try {
                newFile.writeText(formattedContent)

                if (newFile != configFile) {
                    if (configFile.exists()) {
                        val deleted = configFile.delete()
                        if (!deleted) {
                            Log.w(
                                TAG,
                                "Failed to delete old config file: " + configFile.absolutePath
                            )
                        }
                    }
                    configFile = newFile
                    originalFilePath = newFile.absolutePath
                }

                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@ConfigEditActivity)
                        .setMessage(R.string.config_save_success)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                return@withContext formattedContent
            } catch (e: IOException) {
                Log.e(TAG, "Error writing config file", e)
                return@withContext null
            }
        }

    private fun shareConfigFile() {
        if (!configFile.exists()) {
            Log.e(TAG, "Config file not found.")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val configContent = readConfigFileContent()

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.setType("text/plain")
                shareIntent.putExtra(Intent.EXTRA_TEXT, configContent)

                startActivity(Intent.createChooser(shareIntent, null))
            }
        }
    }

    private val fileNameWithoutExtension: String
        get() {
            var fileName = configFile.name
            if (fileName.endsWith(".json")) {
                fileName = fileName.substring(0, fileName.length - ".json".length)
            }
            return fileName
        }

    private fun isValidFilename(filename: String): Boolean {
        val invalidChars = "[\\\\/:*?\"<>|]"
        return !Pattern.compile(invalidChars).matcher(filename).find()
    }

    private fun setStatusBarFontColorByTheme(isDark: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)
    }

    companion object {
        private const val TAG = "ConfigEditActivity"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    onSave: (String, String) -> String?,
    onShare: () -> Unit,
    filename: String,
    configContent: String,
    onBackClick: () -> Unit,
    onValidateFilename: (String) -> Int?,
    onConfigContentChange: (String) -> Unit,
    onFilenameChange: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var filenameErrorResId by remember { mutableStateOf<Int?>(null) }

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.config)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.back
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val errorResId = onValidateFilename(filename)
                        if (errorResId != null) {
                            filenameErrorResId = errorResId
                        } else {
                            onSave(configContent, filename)
                        }
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.save),
                            contentDescription = stringResource(id = R.string.save)
                        )
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.share)) },
                            onClick = {
                                onShare()
                                showMenu = false
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(top = paddingValues.calculateTopPadding())
                    .verticalScroll(scrollState)
            ) {
                TextField(
                    value = filename,
                    onValueChange = { v ->
                        onFilenameChange(v)
                        filenameErrorResId = onValidateFilename(v)
                    },
                    label = { Text(stringResource(id = R.string.filename)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                    ),
                    isError = filenameErrorResId != null,
                    supportingText = {
                        if (filenameErrorResId != null) {
                            Text(stringResource(id = filenameErrorResId!!))
                        }
                    }
                )

                TextField(
                    value = configContent,
                    onValueChange = onConfigContentChange,
                    label = { Text(stringResource(R.string.content)) },
                    modifier = Modifier
                        .padding(bottom = if (isKeyboardOpen) 0.dp else paddingValues.calculateBottomPadding())
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                    )
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewConfigEditScreen() {
    ConfigEditScreen(
        onSave = { _, _ -> null },
        onShare = { },
        filename = "",
        configContent = "",
        onBackClick = { },
        onValidateFilename = { null },
        onConfigContentChange = {},
        onFilenameChange = {}
    )
}
