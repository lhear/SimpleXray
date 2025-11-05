package com.simplexray.an.performance

/**
 * Performance Configuration
 * Aggressive performance settings for different use cases
 */
data class PerformanceConfig(
    // CPU
    val enableCPUAffinity: Boolean = true,
    val pinIOToBigCores: Boolean = true,
    val pinCryptoToBigCores: Boolean = true,
    val pinControlToLittleCores: Boolean = true,
    
    // Network
    val enableEpoll: Boolean = true,
    val enableZeroCopy: Boolean = true,
    val enableConnectionPool: Boolean = true,
    val connectionPoolSize: Int = 8,
    
    // Burst Traffic
    val initialWindowSize: Int = 6 * 1024 * 1024, // 6 MB
    val maxConcurrentStreams: Int = 384,
    val adaptiveWindow: Boolean = true,
    
    // TLS
    val enableTLSCache: Boolean = true,
    val tlsCacheSize: Int = 100,
    val tlsCacheTTL: Long = 3600000, // 1 hour
    
    // MTU
    val optimizeMTU: Boolean = true,
    val autoDetectNetworkType: Boolean = true,
    
    // Memory
    val enableMemoryPool: Boolean = true,
    val memoryPoolSize: Int = 16,
    val bufferSize: Int = 65536, // 64 KB
    
    // Crypto
    val enableCryptoAcceleration: Boolean = true,
    val preferHardwareCrypto: Boolean = true,
    
    // Advanced
    val enableKernelPacing: Boolean = true,
    val enableReadAhead: Boolean = true,
    val enableQoS: Boolean = true,
    val enableMMapBatch: Boolean = true,
    
    // Monitoring
    val enablePerformanceMonitoring: Boolean = true,
    
    // Battery
    val batterySaverMode: Boolean = false
) {
    companion object {
        /**
         * Maximum performance profile
         */
        fun maximum(): PerformanceConfig {
            return PerformanceConfig(
                enableCPUAffinity = true,
                pinIOToBigCores = true,
                pinCryptoToBigCores = true,
                pinControlToLittleCores = true,
                enableEpoll = true,
                enableZeroCopy = true,
                enableConnectionPool = true,
                connectionPoolSize = 8,
                initialWindowSize = 8 * 1024 * 1024, // 8 MB
                maxConcurrentStreams = 512,
                adaptiveWindow = true,
                enableTLSCache = true,
                optimizeMTU = true,
                enableMemoryPool = true,
                enableCryptoAcceleration = true,
                enableKernelPacing = true,
                enableReadAhead = true,
                enableQoS = true,
                enableMMapBatch = true,
                enablePerformanceMonitoring = true,
                batterySaverMode = false
            )
        }
        
        /**
         * Balanced profile
         */
        fun balanced(): PerformanceConfig {
            return PerformanceConfig(
                enableCPUAffinity = true,
                pinIOToBigCores = true,
                pinCryptoToBigCores = true,
                pinControlToLittleCores = true,
                enableEpoll = true,
                enableZeroCopy = true,
                enableConnectionPool = true,
                connectionPoolSize = 6,
                initialWindowSize = 4 * 1024 * 1024, // 4 MB
                maxConcurrentStreams = 256,
                adaptiveWindow = true,
                enableTLSCache = true,
                optimizeMTU = true,
                enableMemoryPool = true,
                enableCryptoAcceleration = true,
                enableKernelPacing = false,
                enableReadAhead = true,
                enableQoS = true,
                enableMMapBatch = false,
                enablePerformanceMonitoring = true,
                batterySaverMode = false
            )
        }
        
        /**
         * Battery saver profile
         */
        fun batterySaver(): PerformanceConfig {
            return PerformanceConfig(
                enableCPUAffinity = false,
                pinIOToBigCores = false,
                pinCryptoToBigCores = false,
                pinControlToLittleCores = false,
                enableEpoll = true,
                enableZeroCopy = false,
                enableConnectionPool = true,
                connectionPoolSize = 4,
                initialWindowSize = 2 * 1024 * 1024, // 2 MB
                maxConcurrentStreams = 128,
                adaptiveWindow = true,
                enableTLSCache = true,
                optimizeMTU = false,
                enableMemoryPool = true,
                enableCryptoAcceleration = false,
                enableKernelPacing = false,
                enableReadAhead = false,
                enableQoS = false,
                enableMMapBatch = false,
                enablePerformanceMonitoring = true,
                batterySaverMode = true
            )
        }
    }
}



