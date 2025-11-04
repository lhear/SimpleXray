package com.simplexray.an.prefs

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplexray.an.R
import com.simplexray.an.common.ThemeMode

class Preferences(context: Context) {
    private val contentResolver: ContentResolver
    private val gson: Gson
    private val context1: Context = context.applicationContext

    init {
        this.contentResolver = context1.contentResolver
        this.gson = Gson()
    }

    private fun getPrefData(key: String): Pair<String?, String?> {
        val uri = PrefsContract.PrefsEntry.CONTENT_URI.buildUpon().appendPath(key).build()
        try {
            contentResolver.query(
                uri, arrayOf(
                    PrefsContract.PrefsEntry.COLUMN_PREF_VALUE,
                    PrefsContract.PrefsEntry.COLUMN_PREF_TYPE
                ), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val valueColumnIndex =
                        cursor.getColumnIndex(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE)
                    val typeColumnIndex =
                        cursor.getColumnIndex(PrefsContract.PrefsEntry.COLUMN_PREF_TYPE)
                    val value =
                        if (valueColumnIndex != -1) cursor.getString(valueColumnIndex) else null
                    val type =
                        if (typeColumnIndex != -1) cursor.getString(typeColumnIndex) else null
                    return Pair(value, type)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading preference data for key: $key", e)
        }
        return Pair(null, null)
    }

    private fun getBooleanPref(key: String, default: Boolean): Boolean {
        val (value, type) = getPrefData(key)
        if (value != null && "Boolean" == type) {
            return value.toBoolean()
        }
        return default
    }

    private fun setValueInProvider(key: String, value: Any?) {
        val uri = PrefsContract.PrefsEntry.CONTENT_URI.buildUpon().appendPath(key).build()
        val values = ContentValues()
        when (value) {
            is String -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            is Int -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            is Boolean -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            is Long -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            is Float -> {
                values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, value)
            }

            else -> {
                if (value != null) {
                    Log.e(TAG, "Unsupported type for key: $key with value: $value")
                    return
                }
                values.putNull(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE)
            }
        }
        try {
            val rows = contentResolver.update(uri, values, null, null)
            if (rows == 0) {
                Log.w(TAG, "Update failed or key not found for: $key")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting preference for key: $key", e)
        }
    }

    val socksAddress: String
        get() = getPrefData(SOCKS_ADDR).first ?: "127.0.0.1"

    var socksPort: Int
        get() {
            val value = getPrefData(SOCKS_PORT).first
            val port = value?.toIntOrNull()
            if (value != null && port == null) {
                Log.e(TAG, "Failed to parse SocksPort as Integer: $value")
            }
            return port ?: 10808
        }
        set(port) {
            setValueInProvider(SOCKS_PORT, port.toString())
        }

    val socksUsername: String
        get() = getPrefData(SOCKS_USER).first ?: ""

    val socksPassword: String
        get() = getPrefData(SOCKS_PASS).first ?: ""

    var dnsIpv4: String
        get() = getPrefData(DNS_IPV4).first ?: "8.8.8.8"
        set(addr) {
            setValueInProvider(DNS_IPV4, addr)
        }

    var dnsIpv6: String
        get() = getPrefData(DNS_IPV6).first ?: "2001:4860:4860::8888"
        set(addr) {
            setValueInProvider(DNS_IPV6, addr)
        }

    val udpInTcp: Boolean
        get() = getBooleanPref(UDP_IN_TCP, false)

    var ipv4: Boolean
        get() = getBooleanPref(IPV4, true)
        set(enable) {
            setValueInProvider(IPV4, enable)
        }

    var ipv6: Boolean
        get() = getBooleanPref(IPV6, false)
        set(enable) {
            setValueInProvider(IPV6, enable)
        }

    var global: Boolean
        get() = getBooleanPref(GLOBAL, false)
        set(enable) {
            setValueInProvider(GLOBAL, enable)
        }

    var apps: Set<String?>?
        get() {
            val jsonSet = getPrefData(APPS).first
            return jsonSet?.let {
                try {
                    val type = object : TypeToken<Set<String?>?>() {}.type
                    gson.fromJson<Set<String?>>(it, type)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing APPS StringSet", e)
                    null
                }
            }
        }
        set(apps) {
            val jsonSet = gson.toJson(apps)
            setValueInProvider(APPS, jsonSet)
        }

    var enable: Boolean
        get() = getBooleanPref(ENABLE, false)
        set(enable) {
            setValueInProvider(ENABLE, enable)
        }

    var disableVpn: Boolean
        get() = getBooleanPref(DISABLE_VPN, false)
        set(value) {
            setValueInProvider(DISABLE_VPN, value)
        }

    val tunnelMtu: Int
        get() = 8500

    val tunnelIpv4Address: String
        get() = "198.18.0.1"

    val tunnelIpv4Prefix: Int
        get() = 32

    val tunnelIpv6Address: String
        get() = "fc00::1"

    val tunnelIpv6Prefix: Int
        get() = 128

    val taskStackSize: Int
        get() = 81920

    var selectedConfigPath: String?
        get() = getPrefData(SELECTED_CONFIG_PATH).first
        set(path) {
            setValueInProvider(SELECTED_CONFIG_PATH, path)
        }

    var bypassLan: Boolean
        get() = getBooleanPref(BYPASS_LAN, true)
        set(enable) {
            setValueInProvider(BYPASS_LAN, enable)
        }

    var useTemplate: Boolean
        get() = getBooleanPref(USE_TEMPLATE, true)
        set(enable) {
            setValueInProvider(USE_TEMPLATE, enable)
        }

    var httpProxyEnabled: Boolean
        get() = getBooleanPref(HTTP_PROXY_ENABLED, true)
        set(enable) {
            setValueInProvider(HTTP_PROXY_ENABLED, enable)
        }

    var customGeoipImported: Boolean
        get() = getBooleanPref(CUSTOM_GEOIP_IMPORTED, false)
        set(imported) {
            setValueInProvider(CUSTOM_GEOIP_IMPORTED, imported)
        }

    var customGeositeImported: Boolean
        get() = getBooleanPref(CUSTOM_GEOSITE_IMPORTED, false)
        set(imported) {
            setValueInProvider(CUSTOM_GEOSITE_IMPORTED, imported)
        }

    var configFilesOrder: List<String>
        get() {
            val jsonList = getPrefData(CONFIG_FILES_ORDER).first
            return jsonList?.let {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson(it, type)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing CONFIG_FILES_ORDER List<String>", e)
                    emptyList()
                }
            } ?: emptyList()
        }
        set(order) {
            val jsonList = gson.toJson(order)
            setValueInProvider(CONFIG_FILES_ORDER, jsonList)
        }

