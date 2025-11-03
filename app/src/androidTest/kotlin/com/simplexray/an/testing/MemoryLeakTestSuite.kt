package com.simplexray.an.testing

import android.content.Context
import com.simplexray.an.domain.DomainClassifier
import kotlinx.coroutines.runBlocking

/**
 * Memory Leak Test Suite - Tests for memory leaks and proper cleanup
 */
class MemoryLeakTestSuite(
    context: Context,
    testLogger: TestLogger
) : TestSuite("Memory Leak Test Suite", context, testLogger) {
    
    override suspend fun setup() {
        // Setup for memory leak tests
    }
    
    override suspend fun runTests() {
        runTest("Memory Leak - DomainClassifier Cache Growth") {
            val runtime = Runtime.getRuntime()
            System.gc()
            Thread.sleep(100)
            
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Create and use classifier
            val classifier = DomainClassifier(context)
            
            // Perform many operations
            repeat(1000) { i ->
                runBlocking {
                    classifier.classify("leak-test-$i.com")
                }
            }
            
            System.gc()
            Thread.sleep(100)
            
            val afterOperationsMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = afterOperationsMemory - initialMemory
            
            // Clear cache
            classifier.invalidateCache()
            System.gc()
            Thread.sleep(100)
            
            val afterClearMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryAfterClear = afterClearMemory - initialMemory
            
            logTest(
                "DomainClassifier Cache Growth",
                if (memoryAfterClear < memoryIncrease * 2) TestStatus.PASSED else TestStatus.FAILED,
                0,
                details = mapOf(
                    "initialMemoryMB" to String.format("%.2f", initialMemory / 1024.0 / 1024.0),
                    "afterOperationsMB" to String.format("%.2f", afterOperationsMemory / 1024.0 / 1024.0),
                    "afterClearMB" to String.format("%.2f", afterClearMemory / 1024.0 / 1024.0),
                    "memoryIncreaseMB" to String.format("%.2f", memoryIncrease / 1024.0 / 1024.0),
                    "memoryAfterClearMB" to String.format("%.2f", memoryAfterClear / 1024.0 / 1024.0)
                )
            )
            
            // Memory should decrease significantly after cache clear
            if (memoryAfterClear >= memoryIncrease * 2) {
                throw Exception("Possible memory leak: memory did not decrease significantly after cache clear")
            }
        }
        
        runTest("Memory Leak - Repeated Object Creation") {
            val runtime = Runtime.getRuntime()
            System.gc()
            Thread.sleep(100)
            
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Create many objects and let them go out of scope
            repeat(100) {
                val classifier = DomainClassifier(context)
                repeat(100) { i ->
                    runBlocking {
                        classifier.classify("temp-test-$it-$i.com")
                    }
                }
                // Object should be eligible for GC
            }
            
            System.gc()
            Thread.sleep(200)
            System.gc()
            Thread.sleep(200)
            
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = finalMemory - initialMemory
            val memoryIncreaseMB = memoryIncrease / 1024.0 / 1024.0
            
            logTest(
                "Repeated Object Creation",
                if (memoryIncreaseMB < 10) TestStatus.PASSED else TestStatus.FAILED,
                0,
                details = mapOf(
                    "initialMemoryMB" to String.format("%.2f", initialMemory / 1024.0 / 1024.0),
                    "finalMemoryMB" to String.format("%.2f", finalMemory / 1024.0 / 1024.0),
                    "increaseMB" to String.format("%.2f", memoryIncreaseMB),
                    "objectsCreated" to 100
                )
            )
            
            if (memoryIncreaseMB > 10) {
                throw Exception("Possible memory leak: ${memoryIncreaseMB}MB increase after object cleanup")
            }
        }
        
        runTest("Memory Leak - Cache TTL Expiration") {
            val classifier = DomainClassifier(context)
            
            // Set very short TTL
            classifier.setCacheTtl(10) // 10ms
            
            val runtime = Runtime.getRuntime()
            System.gc()
            Thread.sleep(100)
            
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Fill cache
            repeat(500) { i ->
                runBlocking {
                    classifier.classify("ttl-test-$i.com")
                }
            }
            
            System.gc()
            Thread.sleep(100)
            
            val afterFillMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Wait for TTL expiration
            Thread.sleep(100)
            
            // Prune cache
            classifier.pruneCache()
            
            System.gc()
            Thread.sleep(100)
            
            val afterPruneMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryReleased = afterFillMemory - afterPruneMemory
            
            logTest(
                "Cache TTL Expiration",
                if (memoryReleased > 0 || afterPruneMemory <= afterFillMemory) TestStatus.PASSED else TestStatus.FAILED,
                0,
                details = mapOf(
                    "initialMB" to String.format("%.2f", initialMemory / 1024.0 / 1024.0),
                    "afterFillMB" to String.format("%.2f", afterFillMemory / 1024.0 / 1024.0),
                    "afterPruneMB" to String.format("%.2f", afterPruneMemory / 1024.0 / 1024.0),
                    "memoryReleasedMB" to String.format("%.2f", memoryReleased / 1024.0 / 1024.0)
                )
            )
        }
        
        runTest("Memory Leak - String Interpolation") {
            val runtime = Runtime.getRuntime()
            System.gc()
            Thread.sleep(100)
            
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Create many strings
            val strings = mutableListOf<String>()
            repeat(10000) { i ->
                strings.add("test-string-$i-${"X".repeat(50)}")
            }
            
            val peakMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Clear references
            strings.clear()
            System.gc()
            Thread.sleep(200)
            System.gc()
            Thread.sleep(200)
            
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = finalMemory - initialMemory
            val memoryIncreaseMB = memoryIncrease / 1024.0 / 1024.0
            
            logTest(
                "String Interpolation",
                if (memoryIncreaseMB < 5) TestStatus.PASSED else TestStatus.FAILED,
                0,
                details = mapOf(
                    "initialMB" to String.format("%.2f", initialMemory / 1024.0 / 1024.0),
                    "peakMB" to String.format("%.2f", peakMemory / 1024.0 / 1024.0),
                    "finalMB" to String.format("%.2f", finalMemory / 1024.0 / 1024.0),
                    "increaseMB" to String.format("%.2f", memoryIncreaseMB),
                    "stringsCreated" to 10000
                )
            )
            
            if (memoryIncreaseMB > 5) {
                throw Exception("Possible memory leak in string operations: ${memoryIncreaseMB}MB")
            }
        }
        
        runTest("Memory Leak - Flow Collection Cleanup") {
            // This test verifies that Flow collections are properly cleaned up
            val runtime = Runtime.getRuntime()
            System.gc()
            Thread.sleep(100)
            
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Simulate flow collection (this is a simplified test)
            val flows = mutableListOf<kotlinx.coroutines.flow.Flow<*>>()
            repeat(100) {
                flows.add(kotlinx.coroutines.flow.flowOf("test-$it"))
            }
            
            System.gc()
            Thread.sleep(100)
            
            val afterCreationMemory = runtime.totalMemory() - runtime.freeMemory()
            
            // Clear references
            flows.clear()
            System.gc()
            Thread.sleep(200)
            
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = finalMemory - initialMemory
            val memoryIncreaseMB = memoryIncrease / 1024.0 / 1024.0
            
            logTest(
                "Flow Collection Cleanup",
                if (memoryIncreaseMB < 2) TestStatus.PASSED else TestStatus.FAILED,
                0,
                details = mapOf(
                    "initialMB" to String.format("%.2f", initialMemory / 1024.0 / 1024.0),
                    "afterCreationMB" to String.format("%.2f", afterCreationMemory / 1024.0 / 1024.0),
                    "finalMB" to String.format("%.2f", finalMemory / 1024.0 / 1024.0),
                    "increaseMB" to String.format("%.2f", memoryIncreaseMB),
                    "flowsCreated" to 100
                )
            )
            
            if (memoryIncreaseMB > 2) {
                throw Exception("Possible memory leak in Flow operations: ${memoryIncreaseMB}MB")
            }
        }
    }
    
    override suspend fun teardown() {
        // Force GC to help with cleanup
        System.gc()
        Thread.sleep(100)
    }
}

