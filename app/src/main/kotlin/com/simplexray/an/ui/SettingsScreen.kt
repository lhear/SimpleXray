package com.simplexray.an.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplexray.an.config.ApiConfig
import com.simplexray.an.stats.TrafficViewModel
import com.simplexray.an.service.MonitorService
import com.simplexray.an.xray.XrayConfigBuilder
import com.simplexray.an.xray.XrayCoreLauncher
import com.simplexray.an.xray.XrayConfigPatcher

@Composable
fun SettingsScreen(vm: TrafficViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var host by remember { mutableStateOf(ApiConfig.getHost(context)) }
    var portText by remember { mutableStateOf(ApiConfig.getPort(context).toString()) }
    var mock by remember { mutableStateOf(ApiConfig.isMock(context)) }
    var onlineKey by remember { mutableStateOf(ApiConfig.getOnlineKey(context)) }
    var onlineBytesKey by remember { mutableStateOf(ApiConfig.getOnlineBytesKey(context)) }
    var ipDomainJson by remember { mutableStateOf(ApiConfig.getIpDomainJson(context)) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Xray Stats API", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Host") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Port") }
        )
        Spacer(Modifier.height(8.dp))
        RowCheck(label = "Mock traffic (no server)", checked = mock) { mock = it }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = onlineKey,
            onValueChange = { onlineKey = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Online IP Stat Name (for topology)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = onlineBytesKey,
            onValueChange = { onlineBytesKey = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Online IP Bytes Stat Name (edge weight)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ipDomainJson,
            onValueChange = { ipDomainJson = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("IP → Domain mapping (JSON object)") }
        )
        Spacer(Modifier.height(16.dp))
        Text("Adaptive Polling", style = MaterialTheme.typography.titleMedium)
        var adaptive by remember { mutableStateOf(ApiConfig.isAdaptive(context)) }
        RowCheck(label = "Enable adaptive polling", checked = adaptive) { adaptive = it }
        var baseMs by remember { mutableStateOf(ApiConfig.getBaseIntervalMs(context).toString()) }
        var offMs by remember { mutableStateOf(ApiConfig.getScreenOffIntervalMs(context).toString()) }
        var idleMs by remember { mutableStateOf(ApiConfig.getIdleIntervalMs(context).toString()) }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = baseMs,
            onValueChange = { baseMs = it.filter { ch -> ch.isDigit() }.take(6) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base interval (ms)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = offMs,
            onValueChange = { offMs = it.filter { ch -> ch.isDigit() }.take(6) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Screen off interval (ms)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = idleMs,
            onValueChange = { idleMs = it.filter { ch -> ch.isDigit() }.take(6) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Power save interval (ms)") }
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            val port = portText.toIntOrNull() ?: 10085
            // Persist settings including topology stat name
            ApiConfig.setAll(context, host, port, mock, onlineKey)
            ApiConfig.setOnlineBytesKey(context, onlineBytesKey)
            ApiConfig.setIpDomainJson(context, ipDomainJson)
            ApiConfig.setAdaptive(context, adaptive)
            ApiConfig.setIntervals(
                context,
                base = baseMs.toLongOrNull() ?: 1000L,
                screenOff = offMs.toLongOrNull() ?: 3000L,
                idle = idleMs.toLongOrNull() ?: 5000L
            )
            vm.applySettings(host, port, mock)
            // Optional auto-restart of Xray core on apply
            if (ApiConfig.isRestartOnApply(context)) {
                try {
                    com.simplexray.an.xray.AssetsInstaller.ensureAssets(context)
                    // Ensure a config file exists; if not, write default
                    val cfgFile = java.io.File(context.filesDir, "xray.json")
                    if (!cfgFile.exists()) {
                        val cfg = XrayConfigBuilder.defaultConfig(host, port)
                        XrayConfigBuilder.writeConfig(context, cfg)
                    }
                    com.simplexray.an.xray.XrayCoreLauncher.stop()
                    com.simplexray.an.xray.XrayCoreLauncher.start(context)
                    android.widget.Toast.makeText(context, "Xray core restarted", android.widget.Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    android.widget.Toast.makeText(context, "Xray restart failed: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }) { Text("Apply") }
        Spacer(Modifier.height(16.dp))
        Text("Telemetry", style = MaterialTheme.typography.titleMedium)
        var telemetry by remember { mutableStateOf(ApiConfig.isTelemetry(context)) }
        RowCheck(label = "Show telemetry overlay", checked = telemetry) {
            telemetry = it
            ApiConfig.setTelemetry(context, it)
        }
        Spacer(Modifier.height(16.dp))
        Text("Background Monitoring", style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { MonitorService.start(context) }) { Text("Start service") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { MonitorService.stop(context) }) { Text("Stop service") }
        }
        Spacer(Modifier.height(8.dp))
        val autoStartXray = remember { mutableStateOf(ApiConfig.isAutostartXray(context)) }
        RowCheck(label = "Auto start Xray Core with service", checked = autoStartXray.value) {
            autoStartXray.value = it
            ApiConfig.setAutostartXray(context, it)
        }
        val restartOnApply = remember { mutableStateOf(ApiConfig.isRestartOnApply(context)) }
        RowCheck(label = "Restart Xray Core on Apply", checked = restartOnApply.value) {
            restartOnApply.value = it
            ApiConfig.setRestartOnApply(context, it)
        }

        Spacer(Modifier.height(16.dp))
        Text("Topology Smoothing", style = MaterialTheme.typography.titleMedium)
        var topoAlpha by remember { mutableStateOf(ApiConfig.getTopologyAlpha(context).toString()) }
        OutlinedTextField(
            value = topoAlpha,
            onValueChange = { topoAlpha = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("EMA alpha (0.05 .. 1.0)") }
        )
        Button(onClick = {
            val a = topoAlpha.toFloatOrNull()?.coerceIn(0.05f, 1.0f) ?: 0.3f
            ApiConfig.setTopologyAlpha(context, a)
            android.widget.Toast.makeText(context, "Set alpha=$a", android.widget.Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Apply Smoothing") }
        val autoRdns = remember { mutableStateOf(ApiConfig.isAutoReverseDns(context)) }
        RowCheck(label = "Auto reverse DNS (experimental)", checked = autoRdns.value) {
            autoRdns.value = it
            ApiConfig.setAutoReverseDns(context, it)
        }

        Spacer(Modifier.height(16.dp))
        Text("gRPC Network Profile", style = MaterialTheme.typography.titleMedium)
        val currentProfile = remember { mutableStateOf(ApiConfig.getGrpcProfile(context)) }
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { currentProfile.value = "conservative"; ApiConfig.setGrpcProfile(context, "conservative"); com.simplexray.an.grpc.GrpcChannelFactory.setRetryProfile("conservative") }) { Text("Conservative") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { currentProfile.value = "balanced"; ApiConfig.setGrpcProfile(context, "balanced"); com.simplexray.an.grpc.GrpcChannelFactory.setRetryProfile("balanced") }) { Text("Balanced") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { currentProfile.value = "aggressive"; ApiConfig.setGrpcProfile(context, "aggressive"); com.simplexray.an.grpc.GrpcChannelFactory.setRetryProfile("aggressive") }) { Text("Aggressive") }
        }

        Spacer(Modifier.height(8.dp))
        var deadlineMs by remember { mutableStateOf(ApiConfig.getGrpcDeadlineMs(context).toString()) }
        OutlinedTextField(
            value = deadlineMs,
            onValueChange = { deadlineMs = it.filter { ch -> ch.isDigit() }.take(5) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("gRPC deadline (ms)") }
        )
        Button(onClick = {
            val d = deadlineMs.toLongOrNull()?.coerceIn(500, 10000) ?: 2000
            ApiConfig.setGrpcDeadlineMs(context, d)
            android.widget.Toast.makeText(context, "Set deadline=${d}ms", android.widget.Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Apply gRPC Settings") }

        Spacer(Modifier.height(16.dp))
        Text("Alerts", style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { ApiConfig.applyAlertPreset(context, "conservative"); android.widget.Toast.makeText(context, "Alerts: Conservative", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Conservative") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { ApiConfig.applyAlertPreset(context, "balanced"); android.widget.Toast.makeText(context, "Alerts: Balanced", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Balanced") }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = { ApiConfig.applyAlertPreset(context, "performance"); android.widget.Toast.makeText(context, "Alerts: Performance", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Performance") }
        }
        val burstZ = remember { mutableStateOf(ApiConfig.getAlertBurstZ(context).toString()) }
        val minBps = remember { mutableStateOf(ApiConfig.getAlertBurstMinBps(context).toString()) }
        val thrRatio = remember { mutableStateOf(ApiConfig.getAlertThrottleRatio(context).toString()) }
        val minLong = remember { mutableStateOf(ApiConfig.getAlertMinLongMean(context).toString()) }
        val cooldown = remember { mutableStateOf(ApiConfig.getAlertCooldownMs(context).toString()) }
        OutlinedTextField(value = burstZ.value, onValueChange = { burstZ.value = it.filter { ch -> ch.isDigit() || ch=='.' }.take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("Burst z-threshold") })
        OutlinedTextField(value = minBps.value, onValueChange = { minBps.value = it.filter { ch -> ch.isDigit() }.take(9) }, modifier = Modifier.fillMaxWidth(), label = { Text("Burst min bps") })
        OutlinedTextField(value = thrRatio.value, onValueChange = { thrRatio.value = it.filter { ch -> ch.isDigit() || ch=='.' }.take(4) }, modifier = Modifier.fillMaxWidth(), label = { Text("Throttle drop ratio (0..1)") })
        OutlinedTextField(value = minLong.value, onValueChange = { minLong.value = it.filter { ch -> ch.isDigit() }.take(9) }, modifier = Modifier.fillMaxWidth(), label = { Text("Throttle min long mean bps") })
        OutlinedTextField(value = cooldown.value, onValueChange = { cooldown.value = it.filter { ch -> ch.isDigit() }.take(6) }, modifier = Modifier.fillMaxWidth(), label = { Text("Alert cooldown (ms)") })
        Button(onClick = {
            ApiConfig.setAlertBurstZ(context, burstZ.value.toDoubleOrNull() ?: 3.0)
            ApiConfig.setAlertBurstMinBps(context, minBps.value.toLongOrNull() ?: 1_000_000L)
            ApiConfig.setAlertThrottleRatio(context, thrRatio.value.toDoubleOrNull() ?: 0.2)
            ApiConfig.setAlertMinLongMean(context, minLong.value.toLongOrNull() ?: 2_000_000L)
            ApiConfig.setAlertCooldownMs(context, cooldown.value.toLongOrNull() ?: 60_000L)
            android.widget.Toast.makeText(context, "Alert parameters saved", android.widget.Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Save Alert Params") }
    }
}

@Composable
private fun RowCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
