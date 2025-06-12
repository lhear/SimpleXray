package com.simplexray.an

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

class MainActivity : AppCompatActivity(), OnConfigActionListener {
    private var prefs: Preferences? = null
    private var startReceiver: BroadcastReceiver? = null
    private var stopReceiver: BroadcastReceiver? = null
    private var bottomNavigationView: BottomNavigationView? = null
    private var createFileLauncher: ActivityResultLauncher<String>? = null
    private var compressedBackupData: ByteArray? = null
    private var openFileLauncher: ActivityResultLauncher<Array<String>>? = null
    override var executorService: ExecutorService? = null
        private set
    private var vpnPrepareLauncher: ActivityResultLauncher<Intent>? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var fragmentAdapter: MainFragmentStateAdapter
    private lateinit var toolbar: Toolbar

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences(this)
        prefs!!.enable = isServiceRunning(this, TProxyService::class.java)
        setupUI()
        extractAssetsIfNeeded()
        executorService = Executors.newSingleThreadExecutor()
        viewPager = findViewById(R.id.view_pager)
        fragmentAdapter = MainFragmentStateAdapter(this)
        viewPager.setAdapter(fragmentAdapter)
        viewPager.setUserInputEnabled(false)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        TabLayoutMediator(
            NonDisplayedTabLayout(this), viewPager
        ) { _: TabLayout.Tab?, _: Int -> }.attach()
        bottomNavigationView!!.setOnItemSelectedListener { item: MenuItem ->
            val itemId = item.itemId
            Log.d(TAG, "BottomNavigationView selected item id: $itemId")
            var targetPosition = -1
            when (itemId) {
                R.id.menu_bottom_config -> {
                    targetPosition = 0
                }

                R.id.menu_bottom_log -> {
                    targetPosition = 1
                }

                R.id.menu_bottom_settings -> {
                    targetPosition = 2
                }
            }

            if (targetPosition != -1 && targetPosition != viewPager.currentItem) {
                viewPager.setCurrentItem(targetPosition, true)
                Log.d(TAG, "ViewPager2 swiped to position: $targetPosition")
                return@setOnItemSelectedListener true
            }
            Log.d(TAG, "BottomNavigationView item already selected or position invalid.")
            false
        }
        val pageChangeCallback: OnPageChangeCallback = object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNavigationView!!.menu.getItem(position).setChecked(true)
                var title = getString(R.string.app_name)
                val itemId = bottomNavigationView!!.menu.getItem(position).itemId
                when (itemId) {
                    R.id.menu_bottom_config -> {
                        title = getString(R.string.configuration)
                    }

                    R.id.menu_bottom_log -> {
                        title = getString(R.string.log)
                    }

                    R.id.menu_bottom_settings -> {
                        title = getString(R.string.settings)
                    }
                }
                toolbar.title = title
                invalidateOptionsMenu()
                Log.d(TAG, "ViewPager2 page selected: $position. Action Bar Title updated.")
            }
        }
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
        registerReceivers()
        setupFileLaunchers()
        vpnPrepareLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    controlMenuClickable = true
                    val isEnable = prefs!!.enable
                    startService(
                        Intent(
                            this,
                            TProxyService::class.java
                        ).setAction(if (isEnable) TProxyService.ACTION_DISCONNECT else TProxyService.ACTION_CONNECT)
                    )
                } else {
                    controlMenuClickable = true
                }
            }
        if (savedInstanceState != null) {
            val restoredPosition = savedInstanceState.getInt("viewPagerPosition", 0)
            viewPager.setCurrentItem(restoredPosition, false)
            Log.d(TAG, "Restored ViewPager2 position: $restoredPosition")
            viewPager.post { pageChangeCallback.onPageSelected(restoredPosition) }
        } else {
            viewPager.setCurrentItem(0, false)
            viewPager.post { pageChangeCallback.onPageSelected(0) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("viewPagerPosition", viewPager.currentItem)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        Log.d(TAG, "MainActivity onCreateOptionsMenu called.")
        return true
    }

    private fun setupFileLaunchers() {
        createFileLauncher =
            registerForActivityResult<String, Uri>(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
                if (uri != null) {
                    if (compressedBackupData != null) {
                        val dataToWrite: ByteArray = compressedBackupData as ByteArray
                        compressedBackupData = null
                        executorService!!.submit {
                            try {
                                contentResolver.openOutputStream(uri).use { os ->
                                    if (os != null) {
                                        os.write(dataToWrite)
                                        Log.d(TAG, "Backup successful to: $uri")
                                        runOnUiThread {
                                            Toast.makeText(
                                                this,
                                                R.string.backup_success,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        Log.e(
                                            TAG,
                                            "Failed to open output stream for backup URI: $uri"
                                        )
                                        runOnUiThread {
                                            Toast.makeText(
                                                this,
                                                R.string.backup_failed,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            } catch (e: IOException) {
                                Log.e(TAG, "Error writing backup data to URI: $uri", e)
                                runOnUiThread {
                                    Toast.makeText(
                                        this,
                                        R.string.backup_failed,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Compressed backup data is null in launcher callback.")
                    }
                } else {
                    Log.w(TAG, "Backup file creation cancelled or failed (URI is null).")
                    compressedBackupData = null
                }
            }
        openFileLauncher =
            registerForActivityResult<Array<String>, Uri>(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    startRestoreTask(uri)
                } else {
                    Log.w(TAG, "Restore file selection cancelled or failed (URI is null).")
                }
            }
    }

    override fun performBackup() {
        try {
            val gson = Gson()
            val preferencesMap: MutableMap<String, Any> = mutableMapOf()
            preferencesMap[Preferences.SOCKS_ADDR] = prefs!!.socksAddress
            preferencesMap[Preferences.SOCKS_PORT] = prefs!!.socksPort
            preferencesMap[Preferences.DNS_IPV4] = prefs!!.dnsIpv4
            preferencesMap[Preferences.DNS_IPV6] = prefs!!.dnsIpv6
            preferencesMap[Preferences.IPV6] = prefs!!.ipv6
            preferencesMap[Preferences.APPS] = ArrayList(
                prefs!!.apps ?: emptySet()
            )
            preferencesMap[Preferences.BYPASS_LAN] = prefs!!.bypassLan
            preferencesMap[Preferences.USE_TEMPLATE] = prefs!!.useTemplate
            preferencesMap[Preferences.HTTP_PROXY_ENABLED] = prefs!!.httpProxyEnabled
            val configFilesMap: MutableMap<String, String> = mutableMapOf()
            val filesDir = filesDir
            val files = filesDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile && file.name.endsWith(".json")) {
                        try {
                            val content = readFileContent(file)
                            configFilesMap[file.name] = content
                        } catch (e: IOException) {
                            Log.e(TAG, "Error reading config file: $file.name", e)
                        }
                    }
                }
            }
            val backupData: MutableMap<String, Any> = mutableMapOf()
            backupData["preferences"] = preferencesMap
            backupData["configFiles"] = configFilesMap
            val jsonString = gson.toJson(backupData)
            val input = jsonString.toByteArray(StandardCharsets.UTF_8)
            val deflater = Deflater()
            deflater.setInput(input)
            deflater.finish()
            val outputStream = ByteArrayOutputStream(input.size)
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                outputStream.write(buffer, 0, count)
            }
            outputStream.close()
            compressedBackupData = outputStream.toByteArray()
            deflater.end()
            val filename = "simplexray_backup_" + System.currentTimeMillis() + ".dat"
            createFileLauncher!!.launch(filename)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error during backup process", e)
        }
    }

    @Throws(IOException::class)
    private fun readFileContent(file: File): String {
        val content = StringBuilder()
        BufferedReader(
            InputStreamReader(
                Files.newInputStream(file.toPath()),
                StandardCharsets.UTF_8
            )
        ).use { reader ->
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                content.append(line).append('\n')
            }
        }
        return content.toString()
    }

    private fun setupUI() {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)
        setContentView(R.layout.main)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val mainLinearLayout = findViewById<LinearLayout>(R.id.main_linear_layout)
        ViewCompat.setOnApplyWindowInsetsListener(mainLinearLayout) { v: View, insets: WindowInsetsCompat ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBarsInsets.left, systemBarsInsets.top, systemBarsInsets.right, 0)
            insets
        }
        bottomNavigationView = findViewById(R.id.bottom_navigation)
    }

    private fun registerReceivers() {
        startReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Service started")
                val log = fragmentAdapter.logFragment
                log.reloadLogs()
                Log.d(TAG, "Called reloadLogs on LogFragment.")

                Handler(mainLooper).postDelayed({
                    prefs!!.enable = true
                    val config = fragmentAdapter.configFragment
                    config.updateControlMenuItemIcon()
                    Log.d(TAG, "Called updateControlMenuItemIcon on ConfigFragment.")
                    controlMenuClickable = true
                }, 100)
            }
        }
        stopReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Service stopped")
                Handler(mainLooper).postDelayed({
                    prefs!!.enable = false
                    val config = fragmentAdapter.configFragment
                    config.updateControlMenuItemIcon()
                    Log.d(TAG, "Called updateControlMenuItemIcon on ConfigFragment.")
                    controlMenuClickable = true
                }, 300)
            }
        }
        val startSuccessFilter = IntentFilter(TProxyService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(startReceiver, startSuccessFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(startReceiver, startSuccessFilter)
        }
        val stopSuccessFilter = IntentFilter(TProxyService.ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, stopSuccessFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopReceiver, stopSuccessFilter)
        }
    }

    private fun extractAssetsIfNeeded() {
        val context = applicationContext
        val files = arrayOf("geoip.dat", "geosite.dat")
        val dir = context.filesDir
        dir.mkdirs()
        for (file in files) {
            val targetFile = File(dir, file)
            var needsExtraction = false

            var isCustomImported = false
            if ("geoip.dat" == file) {
                isCustomImported = prefs!!.customGeoipImported
            } else if ("geosite.dat" == file) {
                isCustomImported = prefs!!.customGeositeImported
            }

            if (isCustomImported) {
                Log.d(TAG, "Custom file already imported for $file, skipping asset extraction.")
                continue
            }

            if (targetFile.exists()) {
                try {
                    val existingFileHash =
                        calculateSha256(Files.newInputStream(targetFile.toPath()))
                    val assetHash = calculateSha256(context.assets.open(file))
                    if (existingFileHash != assetHash) {
                        needsExtraction = true
                    }
                } catch (e: IOException) {
                    needsExtraction = true
                    Log.d(TAG, e.toString())
                } catch (e: NoSuchAlgorithmException) {
                    needsExtraction = true
                    Log.d(TAG, e.toString())
                }
            } else {
                needsExtraction = true
            }
            if (needsExtraction) {
                try {
                    context.assets.open(file).use { `in` ->
                        FileOutputStream(targetFile).use { out ->
                            val buffer = ByteArray(1024)
                            var read: Int
                            while ((`in`.read(buffer).also { read = it }) != -1) {
                                out.write(buffer, 0, read)
                            }
                            Log.d(
                                TAG,
                                "Extracted asset: " + file + " to " + targetFile.absolutePath
                            )
                        }
                    }
                } catch (e: IOException) {
                    throw RuntimeException("Failed to extract asset: $file", e)
                }
            } else {
                Log.d(TAG, "Asset $file already exists and matches hash, skipping extraction.")
            }
        }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun calculateSha256(`is`: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024)
        var read: Int
        `is`.use { inputStream ->
            while ((inputStream.read(buffer).also { read = it }) != -1) {
                digest.update(buffer, 0, read)
            }
        }

        val hashBytes = digest.digest()
        val sb = StringBuilder()
        for (hashByte in hashBytes) {
            sb.append(String.format("%02x", hashByte))
        }
        return sb.toString()
    }

    override fun switchVpnService() {
        if (!controlMenuClickable) return
        val prefs = Preferences(this)
        val selectedConfigPath = prefs.selectedConfigPath
        if (!prefs.enable && (selectedConfigPath.isNullOrEmpty() || !(File(
                selectedConfigPath
            ).exists()))
        ) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.not_select_config)
                .setPositiveButton(R.string.confirm, null).show()
            Log.w(TAG, "Attempted to start VPN service without a selected config file.")
            return
        }
        val intent = VpnService.prepare(this@MainActivity)
        controlMenuClickable = false
        if (intent != null) {
            vpnPrepareLauncher!!.launch(intent)
        } else {
            val isEnable = prefs.enable
            startService(
                Intent(
                    this,
                    TProxyService::class.java
                ).setAction(if (isEnable) TProxyService.ACTION_DISCONNECT else TProxyService.ACTION_CONNECT)
            )
        }
    }

    override fun reloadConfig() {
        if (!controlMenuClickable) {
            Log.d(TAG, "Reload config request ignored, UI control is not clickable.")
            return
        }
        Log.d(TAG, "Reload config requested from UI.")
        startService(
            Intent(
                this,
                TProxyService::class.java
            ).setAction(TProxyService.ACTION_RELOAD_CONFIG)
        )
    }

    private fun setStatusBarFontColorByTheme(isDark: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(startReceiver)
        unregisterReceiver(stopReceiver)
        if (executorService != null && !executorService!!.isShutdown) {
            executorService!!.shutdownNow()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)
    }

    override fun onEditConfigClick(file: File?) {
        Log.d(TAG, "ConfigFragment request: Edit file: " + file!!.name)
        val intent = Intent(this, ConfigEditActivity::class.java)
        intent.putExtra("filePath", file.absolutePath)
        startActivity(intent)
    }

    override fun onDeleteConfigClick(file: File?) {
        Log.d(TAG, "ConfigFragment request: Delete file: " + file!!.name)
        MaterialAlertDialogBuilder(this).setTitle(R.string.delete_config).setMessage(
            file.name
        ).setPositiveButton(R.string.confirm) { _: DialogInterface?, _: Int ->
            val config = fragmentAdapter.configFragment
            config.deleteFileAndUpdateList(file)
            Log.d(TAG, "Called deleteFileAndUpdateList on ConfigFragment.")
        }
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            .show()
    }

    override fun createNewConfigFileAndEdit() {
        val filename = System.currentTimeMillis().toString() + ".json"
        val newFile = File(filesDir, filename)
        try {
            val prefs = Preferences(this)
            val fileContent: String
            if (prefs.useTemplate) {
                assets.open("template").use { assetInputStream ->
                    val size = assetInputStream.available()
                    val buffer = ByteArray(size)
                    assetInputStream.read(buffer)
                    fileContent = String(buffer, StandardCharsets.UTF_8)
                }
            } else {
                fileContent = "{}"
            }
            FileOutputStream(newFile).use { fileOutputStream ->
                fileOutputStream.write(fileContent.toByteArray())
            }
            val config = fragmentAdapter.configFragment
            config.refreshFileList()
            Log.d(TAG, "Called refreshFileList on ConfigFragment.")
            val intent = Intent(this, ConfigEditActivity::class.java)
            intent.putExtra("filePath", newFile.absolutePath)
            startActivity(intent)
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
        }
    }

    override fun importConfigFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipboardContent = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

        if (clipboardContent.isNullOrEmpty()) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.clipboard_no_text)
                .setPositiveButton(R.string.confirm, null).show()
            Log.w(TAG, "Clipboard is empty, null, or does not contain text.")
            return
        }

        var contentToProcess = clipboardContent
        try {
            val jsonObject = JSONObject(contentToProcess)
            if (!jsonObject.has("inbounds") || !jsonObject.has("outbounds")) {
                MaterialAlertDialogBuilder(this).setMessage(R.string.invalid_config)
                    .setPositiveButton(R.string.confirm, null).show()
                Log.w(TAG, "JSON missing 'inbounds' or 'outbounds' keys.")
                return
            }
            if (jsonObject.has("log")) {
                val logObject = jsonObject.optJSONObject("log")
                logObject?.let {
                    if (it.has("access")) {
                        it.remove("access")
                        Log.d(TAG, "Removed log.access from imported config.")
                    }
                    if (it.has("error")) {
                        it.remove("error")
                        Log.d(TAG, "Removed log.error from imported config.")
                    }
                }
            }
            contentToProcess = jsonObject.toString(2)
            contentToProcess = contentToProcess.replace("\\\\/".toRegex(), "/")
        } catch (e: JSONException) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.invalid_config)
                .setPositiveButton(R.string.confirm, null).show()
            Log.e(TAG, "Invalid JSON format in clipboard.", e)
            return
        }
        val filename = "imported_" + System.currentTimeMillis() + ".json"
        val newFile = File(filesDir, filename)
        try {
            FileOutputStream(newFile).use { fileOutputStream ->
                fileOutputStream.write(contentToProcess.toByteArray(StandardCharsets.UTF_8))
                val config = fragmentAdapter.configFragment
                config.refreshFileList()
                Log.d(TAG, "Called refreshFileList on ConfigFragment after import.")
                val intent = Intent(this, ConfigEditActivity::class.java)
                intent.putExtra("filePath", newFile.absolutePath)
                startActivity(intent)
                Log.d(
                    TAG,
                    "Successfully imported config from clipboard to: " + newFile.absolutePath
                )
            }
        } catch (e: IOException) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.import_failed)
                .setPositiveButton(R.string.confirm, null).show()
            Log.e(TAG, "Error saving imported config file.", e)
        }
    }

    override fun performRestore() {
        openFileLauncher!!.launch(arrayOf("application/octet-stream", "*/*"))
    }

    private fun startRestoreTask(uri: Uri) {
        executorService!!.submit {
            try {
                var compressedData: ByteArray
                contentResolver.openInputStream(uri).use { `is` ->
                    if (`is` == null) {
                        throw IOException("Failed to open input stream for URI: $uri")
                    }
                    val buffer = ByteArrayOutputStream()
                    var nRead: Int
                    val data = ByteArray(1024)
                    while ((`is`.read(data, 0, data.size).also { nRead = it }) != -1) {
                        buffer.write(data, 0, nRead)
                    }
                    compressedData = buffer.toByteArray()
                }
                val inflater = Inflater()
                inflater.setInput(compressedData)
                val outputStream = ByteArrayOutputStream(compressedData.size)
                val buffer = ByteArray(1024)
                while (!inflater.finished()) {
                    try {
                        val count = inflater.inflate(buffer)
                        if (count == 0 && inflater.needsInput()) {
                            Log.e(TAG, "Incomplete compressed data during inflation.")
                            throw IOException("Incomplete compressed data.")
                        }
                        if (count > 0) {
                            outputStream.write(buffer, 0, count)
                        }
                    } catch (e: DataFormatException) {
                        Log.e(TAG, "Data format error during inflation", e)
                        throw IOException("Error decompressing data: Invalid format.", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during inflation", e)
                        throw IOException("Error decompressing data.", e)
                    }
                }
                outputStream.close()
                val decompressedData = outputStream.toByteArray()
                inflater.end()

                val jsonString = String(decompressedData, StandardCharsets.UTF_8)
                val gson = Gson()
                val backupDataType = object : TypeToken<Map<String?, Any?>?>() {
                }.type
                val backupData = gson.fromJson<Map<String, Any>>(jsonString, backupDataType)

                require(
                    !(backupData == null || !backupData.containsKey("preferences") || !backupData.containsKey(
                        "configFiles"
                    ))
                ) { "Invalid backup file format." }

                var preferencesMap: Map<String?, Any?>? = null
                val preferencesObj = backupData["preferences"]
                if (preferencesObj is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    preferencesMap = preferencesObj as Map<String?, Any?>?
                }

                var configFilesMap: Map<String?, String>? = null
                val configFilesObj = backupData["configFiles"]
                if (configFilesObj is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    configFilesMap = configFilesObj as Map<String?, String>?
                }

                if (preferencesMap != null) {
                    var value = preferencesMap[Preferences.SOCKS_PORT]
                    if (value is Number) {
                        prefs!!.socksPort = value.toInt()
                    } else if (value is String) {
                        try {
                            prefs!!.socksPort = value.toInt()
                        } catch (ignore: NumberFormatException) {
                            Log.w(TAG, "Failed to parse SOCKS_PORT as integer: $value")
                        }
                    }

                    value = preferencesMap[Preferences.DNS_IPV4]
                    if (value is String) {
                        prefs!!.dnsIpv4 = (value as String?)!!
                    }

                    value = preferencesMap[Preferences.DNS_IPV6]
                    if (value is String) {
                        prefs!!.dnsIpv6 = (value as String?)!!
                    }

                    value = preferencesMap[Preferences.IPV6]
                    if (value is Boolean) {
                        prefs!!.ipv6 = (value as Boolean?)!!
                    }

                    value = preferencesMap[Preferences.BYPASS_LAN]
                    if (value is Boolean) {
                        prefs!!.bypassLan = (value as Boolean?)!!
                    }

                    value = preferencesMap[Preferences.USE_TEMPLATE]
                    if (value is Boolean) {
                        prefs!!.useTemplate = (value as Boolean?)!!
                    }

                    value = preferencesMap[Preferences.HTTP_PROXY_ENABLED]
                    if (value is Boolean) {
                        prefs!!.httpProxyEnabled = (value as Boolean?)!!
                    }

                    value = preferencesMap[Preferences.APPS]
                    if (value is List<*>) {
                        val appsSet: MutableSet<String?> = HashSet()
                        for (item in value) {
                            if (item is String) {
                                appsSet.add(item as String?)
                            } else if (item != null) {
                                Log.w(
                                    TAG,
                                    "Skipping non-String item in APPS list: " + item.javaClass.name
                                )
                            }
                        }
                        prefs!!.apps = appsSet
                    } else if (value != null) {
                        Log.w(TAG, "APPS preference is not a List: " + value.javaClass.name)
                    }
                } else {
                    Log.w(TAG, "Preferences map is null or not a Map.")
                }

                if (configFilesMap != null) {
                    val filesDir = filesDir
                    for ((filename, content) in configFilesMap) {
                        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains(
                                "\\"
                            )
                        ) {
                            Log.e(TAG, "Skipping restore of invalid filename: $filename")
                            continue
                        }
                        val configFile = File(filesDir, filename)
                        try {
                            FileOutputStream(configFile).use { fos ->
                                fos.write(content.toByteArray(StandardCharsets.UTF_8))
                                Log.d(TAG, "Successfully restored config file: $filename")
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "Error writing config file: $filename", e)
                        }
                    }
                } else {
                    Log.w(TAG, "Config files map is null or not a Map.")
                }

                runOnUiThread {
                    Toast.makeText(this, R.string.restore_success, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Restore successful.")
                    val config = fragmentAdapter.configFragment
                    config.refreshFileList()
                    Log.d(TAG, "Called refreshFileList on ConfigFragment after restore.")
                    val settings = fragmentAdapter.settingsFragment
                    settings.refreshPreferences()
                    Log.d(TAG, "Called refreshPreferences on SettingsFragment after restore.")
                    invalidateOptionsMenu()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during restore process", e)
                runOnUiThread {
                    Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun triggerAssetExtraction() {
        executorService!!.submit { this.extractAssetsIfNeeded() }
    }

    private class NonDisplayedTabLayout(context: Context) : TabLayout(context)
    companion object {
        private const val TAG = "MainActivity"
        private var controlMenuClickable = true

        @JvmStatic
        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
}