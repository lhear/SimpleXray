package com.simplexray.an.activity

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.simplexray.an.common.ThemeMode
import com.simplexray.an.ui.screens.AppListScreen
import com.simplexray.an.viewmodel.AppListViewModel
import com.simplexray.an.viewmodel.AppListViewModelFactory

class AppListActivity : ComponentActivity() {

    private lateinit var appListViewModel: AppListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        initView()
    }

    private fun initView() {
        val appListViewModel: AppListViewModel by viewModels { AppListViewModelFactory(application) }

        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark =
            currentNightMode == Configuration.UI_MODE_NIGHT_YES
                    || appListViewModel.prefs.theme == ThemeMode.Dark
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !isDark

        setContent {
            val context = LocalContext.current
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                dynamicColor && isDark -> dynamicDarkColorScheme(context)
                dynamicColor && !isDark -> dynamicLightColorScheme(context)
                isDark -> darkColorScheme()
                else -> lightColorScheme()
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
}