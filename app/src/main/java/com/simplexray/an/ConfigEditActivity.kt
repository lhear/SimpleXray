package com.simplexray.an

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

class ConfigEditActivity : AppCompatActivity() {
    private var editTextConfig: EditText? = null
    private var editTextFilename: EditText? = null
    private var configFile: File? = null
    private var originalFilePath: String? = null
    private var toolbar: Toolbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)
        setContentView(R.layout.activity_config)

        editTextConfig = findViewById(R.id.edit_text_config)
        editTextFilename = findViewById(R.id.edit_text_filename)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val mainConfigLayout = findViewById<LinearLayout>(R.id.main_config_layout)
        ViewCompat.setOnApplyWindowInsetsListener(mainConfigLayout) { v: View, insets: WindowInsetsCompat ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val scrollView = findViewById<ScrollView>(R.id.scrollViewConfig)
            if (scrollView != null) {
                v.setPadding(
                    scrollView.paddingLeft,
                    systemBarsInsets.top,
                    scrollView.paddingRight,
                    scrollView.paddingBottom
                )
            }
            v.setPadding(v.paddingLeft, systemBarsInsets.top, v.paddingRight, imeInsets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        originalFilePath = intent.getStringExtra("filePath")
        if (originalFilePath != null) {
            configFile = originalFilePath?.let { File(it) }
            readConfigFile()
        } else {
            Log.e(TAG, "No file path provided in Intent extras.")
            editTextConfig?.setEnabled(false)
            editTextFilename?.setEnabled(false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.config_edit_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.action_save -> {
                saveConfigFile()
                return true
            }

            R.id.action_share -> {
                shareConfigFile()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun readConfigFile() {
        configFile?.let { file ->
            if (!file.exists()) {
                Log.e(TAG, "config not found.")
                editTextConfig?.apply { isEnabled = false }
                editTextFilename?.apply { isEnabled = false }
                return
            }

            editTextFilename?.setText(fileNameWithoutExtension)

            try {
                val configContent = file.readText()
                editTextConfig?.setText(configContent)
            } catch (e: IOException) {
                Log.e(TAG, "Error reading config file", e)
                editTextConfig?.apply { isEnabled = false }
                editTextFilename?.apply { isEnabled = false }
            }
        } ?: run {
            Log.e(TAG, "Config file not set.")
            editTextConfig?.apply { isEnabled = false }
            editTextFilename?.apply { isEnabled = false }
        }
    }

    private fun saveConfigFile() {
        if (configFile == null || originalFilePath == null) {
            Log.e(TAG, "Config file path not set.")
            return
        }

        val configContent = editTextConfig?.text.toString()
        var newFilename = editTextFilename?.text.toString().trim { it <= ' ' }

        if (newFilename.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.filename_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        if (!isValidFilename(newFilename)) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.filename_invalid)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        if (!newFilename.endsWith(".json")) {
            newFilename += ".json"
        }

        val originalFile = originalFilePath?.let { File(it) }
        val parentDir = originalFile?.parentFile
        if (parentDir == null) {
            Log.e(TAG, "Could not determine parent directory.")
            return
        }
        val newFile = File(parentDir, newFilename)

        if (newFile.exists() && newFile != configFile) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.filename_already_exists)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
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
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.invalid_json_format)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        editTextConfig?.setText(formattedContent)

        try {
            newFile.writeText(formattedContent)

            if (newFile != configFile) {
                if (configFile?.exists() == true) {
                    val deleted = configFile!!.delete()
                    if (!deleted) {
                        Log.w(
                            TAG,
                            "Failed to delete old config file: " + configFile!!.absolutePath
                        )
                    }
                }
                configFile = newFile
                originalFilePath = newFile.absolutePath
            }

            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.config_save_success)
                .setPositiveButton(android.R.string.ok, null)
                .show()

        } catch (e: IOException) {
            Log.e(TAG, "Error writing config file", e)
        }

        editTextFilename?.setText(fileNameWithoutExtension)
    }

    private fun shareConfigFile() {
        if (configFile == null || !configFile!!.exists()) {
            Log.e(TAG, "Config file not found.")
            return
        }

        val configContent = editTextConfig?.text.toString()

        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.setType("text/plain")
        shareIntent.putExtra(Intent.EXTRA_TEXT, configContent)

        startActivity(Intent.createChooser(shareIntent, null))
    }

    private val fileNameWithoutExtension: String
        get() {
            var fileName = configFile!!.name
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
