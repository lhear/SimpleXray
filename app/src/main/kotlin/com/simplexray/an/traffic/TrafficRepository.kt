package com.simplexray.an.traffic

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.simplexray.an.common.AppLogger
import com.simplexray.an.service.IVpnServiceBinder
import com.simplexray.an.service.IVpnStateCallback
import com.simplexray.an.service.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile

/**
 * Singleton repository for traffic monitoring.
 * 
 * Key features:
 * - Application-level scope (survives Activity recreation)
 * - Hot SharedFlow with replay buffer (50 samples) and extra capacity (200)
 * - Binder callback integration for real-time updates
 * - Periodic polling fallback (every 2s) when callbacks stop
 * - Sliding window smoothing (3-5 samples) to prevent spikes
 * - Thread-safe delta calculation with overflow handling
 * - Ring buffer for last N samples (50 samples)
 * 
 * Architecture:
 * - Runs in Application scope, never tied to Activity lifecycle
 * - Automatically reattaches binder callbacks on service reconnect
 * - Handles binder death and service restarts gracefully
 * - Maintains last known values across UI recreation
 */
class TrafficRepository private constructor(
    private val application: Application
) {
    companion object {
        private const val TAG = "TrafficRepository"
        
        // SharedFlow configuration
        private const val REPLAY_SIZE = 50
        private const val EXTRA_BUFFER_CAPACITY = 200
        
        // Polling configuration
        private const val POLLING_FALLBACK_INTERVAL_MS = 2000L // 2 seconds
        private const val CALLBACK_TIMEOUT_MS = 2500L // If no callback for 2.5s, start polling
        
        // Smoothing configuration
        private const val SMOOTHING_WINDOW_SIZE = 5 // Use last 5 samples for smoothing
        
        // Ring buffer size
        private const val RING_BUFFER_SIZE = 50
        
        @Volatile
        private var INSTANCE: TrafficRepository? = null
        
        /**
         * Get singleton instance. Must be called from Application.onCreate()
         */
        fun getInstance(application: Application): TrafficRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrafficRepository(application).also { INSTANCE = it }
            }
        }
        
        /**
         * Get instance if already initialized, null otherwise
         */
        fun getInstanceOrNull(): TrafficRepository? = INSTANCE
    }

    // Application-level coroutine scope (never cancelled unless app terminates)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Hot SharedFlow with replay buffer and extra capacity
    private val _trafficFlow = MutableSharedFlow<TrafficSample>(
        replay = REPLAY_SIZE,
        extraBufferCapacity = EXTRA_BUFFER_CAPACITY,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val trafficFlow: SharedFlow<TrafficSample> = _trafficFlow.asSharedFlow()
    
    // Ring buffer for last N samples (thread-safe)
    private val ringBuffer = ArrayDeque<TrafficSample>(RING_BUFFER_SIZE)
    private val ringBufferMutex = Mutex()
    
    // Last known values (for delta calculation and persistence)
    @Volatile
    private var lastSample: TrafficSample? = null
    
    // Smoothing window (last N samples for averaging)
    private val smoothingWindow = ArrayDeque<TrafficSample>(SMOOTHING_WINDOW_SIZE)
    private val smoothingMutex = Mutex()
    
    // Binder connection state
    @Volatile
    private var binder: IVpnServiceBinder? = null
    private var serviceConnection: ServiceConnection? = null
    private var isBinding = false
    
    // Callback for binder traffic updates
    private val trafficCallback = object : IVpnStateCallback.Stub() {
        override fun onConnected() {
            AppLogger.d("$TAG: Service connected, requesting initial traffic snapshot")
            // Request immediate traffic snapshot when connected
            repositoryScope.launch {
                requestTrafficSnapshot()
            }
        }
        
        override fun onDisconnected() {
            AppLogger.d("$TAG: Service disconnected")
            // Emit zero sample on disconnect
            repositoryScope.launch {
                emitSample(TrafficSample(
                    timestamp = System.currentTimeMillis(),
                    rxBytesTotal = lastSample?.rxBytesTotal ?: 0L,
                    txBytesTotal = lastSample?.txBytesTotal ?: 0L,
                    rxSpeedBps = 0f,
                    txSpeedBps = 0f
                ))
            }
        }
        
        override fun onError(error: String?) {
            AppLogger.w("$TAG: Service error: $error")
        }
        
        override fun onTrafficUpdate(uplink: Long, downlink: Long) {
            // This callback is called from binder thread, offload to repository scope
            repositoryScope.launch {
                handleTrafficUpdate(uplink, downlink)
            }
        }
    }
    
    // Death recipient for binder death detection
    private val deathRecipient = object : IBinder.DeathRecipient {
        override fun binderDied() {
            AppLogger.w("$TAG: Binder died, attempting reconnection")
            binder = null
            // Attempt to rebind after a delay
            repositoryScope.launch {
                delay(1000L)
                bindToService()
            }
        }
    }
    
    // Last callback timestamp (for fallback polling detection)
    @Volatile
    private var lastCallbackTimestamp: Long = 0L
    
    // Polling job reference
    private val pollingJob = AtomicReference<kotlinx.coroutines.Job?>(null)
    
    init {
        AppLogger.d("$TAG: Initialized")
        // Start binding to service
        bindToService()
        // Start fallback polling
        startFallbackPolling()
    }
    
    /**
     * Bind to TProxyService and register traffic callback
     */
    private fun bindToService() {
        if (isBinding || binder != null) {
            return
        }
        
        isBinding = true
        val intent = android.content.Intent(application, TProxyService::class.java)
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                AppLogger.d("$TAG: Service connected")
                isBinding = false
                
                try {
                    binder = IVpnServiceBinder.Stub.asInterface(service)
                    if (binder == null) {
                        AppLogger.w("$TAG: Failed to get binder interface")
                        return
                    }
                    
                    // Link to death recipient
                    service?.linkToDeath(deathRecipient, 0)
                    
                    // Register callback
                    val registered = binder!!.registerCallback(trafficCallback)
                    if (registered) {
                        AppLogger.d("$TAG: Traffic callback registered")
                        // Request initial snapshot
                        repositoryScope.launch {
                            requestTrafficSnapshot()
                        }
                    } else {
                        AppLogger.w("$TAG: Failed to register traffic callback")
                    }
                } catch (e: Exception) {
                    AppLogger.w("$TAG: Error during service connection", e)
                    isBinding = false
                }
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                AppLogger.d("$TAG: Service disconnected")
                isBinding = false
                binder = null
                // Attempt to rebind
                repositoryScope.launch {
                    delay(2000L)
                    bindToService()
                }
            }
        }
        
        try {
            val bound = application.bindService(
                intent,
                serviceConnection!!,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
            )
            if (!bound) {
                AppLogger.w("$TAG: Failed to bind to service")
                isBinding = false
            }
        } catch (e: Exception) {
            AppLogger.w("$TAG: Error binding to service", e)
            isBinding = false
        }
    }
    
    /**
     * Handle traffic update from binder callback
     */
    private suspend fun handleTrafficUpdate(uplink: Long, downlink: Long) = withContext(Dispatchers.Default) {
        lastCallbackTimestamp = System.currentTimeMillis()
        
        val now = System.currentTimeMillis()
        val currentSample = TrafficSample(
            timestamp = now,
            rxBytesTotal = downlink,  // Note: downlink = received bytes
            txBytesTotal = uplink,     // Note: uplink = transmitted bytes
            rxSpeedBps = 0f,
            txSpeedBps = 0f
        )
        
        // Calculate delta from previous sample
        val sampleWithSpeed = lastSample?.let { prev ->
            TrafficSample.calculateDelta(prev, currentSample)
        } ?: currentSample
        
        // Apply smoothing
        val smoothedSample = applySmoothing(sampleWithSpeed)
        
        // Emit the sample
        emitSample(smoothedSample)
    }
    
    /**
     * Request immediate traffic snapshot from service
     */
    private suspend fun requestTrafficSnapshot() = withContext(Dispatchers.IO) {
        try {
            val stats = binder?.getTrafficStats()
            if (stats != null && stats.size >= 2) {
                handleTrafficUpdate(stats[0], stats[1]) // uplink, downlink
            }
        } catch (e: RemoteException) {
            AppLogger.w("$TAG: Error requesting traffic snapshot", e)
        } catch (e: Exception) {
            AppLogger.w("$TAG: Unexpected error requesting traffic snapshot", e)
        }
    }
    
    /**
     * Start fallback polling when callbacks stop firing
     */
    private fun startFallbackPolling() {
        pollingJob.get()?.cancel()
        val job = repositoryScope.launch {
            while (isActive) {
                delay(POLLING_FALLBACK_INTERVAL_MS)
                
                val now = System.currentTimeMillis()
                val timeSinceLastCallback = now - lastCallbackTimestamp
                
                // If no callback received for CALLBACK_TIMEOUT_MS, start polling
                if (timeSinceLastCallback > CALLBACK_TIMEOUT_MS && binder != null) {
                    AppLogger.d("$TAG: No callbacks for ${timeSinceLastCallback}ms, polling fallback")
                    requestTrafficSnapshot()
                }
            }
        }
        pollingJob.set(job)
    }
    
    /**
     * Apply sliding window smoothing to prevent spikes
     */
    private suspend fun applySmoothing(sample: TrafficSample): TrafficSample = smoothingMutex.withLock {
        smoothingWindow.addLast(sample)
        if (smoothingWindow.size > SMOOTHING_WINDOW_SIZE) {
            smoothingWindow.removeFirst()
        }
        
        // If we don't have enough samples, return as-is
        if (smoothingWindow.size < 3) {
            return sample
        }
        
        // Calculate average of last N samples
        val rxSpeeds = smoothingWindow.map { it.rxSpeedBps }
        val txSpeeds = smoothingWindow.map { it.txSpeedBps }
        
        val avgRxSpeed = rxSpeeds.average().toFloat()
        val avgTxSpeed = txSpeeds.average().toFloat()
        
        // Return sample with smoothed speeds
        return sample.copy(
            rxSpeedBps = avgRxSpeed,
            txSpeedBps = avgTxSpeed
        )
    }
    
    /**
     * Emit sample to SharedFlow and update ring buffer
     */
    private suspend fun emitSample(sample: TrafficSample) = withContext(Dispatchers.Default) {
        // Update last sample
        lastSample = sample
        
        // Update ring buffer
        ringBufferMutex.withLock {
            ringBuffer.addLast(sample)
            if (ringBuffer.size > RING_BUFFER_SIZE) {
                ringBuffer.removeFirst()
            }
        }
        
        // Emit to SharedFlow (non-blocking, will drop oldest if buffer full)
        _trafficFlow.emit(sample)
    }
    
    /**
     * Get last N samples from ring buffer (thread-safe)
     */
    suspend fun getLastSamples(n: Int): List<TrafficSample> = ringBufferMutex.withLock {
        return ringBuffer.takeLast(n.coerceAtMost(RING_BUFFER_SIZE))
    }
    
    /**
     * Get current sample (last emitted)
     */
    fun getCurrentSample(): TrafficSample? = lastSample
    
    /**
     * Cleanup resources (called from Application.onTerminate())
     */
    fun cleanup() {
        AppLogger.d("$TAG: Cleaning up")
        pollingJob.get()?.cancel()
        try {
            binder?.unregisterCallback(trafficCallback)
        } catch (e: Exception) {
            AppLogger.w("$TAG: Error unregistering callback", e)
        }
        serviceConnection?.let {
            try {
                application.unbindService(it)
            } catch (e: Exception) {
                AppLogger.w("$TAG: Error unbinding service", e)
            }
        }
        repositoryScope.cancel()
    }
}
