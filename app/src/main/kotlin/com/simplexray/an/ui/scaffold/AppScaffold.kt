package com.simplexray.an.ui.scaffold

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.simplexray.an.R
import com.simplexray.an.common.ROUTE_CONFIG
import com.simplexray.an.common.ROUTE_LOG
import com.simplexray.an.common.ROUTE_SETTINGS
import com.simplexray.an.viewmodel.LogViewModel
import com.simplexray.an.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppScaffold(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    mainViewModel: MainViewModel,
    logViewModel: LogViewModel,
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    onPerformExport: () -> Unit,
    onPerformBackup: () -> Unit,
    onPerformRestore: () -> Unit,
    onSwitchVpnService: () -> Unit,
    logListState: LazyListState,
    configListState: LazyListState,
    settingsScrollState: androidx.compose.foundation.ScrollState,
    content: @Composable (paddingValues: androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var isLogSearching by remember { mutableStateOf(false) }
    val logSearchQuery by logViewModel.searchQuery.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isLogSearching) {
        if (isLogSearching) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = Modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopAppBar(
                currentRoute,
                onCreateNewConfigFileAndEdit,
                onImportConfigFromClipboard,
                onPerformExport,
                onPerformBackup,
                onPerformRestore,
                onSwitchVpnService,
                mainViewModel.controlMenuClickable.collectAsState().value,
                mainViewModel.isServiceEnabled.collectAsState().value,
                logViewModel,
                logListState = logListState,
                configListState = configListState,
                settingsScrollState = settingsScrollState,
                isLogSearching = isLogSearching,
                onLogSearchingChange = { isLogSearching = it },
                logSearchQuery = logSearchQuery,
                onLogSearchQueryChange = { logViewModel.onSearchQueryChange(it) },
                focusRequester = focusRequester,
                mainViewModel = mainViewModel
            )
        },
        bottomBar = {
            AppBottomNavigationBar(navController)
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        content(paddingValues)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopAppBar(
    currentRoute: String?,
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    onPerformExport: () -> Unit,
    onPerformBackup: () -> Unit,
    onPerformRestore: () -> Unit,
    onSwitchVpnService: () -> Unit,
    controlMenuClickable: Boolean,
    isServiceEnabled: Boolean,
    logViewModel: LogViewModel,
    logListState: LazyListState,
    configListState: LazyListState,
    settingsScrollState: androidx.compose.foundation.ScrollState,
    isLogSearching: Boolean = false,
    onLogSearchingChange: (Boolean) -> Unit = {},
    logSearchQuery: String = "",
    onLogSearchQueryChange: (String) -> Unit = {},
    focusRequester: FocusRequester = FocusRequester(),
    mainViewModel: MainViewModel
) {
    val title = when (currentRoute) {
        "config" -> stringResource(R.string.configuration)
        "log" -> stringResource(R.string.log)
        "settings" -> stringResource(R.string.settings)
        else -> stringResource(R.string.app_name)
    }

    val defaultTopAppBarColors = TopAppBarDefaults.topAppBarColors()

    val showScrolledColor by remember(
        currentRoute,
        logListState,
        configListState,
        settingsScrollState
    ) {
        derivedStateOf {
            when (currentRoute) {
                "log" -> logListState.firstVisibleItemIndex > 0 || logListState.firstVisibleItemScrollOffset > 0
                "config" -> configListState.firstVisibleItemIndex > 0 || configListState.firstVisibleItemScrollOffset > 0
                "settings" -> settingsScrollState.value > 0
                else -> false
            }
        }
    }

    val appBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.run {
            if (showScrolledColor) surfaceContainer else surface
        },
        scrolledContainerColor = MaterialTheme.colorScheme.run {
            if (showScrolledColor) surfaceContainer else surface
        },
        navigationIconContentColor = defaultTopAppBarColors.navigationIconContentColor,
        titleContentColor = defaultTopAppBarColors.titleContentColor,
        actionIconContentColor = defaultTopAppBarColors.actionIconContentColor
    )

    TopAppBar(
        title = {
            if (currentRoute == "log" && isLogSearching) {
                TextField(
                    value = logSearchQuery,
                    onValueChange = onLogSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            } else {
                Text(text = title)
            }
        },
        navigationIcon = {
            if (currentRoute == "log" && isLogSearching) {
                IconButton(onClick = {
                    onLogSearchingChange(false)
                    onLogSearchQueryChange("")
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.close_search)
                    )
                }
            }
        },
        actions = {
            if (currentRoute == "log" && isLogSearching) {
                if (logSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onLogSearchQueryChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear_search)
                        )
                    }
                }
            } else {
                TopAppBarActions(
                    currentRoute = currentRoute,
                    onCreateNewConfigFileAndEdit = onCreateNewConfigFileAndEdit,
                    onImportConfigFromClipboard = onImportConfigFromClipboard,
                    onPerformExport = onPerformExport,
                    onPerformBackup = onPerformBackup,
                    onPerformRestore = onPerformRestore,
                    onSwitchVpnService = onSwitchVpnService,
                    controlMenuClickable = controlMenuClickable,
                    isServiceEnabled = isServiceEnabled,
                    logViewModel = logViewModel,
                    onLogSearchingChange = onLogSearchingChange,
                    mainViewModel = mainViewModel
                )
            }
        },
        colors = appBarColors
    )
}

