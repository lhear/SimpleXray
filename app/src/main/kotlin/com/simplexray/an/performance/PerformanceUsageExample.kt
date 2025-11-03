package com.simplexray.an.performance

import android.content.Context
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.net.Socket

/**
 * Usage Examples for Performance Module
 * 
 * Bu dosya performans modülünün nasıl kullanılacağını gösterir
 */
object PerformanceUsageExample {
    
    private const val TAG = "PerfUsageExample"
    
    /**
     * Example 1: High-Throughput Streaming
     */
    suspend fun highThroughputStreaming(context: Context, serverHost: String, serverPort: Int) {
        val perf = PerformanceIntegration(context)
        perf.initialize()
        
        // Burst traffic ayarla
        perf.getBurstManager().updateConfig(
            BurstTrafficManager.BurstConfig(
                initialWindowSize = 8 * 1024 * 1024, // 8 MB
                maxConcurrentStreams = 512,
                adaptiveWindow = true
            )
        )
        
        withContext(perf.getIODispatcher()) {
            // Pooled socket kullan
            val socket = perf.getPerformanceManager()
                .getPooledSocket(PerformanceManager.PoolType.VISION)
            
            if (socket > 0) {
                // Connect
                perf.getPerformanceManager().connectPooledSocket(
                    PerformanceManager.PoolType.VISION,
                    0, // slot index
                    serverHost,
                    serverPort
                )
                
                // Zero-copy I/O
                val buffer = perf.getMemoryPool().acquire()
                try {
                    while (true) {
                        val received = perf.getPerformanceManager()
                            .recvZeroCopy(socket, buffer, 0, buffer.capacity())
                        
                        if (received <= 0) break
                        
                        // Process data...
                        buffer.clear()
                    }
                } finally {
                    perf.getMemoryPool().release(buffer)
                    perf.getPerformanceManager()
                        .returnPooledSocket(PerformanceManager.PoolType.VISION, 0)
                }
            }
        }
    }
    
    /**
     * Example 2: Low-Latency Gaming Connection
     */
    suspend fun lowLatencyGaming(context: Context, gameServer: String) {
        val perf = PerformanceIntegration(context)
        perf.initialize()
        
        // TLS session reuse
        val perfManager = perf.getPerformanceManager()
        val ticket = perfManager.getTLSTicket(gameServer)
        
        if (ticket != null) {
            AppLogger.d(TAG, "Reusing TLS session for $gameServer")
            // Session ticket ile tekrar bağlan
        }
        
        // CPU boost
        perfManager.requestCPUBoost(10000) // 10 saniye
        
        withContext(perf.getIODispatcher()) {
            // Low latency için özel socket ayarları
            val socket = perfManager.getPooledSocket(PerformanceManager.PoolType.RESERVE)
            
            if (socket > 0) {
                // Socket buffer'ları optimize et
                perfManager.setSocketBuffers(
                    socket,
                    sendBuffer = 256 * 1024, // 256 KB
                    recvBuffer = 256 * 1024
                )
                
                // Zero-copy I/O
                val buffer = perf.getMemoryPool().acquire()
                try {
                    // Game data processing...
                } finally {
                    perf.getMemoryPool().release(buffer)
                }
            }
        }
    }
    
    /**
     * Example 3: Batch Processing with Ring Buffer
     */
    suspend fun batchProcessing(context: Context) {
        val perf = PerformanceIntegration(context)
        perf.initialize()
        
        val perfManager = perf.getPerformanceManager()
        
        // Ring buffer oluştur
        val ringBuffer = perfManager.createRingBuffer(1024 * 1024) // 1 MB
        
        try {
            // Producer thread
            launch(perf.getIODispatcher()) {
                val data = ByteArray(4096)
                // ... fill data ...
                perfManager.ringBufferWrite(ringBuffer, data, 0, data.size)
            }
            
            // Consumer thread
            launch(perf.getIODispatcher()) {
                val readBuffer = ByteArray(4096)
                while (true) {
                    val read = perfManager.ringBufferRead(ringBuffer, readBuffer, 0, readBuffer.size)
                    if (read <= 0) break
                    // Process readBuffer...
                }
            }
        } finally {
            perfManager.destroyRingBuffer(ringBuffer)
        }
    }
    
    /**
     * Example 4: Crypto-Accelerated Encryption
     */
    suspend fun cryptoAccelerated(context: Context, data: ByteArray, key: ByteArray) {
        val perf = PerformanceIntegration(context)
        perf.initialize()
        
        val perfManager = perf.getPerformanceManager()
        
        // Check hardware support
        if (!perfManager.hasNEON()) {
            AppLogger.w(TAG, "NEON not available, using fallback")
            return
        }
        
        if (!perfManager.hasCryptoExtensions()) {
            AppLogger.w(TAG, "Crypto extensions not available")
            return
        }
        
        withContext(perf.getCryptoDispatcher()) {
            val inputBuffer = ByteBuffer.allocateDirect(data.size)
            val outputBuffer = ByteBuffer.allocateDirect(data.size)
            val keyBuffer = ByteBuffer.allocateDirect(key.size)
            
            inputBuffer.put(data)
            keyBuffer.put(key)
            
            inputBuffer.flip()
            keyBuffer.flip()
            
            // Hardware-accelerated AES
            perfManager.aes128Encrypt(
                inputBuffer, 0, data.size,
                outputBuffer, 0, keyBuffer
            )
            
            // Get encrypted data
            outputBuffer.flip()
            val encrypted = ByteArray(outputBuffer.remaining())
            outputBuffer.get(encrypted)
            
            AppLogger.d(TAG, "Encrypted ${data.size} bytes using hardware acceleration")
        }
    }
    
    /**
     * Example 5: MTU Optimization for Network Type
     */
    fun optimizeMTU(context: Context, tunFd: Int, networkType: PerformanceManager.NetworkType) {
        val perf = PerformanceIntegration(context)
        perf.initialize()
        
        val perfManager = perf.getPerformanceManager()
        
        // Optimal MTU ayarla
        val mtu = perfManager.setOptimalMTU(tunFd, networkType)
        
        if (mtu > 0) {
            AppLogger.d(TAG, "MTU set to $mtu for ${networkType.name}")
        }
        
        // Socket buffer'ları da optimize et
        perfManager.setSocketBuffers(
            tunFd,
            sendBuffer = 2 * 1024 * 1024, // 2 MB
            recvBuffer = 2 * 1024 * 1024
        )
    }
    
    /**
     * Example 6: Performance Monitoring
     */
    fun monitorPerformance(context: Context) {
        val perf = PerformanceIntegration(context)
        perf.initialize()
        
        val monitor = PerformanceMonitor.getInstance()
        
        // Metrics tracking
        perf.getBurstManager().config.collect { config ->
            val stats = perf.getBurstManager().getStats()
            monitor.updateConnections(stats.activeStreams)
        }
        
        // Throughput tracking
        // (Bu örnekte manuel, gerçek uygulamada network callback'lerden alınır)
        monitor.updateThroughput(
            sentBytes = 1024 * 1024,
            receivedBytes = 2 * 1024 * 1024,
            sentPackets = 1000,
            receivedPackets = 2000
        )
        
        // Log metrics
        AppLogger.d(TAG, monitor.getFormattedMetrics())
    }
}

