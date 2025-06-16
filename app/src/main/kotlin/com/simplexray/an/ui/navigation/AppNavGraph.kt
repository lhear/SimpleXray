package com.simplexray.an.ui.navigation

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.simplexray.an.TProxyService
import com.simplexray.an.common.LocalTopAppBarScrollBehavior
import com.simplexray.an.ui.screens.ConfigScreen
import com.simplexray.an.ui.screens.LogScreen
import com.simplexray.an.ui.screens.SettingsScreen
import com.simplexray.an.viewmodel.LogViewModel
import com.simplexray.an.viewmodel.MainViewModel
import java.io.File

private const val TAG = "AppNavGraph"

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideTransitions(
    initialRouteIndex: Int,
    targetRouteIndex: Int
) =
    if (targetRouteIndex > initialRouteIndex) {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) + fadeIn()
    } else {
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) + fadeIn()
    }

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOutTransitions(
    initialRouteIndex: Int,
    targetRouteIndex: Int
) =
    if (targetRouteIndex > initialRouteIndex) {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) + fadeOut()
    } else {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) + fadeOut()
    }

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    paddingValues: PaddingValues,
    mainViewModel: MainViewModel,
    onDeleteConfigClick: (File, () -> Unit) -> Unit,
    logViewModel: LogViewModel,
    geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    onScrollBehaviorChanged: (TopAppBarScrollBehavior?) -> Unit,
    logListState: LazyListState
) {
    val navRoutes = listOf("config", "log", "settings")

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        onScrollBehaviorChanged(null)
    }

    NavHost(
        navController = navController,
        startDestination = "config",
        modifier = Modifier.padding(paddingValues)
    ) {
        composable(
            "config",
            enterTransition = {
                val initialRouteIndex = navRoutes.indexOf(initialState.destination.route)
                val targetRouteIndex = navRoutes.indexOf(targetState.destination.route)
                slideTransitions(initialRouteIndex, targetRouteIndex)
            },
            exitTransition = {
                val initialRouteIndex = navRoutes.indexOf(initialState.destination.route)
                val targetRouteIndex = navRoutes.indexOf(targetState.destination.route)
                slideOutTransitions(initialRouteIndex, targetRouteIndex)
            }
        ) {
            val configScrollBehavior =
                TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
            DisposableEffect(configScrollBehavior) {
                onScrollBehaviorChanged(configScrollBehavior)
                onDispose {
                    onScrollBehaviorChanged(null)
                }
            }
            CompositionLocalProvider(LocalTopAppBarScrollBehavior provides configScrollBehavior) {
                ConfigScreen(
                    onReloadConfig = {
                        if (!mainViewModel.controlMenuClickable.value) {
                            Log.d(
                                TAG,
                                "Reload config request ignored, UI control is not clickable."
                            )
                            return@ConfigScreen
                        }
                        Log.d(TAG, "Reload config requested from UI.")
                        mainViewModel.startTProxyService(TProxyService.ACTION_RELOAD_CONFIG)
                    },
                    onEditConfigClick = { file ->
                        Log.d(TAG, "ConfigFragment request: Edit file: " + file.name)
                        mainViewModel.editConfig(file.absolutePath)
                    },
                    onDeleteConfigClick = onDeleteConfigClick,
                    mainViewModel = mainViewModel
                )
            }
        }
        composable(
            "log",
            enterTransition = {
                val initialRouteIndex = navRoutes.indexOf(initialState.destination.route)
                val targetRouteIndex = navRoutes.indexOf(targetState.destination.route)
                slideTransitions(initialRouteIndex, targetRouteIndex)
            },
            exitTransition = {
                val initialRouteIndex = navRoutes.indexOf(initialState.destination.route)
                val targetRouteIndex = navRoutes.indexOf(targetState.destination.route)
                slideOutTransitions(initialRouteIndex, targetRouteIndex)
            }
        ) {
            val logScrollBehavior =
                TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
            DisposableEffect(logScrollBehavior) {
                onScrollBehaviorChanged(logScrollBehavior)
                onDispose {
                    onScrollBehaviorChanged(null)
                }
            }
            CompositionLocalProvider(LocalTopAppBarScrollBehavior provides logScrollBehavior) {
                LogScreen(
                    logViewModel = logViewModel,
                    listState = logListState
                )
            }
        }
        composable(
            "settings",
            enterTransition = {
                val initialRouteIndex = navRoutes.indexOf(initialState.destination.route)
                val targetRouteIndex = navRoutes.indexOf(targetState.destination.route)
                slideTransitions(initialRouteIndex, targetRouteIndex)
            },
            exitTransition = {
                val initialRouteIndex = navRoutes.indexOf(initialState.destination.route)
                val targetRouteIndex = navRoutes.indexOf(targetState.destination.route)
                slideOutTransitions(initialRouteIndex, targetRouteIndex)
            }
        ) {
            val settingsScrollBehavior =
                TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
            DisposableEffect(settingsScrollBehavior) {
                onScrollBehaviorChanged(settingsScrollBehavior)
                onDispose {
                    onScrollBehaviorChanged(null)
                }
            }
            CompositionLocalProvider(LocalTopAppBarScrollBehavior provides settingsScrollBehavior) {
                SettingsScreen(
                    mainViewModel = mainViewModel,
                    geoipFilePickerLauncher = geoipFilePickerLauncher,
                    geositeFilePickerLauncher = geositeFilePickerLauncher
                )
            }
        }
    }
}
