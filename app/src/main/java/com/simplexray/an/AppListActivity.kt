package com.simplexray.an

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(viewModel: AppListViewModel) {
    val packageList by remember { derivedStateOf { viewModel.packageList } }
    val isLoading by remember { derivedStateOf { viewModel.isLoading } }
    val searchQuery by remember { derivedStateOf { viewModel.searchQuery } }
    val context = LocalContext.current
    var isSearching by remember { mutableStateOf(false) }
    val filteredList by remember(packageList, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                packageList
            } else {
                packageList.filter {
                    it.label.lowercase(Locale.getDefault())
                        .contains(searchQuery.lowercase(Locale.getDefault()))
                }
            }
        }
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(searchQuery) {
        lazyListState.scrollToItem(0)
        scrollBehavior.state.contentOffset = 0f
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.search)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearching = false
                            viewModel.onSearchQueryChange("")
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.close_search)
                            )
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear_search)
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            } else {
                TopAppBar(
                    title = {
                        Text(text = stringResource(R.string.apps_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            (context as? ComponentActivity)?.finish()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                painterResource(id = R.drawable.search),
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = 5.dp,
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredList, key = { it.packageName }) { pkg ->
                        AppItem(pkg) { isChecked ->
                            viewModel.onPackageSelected(pkg, isChecked)
                        }
                    }
                }
                if (filteredList.isEmpty() && searchQuery.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.apps_not_found),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
fun AppItem(pkg: Package, onCheckedChange: (Boolean) -> Unit) {
    val iconBitmap = remember(pkg.icon) {
        drawableToBitmap(pkg.icon)?.asImageBitmap()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable { onCheckedChange(!pkg.selected) },
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pkg.selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            iconBitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = stringResource(R.string.app_icon),
                    modifier = Modifier
                        .size(40.dp)
                        .fillMaxHeight(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = pkg.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(16.dp))
            Checkbox(
                checked = pkg.selected,
                onCheckedChange = onCheckedChange,
                enabled = true,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
