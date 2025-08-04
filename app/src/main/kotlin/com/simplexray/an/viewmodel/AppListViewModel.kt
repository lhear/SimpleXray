package com.simplexray.an.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.an.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Package(
    var selected: Boolean,
    val label: String,
    val icon: Drawable,
    val packageName: String,
    val isSystemApp: Boolean
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Preferences(getApplication<Application>().applicationContext)
    val packageList = mutableStateListOf<Package>()
    var isLoading by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var showSystemApps by mutableStateOf(true)
    var bypassSelectedApps by mutableStateOf(prefs.bypassSelectedApps)
    private var _isChanged by mutableStateOf(false)

    init {
        loadAppList()
    }

    fun loadAppList() {
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val appPackageName = getApplication<Application>().packageName
            val apps = prefs.apps ?: emptySet()
            val loadedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .asSequence()
                .mapNotNull { info ->
                    if (info.packageName == appPackageName) return@mapNotNull null
                    val appInfo = info.applicationInfo ?: return@mapNotNull null
                    val hasInternetPermission =
                        info.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                    if (!hasInternetPermission) return@mapNotNull null
                    val isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    if (!showSystemApps && isSystemApp) return@mapNotNull null
                    val label = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm) ?: pm.defaultActivityIcon
                    Package(
                        selected = apps.contains(info.packageName),
                        label = label,
                        icon = icon,
                        packageName = info.packageName,
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
                packageList.addAll(loadedPackages)
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
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
    }

    fun onShowSystemAppsChange(show: Boolean) {
        showSystemApps = show
        loadAppList()
    }

    fun onBypassSelectedAppsChange(bypass: Boolean) {
        bypassSelectedApps = bypass
        prefs.bypassSelectedApps = bypass
    }

    fun saveChanges() {
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
    }

    fun inverseSelection() {
        for (i in packageList.indices) {
            val pkg = packageList[i]
            packageList[i] = pkg.copy(selected = !pkg.selected)
            _isChanged = true
        }
    }
}

class AppListViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppListViewModel(context.applicationContext as Application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
