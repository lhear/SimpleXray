package com.simplexray.an.performance.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

/**
 * Network speed test and benchmark tools
 */
class SpeedTest {

    /**
     * Ping test result
     */
    data class PingResult(
        val host: String,
        val port: Int,
        val latency: Int, // milliseconds
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Download speed test result
     */
    data class DownloadResult(
        val url: String,
        val bytesDownloaded: Long,
        val durationMs: Long,
        val speedBps: Long, // bytes per second
        val success: Boolean,
        val error: String? = null
    ) {
        val speedKbps: Double get() = speedBps / 1024.0
        val speedMbps: Double get() = speedKbps / 1024.0
    }

    /**
     * Comprehensive benchmark result
     */
    data class BenchmarkResult(
        val timestamp: Long = System.currentTimeMillis(),
        val pingResults: List<PingResult>,
        val downloadResult: DownloadResult?,
        val uploadResult: UploadResult?,
        val packetLoss: Float = 0f,
        val jitter: Int = 0
    ) {
        val averageLatency: Int
            get() = if (pingResults.isNotEmpty()) {
                pingResults.filter { it.success }.map { it.latency }.average().toInt()
            } else 0

        val successRate: Float
            get() = if (pingResults.isNotEmpty()) {
                pingResults.count { it.success }.toFloat() / pingResults.size
            } else 0f
    }

    /**
     * Upload speed test result
     */
    data class UploadResult(
        val url: String,
        val bytesUploaded: Long,
        val durationMs: Long,
        val speedBps: Long,
        val success: Boolean,
        val error: String? = null
    ) {
        val speedKbps: Double get() = speedBps / 1024.0
        val speedMbps: Double get() = speedKbps / 1024.0
    }

    /**
     * Ping a host
     */
    suspend fun ping(host: String, port: Int = 443, timeout: Int = 5000): PingResult = withContext(Dispatchers.IO) {
        try {
            val latency = measureTimeMillis {
                withTimeout(timeout.toLong()) {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), timeout)
                    }
                }
            }.toInt()

            PingResult(
                host = host,
                port = port,
                latency = latency,
                success = true
            )
        } catch (e: Exception) {
            PingResult(
                host = host,
                port = port,
                latency = -1,
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Multiple ping tests to calculate jitter
     */
    suspend fun pingMultiple(
        host: String,
        port: Int = 443,
        count: Int = 10,
        interval: Long = 100
    ): List<PingResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PingResult>()

        repeat(count) {
            val result = ping(host, port)
            results.add(result)
            if (it < count - 1) {
                kotlinx.coroutines.delay(interval)
            }
        }

        results
    }

    /**
     * Calculate jitter from ping results
     */
    fun calculateJitter(pingResults: List<PingResult>): Int {
        val latencies = pingResults.filter { it.success }.map { it.latency }
        if (latencies.size < 2) return 0

        var totalVariation = 0
        for (i in 1 until latencies.size) {
            totalVariation += kotlin.math.abs(latencies[i] - latencies[i - 1])
        }

        return totalVariation / (latencies.size - 1)
    }

    /**
     * Calculate packet loss percentage
     */
    fun calculatePacketLoss(pingResults: List<PingResult>): Float {
        if (pingResults.isEmpty()) return 0f

        val failedCount = pingResults.count { !it.success }
        return (failedCount.toFloat() / pingResults.size) * 100
    }

    /**
     * Download speed test
     */
    suspend fun testDownloadSpeed(
        url: String,
        timeout: Int = 30000,
        minBytes: Long = 1024 * 1024 // 1 MB minimum
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            var bytesDownloaded = 0L
            val durationMs = measureTimeMillis {
                withTimeout(timeout.toLong()) {
                    val connection = java.net.URL(url).openConnection()
                    connection.connectTimeout = timeout
                    connection.readTimeout = timeout

                    connection.getInputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            bytesDownloaded += bytesRead
                            if (bytesDownloaded >= minBytes) break
                        }
                    }
                }
            }

            val speedBps = if (durationMs > 0) {
                (bytesDownloaded * 1000) / durationMs
            } else 0

            DownloadResult(
                url = url,
                bytesDownloaded = bytesDownloaded,
                durationMs = durationMs,
                speedBps = speedBps,
                success = true
            )
        } catch (e: Exception) {
            DownloadResult(
                url = url,
                bytesDownloaded = 0,
                durationMs = 0,
                speedBps = 0,
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Upload speed test
     */
    suspend fun testUploadSpeed(
        url: String,
        dataSize: Long = 1024 * 1024, // 1 MB
        timeout: Int = 30000
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val data = ByteArray(dataSize.toInt()) { it.toByte() }

            val durationMs = measureTimeMillis {
                withTimeout(timeout.toLong()) {
                    val connection = java.net.URL(url).openConnection()
                    connection.connectTimeout = timeout
                    connection.readTimeout = timeout
                    connection.doOutput = true

                    connection.getOutputStream().use { output ->
                        output.write(data)
                        output.flush()
                    }

                    // Read response to complete request
                    connection.getInputStream().use { input ->
                        val buffer = ByteArray(1024)
                        while (input.read(buffer) != -1) {
                            // Consume response
                        }
                    }
                }
            }

            val speedBps = if (durationMs > 0) {
                (dataSize * 1000) / durationMs
            } else 0

            UploadResult(
                url = url,
                bytesUploaded = dataSize,
                durationMs = durationMs,
                speedBps = speedBps,
                success = true
            )
        } catch (e: Exception) {
            UploadResult(
                url = url,
                bytesUploaded = 0,
                durationMs = 0,
                speedBps = 0,
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Run comprehensive benchmark
     */
    suspend fun runBenchmark(
        testHost: String,
        testPort: Int = 443,
        downloadUrl: String? = null,
        uploadUrl: String? = null
    ): BenchmarkResult {
        // Ping tests
        val pingResults = pingMultiple(testHost, testPort, count = 10)

        // Calculate metrics
        val jitter = calculateJitter(pingResults)
        val packetLoss = calculatePacketLoss(pingResults)

        // Download test
        val downloadResult = downloadUrl?.let { testDownloadSpeed(it) }

        // Upload test
        val uploadResult = uploadUrl?.let { testUploadSpeed(it) }

        return BenchmarkResult(
            pingResults = pingResults,
            downloadResult = downloadResult,
            uploadResult = uploadResult,
            packetLoss = packetLoss,
            jitter = jitter
        )
    }

    /**
     * Test server configuration
     */
    data class TestServer(
        val name: String,
        val host: String,
        val port: Int = 443,
        val downloadUrl: String? = null,
        val uploadUrl: String? = null
    )

    companion object {
        /**
         * Common test servers
         */
        val commonTestServers = listOf(
            TestServer(
                name = "Cloudflare",
                host = "1.1.1.1",
                downloadUrl = "https://speed.cloudflare.com/__down?bytes=10485760"
            ),
            TestServer(
                name = "Google",
                host = "8.8.8.8",
                downloadUrl = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png"
            ),
            TestServer(
                name = "Cloudflare DNS",
                host = "one.one.one.one"
            )
        )
    }
}
