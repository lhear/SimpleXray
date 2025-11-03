package com.simplexray.an.ui.navigation

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.simplexray.an.common.ROUTE_CONFIG
import com.simplexray.an.common.ROUTE_LOG
import com.simplexray.an.common.ROUTE_SETTINGS
import com.simplexray.an.common.ROUTE_STATS
import com.simplexray.an.common.ROUTE_XRAY_SETTINGS
import com.simplexray.an.service.TProxyService
import com.simplexray.an.ui.screens.ConfigScreen
import com.simplexray.an.ui.screens.DashboardScreen
import com.simplexray.an.ui.screens.LogScreen
import com.simplexray.an.ui.screens.SettingsScreen
import com.simplexray.an.viewmodel.LogViewModel
import com.simplexray.an.viewmodel.MainViewModel
import java.io.File

private const val TAG = "AppNavGraph"

private val BOTTOM_NAV_ROUTE_INDEX = mapOf(
    ROUTE_STATS to 0,
    ROUTE_CONFIG to 1,
    ROUTE_LOG to 2,
    ROUTE_SETTINGS to 3
)

private fun NavBackStackEntry.routeIndex(): Int =
    destination.route?.let { BOTTOM_NAV_ROUTE_INDEX[it] } ?: 0

private fun AnimatedContentTransitionScope<NavBackStackEntry>.horizontalSlideEnter(): EnterTransition {
    val targetIndex = targetState.routeIndex()
    val initialIndex = initialState.routeIndex()
    val direction = if (targetIndex > initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
    return slideIntoContainer(
        towards = direction,
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(200))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.horizontalSlideExit(): ExitTransition {
    val targetIndex = targetState.routeIndex()
    val initialIndex = initialState.routeIndex()
    val direction = if (targetIndex > initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
    return slideOutOfContainer(
        towards = direction,
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(150))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition() =
    horizontalSlideEnter()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitTransition() =
    horizontalSlideExit()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterTransition() =
    horizontalSlideEnter()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitTransition() =
    horizontalSlideExit()

private fun createReloadConfigCallback(mainViewModel: MainViewModel): () -> Unit = {
    Log.d(TAG, "Reload config requested from UI.")
    mainViewModel.startTProxyService(TProxyService.ACTION_RELOAD_CONFIG)
}

private fun createEditConfigCallback(mainViewModel: MainViewModel): (File) -> Unit = { file ->
    Log.d(TAG, "ConfigFragment request: Edit file: ${file.name}")
    mainViewModel.editConfig(file.absolutePath)
}

@Composable
fun BottomNavHost(
    navController: NavHostController,
    paddingValues: PaddingValues,
    mainViewModel: MainViewModel,
    onDeleteConfigClick: (File, () -> Unit) -> Unit,
    logViewModel: LogViewModel,
    geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    logListState: LazyListState,
    configListState: LazyListState,
    settingsScrollState: ScrollState,
    appNavController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_STATS,
        modifier = Modifier.padding(paddingValues)
    ) {
        composable(
            route = ROUTE_STATS,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() }
        ) {
            DashboardScreen(
                mainViewModel = mainViewModel,
                appNavController = appNavController
            )
        }

        composable(
            route = ROUTE_CONFIG,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() }
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
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() }
        ) {
            LogScreen(
                logViewModel = logViewModel,
                listState = logListState
            )
        }

        composable(
            route = ROUTE_SETTINGS,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() }
        ) {
            SettingsScreen(
                mainViewModel = mainViewModel,
                geoipFilePickerLauncher = geoipFilePickerLauncher,
                geositeFilePickerLauncher = geositeFilePickerLauncher,
                scrollState = settingsScrollState,
                onNavigateToXraySettings = {
                    appNavController.navigate(com.simplexray.an.common.ROUTE_XRAY_SETTINGS)
                }
            )
        }
    }
}