package com.simplexray.an.performance

import android.content.Context
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Connection Warm-up Manager
 * Pre-warms connections and DNS resolution on service startup
 */
class ConnectionWarmupManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val perfManager: PerformanceManager
) {
    
    data class WarmupProgress(
        val total: Int = 0,
        val completed: Int = 0,
        val currentTask: String = "",
        val isComplete: Boolean = false,
        val error: String? = null
    )
    
    private val _progress = MutableStateFlow(WarmupProgress())
    val progress: StateFlow<WarmupProgress> = _progress.asStateFlow()
    
    private var isWarmingUp = false
    
    /**
     * Warm up connections and DNS
     */
    suspend fun warmUp(
        targetHosts: List<String> = defaultTargetHosts,
        connectionPoolTypes: List<PerformanceManager.PoolType> = listOf(
            PerformanceManager.PoolType.H2_STREAM,
            PerformanceManager.PoolType.VISION
        )
    ) {
        if (isWarmingUp) {
            AppLogger.w("$TAG: Warm-up already in progress")
            return
        }
        
        isWarmingUp = true
        val totalTasks = targetHosts.size * 2 + connectionPoolTypes.size // DNS + Connect + Pool fill
        
        _progress.value = WarmupProgress(
            total = totalTasks,
            completed = 0,
            currentTask = "Starting warm-up...",
            isComplete = false
        )
        
        try {
            withContext(Dispatchers.IO) {
                // Step 1: Pre-resolve DNS
                var completed = 0
                for (host in targetHosts) {
                    _progress.value = _progress.value.copy(
                        currentTask = "Resolving DNS: $host",
                        completed = completed
                    )
                    
                    try {
                        preResolveDNS(host)
                        completed++
                        _progress.value = _progress.value.copy(completed = completed)
                    } catch (e: Exception) {
                        AppLogger.w("$TAG: Failed to resolve DNS for $host", e)
                    }
                }
                
                // Step 2: Pre-connect to targets
                for (host in targetHosts) {
                    _progress.value = _progress.value.copy(
                        currentTask = "Pre-connecting: $host",
                        completed = completed
                    )
                    
                    try {
                        preConnect(host, 443)
                        completed++
                        _progress.value = _progress.value.copy(completed = completed)
                    } catch (e: Exception) {
                        AppLogger.w("$TAG: Failed to pre-connect to $host", e)
                    }
                }
                
                // Step 3: Pre-fill connection pools
                for (poolType in connectionPoolTypes) {
                    _progress.value = _progress.value.copy(
                        currentTask = "Pre-filling pool: ${poolType.name}",
                        completed = completed
                    )
                    
                    try {
                        preFillConnectionPool(poolType)
                        completed++
                        _progress.value = _progress.value.copy(completed = completed)
                    } catch (e: Exception) {
                        AppLogger.w("$TAG: Failed to pre-fill pool ${poolType.name}", e)
                    }
                }
                
                // Complete
                _progress.value = _progress.value.copy(
                    completed = totalTasks,
                    currentTask = "Warm-up complete",
                    isComplete = true
                )
                
                AppLogger.d("$TAG: Warm-up completed successfully")
            }
        } catch (e: Exception) {
            AppLogger.e("$TAG: Warm-up failed", e)
            _progress.value = _progress.value.copy(
                error = e.message ?: "Unknown error",
                isComplete = true
            )
        } finally {
            isWarmingUp = false
        }
    }
    
    /**
     * Pre-resolve DNS for a host
     */
    private suspend fun preResolveDNS(host: String) = withContext(Dispatchers.IO) {
        try {
            val address = java.net.InetAddress.getByName(host)
            AppLogger.d("$TAG: DNS resolved for $host: ${address.hostAddress}")
        } catch (e: Exception) {
            throw Exception("DNS resolution failed for $host: ${e.message}", e)
        }
    }
    
    /**
     * Pre-connect to a host:port
     */
    private suspend fun preConnect(host: String, port: Int) = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                // Connection established - TCP Fast Open will be used on pooled connections
                AppLogger.d("$TAG: Pre-connected to $host:$port")
            }
        } catch (e: Exception) {
            throw Exception("Pre-connection failed for $host:$port: ${e.message}", e)
        }
    }
    
    /**
     * Pre-fill connection pool
     */
    private suspend fun preFillConnectionPool(poolType: PerformanceManager.PoolType) = withContext(Dispatchers.IO) {
        try {
            // Get a socket from pool to ensure pool is initialized
            val fd = perfManager.getPooledSocket(poolType)
            if (fd > 0) {
                val slotIndex = perfManager.getPooledSocketSlotIndex(poolType, fd)
                AppLogger.d("$TAG: Pool ${poolType.name} pre-filled (fd: $fd, slot: $slotIndex)")
                // Return socket immediately (it's not connected, just allocated)
                if (slotIndex >= 0) {
                    perfManager.returnPooledSocket(poolType, slotIndex)
                } else {
                    perfManager.returnPooledSocketByFd(poolType, fd)
                }
            }
        } catch (e: Exception) {
            throw Exception("Pool pre-fill failed for ${poolType.name}: ${e.message}", e)
        }
    }
    
    /**
     * Reset warm-up state
     */
    fun reset() {
        isWarmingUp = false
        _progress.value = WarmupProgress()
    }
    
    companion object {
        private const val TAG = "ConnectionWarmupManager"
        
        private val defaultTargetHosts = listOf(
            "www.google.com",
            "www.cloudflare.com",
            "1.1.1.1"
        )
    }
}

