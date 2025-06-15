package com.simplexray.an.ui.scaffold

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.simplexray.an.viewmodel.LogViewModel
import com.simplexray.an.viewmodel.MainViewModel
import com.simplexray.an.R

@OptIn(ExperimentalMaterial3Api::class)
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
    scrollBehavior: TopAppBarScrollBehavior?,
    logListState: LazyListState,
    content: @Composable (paddingValues: androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
            ?: Modifier,
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
                scrollBehavior,
                logListState = logListState
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
    scrollBehavior: TopAppBarScrollBehavior?,
    logListState: LazyListState
) {
    val title = when (currentRoute) {
        "config" -> stringResource(R.string.configuration)
        "log" -> stringResource(R.string.log)
        "settings" -> stringResource(R.string.settings)
        else -> stringResource(R.string.app_name)
    }

    val defaultTopAppBarColors = TopAppBarDefaults.topAppBarColors()

    val appBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = when (currentRoute) {
            "log" -> {
                val showScrolledColor by remember {
                    derivedStateOf {
                        logListState.firstVisibleItemIndex > 0 ||
                                logListState.firstVisibleItemScrollOffset > 0
                    }
                }
                if (showScrolledColor) MaterialTheme.colorScheme.surfaceContainer
                else MaterialTheme.colorScheme.surface
            }

            else -> defaultTopAppBarColors.containerColor
        },
        scrolledContainerColor = when (currentRoute) {
            "log" -> {
                val showScrolledColor by remember {
                    derivedStateOf {
                        logListState.firstVisibleItemIndex > 0 ||
                                logListState.firstVisibleItemScrollOffset > 0
                    }
                }
                if (showScrolledColor) MaterialTheme.colorScheme.surfaceContainer
                else MaterialTheme.colorScheme.surface
            }

            else -> defaultTopAppBarColors.scrolledContainerColor
        },
        navigationIconContentColor = defaultTopAppBarColors.navigationIconContentColor,
        titleContentColor = defaultTopAppBarColors.titleContentColor,
        actionIconContentColor = defaultTopAppBarColors.actionIconContentColor
    )

    TopAppBar(
        title = { Text(text = title) },
        actions = {
            when (currentRoute) {
                "config" -> {
                    var expanded by remember { mutableStateOf(false) }
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
                                onImportConfigFromClipboard()
                                expanded = false
                            }
                        )
                    }
                }

                "log" -> {
                    var expanded by remember { mutableStateOf(false) }
                    val hasLogsToExport by logViewModel.hasLogsToExport.collectAsStateWithLifecycle()
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

                "settings" -> {
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
            }
        },
        scrollBehavior = scrollBehavior,
        colors = appBarColors
    )
}

@Composable
fun AppBottomNavigationBar(navController: NavHostController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        NavigationBarItem(
            alwaysShowLabel = false,
            selected = currentRoute == "config",
            onClick = {
                navController.navigate("config") {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }; launchSingleTop = true; restoreState = true
                }
            },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.code),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.configuration)) }
        )
        NavigationBarItem(
            alwaysShowLabel = false,
            selected = currentRoute == "log",
            onClick = {
                navController.navigate("log") {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }; launchSingleTop = true; restoreState = true
                }
            },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.history),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.log)) }
        )
        NavigationBarItem(
            alwaysShowLabel = false,
            selected = currentRoute == "settings",
            onClick = {
                navController.navigate("settings") {
                    popUpTo(
                        navController.graph.startDestinationId
                    ) { saveState = true }; launchSingleTop =
                    true; restoreState = true
                }
            },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.settings)) }
        )
    }
}
