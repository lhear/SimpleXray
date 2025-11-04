package com.simplexray.an.performance

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread Pool Manager with CPU affinity
 * Creates dedicated dispatchers for I/O, crypto, and control operations
 */
class ThreadPoolManager {
    
    private lateinit var ioDispatcher: CoroutineDispatcher
    private lateinit var cryptoDispatcher: CoroutineDispatcher
    private lateinit var controlDispatcher: CoroutineDispatcher
    
    private lateinit var perfManager: PerformanceManager
    
    fun initialize(context: android.content.Context) {
        perfManager = PerformanceManager.getInstance(context)
        
        // I/O dispatcher - pinned to big cores
        ioDispatcher = Executors.newFixedThreadPool(
            2,
            createThreadFactory("IO-Perf", true) { perfManager.pinToBigCores() }
        ).asCoroutineDispatcher()
        
        // Crypto dispatcher - pinned to big cores
        cryptoDispatcher = Executors.newFixedThreadPool(
            2,
            createThreadFactory("Crypto-Perf", true) { perfManager.pinToBigCores() }
        ).asCoroutineDispatcher()
        
        // Control dispatcher - pinned to little cores
        controlDispatcher = Executors.newFixedThreadPool(
            1,
            createThreadFactory("Control-Perf", true) { perfManager.pinToLittleCores() }
        ).asCoroutineDispatcher()
    }
    
    /**
     * Creates a ThreadFactory that sets CPU affinity when the thread starts
     */
    private fun createThreadFactory(
        namePrefix: String,
        isDaemon: Boolean,
        affinitySetter: () -> Int
    ): ThreadFactory {
        return ThreadFactory { r ->
            Thread({
                // Set CPU affinity from within the thread
                try {
                    affinitySetter()
                } catch (e: Exception) {
                    // If affinity setting fails, continue anyway
                    com.simplexray.an.common.AppLogger.w("Failed to set CPU affinity", e)
                }
                // Execute the actual task
                r.run()
            }, "$namePrefix-${threadCounter.getAndIncrement()}").apply {
                this.isDaemon = isDaemon
            }
        }
    }
    
    fun getIODispatcher(): CoroutineDispatcher = ioDispatcher
    
    fun getCryptoDispatcher(): CoroutineDispatcher = cryptoDispatcher
    
    fun getControlDispatcher(): CoroutineDispatcher = controlDispatcher
    
    companion object {
        private val threadCounter = AtomicInteger(0)
        
        @Volatile
        private var INSTANCE: ThreadPoolManager? = null
        
        fun getInstance(context: android.content.Context): ThreadPoolManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThreadPoolManager().apply {
                    initialize(context)
                    INSTANCE = this
                }
            }
        }
    }
}

