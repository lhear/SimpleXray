package com.simplexray.an.viewmodel

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.BuildConfig
import com.simplexray.an.R
import com.simplexray.an.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class AppListViewUiEvent {
    data class ShowSnackbar(val message: String) : AppListViewUiEvent()
}

data class Package(
    var selected: Boolean,
    val label: String,
    val icon: Drawable,
    val packageName: String,
    val isSystemApp: Boolean
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = Preferences(getApplication<Application>().applicationContext)
    private val packageList = mutableStateListOf<Package>()
    var isLoading by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var showSystemApps by mutableStateOf(true)
    var bypassSelectedApps by mutableStateOf(prefs.bypassSelectedApps)
    private var _isChanged by mutableStateOf(false)

    private val _uiEvent = Channel<AppListViewUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val filteredList by derivedStateOf {
        packageList.filter { pkg ->
            (showSystemApps || !pkg.isSystemApp) &&
                    pkg.label.lowercase(Locale.getDefault())
                        .contains(searchQuery.lowercase(Locale.getDefault()))
        }
    }

    init {
        loadAppList()
    }

    private fun loadAppList() {
        isLoading = true
        val pm = getApplication<Application>().packageManager
        val appPackageName = getApplication<Application>().packageName
        val apps = prefs.apps ?: emptySet()
        var loadedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val startTime = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            while ((loadedPackages.isEmpty() || loadedPackages.size == 1)
                && System.currentTimeMillis() - startTime < 10000
            ) {
                loadedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                delay(500)
            }
            val list = loadedPackages.asSequence()
                .mapNotNull {
                    if (it.packageName == appPackageName) return@mapNotNull null
                    val appInfo = it.applicationInfo ?: return@mapNotNull null
                    val hasInternetPermission =
                        it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                    if (!hasInternetPermission) return@mapNotNull null
                    val isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val label = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm) ?: pm.defaultActivityIcon
                    Package(
                        selected = apps.contains(it.packageName),
                        label = label,
                        icon = icon,
                        packageName = it.packageName,
                        isSystemApp = isSystemApp
                    )
                }
                .sortedWith(
                    compareByDescending<Package> { it.selected }
                        .thenBy { it.label }
                )
                .toList()
            withContext(Dispatchers.Main) {
                packageList.clear()
                packageList.addAll(list)
                isLoading = false
            }
        }
    }

    fun onPackageSelected(pkg: Package, isSelected: Boolean) {
        val index = packageList.indexOf(pkg)
        if (index != -1) {
            val updatedPackage = pkg.copy(selected = isSelected)
            packageList[index] = updatedPackage
            _isChanged = true
            saveChanges()
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
    }

    fun onShowSystemAppsChange(show: Boolean) {
        showSystemApps = show
    }

    fun onBypassSelectedAppsChange(bypass: Boolean) {
        bypassSelectedApps = bypass
        prefs.bypassSelectedApps = bypass
    }

    fun exportAppsToClipboard(context: Context) {
        val selectedApps = packageList.filter { it.selected }.map { it.packageName }
        val bypassMode = bypassSelectedApps.toString()
        val exportString = (listOf(bypassMode) + selectedApps).joinToString("\n")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("App List", exportString)
        clipboard.setPrimaryClip(clip)
        _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.export_success)))
    }

    fun importAppsFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip

        if (clipData != null && clipData.itemCount > 0) {
            val importString = clipData.getItemAt(0).text?.toString()
            if (!importString.isNullOrBlank()) {
                val lines = importString.split("\n")
                lines.indexOf(BuildConfig.APPLICATION_ID).let {
                    if (it > -1) lines.drop(it)
                }
                if (lines.isNotEmpty()) {
                    val newBypassMode = lines[0].toBooleanStrictOrNull()
                    if (newBypassMode != null) {
                        onBypassSelectedAppsChange(newBypassMode)
                        val importedPackageNames = lines.drop(1).toSet()
                        val updatedPackageList = packageList.map { pkg ->
                            pkg.copy(selected = importedPackageNames.contains(pkg.packageName))
                        }
                        packageList.clear()
                        packageList.addAll(updatedPackageList)
                        _isChanged = true
                        saveChanges()
                        _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_success)))
                    } else {
                        _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_invalid_format)))
                    }
                } else {
                    _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_invalid_format)))
                }
            } else {
                _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_failed)))
            }
        } else {
            _uiEvent.trySend(AppListViewUiEvent.ShowSnackbar(context.getString(R.string.import_failed)))
        }
    }

    private fun saveChanges() {
        if (_isChanged) {
            viewModelScope.launch(Dispatchers.IO) {
                val apps: MutableSet<String> = HashSet()
                packageList.forEach { pkg ->
                    if (pkg.selected) apps.add(pkg.packageName)
                }
                prefs.apps = apps
                _isChanged = false
            }
        }
    }

    fun selectAll() {
        for (i in packageList.indices) {
            val pkg = packageList[i]
            if (!pkg.selected) {
                packageList[i] = pkg.copy(selected = true)
                _isChanged = true
            }
        }
        saveChanges()
    }

    fun inverseSelection() {
        for (i in packageList.indices) {
            val pkg = packageList[i]
            packageList[i] = pkg.copy(selected = !pkg.selected)
            _isChanged = true
        }
        saveChanges()
    }
}