    var connectivityTestTarget: String
        get() = getPrefData(CONNECTIVITY_TEST_TARGET).first
            ?: context1.getString(R.string.connectivity_test_url)
        set(value) {
            setValueInProvider(CONNECTIVITY_TEST_TARGET, value)
        }

    var connectivityTestTimeout: Int
        get() = getPrefData(CONNECTIVITY_TEST_TIMEOUT).first?.toIntOrNull() ?: 3000
        set(value) {
            setValueInProvider(CONNECTIVITY_TEST_TIMEOUT, value.toString())
        }

    var geoipUrl: String
        get() = getPrefData(GEOIP_URL).first ?: context1.getString(R.string.geoip_url)
        set(value) {
            setValueInProvider(GEOIP_URL, value)
        }

    var geositeUrl: String
        get() = getPrefData(GEOSITE_URL).first ?: context1.getString(R.string.geosite_url)
        set(value) {
            setValueInProvider(GEOSITE_URL, value)
        }

    var apiPort: Int
        get() {
            val value = getPrefData(API_PORT).first
            val port = value?.toIntOrNull()
            return port ?: 0
        }
        set(port) {
            setValueInProvider(API_PORT, port.toString())
        }

    var bypassSelectedApps: Boolean
        get() = getBooleanPref(BYPASS_SELECTED_APPS, false)
        set(enable) {
            setValueInProvider(BYPASS_SELECTED_APPS, enable)
        }

    var theme: ThemeMode
        get() = getPrefData(THEME).first?.let { ThemeMode.fromString(it) } ?: ThemeMode.Auto
        set(value) {
            setValueInProvider(THEME, value.value)
        }

