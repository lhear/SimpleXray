package com.simplexray.an.activity

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.ui.screens.ConfigEditScreen
import com.simplexray.an.viewmodel.ConfigEditUiEvent
import com.simplexray.an.viewmodel.ConfigEditViewModel
import com.simplexray.an.viewmodel.ConfigEditViewModelFactory
import com.simplexray.an.viewmodel.MainViewModel
import com.simplexray.an.viewmodel.MainViewModelFactory
import kotlinx.coroutines.flow.collectLatest

class ConfigEditActivity : ComponentActivity() {
    private lateinit var initialFilePath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)

        initialFilePath = intent.getStringExtra("filePath").toString()

        setContent {
            val context = LocalContext.current
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                dynamicColor && isDark -> dynamicDarkColorScheme(context)
                dynamicColor && !isDark -> dynamicLightColorScheme(context)
                isDark -> darkColorScheme()
                else -> lightColorScheme()
            }

            val mainViewModel = viewModel<MainViewModel>(
                factory = MainViewModelFactory(application)
            )

            val configEditViewModel: ConfigEditViewModel = viewModel(
                factory = ConfigEditViewModelFactory(
                    application,
                    initialFilePath,
                    mainViewModel.prefs
                )
            )

            val snackbarHostState = remember { SnackbarHostState() }

            val filename by configEditViewModel.filename.collectAsStateWithLifecycle()
            val configContent by configEditViewModel.configContent.collectAsStateWithLifecycle()
            val filenameErrorResId by configEditViewModel.filenameErrorResId.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                configEditViewModel.uiEvent.collectLatest { event ->
                    when (event) {
                        is ConfigEditUiEvent.ShowSnackbar -> {
                            snackbarHostState.showSnackbar(
                                context.getString(event.messageResId),
                                duration = SnackbarDuration.Short
                            )
                        }

                        is ConfigEditUiEvent.ShareContent -> {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, event.content)
                            }
                            startActivity(Intent.createChooser(shareIntent, null))
                        }

                        is ConfigEditUiEvent.FinishActivity -> {
                            finish()
                        }
                    }
                }
            }

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                ConfigEditScreen(
                    onSave = { configEditViewModel.saveConfigFile() },
                    onShare = { configEditViewModel.shareConfigFile() },
                    filename = filename,
                    configContent = configContent,
                    onBackClick = { configEditViewModel.onBackClick() },
                    filenameErrorResId = filenameErrorResId,
                    onConfigContentChange = { configEditViewModel.onConfigContentChange(it) },
                    onFilenameChange = { configEditViewModel.onFilenameChange(it) },
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }

    private fun setStatusBarFontColorByTheme(isDark: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)
    }
}