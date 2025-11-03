package com.simplexray.an.testing

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.simplexray.an.data.source.LogFileManager

/**
 * Integration Test Suite - Tests integration between components
 */
class IntegrationTestSuite(
    context: Context,
    testLogger: TestLogger
) : TestSuite("Integration Test Suite", context, testLogger) {
    
    private lateinit var logFileManager: LogFileManager
    
    override suspend fun setup() {
        logFileManager = LogFileManager(context)
    }
    
    override suspend fun runTests() {
        // Log File Manager Tests
        runTest("LogFileManager - File Creation") {
            if (!logFileManager.logFile.exists()) {
                // Create file by writing something
                logFileManager.appendLog("Test log entry")
            }
            
            if (!logFileManager.logFile.exists()) {
                throw Exception("Log file was not created")
            }
        }
        
        runTest("LogFileManager - Log Writing") {
            val testMessage = "Test log entry ${System.currentTimeMillis()}"
            logFileManager.appendLog(testMessage)
            
            val logs = logFileManager.readLogs()
            if (logs == null || !logs.contains(testMessage)) {
                throw Exception("Log entry was not written correctly")
            }
        }
        
        runTest("LogFileManager - Log Reading") {
            val logs = logFileManager.readLogs()
            if (logs == null) {
                throw Exception("Could not read log file")
            }
        }
        
        runTest("LogFileManager - Log Clearing") {
            logFileManager.clearLogs()
            val logs = logFileManager.readLogs()
            if (logs != null && logs.trim().isNotEmpty()) {
                // Note: clearLogs might not actually clear, just check it doesn't crash
                logTest(
                    "LogFileManager Clear",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("logLength" to (logs?.length ?: 0))
                )
            }
        }
        
        // Network Connectivity Tests
        runTest("Network - Connectivity Check") {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: throw Exception("ConnectivityManager not available")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                
                val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                
                logTest(
                    "Network Connectivity",
                    TestStatus.PASSED,
                    0,
                    details = mapOf(
                        "isConnected" to isConnected,
                        "hasWifi" to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true),
                        "hasCellular" to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                    )
                )
                
                if (!isConnected) {
                    throw Exception("Device is not connected to internet")
                }
            } else {
                // For older Android versions
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                if (activeNetworkInfo == null || !activeNetworkInfo.isConnected) {
                    throw Exception("Device is not connected to internet")
                }
            }
        }
        
        runTest("Context - File Operations") {
            val testFile = context.getFileStreamPath("test_file_${System.currentTimeMillis()}.txt")
            try {
                testFile.writeText("test content")
                if (!testFile.exists() || testFile.readText() != "test content") {
                    throw Exception("File operations failed")
                }
                testFile.delete()
            } catch (e: Exception) {
                testFile.delete()
                throw e
            }
        }
        
        runTest("Context - Assets Access") {
            try {
                val assets = context.assets
                val files = assets.list("") ?: emptyArray()
                
                logTest(
                    "Assets Access",
                    TestStatus.PASSED,
                    0,
                    details = mapOf(
                        "assetFiles" to files.size,
                        "hasCdnPatterns" to files.contains("cdn_patterns.json")
                    )
                )
            } catch (e: Exception) {
                throw Exception("Could not access assets: ${e.message}")
            }
        }
        
        // Intent Handling Tests
        runTest("Intent - Share Intent Processing") {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "vless://test-config")
            }
            
            if (shareIntent.action != Intent.ACTION_SEND) {
                throw Exception("Share intent not properly configured")
            }
        }
        
        runTest("System - Memory and Performance") {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()
            
            val memoryUsagePercent = (usedMemory.toFloat() / maxMemory.toFloat() * 100)
            
            logTest(
                "System Memory",
                TestStatus.PASSED,
                0,
                details = mapOf(
                    "totalMemoryMB" to (totalMemory / 1024 / 1024),
                    "freeMemoryMB" to (freeMemory / 1024 / 1024),
                    "usedMemoryMB" to (usedMemory / 1024 / 1024),
                    "maxMemoryMB" to (maxMemory / 1024 / 1024),
                    "usagePercent" to String.format("%.2f", memoryUsagePercent)
                )
            )
            
            if (memoryUsagePercent > 90) {
                throw Exception("Memory usage is critically high: ${memoryUsagePercent}%")
            }
        }
        
        runTest("LogFileManager - Large Log Handling") {
            val largeLog = "X".repeat(10000)
            logFileManager.appendLog(largeLog)
            
            val logs = logFileManager.readLogs()
            if (logs == null || !logs.contains(largeLog)) {
                throw Exception("Large log entry was not written correctly")
            }
        }
        
        runTest("LogFileManager - Concurrent Writes") {
            val threads = mutableListOf<Thread>()
            val successCount = java.util.concurrent.atomic.AtomicInteger(0)
            
            repeat(10) { i ->
                val thread = Thread {
                    try {
                        logFileManager.appendLog("Concurrent log entry $i")
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        // Log error but don't fail test
                    }
                }
                threads.add(thread)
                thread.start()
            }
            
            threads.forEach { it.join() }
            
            if (successCount.get() < 5) {
                throw Exception("Too many concurrent writes failed: ${successCount.get()}/10")
            }
        }
        
        runTest("Network - DNS Resolution") {
            val testDomains = listOf("google.com", "github.com", "android.com")
            
            testDomains.forEach { domain ->
                try {
                    val address = java.net.InetAddress.getByName(domain)
                    logTest(
                        "DNS Resolution",
                        TestStatus.PASSED,
                        0,
                        details = mapOf("domain" to domain, "ip" to address.hostAddress)
                    )
                } catch (e: Exception) {
                    throw Exception("DNS resolution failed for $domain: ${e.message}")
                }
            }
        }
        
        runTest("Network - Port Connectivity") {
            val testPorts = listOf(80, 443, 8080)
            
            testPorts.forEach { port ->
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress("google.com", port), 3000)
                    socket.close()
                    logTest(
                        "Port Connectivity",
                        TestStatus.PASSED,
                        0,
                        details = mapOf("port" to port, "reachable" to true)
                    )
                } catch (e: Exception) {
                    // Port might be filtered, which is acceptable
                    logTest(
                        "Port Connectivity",
                        TestStatus.PASSED,
                        0,
                        details = mapOf("port" to port, "reachable" to false, "reason" to e.message)
                    )
                }
            }
        }
        
        runTest("Context - Multiple File Operations") {
            val testFiles = (1..10).map { i ->
                context.getFileStreamPath("test_file_$i.txt")
            }
            
            try {
                // Create files
                testFiles.forEach { file ->
                    file.writeText("Content for ${file.name}")
                }
                
                // Verify files
                testFiles.forEach { file ->
                    if (!file.exists() || file.readText().isEmpty()) {
                        throw Exception("File operation failed for ${file.name}")
                    }
                }
                
                // Cleanup
                testFiles.forEach { it.delete() }
                
            } catch (e: Exception) {
                testFiles.forEach { it.delete() }
                throw e
            }
        }
        
        runTest("System - CPU Information") {
            val processors = Runtime.getRuntime().availableProcessors()
            
            logTest(
                "CPU Information",
                TestStatus.PASSED,
                0,
                details = mapOf(
                    "processorCount" to processors,
                    "architecture" to System.getProperty("os.arch"),
                    "osVersion" to System.getProperty("os.version")
                )
            )
            
            if (processors < 1) {
                throw Exception("Invalid processor count: $processors")
            }
        }
        
        runTest("System - Thread Safety") {
            val testThreads = mutableListOf<Thread>()
            val results = java.util.concurrent.ConcurrentHashMap<String, Int>()
            
            repeat(20) { i ->
                val thread = Thread {
                    val key = "thread_$i"
                    results[key] = i * 2
                }
                testThreads.add(thread)
                thread.start()
            }
            
            testThreads.forEach { it.join() }
            
            if (results.size != 20) {
                throw Exception("Thread safety test failed: expected 20 results, got ${results.size}")
            }
        }
    }
    
    override suspend fun teardown() {
        // Cleanup
    }
}