@Composable
private fun TopAppBarActions(
    currentRoute: String?,
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    onPerformExport: () -> Unit,
    onPerformBackup: () -> Unit,
    onPerformRestore: () -> Unit,
    onSwitchVpnService: () -> Unit,
    controlMenuClickable: Boolean,
    isServiceEnabled: Boolean,
    logViewModel: LogViewModel,
    onLogSearchingChange: (Boolean) -> Unit = {},
    mainViewModel: MainViewModel
) {
    when (currentRoute) {
        "config" -> ConfigActions(
            onCreateNewConfigFileAndEdit = onCreateNewConfigFileAndEdit,
            onImportConfigFromClipboard = onImportConfigFromClipboard,
            onSwitchVpnService = onSwitchVpnService,
            controlMenuClickable = controlMenuClickable,
            isServiceEnabled = isServiceEnabled,
            mainViewModel = mainViewModel
        )

        "log" -> LogActions(
            onPerformExport = onPerformExport,
            logViewModel = logViewModel,
            onLogSearchingChange = onLogSearchingChange
        )

        "settings" -> SettingsActions(
            onPerformBackup = onPerformBackup,
            onPerformRestore = onPerformRestore
        )
    }
}

@Composable
private fun ConfigActions(
    onCreateNewConfigFileAndEdit: () -> Unit,
    onImportConfigFromClipboard: () -> Unit,
    onSwitchVpnService: () -> Unit,
    controlMenuClickable: Boolean,
    isServiceEnabled: Boolean,
    mainViewModel: MainViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = onSwitchVpnService,
        enabled = controlMenuClickable
    ) {
        Icon(
            painter = painterResource(
                id = if (isServiceEnabled) R.drawable.pause else R.drawable.play
            ),
            contentDescription = null
        )
    }

    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more)
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.new_profile)) },
            onClick = {
                onCreateNewConfigFileAndEdit()
                expanded = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.import_from_clipboard)) },
            onClick = {
                expanded = false
                scope.launch {
                    delay(100)
                    onImportConfigFromClipboard()
                }
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.connectivity_test)) },
            onClick = {
                mainViewModel.testConnectivity()
                expanded = false
            },
            enabled = isServiceEnabled
        )
    }
}

@Composable
private fun LogActions(
    onPerformExport: () -> Unit,
    logViewModel: LogViewModel,
    onLogSearchingChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val hasLogsToExport by logViewModel.hasLogsToExport.collectAsStateWithLifecycle()

    IconButton(onClick = { onLogSearchingChange(true) }) {
        Icon(
            painterResource(id = R.drawable.search),
            contentDescription = stringResource(R.string.search)
        )
    }
    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more)
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.export)) },
            onClick = {
                onPerformExport()
                expanded = false
            },
            enabled = hasLogsToExport
        )
    }
}

@Composable
private fun SettingsActions(
    onPerformBackup: () -> Unit,
    onPerformRestore: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more)
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.backup)) },
            onClick = {
                onPerformBackup()
                expanded = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.restore)) },
            onClick = {
                onPerformRestore()
                expanded = false
            }
        )
    }
}

@Composable
fun AppBottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            alwaysShowLabel = false,
            selected = currentRoute == ROUTE_CONFIG,
            onClick = { navigateToRoute(navController, ROUTE_CONFIG) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.code),
                    contentDescription = stringResource(R.string.configuration)
                )
            },
            label = { Text(stringResource(R.string.configuration)) }
        )
        NavigationBarItem(
            alwaysShowLabel = false,
            selected = currentRoute == ROUTE_LOG,
            onClick = { navigateToRoute(navController, ROUTE_LOG) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.history),
                    contentDescription = stringResource(R.string.log)
                )
            },
            label = { Text(stringResource(R.string.log)) }
        )
        NavigationBarItem(
            alwaysShowLabel = false,
            selected = currentRoute == ROUTE_SETTINGS,
            onClick = { navigateToRoute(navController, ROUTE_SETTINGS) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = stringResource(R.string.settings)
                )
            },
            label = { Text(stringResource(R.string.settings)) }
        )
    }
}

private fun navigateToRoute(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