    // Advanced Routing Settings
    var advancedRoutingRules: String?
        get() = getPrefData(ADVANCED_ROUTING_RULES).first
        set(value) {
            setValueInProvider(ADVANCED_ROUTING_RULES, value)
        }

    // Gaming Optimization Settings
    var selectedGameProfile: String?
        get() = getPrefData(SELECTED_GAME_PROFILE).first
        set(value) {
            setValueInProvider(SELECTED_GAME_PROFILE, value)
        }

    var gamingOptimizationEnabled: Boolean
        get() = getBooleanPref(GAMING_OPTIMIZATION_ENABLED, false)
        set(enable) {
            setValueInProvider(GAMING_OPTIMIZATION_ENABLED, enable)
        }

    // Streaming Optimization Settings
    var streamingPlatformConfigs: String?
        get() = getPrefData(STREAMING_PLATFORM_CONFIGS).first
        set(value) {
            setValueInProvider(STREAMING_PLATFORM_CONFIGS, value)
        }

    var streamingOptimizationEnabled: Boolean
        get() = getBooleanPref(STREAMING_OPTIMIZATION_ENABLED, true)
        set(enable) {
            setValueInProvider(STREAMING_OPTIMIZATION_ENABLED, enable)
        }

    var selectedStreamingPlatform: String?
        get() = getPrefData(SELECTED_STREAMING_PLATFORM).first
        set(value) {
            setValueInProvider(SELECTED_STREAMING_PLATFORM, value)
        }

    // Performance & Adaptive Tuning Settings
    var performanceProfile: String?
        get() = getPrefData(PERFORMANCE_PROFILE).first ?: "balanced"
        set(value) {
            setValueInProvider(PERFORMANCE_PROFILE, value ?: "balanced")
        }

    var autoTuneEnabled: Boolean
        get() = getBooleanPref(AUTO_TUNE_ENABLED, false)
        set(enable) {
            setValueInProvider(AUTO_TUNE_ENABLED, enable)
        }

    var adaptiveTuningAutoApply: Boolean
        get() = getBooleanPref(ADAPTIVE_TUNING_AUTO_APPLY, false)
        set(value) {
            setValueInProvider(ADAPTIVE_TUNING_AUTO_APPLY, value)
        }

    fun setAdaptiveTuningFeedback(key: String, accepted: Boolean) {
        setValueInProvider("$ADAPTIVE_TUNING_FEEDBACK_PREFIX$key", accepted)
    }

    fun getAdaptiveTuningFeedback(key: String): Boolean? {
        val (value, type) = getPrefData("$ADAPTIVE_TUNING_FEEDBACK_PREFIX$key")
        return if (value != null && "Boolean" == type) {
            value.toBoolean()
        } else {
            null
        }
    }

    var enablePerformanceMode: Boolean
        get() = getBooleanPref(ENABLE_PERFORMANCE_MODE, false)
        set(value) {
            setValueInProvider(ENABLE_PERFORMANCE_MODE, value)
        }
    
    // Advanced Performance Settings
    var cpuAffinityEnabled: Boolean
        get() = getBooleanPref(CPU_AFFINITY_ENABLED, true)
        set(value) {
            setValueInProvider(CPU_AFFINITY_ENABLED, value)
        }
    
    var memoryPoolSize: Int
        get() = getPrefData(MEMORY_POOL_SIZE).first?.toIntOrNull() ?: 16
        set(value) {
            setValueInProvider(MEMORY_POOL_SIZE, value.toString())
        }
    
    var connectionPoolSize: Int
        get() = getPrefData(CONNECTION_POOL_SIZE).first?.toIntOrNull() ?: 8
        set(value) {
            setValueInProvider(CONNECTION_POOL_SIZE, value.toString())
        }
    
    var socketBufferMultiplier: Float
        get() = getPrefData(SOCKET_BUFFER_MULTIPLIER).first?.toFloatOrNull() ?: 2.0f
        set(value) {
            setValueInProvider(SOCKET_BUFFER_MULTIPLIER, value.toString())
        }
    
    var threadPoolSize: Int
        get() = getPrefData(THREAD_POOL_SIZE).first?.toIntOrNull() ?: 4
        set(value) {
            setValueInProvider(THREAD_POOL_SIZE, value.toString())
        }
    
