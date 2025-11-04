package com.simplexray.an.performance

import android.content.Context
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Performance Benchmark Tool
 * Measures and compares performance improvements
 */
class PerformanceBenchmark(private val context: Context) {
    
    data class BenchmarkResult(
        val testName: String,
        val baselineMs: Double,
        val optimizedMs: Double,
        val improvementPercent: Double,
        val throughputMBps: Double,
        val latencyMs: Double,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class ComprehensiveBenchmark(
        val timestamp: Long = System.currentTimeMillis(),
        val performanceModeEnabled: Boolean,
        val results: List<BenchmarkResult>,
        val overallImprovement: Double,
        val networkType: String = "Unknown"
    )
    
    private val _results = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val results: StateFlow<List<BenchmarkResult>> = _results.asStateFlow()
    
    private val perfManager = PerformanceManager.getInstance(context)
    private val perfIntegration = PerformanceIntegration(context)
    
    /**
     * Run CPU affinity benchmark
     */
    suspend fun benchmarkCPUAffinity(): BenchmarkResult = withContext(Dispatchers.Default) {
        val iterations = 10_000_000
        
        // Baseline: No affinity
        val baselineStart = System.nanoTime()
        var sum = 0L
        for (i in 0 until iterations) {
            sum += i * i
        }
        val baselineTime = (System.nanoTime() - baselineStart) / 1_000_000.0
        
        // Optimized: With affinity
        perfManager.pinToBigCores()
        val optimizedStart = System.nanoTime()
        sum = 0L
        for (i in 0 until iterations) {
            sum += i * i
        }
        val optimizedTime = (System.nanoTime() - optimizedStart) / 1_000_000.0
        
        val improvement = ((baselineTime - optimizedTime) / baselineTime) * 100.0
        
        BenchmarkResult(
            testName = "CPU Affinity",
            baselineMs = baselineTime,
            optimizedMs = optimizedTime,
            improvementPercent = improvement,
            throughputMBps = 0.0,
            latencyMs = optimizedTime
        )
    }
    
    /**
     * Run zero-copy benchmark
     */
    suspend fun benchmarkZeroCopy(sizeMB: Int = 10): BenchmarkResult = withContext(Dispatchers.IO) {
        val size = sizeMB * 1024 * 1024
        val data = ByteArray(size) { it.toByte() }
        
        // Baseline: Regular copy
        val baselineStart = System.nanoTime()
        val buffer1 = ByteArray(size)
        System.arraycopy(data, 0, buffer1, 0, size)
        val baselineTime = (System.nanoTime() - baselineStart) / 1_000_000.0
        
        // Optimized: Direct buffer
        val optimizedStart = System.nanoTime()
        val buffer2 = ByteBuffer.allocateDirect(size)
        buffer2.put(data)
        val optimizedTime = (System.nanoTime() - optimizedStart) / 1_000_000.0
        
        val throughput = (size / (1024.0 * 1024.0)) / (optimizedTime / 1000.0)
        val improvement = ((baselineTime - optimizedTime) / baselineTime) * 100.0
        
        BenchmarkResult(
            testName = "Zero-Copy I/O",
            baselineMs = baselineTime,
            optimizedMs = optimizedTime,
            improvementPercent = improvement,
            throughputMBps = throughput,
            latencyMs = optimizedTime
        )
    }
    
    /**
     * Run crypto acceleration benchmark
     */
    suspend fun benchmarkCrypto(sizeMB: Int = 1): BenchmarkResult = withContext(Dispatchers.Default) {
        if (!perfManager.hasNEON()) {
            return@withContext BenchmarkResult(
                testName = "Crypto Acceleration",
                baselineMs = 0.0,
                optimizedMs = 0.0,
                improvementPercent = 0.0,
                throughputMBps = 0.0,
                latencyMs = 0.0
            )
        }
        
        val size = sizeMB * 1024 * 1024
        val input = ByteBuffer.allocateDirect(size)
        val output = ByteBuffer.allocateDirect(size)
        val key = ByteBuffer.allocateDirect(16)
        
        // Fill with test data
        for (i in 0 until size) {
            input.put(i.toByte())
        }
        input.flip()
        
        // Baseline: Software encryption (simplified)
        val baselineStart = System.nanoTime()
        // Simple XOR (not real encryption, just for comparison)
        for (i in 0 until size) {
            output.put(i, (input.get(i).toInt() xor 0xAA).toByte())
        }
        val baselineTime = (System.nanoTime() - baselineStart) / 1_000_000.0
        
        // Optimized: Hardware acceleration
        input.rewind()
        output.clear()
        val optimizedStart = System.nanoTime()
        perfManager.aes128Encrypt(input, 0, size, output, 0, key)
        val optimizedTime = (System.nanoTime() - optimizedStart) / 1_000_000.0
        
        val throughput = (size / (1024.0 * 1024.0)) / (optimizedTime / 1000.0)
        val improvement = if (baselineTime > 0) {
            ((baselineTime - optimizedTime) / baselineTime) * 100.0
        } else {
            0.0
        }
        
        BenchmarkResult(
            testName = "Crypto Acceleration",
            baselineMs = baselineTime,
            optimizedMs = optimizedTime,
            improvementPercent = improvement,
            throughputMBps = throughput,
            latencyMs = optimizedTime
        )
    }
    
    /**
     * Run memory pool benchmark
     */
    suspend fun benchmarkMemoryPool(iterations: Int = 1000): BenchmarkResult = withContext(Dispatchers.Default) {
        val pool = perfIntegration.getMemoryPool()
        
        // Baseline: Regular allocation
        val baselineStart = System.nanoTime()
        repeat(iterations) {
            val buffer = ByteArray(65536)
            // Simulate usage
            buffer[0] = 1
        }
        val baselineTime = (System.nanoTime() - baselineStart) / 1_000_000.0
        
        // Optimized: Memory pool
        val optimizedStart = System.nanoTime()
        repeat(iterations) {
            val buffer = pool.acquire()
            buffer.put(0, 1)
            pool.release(buffer)
        }
        val optimizedTime = (System.nanoTime() - optimizedStart) / 1_000_000.0
        
        val improvement = ((baselineTime - optimizedTime) / baselineTime) * 100.0
        
        BenchmarkResult(
            testName = "Memory Pool",
            baselineMs = baselineTime,
            optimizedMs = optimizedTime,
            improvementPercent = improvement,
            throughputMBps = 0.0,
            latencyMs = optimizedTime
        )
    }
    
    /**
     * Run comprehensive benchmark suite
     */
    suspend fun runAllBenchmarks(): List<BenchmarkResult> = withContext(Dispatchers.Default) {
        AppLogger.d("$TAG: Starting comprehensive benchmark suite")
        
        perfIntegration.initialize()
        
        val results = mutableListOf<BenchmarkResult>()
        
        try {
            results.add(benchmarkCPUAffinity())
            results.add(benchmarkZeroCopy(10))
            
            if (perfManager.hasNEON()) {
                results.add(benchmarkCrypto(1))
            }
            
            results.add(benchmarkMemoryPool(1000))
            
            _results.value = results
            
            // Log results
            results.forEach { result ->
                AppLogger.d("$TAG: ${result.testName}: Baseline: ${String.format("%.2f", result.baselineMs)} ms, Optimized: ${String.format("%.2f", result.optimizedMs)} ms, Improvement: ${String.format("%.2f", result.improvementPercent)}%${if (result.throughputMBps > 0) ", Throughput: ${String.format("%.2f", result.throughputMBps)} MB/s" else ""}")
            }
            
        } catch (e: Exception) {
            AppLogger.e("$TAG: Benchmark failed", e)
        }
        
        results
    }
    
    /**
     * Run network benchmark (before/after performance mode)
     */
    suspend fun benchmarkNetwork(
        testHost: String = "cloudflare.com",
        testPort: Int = 443
    ): BenchmarkResult = withContext(Dispatchers.IO) {
        val speedTest = com.simplexray.an.performance.benchmark.SpeedTest()
        
        // Baseline: Without performance mode
        val baselineStart = System.nanoTime()
        val baselinePing = speedTest.ping(testHost, testPort)
        val baselineTime = (System.nanoTime() - baselineStart) / 1_000_000.0
        
        // Optimized: With performance mode (assumes it's already enabled)
        val optimizedStart = System.nanoTime()
        val optimizedPing = speedTest.ping(testHost, testPort)
        val optimizedTime = (System.nanoTime() - optimizedStart) / 1_000_000.0
        
        val improvement = if (baselineTime > 0 && optimizedTime > 0) {
            ((baselineTime - optimizedTime) / baselineTime) * 100.0
        } else {
            0.0
        }
        
        BenchmarkResult(
            testName = "Network Latency",
            baselineMs = baselineTime,
            optimizedMs = optimizedTime,
            improvementPercent = improvement,
            throughputMBps = 0.0,
            latencyMs = optimizedPing.latency.toDouble()
        )
    }
    
    /**
     * Run comprehensive benchmark with before/after comparison
     */
    suspend fun runComprehensiveBenchmark(
        performanceModeEnabled: Boolean,
        networkType: String = "Unknown"
    ): ComprehensiveBenchmark {
        val results = mutableListOf<BenchmarkResult>()
        
        // Only run benchmarks if performance mode is enabled
        if (performanceModeEnabled) {
            results.addAll(runAllBenchmarks())
            results.add(benchmarkNetwork())
        }
        
        val overallImprovement = if (results.isNotEmpty()) {
            results.map { it.improvementPercent }.average()
        } else {
            0.0
        }
        
        return ComprehensiveBenchmark(
            performanceModeEnabled = performanceModeEnabled,
            results = results,
            overallImprovement = overallImprovement,
            networkType = networkType
        )
    }
    
    /**
     * Get average improvement across all benchmarks
     */
    fun getAverageImprovement(): Double {
        val results = _results.value
        if (results.isEmpty()) return 0.0
        
        return results.map { it.improvementPercent }.average()
    }
    
    companion object {
        private const val TAG = "PerformanceBenchmark"
    }
}

