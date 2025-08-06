package com.simplexray.an.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simplexray.an.R
import com.simplexray.an.ui.theme.ScrollbarDefaults
import com.simplexray.an.viewmodel.AppListViewModel
import com.simplexray.an.viewmodel.AppListViewUiEvent
import com.simplexray.an.viewmodel.Package
import kotlinx.coroutines.flow.collectLatest
import my.nanihadesuka.compose.LazyColumnScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(viewModel: AppListViewModel) {
    val isLoading by remember { derivedStateOf { viewModel.isLoading } }
    val searchQuery by remember { derivedStateOf { viewModel.searchQuery } }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isSearching by remember { mutableStateOf(false) }
    val filteredList by remember { derivedStateOf { viewModel.filteredList } }
    val showSystemApps by remember { derivedStateOf { viewModel.showSystemApps } }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(searchQuery, showSystemApps) {
        lazyListState.scrollToItem(0)
        scrollBehavior.state.contentOffset = 0f
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is AppListViewUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        event.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadAppList()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.select_all)) },
                                onClick = {
                                    viewModel.selectAll()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.inverse_selection)) },
                                onClick = {
                                    viewModel.inverseSelection()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_to_clipboard)) },
                                onClick = {
                                    viewModel.exportAppsToClipboard(context)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_from_clipboard)) },
                                onClick = {
                                    viewModel.importAppsFromClipboard(context)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(R.string.show_system_apps),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Checkbox(
                                            checked = showSystemApps,
                                            onCheckedChange = null
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.onShowSystemAppsChange(!showSystemApps)
                                    showMenu = false
                                })

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(R.string.bypass_selected_apps),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Checkbox(
                                            checked = viewModel.bypassSelectedApps,
                                            onCheckedChange = null
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.onBypassSelectedAppsChange(!viewModel.bypassSelectedApps)
                                    showMenu = false
                                }
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
                LazyColumnScrollbar(
                    state = lazyListState,
                    settings = ScrollbarDefaults.defaultScrollbarSettings()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        state = lazyListState,
                        contentPadding = PaddingValues(
                            top = 16.dp,
                            bottom = paddingValues.calculateBottomPadding().plus(16.dp),
                            start = 8.dp,
                            end = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filteredList, key = { it.packageName }) { pkg ->
                            AppItem(pkg) { isChecked ->
                                viewModel.onPackageSelected(pkg, isChecked)
                            }
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
