package com.simplexray.an.ui.performance

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.simplexray.an.common.ROUTE_ADVANCED_PERFORMANCE_SETTINGS
import com.simplexray.an.ui.performance.AdaptiveTuningStatusCard
import com.simplexray.an.viewmodel.PerformanceViewModel

@Composable
fun PerformanceScreenWithViewModel(
    onBackClick: () -> Unit = {},
    navController: NavController? = null,
    viewModel: PerformanceViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val currentMetrics by viewModel.currentMetrics.collectAsStateWithLifecycle()
    val metricsHistory by viewModel.metricsHistory.collectAsStateWithLifecycle()
    val bottlenecks by viewModel.bottlenecks.collectAsStateWithLifecycle()
    val autoTuneEnabled by viewModel.autoTuneEnabled.collectAsStateWithLifecycle()
    val batteryData by viewModel.batteryData.collectAsStateWithLifecycle()
    val benchmarkResults by viewModel.benchmarkResults.collectAsStateWithLifecycle()
    val isRunningBenchmark by viewModel.isRunningBenchmark.collectAsStateWithLifecycle()
    var showMonitoring by remember { mutableStateOf(false) }

    if (showMonitoring) {
        // Show enhanced monitoring dashboard
        MonitoringDashboard(
            currentMetrics = currentMetrics,
            history = metricsHistory,
            bottlenecks = bottlenecks,
            onRunSpeedTest = {
                viewModel.runSpeedTest()
            },
            onExportData = {
                viewModel.exportData(com.simplexray.an.viewmodel.ExportFormat.CSV)
            },
            onBackClick = {
                showMonitoring = false
            }
        )
    } else {
        // Show performance settings screen
        PerformanceScreen(
            currentProfile = currentProfile,
            currentMetrics = currentMetrics,
            onProfileSelected = { profile ->
                viewModel.selectProfile(profile)
            },
            onAutoTuneToggled = {
                viewModel.toggleAutoTune()
            },
            autoTuneEnabled = autoTuneEnabled,
            onBackClick = onBackClick,
            onShowMonitoring = {
                showMonitoring = true
            },
            batteryData = batteryData,
            benchmarkResults = benchmarkResults,
            isRunningBenchmark = isRunningBenchmark,
            onRunBenchmark = {
                viewModel.runBenchmark()
            },
            onRunComprehensiveBenchmark = {
                viewModel.runComprehensiveBenchmark()
            },
            onAdvancedSettingsClick = {
                navController?.navigate(ROUTE_ADVANCED_PERFORMANCE_SETTINGS)
            },
            navController = navController,
            viewModel = viewModel
        )
    }
}
