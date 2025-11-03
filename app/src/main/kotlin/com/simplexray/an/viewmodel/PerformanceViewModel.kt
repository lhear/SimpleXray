package com.simplexray.an.viewmodel

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.performance.model.MetricsHistory
import com.simplexray.an.performance.monitor.PerformanceMonitor
import com.simplexray.an.performance.monitor.ConnectionAnalyzer
import com.simplexray.an.performance.monitor.Bottleneck
import com.simplexray.an.performance.optimizer.PerformanceOptimizer
import com.simplexray.an.performance.export.DataExporter
import com.simplexray.an.performance.speedtest.SpeedTest
import com.simplexray.an.performance.speedtest.SpeedTestResult
import com.simplexray.an.service.TProxyService
import com.simplexray.an.xray.XrayConfigPatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/**
 * ViewModel for Performance Optimization screen
 */
class PerformanceViewModel(application: Application) : AndroidViewModel(application) {

    private val performanceMonitor = PerformanceMonitor(application)
    private val performanceOptimizer = PerformanceOptimizer(application)
    private val connectionAnalyzer = ConnectionAnalyzer()
    private val dataExporter = DataExporter(application)
    private val speedTest = SpeedTest()

    private val _currentProfile: MutableStateFlow<PerformanceProfile> = MutableStateFlow(PerformanceProfile.Balanced)
    val currentProfile: StateFlow<PerformanceProfile> = _currentProfile.asStateFlow()

    private val _currentMetrics = MutableStateFlow(
        PerformanceMetrics(
            cpuUsage = 0.0f,
            memoryUsage = 0L,
            latency = 0,
            packetLoss = 0.0f
        )
    )
    val currentMetrics: StateFlow<PerformanceMetrics> = _currentMetrics.asStateFlow()

    private val _metricsHistory = MutableStateFlow(MetricsHistory())
    val metricsHistory: StateFlow<MetricsHistory> = _metricsHistory.asStateFlow()

    private val _bottlenecks = MutableStateFlow<List<Bottleneck>>(emptyList())
    val bottlenecks: StateFlow<List<Bottleneck>> = _bottlenecks.asStateFlow()

    private val _autoTuneEnabled = MutableStateFlow(false)
    val autoTuneEnabled: StateFlow<Boolean> = _autoTuneEnabled.asStateFlow()

    private val _speedTestResult = MutableStateFlow<SpeedTestResult?>(null)
    val speedTestResult: StateFlow<SpeedTestResult?> = _speedTestResult.asStateFlow()

    private val _isRunningSpeedTest = MutableStateFlow(false)
    val isRunningSpeedTest: StateFlow<Boolean> = _isRunningSpeedTest.asStateFlow()

