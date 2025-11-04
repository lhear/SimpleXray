package com.simplexray.an.viewmodel

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.simplexray.an.common.AppLogger
import androidx.lifecycle.viewModelScope
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.performance.model.MetricsHistory
import com.simplexray.an.performance.monitor.PerformanceMonitor
import com.simplexray.an.performance.monitor.ConnectionAnalyzer
import com.simplexray.an.performance.monitor.Bottleneck
import com.simplexray.an.performance.optimizer.PerformanceOptimizer
import com.simplexray.an.performance.optimizer.AdaptivePerformanceTuner
import com.simplexray.an.performance.optimizer.ProfileRecommendation
import com.simplexray.an.performance.optimizer.TuningState
import com.simplexray.an.performance.export.DataExporter
import com.simplexray.an.performance.speedtest.SpeedTest
import com.simplexray.an.performance.speedtest.SpeedTestResult
import com.simplexray.an.performance.BatteryImpactMonitor
import com.simplexray.an.performance.PerformanceBenchmark
import com.simplexray.an.performance.PerformanceIntegration
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
    private val adaptiveTuner = AdaptivePerformanceTuner(application, performanceOptimizer, viewModelScope)
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
    
    // Battery monitoring
    private var perfIntegration: PerformanceIntegration? = null
    private val _batteryData = MutableStateFlow<BatteryImpactMonitor.BatteryImpactData?>(null)
    val batteryData: StateFlow<BatteryImpactMonitor.BatteryImpactData?> = _batteryData.asStateFlow()
    
    // Benchmark
    private val benchmark = PerformanceBenchmark(application)
    private val _benchmarkResults = MutableStateFlow<List<PerformanceBenchmark.BenchmarkResult>>(emptyList())
    val benchmarkResults: StateFlow<List<PerformanceBenchmark.BenchmarkResult>> = _benchmarkResults.asStateFlow()
    private val _isRunningBenchmark = MutableStateFlow(false)
    val isRunningBenchmark: StateFlow<Boolean> = _isRunningBenchmark.asStateFlow()
    
    // Adaptive Tuning
    val tuningState: StateFlow<TuningState> = adaptiveTuner.tuningState
    val lastRecommendation: StateFlow<ProfileRecommendation?> = adaptiveTuner.lastRecommendation

    init {
        // Initialize performance integration if available
        try {
            perfIntegration = PerformanceIntegration(application)
            if (TProxyService.isRunning()) {
                perfIntegration?.initialize()
                
                // Collect battery data
                viewModelScope.launch {
                    perfIntegration?.getBatteryImpactData()?.collect { data ->
                        _batteryData.value = data
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Failed to initialize performance integration", e)
        }
        
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

                        // Update adaptive tuner with latest metrics
                        if (_autoTuneEnabled.value) {
                            adaptiveTuner.updateMetrics(metrics)
                        }

                        // Auto-tune if enabled (legacy method)
                        if (_autoTuneEnabled.value) {
                            try {
                                performanceOptimizer.autoTune(metrics)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Log exception to prevent crash
                                AppLogger.e("Auto-tune failed", e)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("Error processing metrics", e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("Performance monitoring failed", e)
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
                        AppLogger.e("Error updating history", e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("History collection failed", e)
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
                                AppLogger.d("Config updated for auto-tuned profile: ${profile.name}")
                                
                                // Reload Xray connection to apply new config
                                reloadXrayConfig()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppLogger.w("Failed to update config file after auto-tune", e)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("Error updating profile", e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("Profile collection failed", e)
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
                        AppLogger.e("Error updating auto-tune state", e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("Auto-tune collection failed", e)
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
                        AppLogger.d("Config updated for profile: ${profile.name}")
                        
                        // Reload Xray connection to apply new config
                        reloadXrayConfig()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.w("Failed to update config file", e)
                    }
                }
                
                AppLogger.d("Profile selected: ${profile.name}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("Failed to select profile: ${profile.name}", e)
            }
        }
    }

    fun toggleAutoTune() {
        val newState = !_autoTuneEnabled.value
        viewModelScope.launch {
            performanceOptimizer.setAutoTuneEnabled(newState)
            adaptiveTuner.setEnabled(newState)
            _autoTuneEnabled.value = newState

            if (newState) {
                // Immediately run auto-tune
                try {
                    performanceOptimizer.autoTune(_currentMetrics.value)
                    adaptiveTuner.updateMetrics(_currentMetrics.value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Log exception to prevent crash
                    AppLogger.e("Auto-tune toggle failed", e)
                }
            }
        }
    }
    
    /**
     * Apply adaptive tuning recommendation
     */
    fun applyRecommendation(recommendation: ProfileRecommendation) {
        adaptiveTuner.applyRecommendation(recommendation)
    }
    
    /**
     * Provide feedback on recommendation
     */
    fun provideFeedback(recommendation: ProfileRecommendation, accepted: Boolean) {
        adaptiveTuner.provideFeedback(recommendation, accepted)
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
                AppLogger.e("Export failed", e)
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
    /**
     * Run performance benchmark
     */
    fun runBenchmark() {
        viewModelScope.launch {
            if (_isRunningBenchmark.value) return@launch
            
            _isRunningBenchmark.value = true
            try {
                val networkType = com.simplexray.an.performance.PerformanceUtils.detectNetworkType(getApplication())
                val results = benchmark.runAllBenchmarks()
                _benchmarkResults.value = results
                AppLogger.d("Benchmark completed: ${results.size} tests")
            } catch (e: Exception) {
                AppLogger.e("Benchmark failed", e)
            } finally {
                _isRunningBenchmark.value = false
            }
        }
    }
    
    /**
     * Run comprehensive benchmark with before/after comparison
     */
    fun runComprehensiveBenchmark() {
        viewModelScope.launch {
            if (_isRunningBenchmark.value) return@launch
            
            _isRunningBenchmark.value = true
            try {
                val networkType = com.simplexray.an.performance.PerformanceUtils.detectNetworkType(getApplication())
                val isEnabled = TProxyService.isRunning() && 
                    com.simplexray.an.prefs.Preferences(getApplication()).enablePerformanceMode
                
                val comprehensive = benchmark.runComprehensiveBenchmark(
                    performanceModeEnabled = isEnabled,
                    networkType = networkType.name
                )
                _benchmarkResults.value = comprehensive.results
                AppLogger.d("Comprehensive benchmark completed: ${comprehensive.overallImprovement}% improvement")
            } catch (e: Exception) {
                AppLogger.e("Comprehensive benchmark failed", e)
            } finally {
                _isRunningBenchmark.value = false
            }
        }
    }
    
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
                AppLogger.e("Speed test failed", e)
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
                action = "com.simplexray.an.RELOAD_CONFIG"
            }
            getApplication<Application>().startService(intent)
            AppLogger.d("Reload config request sent to TProxyService")
        } catch (e: Exception) {
            AppLogger.w("Failed to reload Xray config", e)
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
