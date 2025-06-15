package com.simplexray.an.activity

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.simplexray.an.viewmodel.AppListViewModel
import com.simplexray.an.viewmodel.AppListViewModelFactory
import com.simplexray.an.ui.screens.AppListScreen

class AppListActivity : ComponentActivity() {

    private lateinit var appListViewModel: AppListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)

        setContent {
            val context = LocalContext.current
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                dynamicColor && isDark -> dynamicDarkColorScheme(context)
                dynamicColor && !isDark -> dynamicLightColorScheme(context)
                isDark -> darkColorScheme()
                else -> lightColorScheme()
            }

            appListViewModel = remember {
                ViewModelProvider(
                    this,
                    AppListViewModelFactory(applicationContext)
                )[AppListViewModel::class.java]
            }

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                AppListScreen(appListViewModel)
            }

        }
    }

    override fun onResume() {
        super.onResume()
        if (::appListViewModel.isInitialized) {
            appListViewModel.loadAppList()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::appListViewModel.isInitialized) {
            appListViewModel.saveChanges()
        }
    }

    private fun setStatusBarFontColorByTheme(isDark: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
    }
}