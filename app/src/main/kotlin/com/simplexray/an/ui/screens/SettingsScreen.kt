package com.simplexray.an.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.an.R
import com.simplexray.an.viewmodel.MainViewModel

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

    val vpnDisabled = settingsState.switches.disableVpn

    var showGeoipDialog by remember { mutableStateOf(false) }
    var geoipUrl by remember(settingsState.info.geoipUrl) { mutableStateOf(settingsState.info.geoipUrl) }
    var showGeositeDialog by remember { mutableStateOf(false) }
    var geositeUrl by remember(settingsState.info.geositeUrl) { mutableStateOf(settingsState.info.geositeUrl) }

    if (showGeoipDialog) {
        AlertDialog(
            onDismissRequest = { showGeoipDialog = false },
            title = { Text(stringResource(R.string.rule_file_update_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = geoipUrl,
                    onValueChange = { geoipUrl = it },
                    label = { Text("URL") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mainViewModel.downloadRuleFile(geoipUrl, "geoip.dat")
                        showGeoipDialog = false
                    }
                ) {
                    Text(stringResource(R.string.update))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeoipDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showGeositeDialog) {
        AlertDialog(
            onDismissRequest = { showGeositeDialog = false },
            title = { Text(stringResource(R.string.rule_file_update_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = geositeUrl,
                    onValueChange = { geositeUrl = it },
                    label = { Text("URL") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mainViewModel.downloadRuleFile(geositeUrl, "geosite.dat")
                        showGeositeDialog = false
                    }
                ) {
                    Text(stringResource(R.string.update))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeositeDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
            },
            modifier = Modifier.clickable {
                mainViewModel.setUseTemplateEnabled(!settingsState.switches.useTemplateEnabled)
            }
        )

        PreferenceCategoryTitle(stringResource(R.string.vpn_interface))

        ListItem(
            modifier = Modifier.clickable {
                mainViewModel.navigateToAppList()
            },
            headlineContent = { Text(stringResource(R.string.apps_title)) },
            supportingContent = { Text(stringResource(R.string.apps_summary)) },
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
            },
            modifier = Modifier.clickable {
                mainViewModel.setDisableVpnEnabled(!settingsState.switches.disableVpn)
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = settingsState.socksPort.value,
            onValueChange = { newValue ->
                mainViewModel.updateSocksPort(newValue)
            },
            label = { Text(stringResource(R.string.socks_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !settingsState.socksPort.isValid,
            supportingText = {
                if (!settingsState.socksPort.isValid) {
                    Text(text = settingsState.socksPort.error ?: "")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            enabled = !vpnDisabled
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = settingsState.dnsIpv4.value,
            onValueChange = { newValue ->
                mainViewModel.updateDnsIpv4(newValue)
            },
            label = { Text(stringResource(R.string.dns_ipv4)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !settingsState.dnsIpv4.isValid,
            supportingText = {
                if (!settingsState.dnsIpv4.isValid) {
                    Text(text = settingsState.dnsIpv4.error ?: "")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            enabled = !vpnDisabled
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = settingsState.dnsIpv6.value,
            onValueChange = { newValue ->
                mainViewModel.updateDnsIpv6(newValue)
            },
            label = { Text(stringResource(R.string.dns_ipv6)) },
            enabled = settingsState.switches.ipv6Enabled && !vpnDisabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            isError = !settingsState.dnsIpv6.isValid,
            supportingText = {
                if (!settingsState.dnsIpv6.isValid) {
                    Text(text = settingsState.dnsIpv6.error ?: "")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

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
            },
            modifier = Modifier.clickable(enabled = !vpnDisabled) {
                mainViewModel.setIpv6Enabled(!settingsState.switches.ipv6Enabled)
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
            },
            modifier = Modifier.clickable(enabled = !vpnDisabled) {
                mainViewModel.setHttpProxyEnabled(!settingsState.switches.httpProxyEnabled)
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
            },
            modifier = Modifier.clickable(enabled = !vpnDisabled) {
                mainViewModel.setBypassLanEnabled(!settingsState.switches.bypassLanEnabled)
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
                        IconButton(onClick = { showGeoipDialog = true }) {
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
                            IconButton(onClick = { mainViewModel.restoreDefaultGeoip { } }) {
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
                        IconButton(onClick = { showGeositeDialog = true }) {
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
                            IconButton(onClick = { mainViewModel.restoreDefaultGeosite { } }) {
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

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = settingsState.connectivityTestTarget.value,
            onValueChange = { newValue ->
                mainViewModel.updateConnectivityTestTarget(newValue)
            },
            label = { Text(stringResource(R.string.connectivity_test_target)) },
            isError = !settingsState.connectivityTestTarget.isValid,
            supportingText = {
                if (!settingsState.connectivityTestTarget.isValid) {
                    Text(text = settingsState.connectivityTestTarget.error ?: "")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = settingsState.connectivityTestTimeout.value,
            onValueChange = { newValue ->
                mainViewModel.updateConnectivityTestTimeout(newValue)
            },
            label = { Text(stringResource(R.string.connectivity_test_timeout)) },
            isError = !settingsState.connectivityTestTimeout.isValid,
            supportingText = {
                if (!settingsState.connectivityTestTimeout.isValid) {
                    Text(text = settingsState.connectivityTestTimeout.error ?: "")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
        )

        PreferenceCategoryTitle(stringResource(R.string.about))

        ListItem(
            headlineContent = { Text(stringResource(R.string.version)) },
            supportingContent = { Text(settingsState.info.appVersion) }
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
            supportingContent = { Text(stringResource(R.string.open_source)) }
        )
    }
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
