package com.simplexray.an.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
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
    val packageName: String
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Preferences(getApplication<Application>().applicationContext)
    val packageList = mutableStateListOf<Package>()
    var isLoading by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    private var _isChanged by mutableStateOf(false)

    init {
        loadAppList()
    }

    fun loadAppList() {
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val apps = prefs.apps ?: emptySet()
            val pm = getApplication<Application>().packageManager
            val loadedPackages: MutableList<Package> = ArrayList()
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (info in installedPackages) {
                if (info.packageName == getApplication<Application>().packageName) continue
                val hasInternetPermission = info.requestedPermissions?.any {
                    it == Manifest.permission.INTERNET
                } == true
                if (!hasInternetPermission) continue
                val selected = apps.contains(info.packageName)
                val label = info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName
                val icon = info.applicationInfo?.loadIcon(pm)
                    ?: getApplication<Application>().packageManager.defaultActivityIcon
                val pkg = Package(selected, label, icon, info.packageName)
                loadedPackages.add(pkg)
            }
            loadedPackages.sortWith(compareByDescending<Package> { it.selected }.thenBy { it.label })
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