    var jitWarmupEnabled: Boolean
        get() = getBooleanPref(JIT_WARMUP_ENABLED, true)
        set(value) {
            setValueInProvider(JIT_WARMUP_ENABLED, value)
        }
    
    var tcpFastOpenEnabled: Boolean
        get() = getBooleanPref(TCP_FASTOPEN_ENABLED, true)
        set(value) {
            setValueInProvider(TCP_FASTOPEN_ENABLED, value)
        }

    // Configuration Management Settings
    fun getMergeInbounds(default: Boolean): Boolean {
        return getBooleanPref(MERGE_INBOUNDS, default)
    }

    fun setMergeInbounds(value: Boolean) {
        setValueInProvider(MERGE_INBOUNDS, value)
    }

    fun getMergeOutbounds(default: Boolean): Boolean {
        return getBooleanPref(MERGE_OUTBOUNDS, default)
    }

    fun setMergeOutbounds(value: Boolean) {
        setValueInProvider(MERGE_OUTBOUNDS, value)
    }

    fun getMergeTransport(default: Boolean): Boolean {
        return getBooleanPref(MERGE_TRANSPORT, default)
    }

    fun setMergeTransport(value: Boolean) {
        setValueInProvider(MERGE_TRANSPORT, value)
    }

    fun getAutoReloadConfig(default: Boolean): Boolean {
        return getBooleanPref(AUTO_RELOAD_CONFIG, default)
    }

    fun setAutoReloadConfig(value: Boolean) {
        setValueInProvider(AUTO_RELOAD_CONFIG, value)
    }

    fun getCurrentConfigFile(default: String): String {
        return getPrefData(CURRENT_CONFIG_FILE).first ?: default
    }

    fun setCurrentConfigFile(filename: String) {
        setValueInProvider(CURRENT_CONFIG_FILE, filename)
    }

    /**
     * Clear Xray Settings server information (server address, port, ID, etc.)
     * This removes any leftover data from XraySettingsViewModel
     */
    fun clearXrayServerInfo() {
        // Common key names that might have been used by XraySettingsViewModel
        val possibleKeys = listOf(
            "ServerAddress",
            "ServerPort",
            "VlessId",
            "VlessServerAddress",
            "VlessServerPort",
            "VlessServerId",
            "XrayServerAddress",
            "XrayServerPort",
            "XrayServerId",
            "vless_address",
            "vless_port",
            "vless_id",
            "server_address",
            "server_port",
            "server_id"
        )
        
        // Access SharedPreferences directly to remove keys
        val sharedPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context1)
        val editor = sharedPrefs.edit()
        
        possibleKeys.forEach { key ->
            try {
                if (sharedPrefs.contains(key)) {
                    editor.remove(key)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing key: $key", e)
            }
        }
        
        editor.apply()
    }

