package com.simplexray.an.viewmodel

data class InputFieldState(
    val value: String,
    val error: String? = null,
    val isValid: Boolean = true
)

data class SwitchStates(
    val ipv6Enabled: Boolean,
    val useTemplateEnabled: Boolean,
    val httpProxyEnabled: Boolean,
    val bypassLanEnabled: Boolean,
    val disableVpn: Boolean,
    val profileProtectionEnabled: Boolean
)

data class InfoStates(
    val appVersion: String,
    val kernelVersion: String,
    val geoipSummary: String,
    val geositeSummary: String,
    val geoipUrl: String,
    val geositeUrl: String,
    val strongBoxStatus: Boolean
)

data class FileStates(
    val isGeoipCustom: Boolean,
    val isGeositeCustom: Boolean
)

data class SettingsState(
    val socksPort: InputFieldState,
    val dnsIpv4: InputFieldState,
    val dnsIpv6: InputFieldState,
    val switches: SwitchStates,
    val info: InfoStates,
    val files: FileStates,
    val connectivityTestTarget: InputFieldState,
    val connectivityTestTimeout: InputFieldState
) 