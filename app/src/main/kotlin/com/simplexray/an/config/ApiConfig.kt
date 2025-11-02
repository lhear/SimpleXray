package com.simplexray.an.config

import android.content.Context
import android.content.SharedPreferences

object ApiConfig {
    private const val PREFS = "simplexray_prefs"
    private const val KEY_HOST = "api_host"
    private const val KEY_PORT = "api_port"
    private const val KEY_MOCK = "mock_enabled"
    private const val KEY_ONLINE = "online_ip_stat_name"
    private const val KEY_IP_DOMAIN_JSON = "ip_domain_map_json"
    private const val KEY_ADAPTIVE = "adaptive_enabled"
    private const val KEY_BASE_MS = "base_interval_ms"
    private const val KEY_OFF_MS = "screen_off_interval_ms"
    private const val KEY_IDLE_MS = "idle_interval_ms"
    private const val KEY_TELEMETRY = "telemetry_enabled"
    private const val KEY_ONLINE_BYTES = "online_ip_bytes_stat_name"
    private const val KEY_AUTOSTART_XRAY = "autostart_xray"
    private const val KEY_TOPO_ALPHA = "topology_smoothing_alpha"
    private const val KEY_GRPC_PROFILE = "grpc_profile"
    private const val KEY_GRPC_DEADLINE_MS = "grpc_deadline_ms"
    private const val KEY_RESTART_ON_APPLY = "restart_on_apply"
    private const val KEY_AUTO_RDNS = "auto_reverse_dns"
    private const val KEY_ALERT_Z = "alert_burst_z"
    private const val KEY_ALERT_MIN_BPS = "alert_burst_min_bps"
    private const val KEY_ALERT_RATIO = "alert_throttle_ratio"
    private const val KEY_ALERT_MIN_LONG = "alert_throttle_min_long"
    private const val KEY_ALERT_COOLDOWN = "alert_cooldown_ms"

    fun getHost(context: Context): String = prefs(context).getString(KEY_HOST, "127.0.0.1") ?: "127.0.0.1"
    fun getPort(context: Context): Int = prefs(context).getInt(KEY_PORT, 10085)
    fun isMock(context: Context): Boolean = prefs(context).getBoolean(KEY_MOCK, false)
    fun getOnlineKey(context: Context): String = prefs(context).getString(KEY_ONLINE, "") ?: ""
    fun getIpDomainJson(context: Context): String = prefs(context).getString(KEY_IP_DOMAIN_JSON, "") ?: ""
    fun isAdaptive(context: Context): Boolean = prefs(context).getBoolean(KEY_ADAPTIVE, true)
    fun getBaseIntervalMs(context: Context): Long = prefs(context).getLong(KEY_BASE_MS, 1000L)
    fun getScreenOffIntervalMs(context: Context): Long = prefs(context).getLong(KEY_OFF_MS, 3000L)
    fun getIdleIntervalMs(context: Context): Long = prefs(context).getLong(KEY_IDLE_MS, 5000L)
    fun isTelemetry(context: Context): Boolean = prefs(context).getBoolean(KEY_TELEMETRY, false)
    fun getOnlineBytesKey(context: Context): String = prefs(context).getString(KEY_ONLINE_BYTES, "") ?: ""
    fun isAutostartXray(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTOSTART_XRAY, false)
    fun getTopologyAlpha(context: Context): Float = prefs(context).getFloat(KEY_TOPO_ALPHA, 0.3f).coerceIn(0.05f, 1.0f)
    
    fun setTopologyPreset(context: Context, preset: String) {
        val alpha = when (preset.lowercase()) {
            "performance" -> 0.5f // Faster response to changes
            "powersaver" -> 0.15f // Smoother, less CPU
            else -> 0.3f // Balanced
        }
        setTopologyAlpha(context, alpha)
    }
    fun getGrpcProfile(context: Context): String = prefs(context).getString(KEY_GRPC_PROFILE, "balanced") ?: "balanced"
    fun getGrpcDeadlineMs(context: Context): Long = prefs(context).getLong(KEY_GRPC_DEADLINE_MS, 2000L)
    fun isRestartOnApply(context: Context): Boolean = prefs(context).getBoolean(KEY_RESTART_ON_APPLY, false)
    fun isAutoReverseDns(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_RDNS, false)
    fun getAlertBurstZ(context: Context): Double = java.lang.Double.longBitsToDouble(prefs(context).getLong(KEY_ALERT_Z, java.lang.Double.doubleToRawLongBits(3.0)))
    fun getAlertBurstMinBps(context: Context): Long = prefs(context).getLong(KEY_ALERT_MIN_BPS, 1_000_000L)
    fun getAlertThrottleRatio(context: Context): Double = java.lang.Double.longBitsToDouble(prefs(context).getLong(KEY_ALERT_RATIO, java.lang.Double.doubleToRawLongBits(0.2)))
    fun getAlertMinLongMean(context: Context): Long = prefs(context).getLong(KEY_ALERT_MIN_LONG, 2_000_000L)
    fun getAlertCooldownMs(context: Context): Long = prefs(context).getLong(KEY_ALERT_COOLDOWN, 60_000L)

