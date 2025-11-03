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
    
    private val ioDispatcher: CoroutineDispatcher
    private val cryptoDispatcher: CoroutineDispatcher
    private val controlDispatcher: CoroutineDispatcher
    
    private lateinit var perfManager: PerformanceManager
    
    fun initialize(context: android.content.Context) {
        perfManager = PerformanceManager.getInstance(context)
        
        // I/O dispatcher - pinned to big cores
        ioDispatcher = Executors.newFixedThreadPool(
            2,
            ThreadFactory { r ->
                Thread(r, "IO-Perf-${threadCounter.getAndIncrement()}").apply {
                    isDaemon = true
                    perfManager.pinToBigCores()
                }
            }
        ).asCoroutineDispatcher()
        
        // Crypto dispatcher - pinned to big cores
        cryptoDispatcher = Executors.newFixedThreadPool(
            2,
            ThreadFactory { r ->
                Thread(r, "Crypto-Perf-${threadCounter.getAndIncrement()}").apply {
                    isDaemon = true
                    perfManager.pinToBigCores()
                }
            }
        ).asCoroutineDispatcher()
        
        // Control dispatcher - pinned to little cores
        controlDispatcher = Executors.newFixedThreadPool(
            1,
            ThreadFactory { r ->
                Thread(r, "Control-Perf-${threadCounter.getAndIncrement()}").apply {
                    isDaemon = true
                    perfManager.pinToLittleCores()
                }
            }
        ).asCoroutineDispatcher()
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

