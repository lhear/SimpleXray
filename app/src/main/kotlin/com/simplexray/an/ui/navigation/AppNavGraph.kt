package com.simplexray.an.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simplexray.an.common.ROUTE_APP_LIST
import com.simplexray.an.common.ROUTE_CONFIG_EDIT
import com.simplexray.an.common.ROUTE_MAIN
import com.simplexray.an.common.ROUTE_PERFORMANCE
import com.simplexray.an.common.ROUTE_GAMING
import com.simplexray.an.common.ROUTE_STREAMING
import com.simplexray.an.common.ROUTE_ADVANCED_ROUTING
import com.simplexray.an.common.ROUTE_NETWORK_VISUALIZATION
import com.simplexray.an.ui.screens.AppListScreen
import com.simplexray.an.ui.screens.ConfigEditScreen
import com.simplexray.an.ui.screens.MainScreen
import com.simplexray.an.ui.performance.PerformanceScreenWithViewModel
import com.simplexray.an.ui.gaming.GamingScreen
import com.simplexray.an.ui.streaming.StreamingScreen
import com.simplexray.an.ui.routing.AdvancedRoutingScreen
import com.simplexray.an.ui.visualization.NetworkVisualizationScreen
import com.simplexray.an.viewmodel.MainViewModel
import com.simplexray.an.ui.components.UpdateDialog
import com.simplexray.an.BuildConfig
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppNavHost(
    mainViewModel: MainViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_MAIN
    ) {
        composable(
            route = ROUTE_MAIN,
            enterTransition = { EnterTransition.None },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { ExitTransition.None }
        ) {
            MainScreen(
                mainViewModel = mainViewModel,
                appNavController = navController,
                snackbarHostState = remember { SnackbarHostState() }
            )
        }

        composable(
            route = ROUTE_APP_LIST,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            AppListScreen(
                viewModel = mainViewModel.appListViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_CONFIG_EDIT,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            ConfigEditScreen(
                onBackClick = { navController.popBackStack() },
                snackbarHostState = remember { SnackbarHostState() },
                viewModel = mainViewModel.configEditViewModel
            )
        }

        composable(
            route = ROUTE_PERFORMANCE,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            PerformanceScreenWithViewModel(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_GAMING,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            GamingScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_STREAMING,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            StreamingScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_ADVANCED_ROUTING,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            AdvancedRoutingScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_NETWORK_VISUALIZATION,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            NetworkVisualizationScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
    
    // Update dialog
    val newVersionTag by mainViewModel.newVersionAvailable.collectAsStateWithLifecycle()
    val isDownloadingUpdate by mainViewModel.isDownloadingUpdate.collectAsStateWithLifecycle()
    val downloadProgress by mainViewModel.downloadProgress.collectAsStateWithLifecycle()
    
    newVersionTag?.let { version ->
        UpdateDialog(
            currentVersion = BuildConfig.VERSION_NAME,
            newVersion = version,
            isDownloading = isDownloadingUpdate,
            downloadProgress = downloadProgress,
            onDownload = { mainViewModel.downloadNewVersion(version) },
            onDismiss = { 
                if (!isDownloadingUpdate) {
                    mainViewModel.clearNewVersionAvailable()
                }
            }
        )
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition() =
    scaleIn(
        initialScale = 0.8f,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(400))

private fun exitTransition() =
    fadeOut(animationSpec = tween(300)) + scaleOut(
        targetScale = 0.9f,
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    )

private fun popEnterTransition() = fadeIn(animationSpec = tween(400)) + scaleIn(
    initialScale = 0.9f,
    animationSpec = tween(400, easing = FastOutSlowInEasing)
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitTransition() =
    scaleOut(
        targetScale = 0.8f,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(400))
