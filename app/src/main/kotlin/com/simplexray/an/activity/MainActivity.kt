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
import com.simplexray.an.logging.LoggerRepository
import com.simplexray.an.logging.LogEvent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log activity lifecycle
        LoggerRepository.addInstrumentation(
            type = LogEvent.InstrumentationType.ACTIVITY_LIFECYCLE,
            message = "MainActivity.onCreate()",
            data = mapOf(
                "savedInstanceState" to (savedInstanceState != null),
                "pid" to android.os.Process.myPid()
            )
        )
        
        // Clear Xray Settings server information (from removed XraySettingsScreen)
        Preferences(applicationContext).clearXrayServerInfo()
        // Schedule periodic pruning of time-series data
        try {
            TrafficPruneWorker.schedule(applicationContext)
        } catch (e: Exception) {
            AppLogger.w("Failed to schedule TrafficPruneWorker", e)
        }
        // Initialize traffic monitoring background worker (respects user opt-in preference)
        try {
            TrafficWorkScheduler.schedule(this)
        } catch (e: Exception) {
            AppLogger.w("Failed to schedule TrafficWorkScheduler", e)
        }
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
        
        // Log activity lifecycle
        LoggerRepository.addInstrumentation(
            type = LogEvent.InstrumentationType.ACTIVITY_LIFECYCLE,
            message = "MainActivity.onResume()",
            data = mapOf("pid" to android.os.Process.myPid())
        )
        
        // Check service state when app comes to foreground
        // This ensures UI shows correct connection state even if app was killed
        checkAndUpdateServiceState()
    }
    
    override fun onPause() {
        super.onPause()
        LoggerRepository.addInstrumentation(
            type = LogEvent.InstrumentationType.ACTIVITY_LIFECYCLE,
            message = "MainActivity.onPause()",
            data = mapOf("pid" to android.os.Process.myPid())
        )
    }
    
    override fun onStop() {
        super.onStop()
        LoggerRepository.addInstrumentation(
            type = LogEvent.InstrumentationType.ACTIVITY_LIFECYCLE,
            message = "MainActivity.onStop()",
            data = mapOf("pid" to android.os.Process.myPid())
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LoggerRepository.addInstrumentation(
            type = LogEvent.InstrumentationType.ACTIVITY_LIFECYCLE,
            message = "MainActivity.onDestroy()",
            data = mapOf("pid" to android.os.Process.myPid())
        )
    }
    
    /**
     * Check if TProxyService is running.
     * This is called when app resumes to log service state.
     * The actual UI update will be handled by MainScreen lifecycle observer.
     * TODO: Consider caching service state to reduce repeated checks
     * TODO: Add retry mechanism for transient service state detection failures
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
