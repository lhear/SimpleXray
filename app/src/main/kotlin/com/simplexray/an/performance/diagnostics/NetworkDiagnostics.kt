package com.simplexray.an.performance.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Network diagnostic tools
 */
class NetworkDiagnostics {

    /**
     * Ping a host and measure response time
     */
    suspend fun ping(host: String, count: Int = 4): PingResult = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<Long>()
            var successCount = 0

            repeat(count) {
                val startTime = System.nanoTime()
                val reachable = InetAddress.getByName(host).isReachable(5000)
                val endTime = System.nanoTime()

                if (reachable) {
                    val latency = (endTime - startTime) / 1_000_000 // Convert to milliseconds
                    results.add(latency)
                    successCount++
                }
            }

            if (results.isEmpty()) {
                PingResult(
                    host = host,
                    packetsTransmitted = count,
                    packetsReceived = 0,
                    packetLoss = 100f,
                    minLatency = 0,
                    maxLatency = 0,
                    avgLatency = 0,
                    success = false,
                    error = "Host unreachable"
                )
            } else {
                PingResult(
                    host = host,
                    packetsTransmitted = count,
                    packetsReceived = successCount,
                    packetLoss = ((count - successCount) / count.toFloat()) * 100,
                    minLatency = results.minOrNull()?.toInt() ?: 0,
                    maxLatency = results.maxOrNull()?.toInt() ?: 0,
                    avgLatency = results.average().toInt(),
                    success = true
                )
            }
        } catch (e: Exception) {
            PingResult(
                host = host,
                packetsTransmitted = count,
                packetsReceived = 0,
                packetLoss = 100f,
                minLatency = 0,
                maxLatency = 0,
                avgLatency = 0,
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * DNS lookup for a hostname
     */
    suspend fun dnsLookup(hostname: String): DnsResult = withContext(Dispatchers.IO) {
        try {
            val startTime = System.nanoTime()
            val addresses = InetAddress.getAllByName(hostname)
            val endTime = System.nanoTime()

            val durationMs = (endTime - startTime) / 1_000_000

            DnsResult(
                hostname = hostname,
                addresses = addresses.map { it.hostAddress ?: "" },
                resolveTimeMs = durationMs.toInt(),
                success = true
            )
        } catch (e: Exception) {
            DnsResult(
                hostname = hostname,
                addresses = emptyList(),
                resolveTimeMs = 0,
                success = false,
                error = e.message ?: "DNS lookup failed"
            )
        }
    }

    /**
     * HTTP connectivity test
     */
    suspend fun httpConnectivityTest(url: String): HttpConnectivityResult = withContext(Dispatchers.IO) {
        try {
            val startTime = System.nanoTime()
            val connection = URL(url).openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "HEAD"
                connectTimeout = 10000
                readTimeout = 10000
                connect()
            }

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            val endTime = System.nanoTime()

            val durationMs = (endTime - startTime) / 1_000_000

            connection.disconnect()

            HttpConnectivityResult(
                url = url,
                responseCode = responseCode,
                responseMessage = responseMessage,
                responseTimeMs = durationMs.toInt(),
                success = responseCode in 200..299
            )
        } catch (e: Exception) {
            HttpConnectivityResult(
                url = url,
                responseCode = 0,
                responseMessage = "",
                responseTimeMs = 0,
                success = false,
                error = e.message ?: "Connection failed"
            )
        }
    }

    /**
     * Check internet connectivity
     */
    suspend fun checkInternetConnectivity(): InternetConnectivityResult = withContext(Dispatchers.IO) {
        val tests = listOf(
            "https://www.google.com",
            "https://www.cloudflare.com",
            "https://1.1.1.1"
        )

        val results = mutableListOf<Boolean>()
        var fastestResponseTime = Int.MAX_VALUE

        tests.forEach { testUrl ->
            try {
                val startTime = System.nanoTime()
                val connection = URL(testUrl).openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "HEAD"
                    connectTimeout = 5000
                    readTimeout = 5000
                    connect()
                }

                val responseCode = connection.responseCode
                val endTime = System.nanoTime()
                val durationMs = ((endTime - startTime) / 1_000_000).toInt()

                connection.disconnect()

                if (responseCode in 200..399) {
                    results.add(true)
                    if (durationMs < fastestResponseTime) {
                        fastestResponseTime = durationMs
                    }
                } else {
                    results.add(false)
                }
            } catch (e: Exception) {
                results.add(false)
            }
        }

        val successCount = results.count { it }

        InternetConnectivityResult(
            isConnected = successCount > 0,
            successfulTests = successCount,
            totalTests = tests.size,
            fastestResponseTimeMs = if (fastestResponseTime != Int.MAX_VALUE) fastestResponseTime else 0,
            reliability = (successCount / tests.size.toFloat()) * 100
        )
    }

    /**
     * Get public IP address
     */
    suspend fun getPublicIp(): PublicIpResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL("https://api.ipify.org?format=text").openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                connect()
            }

            val ip = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText().trim()
            }

            connection.disconnect()

            PublicIpResult(
                ip = ip,
                success = true
            )
        } catch (e: Exception) {
            PublicIpResult(
                ip = "",
                success = false,
                error = e.message ?: "Failed to get public IP"
            )
        }
    }

    /**
     * Run comprehensive network diagnostics
     */
    suspend fun runComprehensiveDiagnostics(target: String = "www.google.com"): ComprehensiveDiagnostics {
        return ComprehensiveDiagnostics(
            internetConnectivity = checkInternetConnectivity(),
            publicIp = getPublicIp(),
            dnsLookup = dnsLookup(target),
            ping = ping(target),
            httpConnectivity = httpConnectivityTest("https://$target")
        )
    }
}

/**
 * Ping result
 */
data class PingResult(
    val host: String,
    val packetsTransmitted: Int,
    val packetsReceived: Int,
    val packetLoss: Float,
    val minLatency: Int,
    val maxLatency: Int,
    val avgLatency: Int,
    val success: Boolean,
    val error: String? = null
)

/**
 * DNS lookup result
 */
data class DnsResult(
    val hostname: String,
    val addresses: List<String>,
    val resolveTimeMs: Int,
    val success: Boolean,
    val error: String? = null
)

/**
 * HTTP connectivity result
 */
data class HttpConnectivityResult(
    val url: String,
    val responseCode: Int,
    val responseMessage: String,
    val responseTimeMs: Int,
    val success: Boolean,
    val error: String? = null
)

/**
 * Internet connectivity result
 */
data class InternetConnectivityResult(
    val isConnected: Boolean,
    val successfulTests: Int,
    val totalTests: Int,
    val fastestResponseTimeMs: Int,
    val reliability: Float
)

/**
 * Public IP result
 */
data class PublicIpResult(
    val ip: String,
    val success: Boolean,
    val error: String? = null
)

/**
 * Comprehensive diagnostics result
 */
data class ComprehensiveDiagnostics(
    val internetConnectivity: InternetConnectivityResult,
    val publicIp: PublicIpResult,
    val dnsLookup: DnsResult,
    val ping: PingResult,
    val httpConnectivity: HttpConnectivityResult
)
