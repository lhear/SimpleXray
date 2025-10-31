package com.simplexray.an.ui.performance

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.viewmodel.PerformanceViewModel

@Composable
fun PerformanceScreenWithViewModel(
    onBackClick: () -> Unit = {},
    viewModel: PerformanceViewModel = viewModel()
) {
    val currentProfile by viewModel.currentProfile.collectAsState()
    val currentMetrics by viewModel.currentMetrics.collectAsState()
    val autoTuneEnabled by viewModel.autoTuneEnabled.collectAsState()

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
        onBackClick = onBackClick
    )
}
