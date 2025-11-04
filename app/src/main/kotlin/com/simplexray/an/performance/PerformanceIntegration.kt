package com.simplexray.an.performance

import android.content.Context
import com.simplexray.an.common.AppLogger
import com.simplexray.an.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Integration helper for TProxyService
 * Provides easy-to-use performance optimizations
 */
class PerformanceIntegration(private val context: Context) {
    
    private val perfManager = PerformanceManager.getInstance(context)
    private val threadPoolManager = ThreadPoolManager.getInstance(context)
    private val prefs = Preferences(context)
    
    // Initialize memory pool with user-configured size
    private val memoryPool = MemoryPool(
        65536, // 64KB buffers
        prefs.memoryPoolSize.coerceIn(8, 32) // Use preference with validation
    )
    private val burstManager = BurstTrafficManager()
    private val batteryMonitor = BatteryImpactMonitor(context, CoroutineScope(Dispatchers.Default))
    private val warmupManager = ConnectionWarmupManager(
        context,
        CoroutineScope(Dispatchers.Default),
        perfManager
    )
    
    private var initialized = false
    
    /**
     * Initialize performance optimizations
     */
    fun initialize() {
        if (initialized) return
        
        try {
            // Initialize with user-configured connection pool size
            val connectionPoolSize = prefs.connectionPoolSize.coerceIn(4, 16)
            perfManager.initialize(connectionPoolSize)
            
            // Pin I/O thread to big cores if enabled (best-effort, may not work without root)
            if (prefs.cpuAffinityEnabled) {
                try {
                    perfManager.pinToBigCores()
                    AppLogger.d("$TAG: I/O thread pinned to big cores")
                } catch (e: Exception) {
                    AppLogger.w("$TAG: Failed to pin I/O thread to big cores", e)
                }
            } else {
                AppLogger.d("$TAG: CPU affinity disabled by user preference")
            }
            
            // Initialize epoll loop
            val epollHandle = perfManager.initEpoll()
            if (epollHandle != 0L) {
                AppLogger.d("$TAG: Epoll loop initialized")
            }
            
            // JIT warm-up if enabled (best-effort)
            if (prefs.jitWarmupEnabled) {
                try {
                    perfManager.jitWarmup()
                    AppLogger.d("$TAG: JIT warm-up completed")
                } catch (e: Exception) {
                    AppLogger.w("$TAG: JIT warm-up failed", e)
                }
            } else {
                AppLogger.d("$TAG: JIT warm-up disabled by user preference")
            }
            
            // Configure burst traffic
            burstManager.updateConfig(BurstTrafficManager.BurstConfig())
            
            // Request CPU boost for initial operations (best-effort, may fail without root)
            try {
                perfManager.requestCPUBoost(5000) // 5 seconds
            } catch (e: Exception) {
                AppLogger.w("$TAG: CPU boost request failed (may require root)", e)
            }
            
            initialized = true
            AppLogger.d("$TAG: Performance integration initialized")
            
            // Start battery monitoring
            batteryMonitor.startMonitoring(true)
            AppLogger.d("$TAG: Battery monitoring started")
            
            // Start connection warm-up (async, non-blocking)
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    warmupManager.warmUp()
                    AppLogger.d("$TAG: Connection warm-up completed")
                } catch (e: Exception) {
                    AppLogger.w("$TAG: Connection warm-up failed", e)
                }
            }
            
            // Log hardware capabilities
            logHardwareCapabilities(perfManager)
            
