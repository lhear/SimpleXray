package com.simplexray.an.ui.navigation

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.simplexray.an.TProxyService
import com.simplexray.an.ui.screens.ConfigScreen
import com.simplexray.an.ui.screens.LogScreen
import com.simplexray.an.ui.screens.SettingsScreen
import com.simplexray.an.viewmodel.LogViewModel
import com.simplexray.an.viewmodel.MainViewModel
import java.io.File

private const val TAG = "AppNavGraph"
private const val ROUTE_CONFIG = "config"
private const val ROUTE_LOG = "log"
private const val ROUTE_SETTINGS = "settings"

private val NAV_ROUTES = listOf(ROUTE_CONFIG, ROUTE_LOG, ROUTE_SETTINGS)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideTransitions(
    initialRouteIndex: Int,
    targetRouteIndex: Int
) =
    if (targetRouteIndex > initialRouteIndex) {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) + fadeIn()
    } else {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) + fadeIn()
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOutTransitions(
    initialRouteIndex: Int,
    targetRouteIndex: Int
) =
    if (targetRouteIndex > initialRouteIndex) {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) + fadeOut()
    } else {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) + fadeOut()
    }

private fun createReloadConfigCallback(mainViewModel: MainViewModel): () -> Unit = {
    Log.d(TAG, "Reload config requested from UI.")
    mainViewModel.startTProxyService(TProxyService.ACTION_RELOAD_CONFIG)
}

private fun createEditConfigCallback(mainViewModel: MainViewModel): (File) -> Unit = { file ->
    Log.d(TAG, "ConfigFragment request: Edit file: ${file.name}")
    mainViewModel.editConfig(file.absolutePath)
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    paddingValues: PaddingValues,
    mainViewModel: MainViewModel,
    onDeleteConfigClick: (File, () -> Unit) -> Unit,
    logViewModel: LogViewModel,
    geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    logListState: LazyListState,
    configListState: LazyListState,
    settingsScrollState: androidx.compose.foundation.ScrollState
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_CONFIG,
        modifier = Modifier.padding(paddingValues)
    ) {
        composable(
            route = ROUTE_CONFIG,
            enterTransition = { slideTransitions(NAV_ROUTES, initialState, targetState) },
            exitTransition = { slideOutTransitions(NAV_ROUTES, initialState, targetState) }
        ) {
            ConfigScreen(
                onReloadConfig = createReloadConfigCallback(mainViewModel),
                onEditConfigClick = createEditConfigCallback(mainViewModel),
                onDeleteConfigClick = onDeleteConfigClick,
                mainViewModel = mainViewModel,
                listState = configListState
            )
        }

        composable(
            route = ROUTE_LOG,
            enterTransition = { slideTransitions(NAV_ROUTES, initialState, targetState) },
            exitTransition = { slideOutTransitions(NAV_ROUTES, initialState, targetState) }
        ) {
            LogScreen(
                logViewModel = logViewModel,
                listState = logListState
            )
        }

        composable(
            route = ROUTE_SETTINGS,
            enterTransition = { slideTransitions(NAV_ROUTES, initialState, targetState) },
            exitTransition = { slideOutTransitions(NAV_ROUTES, initialState, targetState) }
        ) {
            SettingsScreen(
                mainViewModel = mainViewModel,
                geoipFilePickerLauncher = geoipFilePickerLauncher,
                geositeFilePickerLauncher = geositeFilePickerLauncher,
                scrollState = settingsScrollState
            )
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideTransitions(
    navRoutes: List<String>,
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry
) = slideTransitions(
    navRoutes.indexOf(initialState.destination.route),
    navRoutes.indexOf(targetState.destination.route)
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOutTransitions(
    navRoutes: List<String>,
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry
) = slideOutTransitions(
    navRoutes.indexOf(initialState.destination.route),
    navRoutes.indexOf(targetState.destination.route)
)
