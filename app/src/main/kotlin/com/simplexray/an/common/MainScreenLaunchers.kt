package com.simplexray.an.common

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.simplexray.an.TProxyService
import com.simplexray.an.viewmodel.MainViewModel
import kotlinx.coroutines.launch

data class MainScreenLaunchers(
    val createFileLauncher: ActivityResultLauncher<String>,
    val openFileLauncher: ActivityResultLauncher<Array<String>>,
    val vpnPrepareLauncher: ActivityResultLauncher<Intent>,
    val geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    val geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>
)

@Composable
fun rememberMainScreenLaunchers(mainViewModel: MainViewModel): MainScreenLaunchers {
    val scope = rememberCoroutineScope()

    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                mainViewModel.handleBackupFileCreationResult(uri)
            }
        } else {
            Log.w("MainActivity", "Backup file creation cancelled or failed (URI is null).")
            mainViewModel.clearCompressedBackupData()
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                mainViewModel.startRestoreTask(uri)
            }
        } else {
            Log.w("MainActivity", "Restore file selection cancelled or failed (URI is null).")
        }
    }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            mainViewModel.setControlMenuClickable(true)
            mainViewModel.setServiceEnabled(true)
            mainViewModel.startTProxyService(TProxyService.ACTION_CONNECT)
        } else {
            mainViewModel.setControlMenuClickable(true)
        }
    }

    val geoipFilePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    mainViewModel.importRuleFile(uri, "geoip.dat")
                }
            } else {
                Log.d("MainActivity", "Geoip file picking cancelled or failed (URI is null).")
            }
        }

    val geositeFilePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    mainViewModel.importRuleFile(uri, "geosite.dat")
                }
            } else {
                Log.d("MainActivity", "Geosite file picking cancelled or failed (URI is null).")
            }
        }

    return MainScreenLaunchers(
        createFileLauncher = createFileLauncher,
        openFileLauncher = openFileLauncher,
        vpnPrepareLauncher = vpnPrepareLauncher,
        geoipFilePickerLauncher = geoipFilePickerLauncher,
        geositeFilePickerLauncher = geositeFilePickerLauncher
    )
}
