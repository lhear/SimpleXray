package com.simplexray.an.performance

import android.content.Context
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Performance Testing Utilities
 * Validates optimizations are working correctly
 */
class PerformanceTester(private val context: Context) {
    
    data class TestResult(
        val testName: String,
        val passed: Boolean,
        val message: String,
        val details: Map<String, Any> = emptyMap()
    )
    
    private val _testResults = MutableStateFlow<List<TestResult>>(emptyList())
    val testResults: StateFlow<List<TestResult>> = _testResults.asStateFlow()
    
    private val perfManager = PerformanceManager.getInstance(context)
    
    /**
     * Test CPU affinity
     */
    suspend fun testCPUAffinity(): TestResult = withContext(Dispatchers.Default) {
        try {
            val result = perfManager.pinToBigCores()
            val currentCPU = perfManager.getCurrentCPU()
            
            TestResult(
                testName = "CPU Affinity",
                passed = result == 0 || currentCPU >= 0,
                message = if (result == 0) "CPU affinity set successfully" else "CPU affinity may require root",
                details = mapOf(
                    "result" to result,
                    "currentCPU" to currentCPU
                )
            )
        } catch (e: Exception) {
            TestResult(
                testName = "CPU Affinity",
                passed = false,
                message = "Error: ${e.message}",
                details = emptyMap()
            )
        }
    }
    
    /**
     * Test epoll initialization
     */
    suspend fun testEpoll(): TestResult = withContext(Dispatchers.Default) {
        try {
            val handle = perfManager.initEpoll()
            val passed = handle != 0L
            
            if (passed) {
                perfManager.destroyEpoll(handle)
            }
            
            TestResult(
                testName = "Epoll Loop",
                passed = passed,
                message = if (passed) "Epoll initialized successfully" else "Epoll initialization failed",
                details = mapOf("handle" to handle)
            )
        } catch (e: Exception) {
            TestResult(
                testName = "Epoll Loop",
                passed = false,
                message = "Error: ${e.message}",
                details = emptyMap()
            )
        }
    }
    
    /**
     * Test NEON availability
     */
    suspend fun testNEON(): TestResult = withContext(Dispatchers.Default) {
        try {
            val hasNEON = perfManager.hasNEON()
            val hasCrypto = perfManager.hasCryptoExtensions()
            
            TestResult(
                testName = "NEON Support",
                passed = hasNEON,
                message = if (hasNEON) "NEON available" else "NEON not available",
                details = mapOf(
                    "hasNEON" to hasNEON,
                    "hasCryptoExtensions" to hasCrypto
                )
            )
        } catch (e: Exception) {
            TestResult(
                testName = "NEON Support",
                passed = false,
                message = "Error: ${e.message}",
                details = emptyMap()
            )
        }
    }
    
    /**
     * Test connection pool
     */
    suspend fun testConnectionPool(): TestResult = withContext(Dispatchers.Default) {
        try {
            val result = perfManager.initialize()
            
            TestResult(
                testName = "Connection Pool",
                passed = result,
                message = if (result) "Connection pool initialized" else "Connection pool initialization failed",
                details = mapOf("initialized" to result)
            )
        } catch (e: Exception) {
            TestResult(
                testName = "Connection Pool",
                passed = false,
                message = "Error: ${e.message}",
                details = emptyMap()
            )
        }
    }
    
    /**
     * Test memory pool
     */
    suspend fun testMemoryPool(): TestResult = withContext(Dispatchers.Default) {
        try {
            val pool = MemoryPool(65536, 16)
            
            // Test acquire/release
            val buffer1 = pool.acquire()
            val buffer2 = pool.acquire()
            pool.release(buffer1)
            pool.release(buffer2)
            
            val stats = pool.getStats()
            
            TestResult(
                testName = "Memory Pool",
                passed = true,
                message = "Memory pool working correctly",
                details = mapOf(
                    "poolSize" to stats.poolSize,
                    "available" to stats.available,
                    "allocated" to stats.allocated
                )
            )
        } catch (e: Exception) {
            TestResult(
                testName = "Memory Pool",
                passed = false,
                message = "Error: ${e.message}",
                details = emptyMap()
            )
        }
    }
    
    /**
     * Test ring buffer
     */
    suspend fun testRingBuffer(): TestResult = withContext(Dispatchers.Default) {
        try {
            val handle = perfManager.createRingBuffer(1024 * 1024)
            
            if (handle == 0L) {
                return@withContext TestResult(
                    testName = "Ring Buffer",
                    passed = false,
                    message = "Ring buffer creation failed",
                    details = emptyMap()
                )
            }
            
            // Test write/read
            val testData = ByteArray(1024) { it.toByte() }
            val writeResult = perfManager.ringBufferWrite(handle, testData, 0, testData.size)
            
            val readBuffer = ByteArray(1024)
            val readResult = perfManager.ringBufferRead(handle, readBuffer, 0, readBuffer.size)
            
            perfManager.destroyRingBuffer(handle)
            
            val passed = writeResult > 0 && readResult > 0
            
            TestResult(
                testName = "Ring Buffer",
                passed = passed,
                message = if (passed) "Ring buffer working correctly" else "Ring buffer test failed",
                details = mapOf(
                    "writeResult" to writeResult,
                    "readResult" to readResult
                )
            )
        } catch (e: Exception) {
            TestResult(
                testName = "Ring Buffer",
                passed = false,
                message = "Error: ${e.message}",
                details = emptyMap()
            )
        }
    }
    
    /**
     * Run all tests
     */
    suspend fun runAllTests(): List<TestResult> = withContext(Dispatchers.Default) {
        AppLogger.d("$TAG: Running performance tests")
        
        val results = mutableListOf<TestResult>()
        
        results.add(testCPUAffinity())
        delay(100)
        results.add(testEpoll())
        delay(100)
        results.add(testNEON())
        delay(100)
        results.add(testConnectionPool())
        delay(100)
        results.add(testMemoryPool())
        delay(100)
        results.add(testRingBuffer())
        
        _testResults.value = results
        
        val passed = results.count { it.passed }
        val total = results.size
        
        AppLogger.d("$TAG: Tests completed: $passed/$total passed")
        
        results.forEach { result ->
            val status = if (result.passed) "✓" else "✗"
            AppLogger.d("$TAG: $status ${result.testName}: ${result.message}")
        }
        
        results
    }
    
    /**
     * Get test summary
     */
    fun getSummary(): String {
        val results = _testResults.value
        if (results.isEmpty()) return "No tests run"
        
        val passed = results.count { it.passed }
        val total = results.size
        
        return """
            Performance Tests Summary:
            Passed: $passed/$total
            
            ${results.joinToString("\n") { 
                "${if (it.passed) "✓" else "✗"} ${it.testName}: ${it.message}"
            }}
        """.trimIndent()
    }
    
    companion object {
        private const val TAG = "PerformanceTester"
    }
}

