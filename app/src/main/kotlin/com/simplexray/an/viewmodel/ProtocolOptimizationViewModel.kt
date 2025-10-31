package com.simplexray.an.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.protocol.optimization.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Protocol Optimization
 */
class ProtocolOptimizationViewModel : ViewModel() {

    private val _config = MutableStateFlow(ProtocolConfig())
    val config: StateFlow<ProtocolConfig> = _config.asStateFlow()

    private val _stats = MutableStateFlow(ProtocolStats())
    val stats: StateFlow<ProtocolStats> = _stats.asStateFlow()

    private val _selectedProfile = MutableStateFlow<OptimizationProfile>(OptimizationProfile.BALANCED)
    val selectedProfile: StateFlow<OptimizationProfile> = _selectedProfile.asStateFlow()

    private val _tlsSessionCacheSize = MutableStateFlow(100)
    val tlsSessionCacheSize: StateFlow<Int> = _tlsSessionCacheSize.asStateFlow()

    init {
        loadDefaultStats()
    }

    private fun loadDefaultStats() {
        viewModelScope.launch {
            _stats.value = ProtocolStats(
                http3Requests = 1250,
                http2Requests = 3800,
                http1Requests = 150,
                tls13Connections = 4500,
                tls12Connections = 700,
                brotliCompressionSaved = 125_000_000, // 125 MB
                gzipCompressionSaved = 85_000_000, // 85 MB
                zeroRttSuccess = 450,
                zeroRttFailed = 25,
                avgHandshakeTime = 45
            )
        }
    }

    fun applyProfile(profile: OptimizationProfile) {
        viewModelScope.launch {
            _selectedProfile.value = profile
            _config.value = when (profile) {
                OptimizationProfile.GAMING -> ProtocolConfig.forGaming()
                OptimizationProfile.STREAMING -> ProtocolConfig.forStreaming()
                OptimizationProfile.BATTERY_SAVER -> ProtocolConfig.forBatterySaver()
                OptimizationProfile.BALANCED -> ProtocolConfig()
            }
        }
    }

    fun updateConfig(newConfig: ProtocolConfig) {
        viewModelScope.launch {
            _config.value = newConfig
            _selectedProfile.value = OptimizationProfile.CUSTOM
        }
    }

    fun toggleHttp3() {
        updateConfig(_config.value.copy(http3Enabled = !_config.value.http3Enabled))
    }

    fun toggleTls13() {
        updateConfig(_config.value.copy(tls13Enabled = !_config.value.tls13Enabled))
    }

    fun toggleTls13EarlyData() {
        updateConfig(_config.value.copy(tls13EarlyData = !_config.value.tls13EarlyData))
    }

    fun toggleBrotli() {
        updateConfig(_config.value.copy(brotliEnabled = !_config.value.brotliEnabled))
    }

    fun toggleGzip() {
        updateConfig(_config.value.copy(gzipEnabled = !_config.value.gzipEnabled))
    }

    fun setBrotliQuality(quality: Int) {
        updateConfig(_config.value.copy(brotliQuality = quality.coerceIn(0, 11)))
    }

    fun setGzipLevel(level: Int) {
        updateConfig(_config.value.copy(gzipLevel = level.coerceIn(0, 9)))
    }

    fun setQuicMaxStreams(maxStreams: Int) {
        updateConfig(_config.value.copy(quicMaxStreams = maxStreams.coerceIn(10, 500)))
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            _config.value = ProtocolConfig()
            _selectedProfile.value = OptimizationProfile.BALANCED
        }
    }

    fun getCompressionAlgorithms(): List<CompressionAlgorithm> {
        return CompressionAlgorithm.entries
    }

    fun estimateCompressionSavings(originalSize: Long): Map<CompressionAlgorithm, Long> {
        return CompressionAlgorithm.entries.associateWith { algorithm ->
            algorithm.estimateCompressedSize(originalSize)
        }
    }

    enum class OptimizationProfile {
        GAMING,
        STREAMING,
        BATTERY_SAVER,
        BALANCED,
        CUSTOM
    }
}
