package com.simplexray.an.testing

import android.content.Context
import com.simplexray.an.domain.DomainClassifier
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Performance Test Suite - Tests performance and scalability
 */
class PerformanceTestSuite(
    context: Context,
    testLogger: TestLogger
) : TestSuite("Performance Test Suite", context, testLogger) {
    
    private lateinit var domainClassifier: DomainClassifier
    
    override suspend fun setup() {
        domainClassifier = DomainClassifier(context)
    }
    
    override suspend fun runTests() {
        // DomainClassifier Performance Tests
        runTest("Performance - DomainClassifier Single Classification") {
            val domains = listOf(
                "youtube.com", "twitter.com", "steam.com",
                "cloudflare.com", "netflix.com", "facebook.com"
            )
            
            val durations = mutableListOf<Long>()
            domains.forEach { domain ->
                val start = System.currentTimeMillis()
                runBlocking { domainClassifier.classify(domain) }
                durations.add(System.currentTimeMillis() - start)
            }
            
            val avgDuration = durations.average()
            val maxDuration = durations.maxOrNull() ?: 0
            
            logTest(
                "Single Classification Performance",
                if (avgDuration < 100) TestStatus.PASSED else TestStatus.FAILED,
                durations.sum(),
                details = mapOf(
                    "averageMs" to String.format("%.2f", avgDuration),
                    "maxMs" to maxDuration,
                    "totalTests" to domains.size
                )
            )
            
            if (avgDuration > 100) {
                throw Exception("Average classification time ${avgDuration}ms exceeds 100ms threshold")
            }
        }
        
        runTest("Performance - DomainClassifier Bulk Classification") {
            val testDomains = (1..100).map { "test-domain-$it.com" }
            
            val start = System.currentTimeMillis()
            testDomains.forEach { domain ->
                runBlocking { domainClassifier.classify(domain) }
            }
            val totalDuration = System.currentTimeMillis() - start
            
            val avgDuration = totalDuration.toFloat() / testDomains.size
            
            logTest(
                "Bulk Classification Performance",
                if (avgDuration < 50) TestStatus.PASSED else TestStatus.FAILED,
                totalDuration,
                details = mapOf(
                    "totalDomains" to testDomains.size,
                    "totalMs" to totalDuration,
                    "averageMs" to String.format("%.2f", avgDuration),
                    "throughput" to String.format("%.2f", testDomains.size * 1000.0 / totalDuration)
                )
            )
            
            if (avgDuration > 50) {
                throw Exception("Average classification time ${avgDuration}ms exceeds 50ms threshold for bulk operations")
            }
        }
        
        runTest("Performance - Concurrent Classification") {
            val testDomains = (1..50).map { "concurrent-domain-$it.com" }
            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(testDomains.size)
            val successCount = AtomicInteger(0)
            val errorCount = AtomicInteger(0)
            val startTime = System.currentTimeMillis()
            
            testDomains.forEach { domain ->
                executor.execute {
                    try {
                        runBlocking { domainClassifier.classify(domain) }
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            
            latch.await()
            val duration = System.currentTimeMillis() - startTime
            executor.shutdown()
            
            logTest(
                "Concurrent Classification Performance",
                if (errorCount.get() == 0) TestStatus.PASSED else TestStatus.FAILED,
                duration,
                details = mapOf(
                    "threadCount" to threadCount,
                    "totalDomains" to testDomains.size,
                    "successCount" to successCount.get(),
                    "errorCount" to errorCount.get(),
                    "durationMs" to duration
                )
            )
            
            if (errorCount.get() > 0) {
                throw Exception("$errorCount errors occurred during concurrent classification")
            }
        }
        
        runTest("Performance - Memory Usage Under Load") {
            val runtime = Runtime.getRuntime()
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Perform many operations
            (1..1000).forEach { i ->
                runBlocking {
                    domainClassifier.classify("test-$i.com")
                }
            }
            
            System.gc()
            Thread.sleep(100) // Give GC time to run
            
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = finalMemory - initialMemory
            val memoryIncreaseMB = memoryIncrease / 1024.0 / 1024.0
            
            logTest(
                "Memory Usage Under Load",
                if (memoryIncreaseMB < 50) TestStatus.PASSED else TestStatus.FAILED,
                0,
                details = mapOf(
                    "initialMemoryMB" to String.format("%.2f", initialMemory / 1024.0 / 1024.0),
                    "finalMemoryMB" to String.format("%.2f", finalMemory / 1024.0 / 1024.0),
                    "increaseMB" to String.format("%.2f", memoryIncreaseMB),
                    "operations" to 1000
                )
            )
            
            if (memoryIncreaseMB > 50) {
                throw Exception("Memory increased by ${memoryIncreaseMB}MB, exceeds 50MB threshold")
            }
        }
        
        runTest("Performance - Cache Hit Rate") {
            // Warm up cache
            val warmupDomains = listOf("youtube.com", "twitter.com", "steam.com")
            warmupDomains.forEach { domain ->
                runBlocking { domainClassifier.classify(domain) }
            }
            
            // Measure cache hits
            var cacheHits = 0
            var totalCalls = 0
            val startTime = System.currentTimeMillis()
            
            repeat(100) {
                warmupDomains.forEach { domain ->
                    val callStart = System.currentTimeMillis()
                    runBlocking { domainClassifier.classify(domain) }
                    val callDuration = System.currentTimeMillis() - callStart
                    totalCalls++
                    // Cache hits should be very fast (< 5ms typically)
                    if (callDuration < 10) {
                        cacheHits++
                    }
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            val hitRate = (cacheHits.toFloat() / totalCalls * 100)
            
            logTest(
                "Cache Hit Rate Performance",
                if (hitRate > 80) TestStatus.PASSED else TestStatus.FAILED,
                duration,
                details = mapOf(
                    "totalCalls" to totalCalls,
                    "cacheHits" to cacheHits,
                    "hitRate" to String.format("%.2f", hitRate),
                    "durationMs" to duration
                )
            )
            
            if (hitRate < 80) {
                throw Exception("Cache hit rate $hitRate% is below 80% threshold")
            }
        }
        
        // System Performance Tests
        runTest("Performance - System Resource Usage") {
            val runtime = Runtime.getRuntime()
            val processorCount = runtime.availableProcessors()
            val maxMemory = runtime.maxMemory() / 1024 / 1024 // MB
            val totalMemory = runtime.totalMemory() / 1024 / 1024 // MB
            
            logTest(
                "System Resources",
                TestStatus.PASSED,
                0,
                details = mapOf(
                    "processorCount" to processorCount,
                    "maxMemoryMB" to maxMemory,
                    "totalMemoryMB" to totalMemory
                )
            )
        }
    }
    
    override suspend fun teardown() {
        if (::domainClassifier.isInitialized) {
            domainClassifier.invalidateCache()
        }
    }
}
