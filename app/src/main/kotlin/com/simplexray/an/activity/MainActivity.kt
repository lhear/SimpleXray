package com.simplexray.an.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.common.AppLogger
import com.simplexray.an.common.ServiceStateChecker
import com.simplexray.an.db.TrafficPruneWorker
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.service.TProxyService
import com.simplexray.an.ui.navigation.AppNavHost
import com.simplexray.an.viewmodel.MainViewModel
import com.simplexray.an.viewmodel.MainViewModelFactory
import com.simplexray.an.worker.TrafficWorkScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Clear Xray Settings server information (from removed XraySettingsScreen)
        Preferences(applicationContext).clearXrayServerInfo()
        // Schedule periodic pruning of time-series data
        TrafficPruneWorker.schedule(applicationContext)
        // Initialize traffic monitoring background worker (respects user opt-in preference)
        TrafficWorkScheduler.schedule(this)
        setContent {
            MaterialTheme {
                Surface {
                    App()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check service state when app comes to foreground
        // This ensures UI shows correct connection state even if app was killed
        checkAndUpdateServiceState()
    }
    
    /**
     * Check if TProxyService is running.
     * This is called when app resumes to log service state.
     * The actual UI update will be handled by MainScreen lifecycle observer.
     */
    private fun checkAndUpdateServiceState() {
        try {
            val isRunning = ServiceStateChecker.isServiceRunning(applicationContext, TProxyService::class.java)
            val isRunningStatic = TProxyService.isRunning()
            
            AppLogger.d("MainActivity: Service state check on resume - ServiceStateChecker: $isRunning, TProxyService.isRunning(): $isRunningStatic")
            
            // If service is running, send a status broadcast to ensure UI is updated
            // This helps when app was killed and restarted
            if (isRunningStatic) {
                AppLogger.d("MainActivity: Service is running, UI will be updated by MainScreen lifecycle observer")
                // The MainScreen lifecycle observer will handle the UI update
            }
        } catch (e: Exception) {
            AppLogger.w("MainActivity: Error checking service state", e)
        }
    }
}

@Composable
private fun App() {
    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application)
    )
    AppNavHost(mainViewModel = mainViewModel)
}
