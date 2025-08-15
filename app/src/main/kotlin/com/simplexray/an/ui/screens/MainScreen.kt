package com.simplexray.an.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.application
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simplexray.an.common.NAVIGATION_DEBOUNCE_DELAY
import com.simplexray.an.common.ROUTE_CONFIG
import com.simplexray.an.common.ROUTE_LOG
import com.simplexray.an.common.ROUTE_SETTINGS
import com.simplexray.an.common.ROUTE_STATS
import com.simplexray.an.common.rememberMainScreenCallbacks
import com.simplexray.an.common.rememberMainScreenLaunchers
import com.simplexray.an.ui.navigation.BottomNavHost
import com.simplexray.an.ui.scaffold.AppScaffold
import com.simplexray.an.viewmodel.LogViewModel
import com.simplexray.an.viewmodel.LogViewModelFactory
import com.simplexray.an.viewmodel.MainViewModel
import com.simplexray.an.viewmodel.MainViewUiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    appNavController: NavHostController,
    snackbarHostState: SnackbarHostState
) {
    val bottomNavController = rememberNavController()
    val scope = rememberCoroutineScope()

    val launchers = rememberMainScreenLaunchers(mainViewModel)

    val logViewModel: LogViewModel = viewModel(
        factory = LogViewModelFactory(mainViewModel.application)
    )

    val callbacks = rememberMainScreenCallbacks(
        mainViewModel = mainViewModel,
        logViewModel = logViewModel,
        launchers = launchers,
        applicationContext = mainViewModel.application
    )

    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    DisposableEffect(mainViewModel) {
        mainViewModel.registerTProxyServiceReceivers()
        onDispose {
            mainViewModel.unregisterTProxyServiceReceivers()
        }
    }

    var lastNavigationTime = 0L

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            mainViewModel.extractAssetsIfNeeded()
        }

        mainViewModel.uiEvent.collectLatest { event ->
            when (event) {
                is MainViewUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        event.message,
                        duration = SnackbarDuration.Short
                    )
                }

                is MainViewUiEvent.ShareLauncher -> {
                    shareLauncher.launch(event.intent)
                }

                is MainViewUiEvent.StartService -> {
                    mainViewModel.application.startService(event.intent)
                }

                is MainViewUiEvent.RefreshConfigList -> {
                    mainViewModel.refreshConfigFileList()
                }

                is MainViewUiEvent.Navigate -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastNavigationTime >= NAVIGATION_DEBOUNCE_DELAY) {
                        lastNavigationTime = currentTime
                        appNavController.navigate(event.route)
                    }
                }
            }
        }
    }

    val logListState = rememberLazyListState()
    val configListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()

    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val mainScreenRoutes = listOf(ROUTE_STATS, ROUTE_CONFIG, ROUTE_LOG, ROUTE_SETTINGS)

    if (currentRoute in mainScreenRoutes) {
        AppScaffold(
            navController = bottomNavController,
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
            BottomNavHost(
                navController = bottomNavController,
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
    } else {
        BottomNavHost(
            navController = bottomNavController,
            paddingValues = androidx.compose.foundation.layout.PaddingValues(),
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