    companion object {
        const val SOCKS_ADDR: String = "SocksAddr"
        const val SOCKS_PORT: String = "SocksPort"
        const val SOCKS_USER: String = "SocksUser"
        const val SOCKS_PASS: String = "SocksPass"
        const val DNS_IPV4: String = "DnsIpv4"
        const val DNS_IPV6: String = "DnsIpv6"
        const val IPV4: String = "Ipv4"
        const val IPV6: String = "Ipv6"
        const val GLOBAL: String = "Global"
        const val UDP_IN_TCP: String = "UdpInTcp"
        const val APPS: String = "Apps"
        const val ENABLE: String = "Enable"
        const val SELECTED_CONFIG_PATH: String = "SelectedConfigPath"
        const val BYPASS_LAN: String = "BypassLan"
        const val USE_TEMPLATE: String = "UseTemplate"
        const val HTTP_PROXY_ENABLED: String = "HttpProxyEnabled"
        const val CUSTOM_GEOIP_IMPORTED: String = "CustomGeoipImported"
        const val CUSTOM_GEOSITE_IMPORTED: String = "CustomGeositeImported"
        const val CONFIG_FILES_ORDER: String = "ConfigFilesOrder"
        const val DISABLE_VPN: String = "DisableVpn"
        const val CONNECTIVITY_TEST_TARGET: String = "ConnectivityTestTarget"
        const val CONNECTIVITY_TEST_TIMEOUT: String = "ConnectivityTestTimeout"
        const val GEOIP_URL: String = "GeoipUrl"
        const val GEOSITE_URL: String = "GeositeUrl"
        const val API_PORT: String = "ApiPort"
        const val BYPASS_SELECTED_APPS: String = "BypassSelectedApps"
        const val THEME: String = "Theme"
        const val ADVANCED_ROUTING_RULES: String = "AdvancedRoutingRules"
        const val SELECTED_GAME_PROFILE: String = "SelectedGameProfile"
        const val GAMING_OPTIMIZATION_ENABLED: String = "GamingOptimizationEnabled"
        const val STREAMING_PLATFORM_CONFIGS: String = "StreamingPlatformConfigs"
        const val STREAMING_OPTIMIZATION_ENABLED: String = "StreamingOptimizationEnabled"
        const val SELECTED_STREAMING_PLATFORM: String = "SelectedStreamingPlatform"
        const val PERFORMANCE_PROFILE: String = "PerformanceProfile"
        const val AUTO_TUNE_ENABLED: String = "AutoTuneEnabled"
        const val ENABLE_PERFORMANCE_MODE: String = "EnablePerformanceMode"
        const val MERGE_INBOUNDS: String = "MergeInbounds"
        const val MERGE_OUTBOUNDS: String = "MergeOutbounds"
        const val MERGE_TRANSPORT: String = "MergeTransport"
        const val AUTO_RELOAD_CONFIG: String = "AutoReloadConfig"
        const val CURRENT_CONFIG_FILE: String = "CurrentConfigFile"
        const val CPU_AFFINITY_ENABLED: String = "CpuAffinityEnabled"
        const val MEMORY_POOL_SIZE: String = "MemoryPoolSize"
        const val CONNECTION_POOL_SIZE: String = "ConnectionPoolSize"
        const val SOCKET_BUFFER_MULTIPLIER: String = "SocketBufferMultiplier"
        const val THREAD_POOL_SIZE: String = "ThreadPoolSize"
        const val JIT_WARMUP_ENABLED: String = "JitWarmupEnabled"
        const val TCP_FASTOPEN_ENABLED: String = "TcpFastOpenEnabled"
        const val ADAPTIVE_TUNING_AUTO_APPLY: String = "AdaptiveTuningAutoApply"
        const val ADAPTIVE_TUNING_FEEDBACK_PREFIX: String = "AdaptiveTuningFeedback_"
        const val TRAFFIC_SAMPLING_INTERVAL_MINUTES: String = "TrafficSamplingIntervalMinutes"
        const val TRAFFIC_RETENTION_DAYS: String = "TrafficRetentionDays"
        const val BACKGROUND_TRAFFIC_LOGGING_ENABLED: String = "BackgroundTrafficLoggingEnabled"
        const val LATENCY_PROBE_ENDPOINT: String = "LatencyProbeEndpoint"
        private const val TAG = "Preferences"
    }
    
    // Traffic monitoring settings
    var trafficSamplingIntervalMinutes: Int
        get() = getPrefData(TRAFFIC_SAMPLING_INTERVAL_MINUTES).first?.toIntOrNull() ?: 15
        set(value) {
            setValueInProvider(TRAFFIC_SAMPLING_INTERVAL_MINUTES, value.toString())
        }
    
    var trafficRetentionDays: Int
        get() = getPrefData(TRAFFIC_RETENTION_DAYS).first?.toIntOrNull() ?: 30
        set(value) {
            setValueInProvider(TRAFFIC_RETENTION_DAYS, value.toString())
        }
    
    var backgroundTrafficLoggingEnabled: Boolean
        get() = getBooleanPref(BACKGROUND_TRAFFIC_LOGGING_ENABLED, true)
        set(value) {
            setValueInProvider(BACKGROUND_TRAFFIC_LOGGING_ENABLED, value)
        }
    
    var latencyProbeEndpoint: String
        get() = getPrefData(LATENCY_PROBE_ENDPOINT).first ?: "https://www.google.com/generate_204"
        set(value) {
            setValueInProvider(LATENCY_PROBE_ENDPOINT, value)
        }
}