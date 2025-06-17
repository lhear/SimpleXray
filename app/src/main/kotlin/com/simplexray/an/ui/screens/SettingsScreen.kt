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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.an.R
import com.simplexray.an.common.LocalTopAppBarScrollBehavior
import com.simplexray.an.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel,
    geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>,
    geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>
) {
    val context = LocalContext.current

    val socksPortValue by mainViewModel.socksPort.collectAsStateWithLifecycle()
    var socksPortText by remember(socksPortValue) { mutableStateOf(socksPortValue.toString()) }

    val dnsIpv4Value by mainViewModel.dnsIpv4.collectAsStateWithLifecycle()
    var dnsIpv4Text by remember(dnsIpv4Value) { mutableStateOf(dnsIpv4Value) }

    val dnsIpv6Value by mainViewModel.dnsIpv6.collectAsStateWithLifecycle()
    var dnsIpv6Text by remember(dnsIpv6Value) { mutableStateOf(dnsIpv6Value) }

    val ipv6Enabled by mainViewModel.ipv6Enabled.collectAsStateWithLifecycle()
    val useTemplateEnabled by mainViewModel.useTemplateEnabled.collectAsStateWithLifecycle()
    val httpProxyEnabled by mainViewModel.httpProxyEnabled.collectAsStateWithLifecycle()
    val bypassLanEnabled by mainViewModel.bypassLanEnabled.collectAsStateWithLifecycle()

    val appVersion by mainViewModel.appVersion.collectAsStateWithLifecycle()
    val kernelVersion by mainViewModel.kernelVersion.collectAsStateWithLifecycle()

    val isGeoipCustom by mainViewModel.customGeoipImported.collectAsStateWithLifecycle()
    val isGeositeCustom by mainViewModel.customGeositeImported.collectAsStateWithLifecycle()

    val geoipSummary by mainViewModel.geoipSummary.collectAsStateWithLifecycle()
    val geositeSummary by mainViewModel.geositeSummary.collectAsStateWithLifecycle()

    val showClearFilesDialog = remember { mutableStateOf(false) }

    val socksPortError by mainViewModel.socksPortError.collectAsStateWithLifecycle()
    val socksPortErrorMessage by mainViewModel.socksPortErrorMessage.collectAsStateWithLifecycle()

    val dnsIpv4Error by mainViewModel.dnsIpv4Error.collectAsStateWithLifecycle()
    val dnsIpv4ErrorMessage by mainViewModel.dnsIpv4ErrorMessage.collectAsStateWithLifecycle()

    val dnsIpv6Error by mainViewModel.dnsIpv6Error.collectAsStateWithLifecycle()
    val dnsIpv6ErrorMessage by mainViewModel.dnsIpv6ErrorMessage.collectAsStateWithLifecycle()

    val scrollBehavior = LocalTopAppBarScrollBehavior.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
            .let {
                if (scrollBehavior != null)
                    it.nestedScroll(scrollBehavior.nestedScrollConnection) else it
            }
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
                    checked = useTemplateEnabled,
                    onCheckedChange = {
                        mainViewModel.setUseTemplateEnabled(it)
                    }
                )
            },
            modifier = Modifier.clickable {
                mainViewModel.setUseTemplateEnabled(!useTemplateEnabled)
            }
        )
        HorizontalDivider()

        PreferenceCategoryTitle(stringResource(R.string.vpn_settings))

        OutlinedTextField(
            value = socksPortText,
            onValueChange = { newValue ->
                socksPortText = newValue
                mainViewModel.updateSocksPort(newValue)
            },
            label = { Text(stringResource(R.string.socks_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = socksPortError,
            supportingText = {
                if (socksPortError) {
                    Text(text = socksPortErrorMessage)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = dnsIpv4Text,
            onValueChange = { newValue ->
                dnsIpv4Text = newValue
                mainViewModel.updateDnsIpv4(newValue)
            },
            label = { Text(stringResource(R.string.dns_ipv4)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = dnsIpv4Error,
            supportingText = {
                if (dnsIpv4Error) {
                    Text(text = dnsIpv4ErrorMessage)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = dnsIpv6Text,
            onValueChange = { newValue ->
                dnsIpv6Text = newValue
                mainViewModel.updateDnsIpv6(newValue)
            },
            label = { Text(stringResource(R.string.dns_ipv6)) },
            enabled = ipv6Enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            isError = dnsIpv6Error,
            supportingText = {
                if (dnsIpv6Error) {
                    Text(text = dnsIpv6ErrorMessage)
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
                    checked = ipv6Enabled,
                    onCheckedChange = {
                        mainViewModel.setIpv6Enabled(it)
                    }
                )
            },
            modifier = Modifier.clickable {
                mainViewModel.setIpv6Enabled(!ipv6Enabled)
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text(stringResource(R.string.http_proxy_title)) },
            supportingContent = { Text(stringResource(R.string.http_proxy_summary)) },
            trailingContent = {
                Switch(
                    checked = httpProxyEnabled,
                    onCheckedChange = {
                        mainViewModel.setHttpProxyEnabled(it)
                    }
                )
            },
            modifier = Modifier.clickable {
                mainViewModel.setHttpProxyEnabled(!httpProxyEnabled)
            }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text(stringResource(R.string.bypass_lan_title)) },
            supportingContent = { Text(stringResource(R.string.bypass_lan_summary)) },
            trailingContent = {
                Switch(
                    checked = bypassLanEnabled,
                    onCheckedChange = {
                        mainViewModel.setBypassLanEnabled(it)
                    }
                )
            },
            modifier = Modifier.clickable {
                mainViewModel.setBypassLanEnabled(!bypassLanEnabled)
            }
        )
        HorizontalDivider()

        PreferenceCategoryTitle(stringResource(R.string.rule_files_category_title))

        ListItem(
            modifier = Modifier.clickable { geoipFilePickerLauncher.launch(arrayOf("*/*")) },
            headlineContent = { Text("geoip.dat") },
            supportingContent = { Text(geoipSummary) }
        )
        HorizontalDivider()

        ListItem(
            modifier = Modifier.clickable { geositeFilePickerLauncher.launch(arrayOf("*/*")) },
            headlineContent = { Text("geosite.dat") },
            supportingContent = { Text(geositeSummary) }
        )
        HorizontalDivider()

        ListItem(
            modifier = Modifier.clickable(enabled = isGeoipCustom || isGeositeCustom) {
                showClearFilesDialog.value = true
            },
            headlineContent = { Text(stringResource(R.string.rule_file_clear_default_title)) },
            supportingContent = { Text(stringResource(R.string.rule_file_restore_default_summary)) },
        )
        HorizontalDivider()

        PreferenceCategoryTitle(stringResource(R.string.about))

        ListItem(
            headlineContent = { Text(stringResource(R.string.version)) },
            supportingContent = { Text(appVersion) }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text(stringResource(R.string.kernel)) },
            supportingContent = { Text(kernelVersion) }
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
