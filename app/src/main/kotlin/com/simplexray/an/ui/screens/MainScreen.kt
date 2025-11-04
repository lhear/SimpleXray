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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.application
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simplexray.an.common.AppLogger
import com.simplexray.an.common.NAVIGATION_DEBOUNCE_DELAY
import com.simplexray.an.common.ROUTE_CONFIG
import com.simplexray.an.common.ROUTE_LOG
import com.simplexray.an.common.ROUTE_SETTINGS
import com.simplexray.an.common.ROUTE_STATS
import com.simplexray.an.common.ServiceStateChecker
import com.simplexray.an.common.rememberMainScreenCallbacks
import com.simplexray.an.common.rememberMainScreenLaunchers
import com.simplexray.an.service.TProxyService
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
    
    // Check service state when screen becomes visible to ensure UI reflects actual state
    val lifecycleOwner = LocalLifecycleOwner.current
    val lastServiceCheckTime = remember { mutableLongStateOf(0L) }
    val SERVICE_CHECK_DEBOUNCE_MS = 2000L // 2 seconds debounce
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastServiceCheckTime.longValue < SERVICE_CHECK_DEBOUNCE_MS) {
                    return@LifecycleEventObserver
                }
                lastServiceCheckTime.longValue = currentTime
                
                // When screen resumes, reconnect to service and refresh state
                scope.launch(Dispatchers.IO) {
                    try {
                        // CRITICAL: Reconnect binder on resume to ensure state is synced
                        // This fixes the issue where UI loses connection after background
                        mainViewModel.connectionViewModel.reconnectService()
                        
                        // CRITICAL: Refresh routing repository state on resume
                        // This ensures routing rules persist across background→resume
                        com.simplexray.an.protocol.routing.RoutingRepository.onResume()
                        
                        // Wait a bit for reconnect to complete
                        kotlinx.coroutines.delay(500)
                        
                        // Query current state via binder (falls back to service check)
                        val isActuallyRunning = mainViewModel.connectionViewModel.queryServiceState()
                        
                        val currentState = mainViewModel.isServiceEnabled.value
                        if (isActuallyRunning != currentState) {
                            AppLogger.d("MainScreen: Service state mismatch detected. Actual: $isActuallyRunning, UI: $currentState. Updating...")
                            // Update the state to match actual service state
                            mainViewModel.setServiceEnabled(isActuallyRunning)
                        }
                        
                        // If service is running, restart traffic observers to ensure they're active
                        if (isActuallyRunning) {
                            // Note: TrafficViewModel restart is handled internally by observers
                            // They will automatically reconnect when service is available
                            AppLogger.d("MainScreen: Service is running, observers should be active")
                        }
                    } catch (e: Exception) {
                        AppLogger.w("MainScreen: Error checking/reconnecting service state", e)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                settingsScrollState = settingsScrollState,
                appNavController = appNavController
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
            settingsScrollState = settingsScrollState,
            appNavController = appNavController
        )
    }
}
