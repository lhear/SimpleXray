package com.simplexray.an.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.simplexray.an.activity.MainActivity
import com.simplexray.an.common.rememberMainScreenCallbacks
import com.simplexray.an.common.rememberMainScreenLaunchers
import com.simplexray.an.ui.navigation.AppNavHost
import com.simplexray.an.ui.scaffold.AppScaffold
import com.simplexray.an.viewmodel.LogViewModel
import com.simplexray.an.viewmodel.LogViewModelFactory
import com.simplexray.an.viewmodel.MainViewModel
import com.simplexray.an.viewmodel.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainActivity: MainActivity,
    colorScheme: androidx.compose.material3.ColorScheme,
    mainViewModel: MainViewModel
) {
    MaterialTheme(colorScheme = colorScheme) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val launchers = rememberMainScreenLaunchers(mainViewModel)

        val logViewModel: LogViewModel = viewModel(
            factory = LogViewModelFactory(mainActivity.application)
        )

        val callbacks = rememberMainScreenCallbacks(
            mainViewModel = mainViewModel,
            logViewModel = logViewModel,
            launchers = launchers,
            applicationContext = mainActivity.applicationContext
        )

        DisposableEffect(mainViewModel) {
            mainViewModel.registerTProxyServiceReceivers()
            onDispose {
                mainViewModel.unregisterTProxyServiceReceivers()
            }
        }

        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                mainViewModel.extractAssetsIfNeeded()
            }

            mainViewModel.uiEvent.collectLatest { event ->
                when (event) {
                    is UiEvent.ShowSnackbar -> {
                        snackbarHostState.showSnackbar(
                            event.message,
                            duration = SnackbarDuration.Short
                        )
                    }

                    is UiEvent.StartActivity -> {
                        mainActivity.startActivity(event.intent)
                    }

                    is UiEvent.StartService -> {
                        mainActivity.startService(event.intent)
                    }

                    is UiEvent.RefreshConfigList -> {
                        mainViewModel.refreshConfigFileList()
                    }
                }
            }
        }

        val logListState = rememberLazyListState()
        val configListState = rememberLazyListState()
        val settingsScrollState = rememberScrollState()

        AppScaffold(
            navController = navController,
            snackbarHostState = snackbarHostState,
            mainViewModel = mainViewModel,
            logViewModel = logViewModel,
            onCreateNewConfigFileAndEdit = callbacks.onCreateNewConfigFileAndEdit,
            onImportConfigFromClipboard = callbacks.onImportConfigFromClipboard,
            onPerformExport = callbacks.onPerformExport,
            onPerformBackup = callbacks.onPerformBackup,
            onPerformRestore = callbacks.onPerformRestore,
            onSwitchVpnService = callbacks.onSwitchVpnService,
            logListState = logListState,
            configListState = configListState,
            settingsScrollState = settingsScrollState
        ) { paddingValues ->
            AppNavHost(
                navController = navController,
                paddingValues = paddingValues,
                mainViewModel = mainViewModel,
                onDeleteConfigClick = callbacks.onDeleteConfigClick,
                logViewModel = logViewModel,
                geoipFilePickerLauncher = launchers.geoipFilePickerLauncher,
                geositeFilePickerLauncher = launchers.geositeFilePickerLauncher,
                logListState = logListState,
                configListState = configListState,
                settingsScrollState = settingsScrollState
            )
        }
    }
}
