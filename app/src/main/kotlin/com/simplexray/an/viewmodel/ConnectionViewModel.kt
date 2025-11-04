package com.simplexray.an.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import com.simplexray.an.common.AppLogger
import com.simplexray.an.service.IVpnServiceBinder
import com.simplexray.an.service.IVpnStateCallback
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
import kotlin.concurrent.Volatile

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
    
    // Binder reference for service communication
    @Volatile
    private var binder: IVpnServiceBinder? = null
    private var serviceConnection: ServiceConnection? = null
    private var isBinding = false
    
    // State callback for service events
    private val stateCallback = object : IVpnStateCallback.Stub() {
        override fun onConnected() {
            AppLogger.d("ConnectionViewModel: onConnected callback received")
            viewModelScope.launch {
                setServiceEnabled(true)
                setControlMenuClickable(true)
            }
        }
        
        override fun onDisconnected() {
            AppLogger.d("ConnectionViewModel: onDisconnected callback received")
            viewModelScope.launch {
                setServiceEnabled(false)
                setControlMenuClickable(true)
            }
        }
        
        override fun onError(error: String?) {
            AppLogger.w("ConnectionViewModel: onError callback received: $error")
            viewModelScope.launch {
                setControlMenuClickable(true)
            }
        }
        
        override fun onTrafficUpdate(uplink: Long, downlink: Long) {
            // Traffic updates are handled by other observers
            // This callback ensures connection is alive
        }
    }
    
    // DeathRecipient for binder death detection
    private val deathRecipient = object : IBinder.DeathRecipient {
        override fun binderDied() {
            AppLogger.w("ConnectionViewModel: Binder died, reconnecting...")
            binder?.unlinkToDeath(this, 0)
            binder = null
            // Attempt to reconnect
            viewModelScope.launch {
                reconnectService()
            }
        }
    }
    
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
            // Attempt to bind to service on initialization
            bindToService()
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
                
                // Check if binder is alive and reconnect if needed
                if (actualState && (binder == null || !binder!!.asBinder().isBinderAlive)) {
                    AppLogger.d("ConnectionViewModel: Service is running but binder is dead, reconnecting...")
                    reconnectService()
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
            // Try to stop via binder first
            try {
                binder?.let {
                    // Binder is available, but stopping requires sending intent
                    // The binder will notify us via callback when stopped
                    val intent = Intent(
                        getApplication(),
                        TProxyService::class.java
                    ).setAction(TProxyService.ACTION_DISCONNECT)
                    uiEventSender(MainViewUiEvent.StartService(intent))
                } ?: run {
                    // Binder is null, fallback to intent
                    AppLogger.d("ConnectionViewModel: Binder unavailable, using intent fallback")
                    val intent = Intent(
                        getApplication(),
                        TProxyService::class.java
                    ).setAction(TProxyService.ACTION_DISCONNECT)
                    uiEventSender(MainViewUiEvent.StartService(intent))
                }
            } catch (e: RemoteException) {
                // Binder died, use intent fallback
                AppLogger.w("ConnectionViewModel: Binder error, using intent fallback", e)
                val intent = Intent(
                    getApplication(),
                    TProxyService::class.java
                ).setAction(TProxyService.ACTION_DISCONNECT)
                uiEventSender(MainViewUiEvent.StartService(intent))
                // Attempt to reconnect
                reconnectService()
            }
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
    
    /**
     * Bind to TProxyService to establish Binder IPC connection
     * This enables real-time state callbacks and traffic updates
     */
    fun bindToService() {
        if (isBinding || binder != null) {
            AppLogger.d("ConnectionViewModel: Already binding or bound")
            return
        }
        
        isBinding = true
        val intent = Intent(getApplication(), TProxyService::class.java)
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                AppLogger.d("ConnectionViewModel: Service connected")
                isBinding = false
                
                try {
                    binder = IVpnServiceBinder.Stub.asInterface(service)
                    if (binder == null) {
                        AppLogger.w("ConnectionViewModel: Failed to get binder interface")
                        return
                    }
                    
                    // Link to death recipient
                    service?.linkToDeath(deathRecipient, 0)
                    
                    // Register callback
                    val registered = binder!!.registerCallback(stateCallback)
                    if (registered) {
                        AppLogger.d("ConnectionViewModel: Callback registered successfully")
                        
                        // Query current state immediately
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val isConnected = binder!!.isConnected()
                                AppLogger.d("ConnectionViewModel: Current service state: $isConnected")
                                setServiceEnabled(isConnected)
                            } catch (e: RemoteException) {
                                AppLogger.w("ConnectionViewModel: Error querying service state", e)
                            }
                        }
                    } else {
                        AppLogger.w("ConnectionViewModel: Failed to register callback")
                    }
                } catch (e: Exception) {
                    AppLogger.e("ConnectionViewModel: Error in onServiceConnected", e)
                    isBinding = false
                    binder = null
                }
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                AppLogger.w("ConnectionViewModel: Service disconnected")
                isBinding = false
                binder?.unregisterCallback(stateCallback)
                binder?.asBinder()?.unlinkToDeath(deathRecipient, 0)
                binder = null
            }
        }
        
        try {
            val bound = getApplication<Application>().bindService(
                intent,
                serviceConnection!!,
                Context.BIND_AUTO_CREATE
            )
            if (!bound) {
                AppLogger.w("ConnectionViewModel: Failed to bind to service")
                isBinding = false
                serviceConnection = null
            }
        } catch (e: Exception) {
            AppLogger.e("ConnectionViewModel: Error binding to service", e)
            isBinding = false
            serviceConnection = null
        }
    }
    
    /**
     * Reconnect to service after binder death
     */
    fun reconnectService() {
        AppLogger.d("ConnectionViewModel: Reconnecting to service...")
        
        // Unbind first if bound
        unbindFromService()
        
        // Wait a bit before reconnecting
        viewModelScope.launch(Dispatchers.IO) {
            delay(500) // Small delay to avoid rapid reconnection loops
            
            // Check if service is still running
            val isRunning = isServiceRunning(getApplication(), TProxyService::class.java)
            if (isRunning) {
                bindToService()
            } else {
                AppLogger.d("ConnectionViewModel: Service not running, skipping reconnect")
            }
        }
    }
    
    /**
     * Unbind from service
     * CRITICAL: Only call this in onDestroy, NOT in onStop
     */
    fun unbindFromService() {
        try {
            binder?.unregisterCallback(stateCallback)
            binder?.asBinder()?.unlinkToDeath(deathRecipient, 0)
            binder = null
            
            serviceConnection?.let { conn ->
                getApplication<Application>().unbindService(conn)
                serviceConnection = null
            }
            AppLogger.d("ConnectionViewModel: Unbound from service")
        } catch (e: Exception) {
            AppLogger.w("ConnectionViewModel: Error unbinding from service", e)
        }
    }
    
    /**
     * Query service state via binder
     * Falls back to service check if binder is unavailable
     */
    fun queryServiceState(): Boolean {
        return try {
            binder?.isConnected() ?: isServiceRunning(getApplication(), TProxyService::class.java)
        } catch (e: RemoteException) {
            AppLogger.w("ConnectionViewModel: Error querying service state via binder", e)
            isServiceRunning(getApplication(), TProxyService::class.java)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        AppLogger.d("ConnectionViewModel cleared - cleaning up receivers and service connection")
        unbindFromService()
        unregisterTProxyServiceReceivers()
    }
    
    companion object {
        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            return ServiceStateChecker.isServiceRunning(context, serviceClass)
        }
    }
}

