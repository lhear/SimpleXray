package com.simplexray.an.testing

import android.content.Context
import com.simplexray.an.domain.DomainClassifier
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stress Test Suite - Tests system under heavy load
 */
class StressTestSuite(
    context: Context,
    testLogger: TestLogger
) : TestSuite("Stress Test Suite", context, testLogger) {
    
    private lateinit var domainClassifier: DomainClassifier
    
    override suspend fun setup() {
        domainClassifier = DomainClassifier(context)
    }
    
    override suspend fun runTests() {
        runTest("Stress - High Volume Domain Classification") {
            val domainCount = 5000
            val domains = (1..domainCount).map { "test-domain-$it.com" }
            
            val startTime = System.currentTimeMillis()
            var successCount = 0
            var errorCount = 0
            
            domains.forEach { domain ->
                try {
                    runBlocking { domainClassifier.classify(domain) }
                    successCount++
                } catch (e: Exception) {
                    errorCount++
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            val throughput = (domainCount * 1000.0 / duration)
            
            logTest(
                "High Volume Domain Classification",
                if (errorCount < domainCount * 0.1) TestStatus.PASSED else TestStatus.FAILED,
                duration,
                details = mapOf(
                    "totalDomains" to domainCount,
                    "successCount" to successCount,
                    "errorCount" to errorCount,
                    "durationMs" to duration,
                    "throughput" to String.format("%.2f", throughput)
                )
            )
            
            if (errorCount > domainCount * 0.1) {
                throw Exception("Too many errors: $errorCount/$domainCount")
            }
        }
        
        runTest("Stress - Concurrent Domain Classification") {
            val threadCount = 50
            val domainsPerThread = 100
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val errorCount = AtomicInteger(0)
            val startTime = System.currentTimeMillis()
            
            repeat(threadCount) { threadId ->
                executor.execute {
                    try {
                        repeat(domainsPerThread) { i ->
                            val domain = "stress-domain-${threadId}-$i.com"
                            try {
                                runBlocking { domainClassifier.classify(domain) }
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                            }
                        }
                    } catch (e: Exception) {
                        errorCount.addAndGet(domainsPerThread)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            
            latch.await(30, TimeUnit.SECONDS)
            val duration = System.currentTimeMillis() - startTime
            executor.shutdown()
            
            val total = threadCount * domainsPerThread
            val success = successCount.get()
            val errors = errorCount.get()
            
            logTest(
                "Concurrent Domain Classification",
                if (errors < total * 0.05) TestStatus.PASSED else TestStatus.FAILED,
                duration,
                details = mapOf(
                    "threadCount" to threadCount,
                    "domainsPerThread" to domainsPerThread,
                    "total" to total,
                    "successCount" to success,
                    "errorCount" to errors,
                    "durationMs" to duration,
                    "throughput" to String.format("%.2f", total * 1000.0 / duration)
                )
            )
            
            if (errors > total * 0.05) {
                throw Exception("Too many errors under stress: $errors/$total")
            }
        }
        
        runTest("Stress - Memory Pressure Test") {
            val runtime = Runtime.getRuntime()
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Create many objects
            val largeList = mutableListOf<String>()
            repeat(10000) { i ->
                largeList.add("stress-test-string-${i}-${"X".repeat(100)}")
            }
            
            System.gc()
            Thread.sleep(100)
            
            val peakMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = peakMemory - initialMemory
            val memoryIncreaseMB = memoryIncrease / 1024.0 / 1024.0
            
            // Clear references
            largeList.clear()
            System.gc()
            Thread.sleep(100)
            
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            
            logTest(
                "Memory Pressure Test",
                if (memoryIncreaseMB < 100) TestStatus.PASSED else TestStatus.FAILED,
                0,
                details = mapOf(
                    "initialMemoryMB" to String.format("%.2f", initialMemory / 1024.0 / 1024.0),
                    "peakMemoryMB" to String.format("%.2f", peakMemory / 1024.0 / 1024.0),
                    "finalMemoryMB" to String.format("%.2f", finalMemory / 1024.0 / 1024.0),
                    "increaseMB" to String.format("%.2f", memoryIncreaseMB),
                    "objectsCreated" to 10000
                )
            )
            
            if (memoryIncreaseMB > 100) {
                throw Exception("Memory increase too high: ${memoryIncreaseMB}MB")
            }
        }
        
        runTest("Stress - Rapid Cache Operations") {
            val operations = 10000
            var successCount = 0
            var errorCount = 0
            val startTime = System.currentTimeMillis()
            
            repeat(operations) { i ->
                try {
                    val domain = "cache-test-${i % 100}.com"
                    runBlocking { domainClassifier.classify(domain) }
                    successCount++
                } catch (e: Exception) {
                    errorCount++
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            logTest(
                "Rapid Cache Operations",
                if (errorCount < operations * 0.01) TestStatus.PASSED else TestStatus.FAILED,
                duration,
                details = mapOf(
                    "operations" to operations,
                    "successCount" to successCount,
                    "errorCount" to errorCount,
                    "durationMs" to duration,
                    "opsPerSecond" to String.format("%.2f", operations * 1000.0 / duration)
                )
            )
            
            if (errorCount > operations * 0.01) {
                throw Exception("Too many cache operation errors: $errorCount/$operations")
            }
        }
        
        runTest("Stress - Long Running Operations") {
            val durationSeconds = 10
            val startTime = System.currentTimeMillis()
            var iterationCount = 0
            
            while (System.currentTimeMillis() - startTime < durationSeconds * 1000) {
                try {
                    val domain = "long-run-test-${iterationCount % 1000}.com"
                    runBlocking { domainClassifier.classify(domain) }
                    iterationCount++
                } catch (e: Exception) {
                    // Continue despite errors
                }
            }
            
            val actualDuration = System.currentTimeMillis() - startTime
            
            logTest(
                "Long Running Operations",
                if (iterationCount > 100) TestStatus.PASSED else TestStatus.FAILED,
                actualDuration,
                details = mapOf(
                    "targetDuration" to durationSeconds,
                    "actualDurationMs" to actualDuration,
                    "iterations" to iterationCount,
                    "iterationsPerSecond" to String.format("%.2f", iterationCount * 1000.0 / actualDuration)
                )
            )
            
            if (iterationCount < 100) {
                throw Exception("Too few iterations in $durationSeconds seconds: $iterationCount")
            }
        }
    }
    
    override suspend fun teardown() {
        if (::domainClassifier.isInitialized) {
            domainClassifier.invalidateCache()
        }
    }
}

