package com.simplexray.an.performance

import com.simplexray.an.prefs.Preferences
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
    private lateinit var prefs: Preferences
    
    fun initialize(context: android.content.Context) {
        perfManager = PerformanceManager.getInstance(context)
        prefs = Preferences(context)
        
        // Get thread pool size from preferences (2-8), validated
        val threadPoolSize = prefs.threadPoolSize.coerceIn(2, 8)
        
        // Calculate thread counts: distribute threads across I/O and crypto pools
        // I/O gets 60%, Crypto gets 30%, Control gets 10% (minimum 1)
        val ioThreads = maxOf(1, (threadPoolSize * 0.6).toInt())
        val cryptoThreads = maxOf(1, (threadPoolSize * 0.3).toInt())
        val controlThreads = maxOf(1, threadPoolSize - ioThreads - cryptoThreads)
        
        // I/O dispatcher - pinned to big cores
        ioDispatcher = Executors.newFixedThreadPool(
            ioThreads,
            createThreadFactory("IO-Perf", true) { 
                if (prefs.cpuAffinityEnabled) perfManager.pinToBigCores() else 0
            }
        ).asCoroutineDispatcher()
        
        // Crypto dispatcher - pinned to big cores
        cryptoDispatcher = Executors.newFixedThreadPool(
            cryptoThreads,
            createThreadFactory("Crypto-Perf", true) { 
                if (prefs.cpuAffinityEnabled) perfManager.pinToBigCores() else 0
            }
        ).asCoroutineDispatcher()
        
        // Control dispatcher - pinned to little cores
        controlDispatcher = Executors.newFixedThreadPool(
            controlThreads,
            createThreadFactory("Control-Perf", true) { 
                if (prefs.cpuAffinityEnabled) perfManager.pinToLittleCores() else 0
            }
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

