package com.simplexray.an.performance

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.simplexray.an.common.AppLogger

/**
 * Performance Utilities
 * Helper functions for performance optimization
 */
object PerformanceUtils {
    
    /**
     * Detect network type for MTU optimization
     */
    fun detectNetworkType(context: Context): PerformanceManager.NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return PerformanceManager.NetworkType.LTE
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return PerformanceManager.NetworkType.LTE
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return PerformanceManager.NetworkType.LTE
            
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    PerformanceManager.NetworkType.WIFI
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    // Try to detect 5G (simplified - would need actual 5G detection)
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS)) {
                        PerformanceManager.NetworkType.FIVE_G // Heuristic
                    } else {
                        PerformanceManager.NetworkType.LTE
                    }
                }
                else -> PerformanceManager.NetworkType.LTE
            }
        }
        
        return PerformanceManager.NetworkType.LTE
    }
    
    /**
     * Get optimal buffer size for network type
     */
    fun getOptimalBufferSize(networkType: PerformanceManager.NetworkType): Int {
        return when (networkType) {
            PerformanceManager.NetworkType.WIFI -> 2 * 1024 * 1024 // 2 MB
            PerformanceManager.NetworkType.FIVE_G -> 3 * 1024 * 1024 // 3 MB
            PerformanceManager.NetworkType.LTE -> 1 * 1024 * 1024 // 1 MB
        }
    }
    
    /**
     * Get optimal window size for network type
     */
    fun getOptimalWindowSize(networkType: PerformanceManager.NetworkType): Int {
        return when (networkType) {
            PerformanceManager.NetworkType.WIFI -> 8 * 1024 * 1024 // 8 MB
            PerformanceManager.NetworkType.FIVE_G -> 6 * 1024 * 1024 // 6 MB
            PerformanceManager.NetworkType.LTE -> 4 * 1024 * 1024 // 4 MB
        }
    }
    
    /**
     * Check if device supports performance optimizations
     */
    fun checkDeviceSupport(context: Context): DeviceSupport {
        val perfManager = PerformanceManager.getInstance(context)
        
        return DeviceSupport(
            hasNEON = perfManager.hasNEON(),
            hasCryptoExtensions = perfManager.hasCryptoExtensions(),
            currentCPU = perfManager.getCurrentCPU(),
            networkType = detectNetworkType(context)
        )
    }
    
    data class DeviceSupport(
        val hasNEON: Boolean,
        val hasCryptoExtensions: Boolean,
        val currentCPU: Int,
        val networkType: PerformanceManager.NetworkType
    ) {
        fun isOptimal(): Boolean {
            return hasNEON && hasCryptoExtensions && currentCPU >= 0
        }
        
        fun getRecommendation(): String {
            val recommendations = mutableListOf<String>()
            
            if (!hasNEON) {
                recommendations.add("Device doesn't support NEON - crypto acceleration limited")
            }
            
            if (!hasCryptoExtensions) {
                recommendations.add("Device doesn't support crypto extensions - hardware crypto limited")
            }
            
            if (currentCPU < 0) {
                recommendations.add("CPU detection failed - CPU affinity may not work")
            }
            
            return recommendations.joinToString("\n") ?: "Device supports all optimizations"
        }
    }
    
    /**
     * Log performance configuration
     */
    fun logPerformanceConfig(context: Context, config: PerformanceConfig) {
        AppLogger.d("PerformanceUtils", """
            Performance Configuration:
            - CPU Affinity: ${config.enableCPUAffinity}
            - Epoll: ${config.enableEpoll}
            - Zero-Copy: ${config.enableZeroCopy}
            - Connection Pool: ${config.enableConnectionPool} (${config.connectionPoolSize} sockets)
            - Burst Window: ${config.initialWindowSize / 1024 / 1024} MB
            - Max Streams: ${config.maxConcurrentStreams}
            - TLS Cache: ${config.enableTLSCache}
            - MTU Optimization: ${config.optimizeMTU}
            - Memory Pool: ${config.enableMemoryPool}
            - Crypto Acceleration: ${config.enableCryptoAcceleration}
            - Kernel Pacing: ${config.enableKernelPacing}
            - Read-Ahead: ${config.enableReadAhead}
            - QoS: ${config.enableQoS}
            - Battery Saver: ${config.batterySaverMode}
        """.trimIndent())
    }
    
    /**
     * Apply network-specific optimizations
     */
    fun applyNetworkOptimizations(
        context: Context,
        perfManager: PerformanceManager,
        tunFd: Int
    ) {
        val networkType = detectNetworkType(context)
        
        // Set optimal MTU
        val mtu = perfManager.setOptimalMTU(tunFd, networkType)
        if (mtu > 0) {
            AppLogger.d("PerformanceUtils", "MTU set to $mtu for ${networkType.name}")
        }
        
        // Set optimal socket buffers
        val bufferSize = getOptimalBufferSize(networkType)
        perfManager.setSocketBuffers(tunFd, bufferSize, bufferSize)
        
        AppLogger.d("PerformanceUtils", "Network optimizations applied for ${networkType.name}")
    }
}

