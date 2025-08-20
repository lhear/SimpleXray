package com.simplexray.an.data.source

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplexray.an.R
import com.simplexray.an.common.ConfigUtils
import com.simplexray.an.common.FilenameValidator
import com.simplexray.an.common.V2rayUtils
import com.simplexray.an.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.log10
import kotlin.math.pow

class FileManager(private val application: Application, private val prefs: Preferences) {
    @Throws(IOException::class)
    private fun readFileContent(file: File): String {
        return file.readText(StandardCharsets.UTF_8)
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

    private fun getClipboardContent(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clipData: ClipData? = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item: ClipData.Item = clipData.getItemAt(0)
                val text: CharSequence? = item.text
                return text?.toString()
            }
        }
        return null
    }

    suspend fun createConfigFile(assets: AssetManager): String? {
        return withContext(Dispatchers.IO) {
            val filename = System.currentTimeMillis().toString() + ".json"
            val newFile = File(application.filesDir, filename)
            try {
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
                Log.d(TAG, "Created new config file: ${newFile.absolutePath}")
                newFile.absolutePath
            } catch (e: IOException) {
                Log.e(TAG, "Error creating new config file", e)
                return@withContext null
            }
        }
    }

    suspend fun importConfigFromClipboard(): String? {
        return withContext(Dispatchers.IO) {
            val clipboardContent = getClipboardContent(application)

            if (clipboardContent.isNullOrEmpty()) {
                Log.w(TAG, "Clipboard is empty, null, or does not contain text.")
                return@withContext null
            }
            importConfigFromContent(clipboardContent)
        }
    }

    suspend fun importConfigFromContent(content: String): String? {
        Log.d(TAG, "importConfigFromContent, content: $content")
        return withContext(Dispatchers.IO) {
            if (content.isEmpty()) {
                Log.w(TAG, "Content to import is empty.")
                return@withContext null
            }

            val (name, configContent) = if (content.startsWith("simplexray://config/")) {
                try {
                    val parts = content.substring("simplexray://config/".length).split("/")
                    if (parts.size != 2) {
                        Log.e(TAG, "Invalid simplexray URI format")
                        return@withContext null
                    }

                    val decodedName = URLDecoder.decode(parts[0], "UTF-8")

                    val filenameError = FilenameValidator.validateFilename(application, decodedName)
                    if (filenameError != null) {
                        Log.e(TAG, "Invalid filename in simplexray URI: $filenameError")
                        return@withContext null
                    }

                    val decodedContent = Base64.getUrlDecoder().decode(parts[1])

                    val inflater = Inflater()
                    inflater.setInput(decodedContent)
                    val outputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (!inflater.finished()) {
                        val count = inflater.inflate(buffer)
                        outputStream.write(buffer, 0, count)
                    }
                    inflater.end()
                    val decompressed = outputStream.toByteArray().toString(Charsets.UTF_8)

                    Pair(decodedName, decompressed)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse simplexray URI", e)
                    return@withContext null
                }
            } else {
                var configName = ""
                var finalContent: String
                var isUri = false

                val uriContent = when {
                    content.startsWith("vmess://") -> V2rayUtils.importV2rayUri(content)
                    content.startsWith("ss://") -> V2rayUtils.importShadowsocksUri(content)
                    content.startsWith("trojan://") -> V2rayUtils.importTrojanUri(content)
                    content.startsWith("vless://") -> V2rayUtils.importVlessUri(content)
                    else -> null
                }

                if (uriContent != null) {
                    configName = ConfigUtils.getRemarkFromConfig(uriContent)
                    finalContent = uriContent
                    isUri = true
                } else {
                    configName = ConfigUtils.getRemarkFromConfig(content)
                    finalContent = content
                }

                if (configName.isEmpty()) {
                    configName = "imported_share_" + System.currentTimeMillis()
                }

                if (isUri) {
                    try {
                        finalContent = ConfigUtils.mergeWithTemplate(application, finalContent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to merge config with template", e)
                        return@withContext null
                    }
                }

                Pair(configName, finalContent)
            }
            Log.d(TAG, "importConfigFromContent, configContent: $configContent")

            val formattedContent = try {
                ConfigUtils.formatConfigContent(configContent)
            } catch (e: JSONException) {
                Log.e(TAG, "Invalid JSON format in provided content.", e)
                return@withContext null
            }

            val filename = "$name.json"
            val newFile = File(application.filesDir, filename)

            try {
                FileOutputStream(newFile).use { fileOutputStream ->
                    fileOutputStream.write(formattedContent.toByteArray(StandardCharsets.UTF_8))
                }
                Log.d(
                    TAG,
                    "Successfully imported config from content to: ${newFile.absolutePath}"
                )
                newFile.absolutePath
            } catch (e: IOException) {
                Log.e(TAG, "Error saving imported config file from content.", e)
                return@withContext null
            }
        }
    }

    suspend fun deleteConfigFile(fileToDelete: File): Boolean {
        return withContext(Dispatchers.IO) {
            if (fileToDelete.delete()) {
                Log.d(TAG, "Successfully deleted config file: ${fileToDelete.name}")
                true
            } else {
                Log.e(TAG, "Failed to delete config file: ${fileToDelete.name}")
                false
            }
        }
    }

    suspend fun compressBackupData(): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val gson = Gson()
                val preferencesMap: MutableMap<String, Any> = mutableMapOf()
                preferencesMap[Preferences.SOCKS_ADDR] = prefs.socksAddress
                preferencesMap[Preferences.SOCKS_PORT] = prefs.socksPort
                preferencesMap[Preferences.DNS_IPV4] = prefs.dnsIpv4
                preferencesMap[Preferences.DNS_IPV6] = prefs.dnsIpv6
                preferencesMap[Preferences.IPV6] = prefs.ipv6
                preferencesMap[Preferences.APPS] = ArrayList(
                    prefs.apps ?: emptySet()
                )
                preferencesMap[Preferences.BYPASS_LAN] = prefs.bypassLan
                preferencesMap[Preferences.USE_TEMPLATE] = prefs.useTemplate
                preferencesMap[Preferences.HTTP_PROXY_ENABLED] = prefs.httpProxyEnabled
                preferencesMap[Preferences.CONFIG_FILES_ORDER] = prefs.configFilesOrder
                preferencesMap[Preferences.DISABLE_VPN] = prefs.disableVpn
                preferencesMap[Preferences.CONNECTIVITY_TEST_TARGET] = prefs.connectivityTestTarget
                preferencesMap[Preferences.CONNECTIVITY_TEST_TIMEOUT] =
                    prefs.connectivityTestTimeout
                preferencesMap[Preferences.GEOIP_URL] = prefs.geoipUrl
                preferencesMap[Preferences.GEOSITE_URL] = prefs.geositeUrl
                preferencesMap[Preferences.BYPASS_SELECTED_APPS] = prefs.bypassSelectedApps
                val configFilesMap: MutableMap<String, String> = mutableMapOf()
                val filesDir = application.filesDir
                val files = filesDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && file.name.endsWith(".json")) {
                            try {
                                val content = readFileContent(file)
                                configFilesMap[file.name] = content
                            } catch (e: IOException) {
                                Log.e(TAG, "Error reading config file: ${file.name}", e)
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
                deflater.end()
                outputStream.toByteArray()
            } catch (e: Exception) {
                Log.e(TAG, "Error during backup compression", e)
                null
            }
        }
    }

    suspend fun decompressAndRestore(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var compressedData: ByteArray
                application.contentResolver.openInputStream(uri).use { `is` ->
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
                val backupDataType = object : TypeToken<Map<String?, Any?>?>() {}.type
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

                val savedOrderFromBackup = mutableListOf<String>()

                if (preferencesMap != null) {
                    var value = preferencesMap[Preferences.SOCKS_PORT]
                    if (value is Number) {
                        prefs.socksPort = value.toInt()
                    } else if (value is String) {
                        try {
                            prefs.socksPort = value.toInt()
                        } catch (ignore: NumberFormatException) {
                            Log.w(TAG, "Failed to parse SOCKS_PORT as integer: $value")
                        }
                    }

                    value = preferencesMap[Preferences.DNS_IPV4]
                    if (value is String) {
                        prefs.dnsIpv4 = (value as String?)!!
                    }

                    value = preferencesMap[Preferences.DNS_IPV6]
                    if (value is String) {
                        prefs.dnsIpv6 = (value as String?)!!
                    }

                    value = preferencesMap[Preferences.IPV6]
                    if (value is Boolean) {
                        prefs.ipv6 = (value as Boolean?)!!
                    }

                    value = preferencesMap[Preferences.BYPASS_LAN]
                    if (value is Boolean) {
                        prefs.bypassLan = (value as Boolean?)!!
                    }

                    value = preferencesMap[Preferences.USE_TEMPLATE]
                    if (value is Boolean) {
                        prefs.useTemplate = (value as Boolean?)!!
                    }

                    value = preferencesMap[Preferences.HTTP_PROXY_ENABLED]
                    if (value is Boolean) {
                        prefs.httpProxyEnabled = (value as Boolean?)!!
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
                        prefs.apps = appsSet
                    } else if (value != null) {
                        Log.w(TAG, "APPS preference is not a List: " + value.javaClass.name)
                    }

                    value = preferencesMap[Preferences.DISABLE_VPN]
                    if (value is Boolean) {
                        prefs.disableVpn = value
                    }

                    value = preferencesMap[Preferences.CONNECTIVITY_TEST_TARGET]
                    if (value is String) {
                        prefs.connectivityTestTarget = value
                    }
                    value = preferencesMap[Preferences.CONNECTIVITY_TEST_TIMEOUT]
                    if (value is Number) {
                        prefs.connectivityTestTimeout = value.toInt()
                    } else if (value is String) {
                        try {
                            prefs.connectivityTestTimeout = value.toInt()
                        } catch (ignore: NumberFormatException) {
                            Log.w(
                                TAG,
                                "Failed to parse CONNECTIVITY_TEST_TIMEOUT as integer: $value"
                            )
                        }
                    }

                    value = preferencesMap[Preferences.GEOIP_URL]
                    if (value is String) {
                        prefs.geoipUrl = value
                    }

                    value = preferencesMap[Preferences.GEOSITE_URL]
                    if (value is String) {
                        prefs.geositeUrl = value
                    }

                    value = preferencesMap[Preferences.BYPASS_SELECTED_APPS]
                    if (value is Boolean) {
                        prefs.bypassSelectedApps = value
                    }

                    val configOrderObj = preferencesMap[Preferences.CONFIG_FILES_ORDER]
                    if (configOrderObj is List<*>) {
                        for (item in configOrderObj) {
                            if (item is String) {
                                savedOrderFromBackup.add(item)
                            } else if (item != null) {
                                Log.w(
                                    TAG,
                                    "Skipping non-String item in CONFIG_FILES_ORDER list: " + item.javaClass.name
                                )
                            }
                        }
                    } else if (configOrderObj != null) {
                        Log.w(
                            TAG,
                            "CONFIG_FILES_ORDER preference is not a List: " + configOrderObj.javaClass.name
                        )
                    }

                } else {
                    Log.w(TAG, "Preferences map is null or not a Map.")
                }

                val filesDir = application.filesDir

                if (configFilesMap != null) {
                    for ((filename, content) in configFilesMap) {
                        if (filename == null || FilenameValidator.validateFilename(
                                application,
                                filename
                            ) != null
                        ) {
                            Log.e(TAG, "Skipping restore of invalid filename: $filename")
                            continue
                        }
                        val configFile = File(filesDir, filename)
                        try {
                            FileOutputStream(configFile).use { fos ->
                                fos.write(content.toByteArray(StandardCharsets.UTF_8))
                                Log.d(TAG, "Successfully restored/overwrote config file: $filename")
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "Error writing config file: $filename", e)
                        }
                    }
                } else {
                    Log.w(TAG, "Config files map is null or not a Map.")
                }

                val existingFileNames = prefs.configFilesOrder.toMutableList()
                val actualFileNamesAfterRestore =
                    filesDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
                        ?.map { it.name }?.toMutableSet() ?: mutableSetOf()

                val finalConfigOrder = mutableListOf<String>()
                val processedFileNames = mutableSetOf<String>()

                savedOrderFromBackup.forEach { filename ->
                    if (actualFileNamesAfterRestore.contains(filename)) {
                        finalConfigOrder.add(filename)
                        processedFileNames.add(filename)
                    }
                }

                existingFileNames.forEach { filename ->
                    if (actualFileNamesAfterRestore.contains(filename) && !processedFileNames.contains(
                            filename
                        )
                    ) {
                        finalConfigOrder.add(filename)
                        processedFileNames.add(filename)
                    }
                }

                val newlyAddedFileNames =
                    actualFileNamesAfterRestore.filter { !processedFileNames.contains(it) }.sorted()
                finalConfigOrder.addAll(newlyAddedFileNames)

                prefs.configFilesOrder = finalConfigOrder

                Log.d(TAG, "Restore successful.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error during restore process", e)
                false
            }
        }
    }

    fun extractAssetsIfNeeded() {
        val files = arrayOf("geoip.dat", "geosite.dat")
        val dir = application.filesDir
        dir.mkdirs()
        for (file in files) {
            val targetFile = File(dir, file)
            var needsExtraction = false

            val isCustomImported =
                if (file == "geoip.dat") prefs.customGeoipImported else prefs.customGeositeImported

            if (isCustomImported) {
                Log.d(TAG, "Custom file already imported for $file, skipping asset extraction.")
                continue
            }

            if (targetFile.exists()) {
                try {
                    val existingFileHash =
                        calculateSha256(Files.newInputStream(targetFile.toPath()))
                    val assetHash = calculateSha256(application.assets.open(file))
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
                    application.assets.open(file).use { `in` ->
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

    suspend fun importRuleFile(uri: Uri, filename: String): Boolean {
        return withContext(Dispatchers.IO) {
            val targetFile = File(application.filesDir, filename)
            try {
                application.contentResolver.openInputStream(uri).use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        if (inputStream == null) {
                            throw IOException("Failed to open input stream for URI: $uri")
                        }
                        val buffer = ByteArray(1024)
                        var read: Int
                        while ((inputStream.read(buffer).also { read = it }) != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        when (filename) {
                            "geoip.dat" -> prefs.customGeoipImported = true
                            "geosite.dat" -> prefs.customGeositeImported = true
                        }
                        Log.d(TAG, "Successfully imported $filename from URI: $uri")
                        true
                    }
                }
            } catch (e: IOException) {
                if (filename == "geoip.dat") {
                    prefs.customGeoipImported = false
                } else if (filename == "geosite.dat") {
                    prefs.customGeositeImported = false
                }
                Log.e(TAG, "Error importing rule file: $filename", e)
                false
            } catch (e: Exception) {
                if (filename == "geoip.dat") {
                    prefs.customGeoipImported = false
                } else if (filename == "geosite.dat") {
                    prefs.customGeositeImported = false
                }
                Log.e(TAG, "Unexpected error during rule file import: $filename", e)
                false
            }
        }
    }

    suspend fun saveRuleFile(
        inputStream: InputStream,
        filename: String,
        onProgress: (Int) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val targetFile = File(application.filesDir, filename)
            val tempFile = File(application.filesDir, "$filename.tmp")
            try {
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(4096)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        onProgress(read)
                    }
                }

                if (tempFile.renameTo(targetFile)) {
                    when (filename) {
                        "geoip.dat" -> prefs.customGeoipImported = true
                        "geosite.dat" -> prefs.customGeositeImported = true
                    }
                    Log.d(TAG, "Successfully saved $filename from stream")
                    true
                } else {
                    Log.e(TAG, "Failed to rename temp file to $filename")
                    tempFile.delete()
                    false
                }
            } catch (e: IOException) {
                tempFile.delete()
                Log.e(TAG, "Error saving rule file: $filename", e)
                false
            } catch (e: Exception) {
                tempFile.delete()
                Log.e(TAG, "Unexpected error during rule file save: $filename", e)
                false
            }
        }
    }

    fun getRuleFileSummary(filename: String): String {
        Log.d(TAG, "getRuleFileSummary called with filename: $filename")
        val file = File(application.filesDir, filename)
        val isCustomImported =
            if (filename == "geoip.dat") prefs.customGeoipImported else prefs.customGeositeImported
        return if (file.exists() && isCustomImported) {
            val lastModified = file.lastModified()
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            val date = sdf.format(Date(lastModified))
            val size = formatFileSize(file.length())
            "$date | $size"
        } else {
            application.getString(R.string.rule_file_default)
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            size / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    suspend fun renameConfigFile(oldFile: File, newFile: File, newContent: String): Boolean =
        withContext(Dispatchers.IO) {
            if (oldFile.absolutePath == newFile.absolutePath) {
                try {
                    newFile.writeText(newContent)
                    Log.d(TAG, "Content updated for file: ${newFile.absolutePath}")
                    return@withContext true
                } catch (e: IOException) {
                    Log.e(TAG, "Error writing content to file: ${newFile.absolutePath}", e)
                    return@withContext false
                }
            }

            try {
                newFile.writeText(newContent)
                Log.d(TAG, "Content written to new file: ${newFile.absolutePath}")

                if (oldFile.exists()) {
                    val deleted = oldFile.delete()
                    if (!deleted) {
                        Log.w(TAG, "Failed to delete old config file: ${oldFile.absolutePath}")
                    }
                }

                val currentOrder = prefs.configFilesOrder.toMutableList()
                val oldName = oldFile.name
                val newName = newFile.name

                val oldNameIndex = currentOrder.indexOf(oldName)
                if (oldNameIndex != -1) {
                    currentOrder[oldNameIndex] = newName
                    prefs.configFilesOrder = currentOrder
                    Log.d(TAG, "Updated configFilesOrder: $oldName -> $newName")
                } else {
                    currentOrder.add(newName)
                    prefs.configFilesOrder = currentOrder
                    Log.w(TAG, "Old file name not found in order, adding new name to end: $newName")
                }

                if (prefs.selectedConfigPath == oldFile.absolutePath) {
                    prefs.selectedConfigPath = newFile.absolutePath
                    Log.d(
                        TAG,
                        "Updated selectedConfigPath: ${oldFile.absolutePath} -> ${newFile.absolutePath}"
                    )
                }

                return@withContext true
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "Error renaming config file from ${oldFile.absolutePath} to ${newFile.absolutePath}",
                    e
                )
                if (newFile.exists()) {
                    newFile.delete()
                }
                return@withContext false
            }
        }

    suspend fun restoreDefaultGeoip(): Boolean {
        return withContext(Dispatchers.IO) {
            prefs.customGeoipImported = false
            val file = File(application.filesDir, "geoip.dat")
            application.assets.open("geoip.dat").use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            true
        }
    }

    suspend fun restoreDefaultGeosite(): Boolean {
        return withContext(Dispatchers.IO) {
            prefs.customGeositeImported = false
            val file = File(application.filesDir, "geosite.dat")
            application.assets.open("geosite.dat").use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            true
        }
    }

    companion object {
        const val TAG = "FileManager"
    }
}