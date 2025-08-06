package com.simplexray.an.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.an.R
import com.simplexray.an.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel,
    geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val context = LocalContext.current
    val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()
    val geoipProgress by mainViewModel.geoipDownloadProgress.collectAsStateWithLifecycle()
    val geositeProgress by mainViewModel.geositeDownloadProgress.collectAsStateWithLifecycle()
    val isCheckingForUpdates by mainViewModel.isCheckingForUpdates.collectAsStateWithLifecycle()
    val newVersionTag by mainViewModel.newVersionAvailable.collectAsStateWithLifecycle()

    val vpnDisabled = settingsState.switches.disableVpn

    var showGeoipDeleteDialog by remember { mutableStateOf(false) }
    var showGeositeDeleteDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var editingRuleFile by remember { mutableStateOf<String?>(null) }
    var ruleFileUrl by remember { mutableStateOf("") }

    if (editingRuleFile != null) {
        ModalBottomSheet(
            onDismissRequest = { editingRuleFile = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = ruleFileUrl,
                    onValueChange = { ruleFileUrl = it },
                    label = { Text("URL") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp),
                    trailingIcon = {
                        val clipboardManager = LocalClipboard.current
                        IconButton(onClick = {
                            scope.launch {
                                clipboardManager.getClipEntry()?.clipData?.getItemAt(0)?.text
                                    .let {
                                        ruleFileUrl = it.toString()
                                    }
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.paste),
                                contentDescription = "Paste"
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = {
                    ruleFileUrl =
                        if (editingRuleFile == "geoip.dat") context.getString(R.string.geoip_url)
                        else context.getString(R.string.geosite_url)
                }) {
                    Text(stringResource(id = R.string.restore_default_url))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                editingRuleFile = null
                            }
                        }
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        mainViewModel.downloadRuleFile(ruleFileUrl, editingRuleFile!!)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                editingRuleFile = null
                            }
                        }
                    }) {
                        Text(stringResource(R.string.update))
                    }
                }
            }
        }
    }

    if (showGeoipDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showGeoipDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_rule_file_title)) },
            text = { Text(stringResource(R.string.delete_rule_file_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        mainViewModel.restoreDefaultGeoip { }
                        showGeoipDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeoipDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showGeositeDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showGeositeDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_rule_file_title)) },
            text = { Text(stringResource(R.string.delete_rule_file_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        mainViewModel.restoreDefaultGeosite { }
                        showGeositeDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeositeDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (newVersionTag != null) {
        AlertDialog(
            onDismissRequest = { mainViewModel.clearNewVersionAvailable() },
            title = { Text(stringResource(R.string.new_version_available_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.new_version_available_message,
                        newVersionTag!!
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { mainViewModel.downloadNewVersion(newVersionTag!!) }) {
                    Text(stringResource(R.string.download))
                }
            },
            dismissButton = {
                TextButton(onClick = { mainViewModel.clearNewVersionAvailable() }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(10.dp)
    ) {
        PreferenceCategoryTitle(stringResource(R.string.general))

        ListItem(
            headlineContent = { Text(stringResource(R.string.use_template_title)) },
            supportingContent = { Text(stringResource(R.string.use_template_summary)) },
            trailingContent = {
                Switch(
                    checked = settingsState.switches.useTemplateEnabled,
                    onCheckedChange = {
                        mainViewModel.setUseTemplateEnabled(it)
                    }
                )
            }
        )

        PreferenceCategoryTitle(stringResource(R.string.vpn_interface))

        ListItem(
            modifier = Modifier.clickable {
                mainViewModel.navigateToAppList()
            },
            headlineContent = { Text(stringResource(R.string.apps_title)) },
            supportingContent = { Text(stringResource(R.string.apps_summary)) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.disable_vpn_title)) },
            supportingContent = { Text(stringResource(R.string.disable_vpn_summary)) },
            trailingContent = {
                Switch(
                    checked = settingsState.switches.disableVpn,
                    onCheckedChange = {
                        mainViewModel.setDisableVpnEnabled(it)
                    }
                )
            }
        )

        EditableListItemWithBottomSheet(
            headline = stringResource(R.string.socks_port),
            currentValue = settingsState.socksPort.value,
            onValueConfirmed = { newValue -> mainViewModel.updateSocksPort(newValue) },
            label = stringResource(R.string.socks_port),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !settingsState.socksPort.isValid,
            errorMessage = settingsState.socksPort.error,
            enabled = !vpnDisabled,
            sheetState = sheetState,
            scope = scope
        )

        EditableListItemWithBottomSheet(
            headline = stringResource(R.string.dns_ipv4),
            currentValue = settingsState.dnsIpv4.value,
            onValueConfirmed = { newValue -> mainViewModel.updateDnsIpv4(newValue) },
            label = stringResource(R.string.dns_ipv4),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !settingsState.dnsIpv4.isValid,
            errorMessage = settingsState.dnsIpv4.error,
            enabled = !vpnDisabled,
            sheetState = sheetState,
            scope = scope
        )

        EditableListItemWithBottomSheet(
            headline = stringResource(R.string.dns_ipv6),
            currentValue = settingsState.dnsIpv6.value,
            onValueConfirmed = { newValue -> mainViewModel.updateDnsIpv6(newValue) },
            label = stringResource(R.string.dns_ipv6),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            isError = !settingsState.dnsIpv6.isValid,
            errorMessage = settingsState.dnsIpv6.error,
            enabled = settingsState.switches.ipv6Enabled && !vpnDisabled,
            sheetState = sheetState,
            scope = scope
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.ipv6)) },
            supportingContent = { Text(stringResource(R.string.ipv6_enabled)) },
            trailingContent = {
                Switch(
                    checked = settingsState.switches.ipv6Enabled,
                    onCheckedChange = {
                        mainViewModel.setIpv6Enabled(it)
                    },
                    enabled = !vpnDisabled
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.http_proxy_title)) },
            supportingContent = { Text(stringResource(R.string.http_proxy_summary)) },
            trailingContent = {
                Switch(
                    checked = settingsState.switches.httpProxyEnabled,
                    onCheckedChange = {
                        mainViewModel.setHttpProxyEnabled(it)
                    },
                    enabled = !vpnDisabled
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.bypass_lan_title)) },
            supportingContent = { Text(stringResource(R.string.bypass_lan_summary)) },
            trailingContent = {
                Switch(
                    checked = settingsState.switches.bypassLanEnabled,
                    onCheckedChange = {
                        mainViewModel.setBypassLanEnabled(it)
                    },
                    enabled = !vpnDisabled
                )
            }
        )

        PreferenceCategoryTitle(stringResource(R.string.rule_files_category_title))

        ListItem(
            headlineContent = { Text("geoip.dat") },
            supportingContent = { Text(geoipProgress ?: settingsState.info.geoipSummary) },
            trailingContent = {
                Row {
                    if (geoipProgress != null) {
                        IconButton(onClick = { mainViewModel.cancelDownload("geoip.dat") }) {
                            Icon(
                                painter = painterResource(id = R.drawable.cancel),
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            ruleFileUrl = settingsState.info.geoipUrl
                            editingRuleFile = "geoip.dat"
                            scope.launch { sheetState.show() }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.cloud_download),
                                contentDescription = stringResource(R.string.rule_file_update_url)
                            )
                        }
                        if (!settingsState.files.isGeoipCustom) {
                            IconButton(onClick = { geoipFilePickerLauncher.launch(arrayOf("*/*")) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.place_item),
                                    contentDescription = stringResource(R.string.import_file)
                                )
                            }
                        } else {
                            IconButton(onClick = { showGeoipDeleteDialog = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.delete),
                                    contentDescription = stringResource(R.string.reset_file)
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier
        )

        ListItem(
            headlineContent = { Text("geosite.dat") },
            supportingContent = { Text(geositeProgress ?: settingsState.info.geositeSummary) },
            trailingContent = {
                Row {
                    if (geositeProgress != null) {
                        IconButton(onClick = { mainViewModel.cancelDownload("geosite.dat") }) {
                            Icon(
                                painter = painterResource(id = R.drawable.cancel),
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            ruleFileUrl = settingsState.info.geositeUrl
                            editingRuleFile = "geosite.dat"
                            scope.launch { sheetState.show() }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.cloud_download),
                                contentDescription = stringResource(R.string.rule_file_update_url)
                            )
                        }
                        if (!settingsState.files.isGeositeCustom) {
                            IconButton(onClick = { geositeFilePickerLauncher.launch(arrayOf("*/*")) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.place_item),
                                    contentDescription = stringResource(R.string.import_file)
                                )
                            }
                        } else {
                            IconButton(onClick = { showGeositeDeleteDialog = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.delete),
                                    contentDescription = stringResource(R.string.reset_file)
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier
        )

        PreferenceCategoryTitle(stringResource(R.string.connectivity_test))

        EditableListItemWithBottomSheet(
            headline = stringResource(R.string.connectivity_test_target),
            currentValue = settingsState.connectivityTestTarget.value,
            onValueConfirmed = { newValue -> mainViewModel.updateConnectivityTestTarget(newValue) },
            label = stringResource(R.string.connectivity_test_target),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            isError = !settingsState.connectivityTestTarget.isValid,
            errorMessage = settingsState.connectivityTestTarget.error,
            sheetState = sheetState,
            scope = scope
        )

        EditableListItemWithBottomSheet(
            headline = stringResource(R.string.connectivity_test_timeout),
            currentValue = settingsState.connectivityTestTimeout.value,
            onValueConfirmed = { newValue -> mainViewModel.updateConnectivityTestTimeout(newValue) },
            label = stringResource(R.string.connectivity_test_timeout),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !settingsState.connectivityTestTimeout.isValid,
            errorMessage = settingsState.connectivityTestTimeout.error,
            sheetState = sheetState,
            scope = scope
        )

        PreferenceCategoryTitle(stringResource(R.string.about))

        ListItem(
            headlineContent = { Text(stringResource(R.string.version)) },
            supportingContent = { Text(settingsState.info.appVersion) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            mainViewModel.checkForUpdates()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            disabledContainerColor = Color.Transparent
                        ),
                        enabled = !isCheckingForUpdates
                    ) {
                        if (isCheckingForUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.check_for_updates))
                        }
                    }
                }
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.kernel)) },
            supportingContent = { Text(settingsState.info.kernelVersion) }
        )

        ListItem(
            modifier = Modifier.clickable {
                val browserIntent =
                    Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.source_url)))
                context.startActivity(browserIntent)
            },
            headlineContent = { Text(stringResource(R.string.source)) },
            supportingContent = { Text(stringResource(R.string.open_source)) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableListItemWithBottomSheet(
    headline: String,
    currentValue: String,
    onValueConfirmed: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    sheetState: SheetState,
    scope: CoroutineScope
) {
    var showSheet by remember { mutableStateOf(false) }
    var tempValue by remember { mutableStateOf(currentValue) }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    label = { Text(label) },
                    keyboardOptions = keyboardOptions,
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(text = errorMessage ?: "")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showSheet = false
                            }
                        }
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onValueConfirmed(tempValue)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showSheet = false
                            }
                        }
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }

    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(currentValue) },
        modifier = Modifier.clickable(enabled = enabled) {
            tempValue = currentValue
            showSheet = true
        },
        trailingContent = {
            if (isError) {
                Icon(
                    painter = painterResource(id = R.drawable.cancel),
                    contentDescription = errorMessage,
                    tint = MaterialTheme.colorScheme.error
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        }
    )
}

@Composable
fun PreferenceCategoryTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
    )
}
