package com.simplexray.an.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.simplexray.an.viewmodel.MainViewModelFactory
import com.simplexray.an.viewmodel.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainActivity: MainActivity,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    MaterialTheme(colorScheme = colorScheme) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val mainViewModel: MainViewModel = viewModel(
            factory = MainViewModelFactory(mainActivity.application)
        )

        val files by mainViewModel.configFiles.collectAsStateWithLifecycle()
        val selectedFile by mainViewModel.selectedConfigFile.collectAsStateWithLifecycle()

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

        val currentScrollBehaviorState = remember { mutableStateOf<TopAppBarScrollBehavior?>(null) }
        val currentScrollBehavior by currentScrollBehaviorState

        val logListState = rememberLazyListState()

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
            scrollBehavior = currentScrollBehavior,
            logListState = logListState
        ) { paddingValues ->
            AppNavHost(
                navController = navController,
                paddingValues = paddingValues,
                mainViewModel = mainViewModel,
                onDeleteConfigClick = callbacks.onDeleteConfigClick,
                files = files,
                selectedFile = selectedFile,
                logViewModel = logViewModel,
                geoipFilePickerLauncher = launchers.geoipFilePickerLauncher,
                geositeFilePickerLauncher = launchers.geositeFilePickerLauncher,
                onScrollBehaviorChanged = { newBehavior ->
                    currentScrollBehaviorState.value = newBehavior
                },
                logListState = logListState
            )
        }
    }
}