    fun set(context: Context, host: String, port: Int, mock: Boolean) {
        prefs(context).edit()
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .putBoolean(KEY_MOCK, mock)
            .apply()
    }

    fun setAll(context: Context, host: String, port: Int, mock: Boolean, onlineKey: String) {
        prefs(context).edit()
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .putBoolean(KEY_MOCK, mock)
            .putString(KEY_ONLINE, onlineKey)
            .apply()
    }

    fun setOnlineKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_ONLINE, key).apply()
    }

    fun setIpDomainJson(context: Context, json: String) {
        prefs(context).edit().putString(KEY_IP_DOMAIN_JSON, json).apply()
    }

    fun setAdaptive(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADAPTIVE, enabled).apply()
    }
    fun setIntervals(context: Context, base: Long, screenOff: Long, idle: Long) {
        prefs(context).edit()
            .putLong(KEY_BASE_MS, base)
            .putLong(KEY_OFF_MS, screenOff)
            .putLong(KEY_IDLE_MS, idle)
            .apply()
    }

    fun setTelemetry(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TELEMETRY, enabled).apply()
    }

    fun setOnlineBytesKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_ONLINE_BYTES, key).apply()
    }

    fun setAutostartXray(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOSTART_XRAY, enabled).apply()
    }

    fun setTopologyAlpha(context: Context, alpha: Float) {
        val a = alpha.coerceIn(0.05f, 1.0f)
        prefs(context).edit().putFloat(KEY_TOPO_ALPHA, a).apply()
    }

    fun setGrpcProfile(context: Context, profile: String) {
        val p = when (profile.lowercase()) { "aggressive", "balanced", "conservative" -> profile.lowercase() else -> "balanced" }
        prefs(context).edit().putString(KEY_GRPC_PROFILE, p).apply()
    }

    fun setGrpcDeadlineMs(context: Context, ms: Long) {
        val v = ms.coerceIn(500L, 10000L)
        prefs(context).edit().putLong(KEY_GRPC_DEADLINE_MS, v).apply()
    }

    fun setRestartOnApply(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RESTART_ON_APPLY, enabled).apply()
    }

    fun setAutoReverseDns(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RDNS, enabled).apply()
    }

    fun setAlertBurstZ(context: Context, z: Double) {
        prefs(context).edit().putLong(KEY_ALERT_Z, java.lang.Double.doubleToRawLongBits(z.coerceIn(1.0, 10.0))).apply()
    }
    fun setAlertBurstMinBps(context: Context, bps: Long) {
        prefs(context).edit().putLong(KEY_ALERT_MIN_BPS, bps.coerceIn(100_000L, 100_000_000L)).apply()
    }
    fun setAlertThrottleRatio(context: Context, ratio: Double) {
        prefs(context).edit().putLong(KEY_ALERT_RATIO, java.lang.Double.doubleToRawLongBits(ratio.coerceIn(0.05, 0.9))).apply()
    }
    fun setAlertMinLongMean(context: Context, bps: Long) {
        prefs(context).edit().putLong(KEY_ALERT_MIN_LONG, bps.coerceIn(100_000L, 100_000_000L)).apply()
    }
    fun setAlertCooldownMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_ALERT_COOLDOWN, ms.coerceIn(5_000L, 600_000L)).apply()
    }

    fun applyAlertPreset(context: Context, preset: String) {
        when (preset.lowercase()) {
            "performance" -> {
                setAlertBurstZ(context, 2.0); setAlertBurstMinBps(context, 500_000);
                setAlertThrottleRatio(context, 0.3); setAlertMinLongMean(context, 1_000_000); setAlertCooldownMs(context, 30_000)
            }
            "conservative" -> {
                setAlertBurstZ(context, 4.0); setAlertBurstMinBps(context, 2_000_000);
                setAlertThrottleRatio(context, 0.15); setAlertMinLongMean(context, 3_000_000); setAlertCooldownMs(context, 90_000)
            }
            else -> { // balanced
                setAlertBurstZ(context, 3.0); setAlertBurstMinBps(context, 1_000_000);
                setAlertThrottleRatio(context, 0.2); setAlertMinLongMean(context, 2_000_000); setAlertCooldownMs(context, 60_000)
            }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
