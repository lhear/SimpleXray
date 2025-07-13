package com.simplexray.an.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    val showClearFilesDialog = remember { mutableStateOf(false) }
    val vpnDisabled = settingsState.switches.disableVpn

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(10.dp)
    ) {
        ListItem(
            modifier = Modifier.clickable {
                mainViewModel.navigateToAppList()
            },
            headlineContent = { Text(stringResource(R.string.apps_title)) },
            supportingContent = { Text(stringResource(R.string.apps_summary)) },
        )
        HorizontalDivider()

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
        HorizontalDivider()

        PreferenceCategoryTitle(stringResource(R.string.vpn_settings))

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
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth()
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
        HorizontalDivider()

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
        HorizontalDivider()

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
        HorizontalDivider()

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
        HorizontalDivider()

        PreferenceCategoryTitle(stringResource(R.string.rule_files_category_title))

        ListItem(
            modifier = Modifier.clickable { geoipFilePickerLauncher.launch(arrayOf("*/*")) },
            headlineContent = { Text("geoip.dat") },
            supportingContent = { Text(settingsState.info.geoipSummary) }
        )
        HorizontalDivider()

        ListItem(
            modifier = Modifier.clickable { geositeFilePickerLauncher.launch(arrayOf("*/*")) },
            headlineContent = { Text("geosite.dat") },
            supportingContent = { Text(settingsState.info.geositeSummary) }
        )
        HorizontalDivider()

        ListItem(
            modifier = Modifier.clickable(enabled = settingsState.files.isGeoipCustom || settingsState.files.isGeositeCustom) {
                showClearFilesDialog.value = true
            },
            headlineContent = { Text(stringResource(R.string.rule_file_clear_default_title)) },
            supportingContent = { Text(stringResource(R.string.rule_file_restore_default_summary)) },
        )
        HorizontalDivider()

        PreferenceCategoryTitle(stringResource(R.string.about))

        ListItem(
            headlineContent = { Text(stringResource(R.string.version)) },
            supportingContent = { Text(settingsState.info.appVersion) }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text(stringResource(R.string.kernel)) },
            supportingContent = { Text(settingsState.info.kernelVersion) }
        )
        HorizontalDivider()

        ListItem(
            modifier = Modifier.clickable {
                val browserIntent =
                    Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.source_url)))
                context.startActivity(browserIntent)
            },
            headlineContent = { Text(stringResource(R.string.source)) },
            supportingContent = { Text(stringResource(R.string.open_source)) }
        )

        if (showClearFilesDialog.value) {
            AlertDialog(
                onDismissRequest = { showClearFilesDialog.value = false },
                title = { Text(stringResource(R.string.rule_file_restore_default_summary)) },
                text = { Text(stringResource(R.string.rule_file_restore_default_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        mainViewModel.restoreDefaultRuleFile {}
                        showClearFilesDialog.value = false
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearFilesDialog.value = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
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
