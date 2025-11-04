package com.simplexray.an.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import com.simplexray.an.common.AppLogger
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplexray.an.R
import com.simplexray.an.common.ServiceStateChecker
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.service.TProxyService
import com.simplexray.an.viewmodel.MainViewUiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ConnectionViewModel"

/**
 * ViewModel for managing VPN connection state
 * TODO: Add connection retry mechanism with exponential backoff
 * TODO: Implement connection state persistence across app restarts
 * TODO: Add connection quality monitoring
 */
class ConnectionViewModel(
    application: Application,
    private val prefs: Preferences,
    private val selectedConfigFile: StateFlow<File?>,
    private val uiEventSender: (MainViewUiEvent) -> Unit
) : AndroidViewModel(application) {
    
    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()
    
    private val _controlMenuClickable = MutableStateFlow(true)
    val controlMenuClickable: StateFlow<Boolean> = _controlMenuClickable.asStateFlow()
    
    private val startReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppLogger.d("Service started")
            setServiceEnabled(true)
            setControlMenuClickable(true)
        }
    }
    
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppLogger.d("Service stopped")
            setServiceEnabled(false)
            setControlMenuClickable(true)
        }
    }
    
    init {
        AppLogger.d("ConnectionViewModel initialized")
        viewModelScope.launch(Dispatchers.IO) {
            _isServiceEnabled.value = isServiceRunning(getApplication(), TProxyService::class.java)
        }
        
        // Periodically check service state to detect zombie processes
        // This ensures UI stays in sync even if broadcasts are missed
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(10000) // Check every 10 seconds
                val actualState = isServiceRunning(getApplication(), TProxyService::class.java)
                val currentState = _isServiceEnabled.value
                
                // If state mismatch detected, update UI
                if (actualState != currentState) {
                    AppLogger.d("ConnectionViewModel: State mismatch detected. Actual: $actualState, UI: $currentState. Updating...")
                    setServiceEnabled(actualState)
                }
            }
        }
    }
    
    fun setControlMenuClickable(isClickable: Boolean) {
        _controlMenuClickable.value = isClickable
    }
    
    fun setServiceEnabled(enabled: Boolean) {
        _isServiceEnabled.value = enabled
        prefs.enable = enabled
    }
    
    fun startTProxyService(action: String) {
        viewModelScope.launch {
            if (selectedConfigFile.value == null) {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.not_select_config)))
                AppLogger.w("Cannot start service: no config file selected.")
                setControlMenuClickable(true)
                return@launch
            }
            val intent = Intent(getApplication(), TProxyService::class.java).setAction(action)
            uiEventSender(MainViewUiEvent.StartService(intent))
        }
    }
    
    fun stopTProxyService() {
        viewModelScope.launch {
            val intent = Intent(
                getApplication(),
                TProxyService::class.java
            ).setAction(TProxyService.ACTION_DISCONNECT)
            uiEventSender(MainViewUiEvent.StartService(intent))
        }
    }
    
    // VPN permission state caching
    private var cachedVpnPermissionState: Boolean? = null
    private var vpnPermissionCacheTime: Long = 0
    private val VPN_PERMISSION_CACHE_TTL_MS = 60000L // 1 minute
    
    fun prepareAndStartVpn(vpnPrepareLauncher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            if (selectedConfigFile.value == null) {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.not_select_config)))
                AppLogger.w("Cannot prepare VPN: no config file selected.")
                setControlMenuClickable(true)
                return@launch
            }
            // Validate config file before starting VPN
            val configFile = selectedConfigFile.value
            if (configFile == null || !configFile.exists() || !configFile.canRead()) {
                uiEventSender(MainViewUiEvent.ShowSnackbar(getApplication<Application>().getString(R.string.not_select_config)))
                AppLogger.w("Cannot prepare VPN: invalid config file.")
                setControlMenuClickable(true)
                return@launch
            }
            
            // Check VPN permission with caching
            val now = System.currentTimeMillis()
            val vpnIntent = if (cachedVpnPermissionState != null && 
                (now - vpnPermissionCacheTime) < VPN_PERMISSION_CACHE_TTL_MS) {
                // Use cached state
                if (cachedVpnPermissionState == true) {
                    VpnService.prepare(getApplication())
                } else {
                    null
                }
            } else {
                // Check permission and update cache
                val intent = VpnService.prepare(getApplication())
                cachedVpnPermissionState = intent != null
                vpnPermissionCacheTime = now
                intent
            }
            if (vpnIntent != null) {
                vpnPrepareLauncher.launch(vpnIntent)
            } else {
                startTProxyService(TProxyService.ACTION_CONNECT)
            }
        }
    }
    
    fun registerTProxyServiceReceivers() {
        val application = getApplication<Application>()
        val startSuccessFilter = IntentFilter(TProxyService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(
                startReceiver,
                startSuccessFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            getApplication<Application>().registerReceiver(startReceiver, startSuccessFilter)
        }
        
        val stopSuccessFilter = IntentFilter(TProxyService.ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(
                stopReceiver,
                stopSuccessFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            getApplication<Application>().registerReceiver(stopReceiver, stopSuccessFilter)
        }
        AppLogger.d("TProxyService receivers registered.")
    }
    
    fun unregisterTProxyServiceReceivers() {
        val application = getApplication<Application>()
        try {
            getApplication<Application>().unregisterReceiver(startReceiver)
        } catch (e: IllegalArgumentException) {
            AppLogger.w("Start receiver was not registered", e)
        }
        try {
            getApplication<Application>().unregisterReceiver(stopReceiver)
        } catch (e: IllegalArgumentException) {
            AppLogger.w("Stop receiver was not registered", e)
        }
        AppLogger.d("TProxyService receivers unregistered.")
    }
    
    override fun onCleared() {
        super.onCleared()
        AppLogger.d("ConnectionViewModel cleared - cleaning up receivers")
        unregisterTProxyServiceReceivers()
    }
    
    companion object {
        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            return ServiceStateChecker.isServiceRunning(context, serviceClass)
        }
    }
}