            // Apply network-specific optimizations if tunFd available
            // (This would be called from TProxyService after VPN is established)
        } catch (e: Exception) {
            AppLogger.e("$TAG: Failed to initialize performance integration", e)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        if (initialized) {
            try {
                batteryMonitor.stopMonitoring()
                perfManager.cleanup()
                memoryPool.clear()
                initialized = false
                AppLogger.d("$TAG: Performance integration cleaned up")
            } catch (e: Exception) {
                AppLogger.e("$TAG: Error during cleanup", e)
            }
        }
    }
    
    /**
     * Get battery impact monitor
     */
    fun getBatteryMonitor(): BatteryImpactMonitor = batteryMonitor
    
    /**
     * Get battery impact data
     */
    fun getBatteryImpactData(): StateFlow<BatteryImpactMonitor.BatteryImpactData> = batteryMonitor.batteryData
    
    /**
     * Get connection warm-up progress
     */
    fun getWarmupProgress(): StateFlow<ConnectionWarmupManager.WarmupProgress> = warmupManager.progress
    
    /**
     * Get connection warm-up manager
     */
    fun getWarmupManager(): ConnectionWarmupManager = warmupManager
    
    /**
     * Apply network optimizations for VPN TUN interface
     * Call this after VPN is established and tunFd is available
     */
    fun applyNetworkOptimizations(tunFd: Int) {
        if (!initialized) {
            AppLogger.w("$TAG: Not initialized, skipping network optimizations")
            return
        }
        
        try {
            // Detect network type and apply optimizations
            val networkType = PerformanceUtils.detectNetworkType(context)
            PerformanceUtils.applyNetworkOptimizations(context, perfManager, tunFd)
            
            // Set QoS for TUN interface
            perfManager.setSocketPriority(tunFd, 6) // Highest priority
            perfManager.setIPTOS(tunFd, 0x10) // IPTOS_LOWDELAY
            
            // Optimize TCP settings
            perfManager.optimizeKeepAlive(tunFd)
            
            // Apply socket buffer multiplier from preferences
            val baseBufferSize = 256 * 1024 // 256KB base
            val bufferMultiplier = prefs.socketBufferMultiplier.coerceIn(1.0f, 4.0f)
            val adjustedBufferSize = (baseBufferSize * bufferMultiplier).toInt()
            perfManager.setSocketBuffers(tunFd, adjustedBufferSize, adjustedBufferSize)
            AppLogger.d("$TAG: Socket buffers set to ${adjustedBufferSize / 1024}KB (${bufferMultiplier}x multiplier)")
            
            // Apply network-specific optimizations
            perfManager.optimizeSocketBuffers(tunFd, networkType)
            
            // Enable TCP Fast Open if supported and enabled in preferences
            if (prefs.tcpFastOpenEnabled) {
                try {
                    if (perfManager.isTCPFastOpenSupported()) {
                        perfManager.enableTCPFastOpen(tunFd)
                        AppLogger.d("$TAG: TCP Fast Open enabled")
                    } else {
                        AppLogger.d("$TAG: TCP Fast Open not supported on this device")
                    }
                } catch (e: Exception) {
                    AppLogger.w("$TAG: Failed to enable TCP Fast Open", e)
                }
            } else {
                AppLogger.d("$TAG: TCP Fast Open disabled by user preference")
            }
            
            AppLogger.d("$TAG: Network optimizations applied for $networkType")
        } catch (e: Exception) {
            AppLogger.w("$TAG: Failed to apply network optimizations", e)
        }
    }
    
    /**
     * Get I/O dispatcher (pinned to big cores)
     */
    fun getIODispatcher() = threadPoolManager.getIODispatcher()
    
    /**
     * Get crypto dispatcher (pinned to big cores)
     */
    fun getCryptoDispatcher() = threadPoolManager.getCryptoDispatcher()
    
    /**
     * Get memory pool for buffer reuse
     */
    fun getMemoryPool() = memoryPool
    
    /**
     * Get performance manager
     */
    fun getPerformanceManager() = perfManager
    
    /**
     * Get burst traffic manager
     */
    fun getBurstManager() = burstManager
    
    /**
     * Log hardware capabilities for debugging
     */
    private fun logHardwareCapabilities(perfManager: PerformanceManager) {
        try {
            val hasNEON = perfManager.hasNEON()
            val hasCrypto = perfManager.hasCryptoExtensions()
            val currentCPU = perfManager.getCurrentCPU()
            
            AppLogger.d("$TAG: Hardware capabilities:")
            AppLogger.d("$TAG:   - NEON: $hasNEON")
            AppLogger.d("$TAG:   - Crypto Extensions: $hasCrypto")
            AppLogger.d("$TAG:   - Current CPU: $currentCPU")
        } catch (e: Exception) {
            AppLogger.w("$TAG: Failed to check hardware capabilities", e)
        }
    }
    
    companion object {
        private const val TAG = "PerformanceIntegration"
    }
}

