package com.simplexray.an.activity

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.simplexray.an.common.ThemeMode
import com.simplexray.an.ui.navigation.AppNavHost
import com.simplexray.an.viewmodel.MainViewModel
import com.simplexray.an.viewmodel.MainViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        mainViewModel.reloadView = { initView() }
        initView()

        processShareIntent(intent)
        Log.d(TAG, "MainActivity onCreate called.")
    }

    private fun initView() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val currentNightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = when (mainViewModel.prefs.theme) {
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
            ThemeMode.Auto -> currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }
        insetsController.isAppearanceLightStatusBars = !isDark

        setContent {
            val context = LocalContext.current
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                dynamicColor && isDark -> dynamicDarkColorScheme(context)
                dynamicColor && !isDark -> dynamicLightColorScheme(context)
                isDark -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost(mainViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let {
            processShareIntent(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity Coroutine Scope cancelled.")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = when (mainViewModel.prefs.theme) {
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
            ThemeMode.Auto -> currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        Log.d(TAG, "MainActivity onConfigurationChanged called.")
    }

    private fun processShareIntent(intent: Intent) {
        val currentIntentHash = intent.hashCode()
        if (lastProcessedIntentHash == currentIntentHash) return
        lastProcessedIntentHash = currentIntentHash

        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.clipData?.getItemAt(0)?.uri?.let { uri ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val text =
                                contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                            text?.let { mainViewModel.handleSharedContent(it) }
                        } catch (e: Exception) {
                            Log.e("Share", "Error reading shared file", e)
                        }
                    }
                }
            }

            Intent.ACTION_VIEW -> {
                intent.data?.toString()?.let { uriString ->
                    if (uriString.startsWith("simplexray://")) {
                        lifecycleScope.launch {
                            mainViewModel.handleSharedContent(uriString)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "MainActivity"
        private var lastProcessedIntentHash: Int = 0
    }
}