    init {
        // Start monitoring
        viewModelScope.launch {
            try {
                performanceMonitor.start()
                performanceMonitor.currentMetrics.collect { metrics ->
                    try {
                        _currentMetrics.value = metrics

                        // Update history
                        _metricsHistory.value = _metricsHistory.value.add(metrics)

                        // Detect bottlenecks
                        _bottlenecks.value = connectionAnalyzer.detectBottlenecks(metrics)

                        // Auto-tune if enabled
                        if (_autoTuneEnabled.value) {
                            try {
                                performanceOptimizer.autoTune(metrics)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Log exception to prevent crash
                                android.util.Log.e("PerformanceViewModel", "Auto-tune failed", e)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("PerformanceViewModel", "Error processing metrics", e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PerformanceViewModel", "Performance monitoring failed", e)
            }
        }

        // Collect metrics history from monitor
        viewModelScope.launch {
            try {
                performanceMonitor.metricsHistory.collect { history ->
                    try {
                        _metricsHistory.value = history
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("PerformanceViewModel", "Error updating history", e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PerformanceViewModel", "History collection failed", e)
            }
        }

        // Observe profile changes from optimizer (e.g., from auto-tune)
        viewModelScope.launch {
            try {
                performanceOptimizer.currentProfile.collect { profile ->
                    try {
                        _currentProfile.value = profile
                        
                        // Update Xray config when profile changes (e.g., from auto-tune)
                        withContext(Dispatchers.IO) {
                            try {
                                XrayConfigPatcher.patchConfig(getApplication())
                                android.util.Log.d("PerformanceViewModel", "Config updated for auto-tuned profile: ${profile.name}")
                                
                                // Reload Xray connection to apply new config
                                reloadXrayConfig()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("PerformanceViewModel", "Failed to update config file after auto-tune", e)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("PerformanceViewModel", "Error updating profile", e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PerformanceViewModel", "Profile collection failed", e)
            }
        }

        // Observe auto-tune state changes
        viewModelScope.launch {
            try {
                performanceOptimizer.autoTuneEnabled.collect { enabled ->
                    try {
                        _autoTuneEnabled.value = enabled
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("PerformanceViewModel", "Error updating auto-tune state", e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PerformanceViewModel", "Auto-tune collection failed", e)
            }
        }
    }

    fun selectProfile(profile: PerformanceProfile) {
        viewModelScope.launch {
            try {
                performanceOptimizer.setProfile(profile)
                _currentProfile.value = profile
                
                // Update Xray config file with new performance settings
                withContext(Dispatchers.IO) {
                    try {
                        XrayConfigPatcher.patchConfig(getApplication())
                        android.util.Log.d("PerformanceViewModel", "Config updated for profile: ${profile.name}")
                        
                        // Reload Xray connection to apply new config
                        reloadXrayConfig()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("PerformanceViewModel", "Failed to update config file", e)
                    }
                }
                
                android.util.Log.d("PerformanceViewModel", "Profile selected: ${profile.name}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PerformanceViewModel", "Failed to select profile: ${profile.name}", e)
            }
        }
    }

    fun toggleAutoTune() {
        val newState = !_autoTuneEnabled.value
        viewModelScope.launch {
            performanceOptimizer.setAutoTuneEnabled(newState)
            _autoTuneEnabled.value = newState

            if (newState) {
                // Immediately run auto-tune
                try {
                    performanceOptimizer.autoTune(_currentMetrics.value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Log exception to prevent crash
                    android.util.Log.e("PerformanceViewModel", "Auto-tune toggle failed", e)
                }
            }
        }
    }

    /**
     * Export performance data
     */
    fun exportData(format: ExportFormat) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val file = when (format) {
                        ExportFormat.CSV -> dataExporter.exportToCsv(_metricsHistory.value)
                        ExportFormat.JSON -> dataExporter.exportToJson(_metricsHistory.value)
                    }

                    // Share the file
                    withContext(Dispatchers.Main) {
                        dataExporter.shareFile(file)
                        Toast.makeText(
                            getApplication(),
                            "Data exported successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PerformanceViewModel", "Export failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Export failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Run speed test
     */
    fun runSpeedTest() {
        if (_isRunningSpeedTest.value) {
            return // Already running
        }

        viewModelScope.launch {
            try {
                _isRunningSpeedTest.value = true
                speedTest.runSpeedTest().collect { result ->
                    _speedTestResult.value = result

                    // Show toast for final result
                    if (result is SpeedTestResult.Complete) {
                        withContext(Dispatchers.Main) {
                            val downloadMbps = result.downloadSpeed / (1024f * 1024f)
                            val uploadMbps = result.uploadSpeed / (1024f * 1024f)
                            Toast.makeText(
                                getApplication(),
                                "Speed Test Complete\nDownload: ${String.format("%.2f", downloadMbps)} MB/s\nUpload: ${String.format("%.2f", uploadMbps)} MB/s\nLatency: ${result.latency} ms",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Clean up state before re-throwing
                _isRunningSpeedTest.value = false
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PerformanceViewModel", "Speed test failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Speed test failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                _isRunningSpeedTest.value = false
            }
        }
    }

    /**
     * Reload Xray config by sending ACTION_RELOAD_CONFIG to TProxyService
     */
    private fun reloadXrayConfig() {
        try {
            val intent = Intent(getApplication(), TProxyService::class.java).apply {
                action = TProxyService.ACTION_RELOAD_CONFIG
            }
            getApplication<Application>().startService(intent)
            android.util.Log.d("PerformanceViewModel", "Reload config request sent to TProxyService")
        } catch (e: Exception) {
            android.util.Log.w("PerformanceViewModel", "Failed to reload Xray config", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        performanceMonitor.stop()
    }
}

enum class ExportFormat {
    CSV,
    JSON
}
