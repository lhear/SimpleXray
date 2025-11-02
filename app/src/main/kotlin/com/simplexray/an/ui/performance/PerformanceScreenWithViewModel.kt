package com.simplexray.an.ui.performance

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.viewmodel.PerformanceViewModel

@Composable
fun PerformanceScreenWithViewModel(
    onBackClick: () -> Unit = {},
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
            }
        )
    }
}
