package com.simplexray.an.performance

import android.content.Context
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Integration helper for TProxyService
 * Provides easy-to-use performance optimizations
 */
class PerformanceIntegration(private val context: Context) {
    
    private val perfManager = PerformanceManager.getInstance(context)
    private val threadPoolManager = ThreadPoolManager.getInstance(context)
    private val memoryPool = MemoryPool(65536, 16) // 64KB buffers
    private val burstManager = BurstTrafficManager()
    
    private var initialized = false
    
    /**
     * Initialize performance optimizations
     */
    fun initialize() {
        if (initialized) return
        
        try {
            perfManager.initialize()
            
            // Pin I/O thread to big cores (best-effort, may not work without root)
            try {
                perfManager.pinToBigCores()
                AppLogger.d("$TAG: I/O thread pinned to big cores")
            } catch (e: Exception) {
                AppLogger.w("$TAG: Failed to pin I/O thread to big cores", e)
            }
            
            // Initialize epoll loop
            val epollHandle = perfManager.initEpoll()
            if (epollHandle != 0L) {
                AppLogger.d("$TAG: Epoll loop initialized")
            }
            
            // JIT warm-up (best-effort)
            try {
                perfManager.jitWarmup()
            } catch (e: Exception) {
                AppLogger.w("$TAG: JIT warm-up failed", e)
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

