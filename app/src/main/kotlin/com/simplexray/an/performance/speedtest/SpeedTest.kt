package com.simplexray.an.performance.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

/**
 * Built-in speed test functionality
 */
class SpeedTest {

    /**
     * Run complete speed test with download, upload, and latency measurements
     */
    fun runSpeedTest(): Flow<SpeedTestResult> = flow {
        emit(SpeedTestResult.InProgress("Testing latency..."))

        // Test latency
        val latency = measureLatency()
        emit(SpeedTestResult.LatencyResult(latency))

        emit(SpeedTestResult.InProgress("Testing download speed..."))

        // Test download speed
        val downloadSpeed = measureDownloadSpeed()
        emit(SpeedTestResult.DownloadResult(downloadSpeed))

        emit(SpeedTestResult.InProgress("Testing upload speed..."))

        // Test upload speed
        val uploadSpeed = measureUploadSpeed()
        emit(SpeedTestResult.UploadResult(uploadSpeed))

        emit(SpeedTestResult.Complete(
            latency = latency,
            downloadSpeed = downloadSpeed,
            uploadSpeed = uploadSpeed
        ))
    }

    /**
     * Measure latency by pinging a server
     */
    private suspend fun measureLatency(): Int = withContext(Dispatchers.IO) {
        try {
            val iterations = 5
            val latencies = mutableListOf<Long>()

            repeat(iterations) {
                val startTime = System.currentTimeMillis()
                val connection = URL("https://www.cloudflare.com/cdn-cgi/trace")
                    .openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    connect()
                }

                val endTime = System.currentTimeMillis()
                val latency = endTime - startTime

                if (connection.responseCode == 200) {
                    latencies.add(latency)
                }

                connection.disconnect()
            }

            // Return median latency
            if (latencies.isEmpty()) 0
            else latencies.sorted()[latencies.size / 2].toInt()
        } catch (e: Exception) {
            android.util.Log.e("SpeedTest", "Latency test failed", e)
            0
        }
    }

    /**
     * Measure download speed
     */
    private suspend fun measureDownloadSpeed(): Long = withContext(Dispatchers.IO) {
        try {
            // Use a 10MB test file
            val testUrl = "https://speed.cloudflare.com/__down?bytes=10000000"
            val connection = URL(testUrl).openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 30000
                connect()
            }

            if (connection.responseCode != 200) {
                connection.disconnect()
                return@withContext 0L
            }

            val startTime = System.currentTimeMillis()
            var totalBytes = 0L
            val buffer = ByteArray(8192)

            connection.inputStream.use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                }
            }

            val endTime = System.currentTimeMillis()
            val durationSeconds = (endTime - startTime) / 1000.0

            connection.disconnect()

            // Return bytes per second
            if (durationSeconds > 0) {
                (totalBytes / durationSeconds).toLong()
            } else {
                0L
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeedTest", "Download test failed", e)
            0L
        }
    }

    /**
     * Measure upload speed
     */
    private suspend fun measureUploadSpeed(): Long = withContext(Dispatchers.IO) {
        try {
            // Upload 5MB of random data
            val testUrl = "https://speed.cloudflare.com/__up"
            val uploadSize = 5 * 1024 * 1024 // 5MB
            val data = ByteArray(uploadSize) { (it % 256).toByte() }

            val connection = URL(testUrl).openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 30000
                setFixedLengthStreamingMode(uploadSize)
                setRequestProperty("Content-Type", "application/octet-stream")
                connect()
            }

            val startTime = System.currentTimeMillis()

            connection.outputStream.use { output ->
                output.write(data)
                output.flush()
            }

            // Read response
            val responseCode = connection.responseCode
            val endTime = System.currentTimeMillis()

            connection.disconnect()

            if (responseCode == 200) {
                val durationSeconds = (endTime - startTime) / 1000.0

                // Return bytes per second
                if (durationSeconds > 0) {
                    (uploadSize / durationSeconds).toLong()
                } else {
                    0L
                }
            } else {
                0L
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeedTest", "Upload test failed", e)
            0L
        }
    }
}

/**
 * Speed test result states
 */
sealed class SpeedTestResult {
    data class InProgress(val message: String) : SpeedTestResult()
    data class LatencyResult(val latencyMs: Int) : SpeedTestResult()
    data class DownloadResult(val speedBytesPerSec: Long) : SpeedTestResult()
    data class UploadResult(val speedBytesPerSec: Long) : SpeedTestResult()
    data class Complete(
        val latency: Int,
        val downloadSpeed: Long,
        val uploadSpeed: Long
    ) : SpeedTestResult()
    data class Error(val message: String) : SpeedTestResult()
}
