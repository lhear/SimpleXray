package com.simplexray.an.testing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Network Test Suite - Tests network connectivity, latency, and bandwidth
 */
class NetworkTestSuite(
    context: Context,
    testLogger: TestLogger
) : TestSuite("Network Test Suite", context, testLogger) {
    
    private lateinit var connectivityManager: ConnectivityManager
    
    override suspend fun setup() {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    
    override suspend fun runTests() {
        runTest("Network - Connectivity Status") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                
                val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                val hasWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val hasCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                val hasEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                
                logTest(
                    "Network Connectivity Status",
                    if (isConnected) TestStatus.PASSED else TestStatus.FAILED,
                    0,
                    details = mapOf(
                        "isConnected" to isConnected,
                        "hasWifi" to hasWifi,
                        "hasCellular" to hasCellular,
                        "hasEthernet" to hasEthernet,
                        "downloadSpeed" to (capabilities?.linkDownstreamBandwidthKbps ?: -1),
                        "uploadSpeed" to (capabilities?.linkUpstreamBandwidthKbps ?: -1)
                    )
                )
                
                if (!isConnected) {
                    throw Exception("Device is not connected to internet")
                }
            }
        }
        
        runTest("Network - DNS Resolution Speed") {
            val testDomains = listOf("google.com", "github.com", "android.com", "stackoverflow.com")
            val results = mutableListOf<Long>()
            
            testDomains.forEach { domain ->
                val startTime = System.currentTimeMillis()
                try {
                    val address = java.net.InetAddress.getByName(domain)
                    val duration = System.currentTimeMillis() - startTime
                    results.add(duration)
                    
                    logTest(
                        "DNS Resolution",
                        TestStatus.PASSED,
                        duration,
                        details = mapOf("domain" to domain, "ip" to address.hostAddress, "durationMs" to duration)
                    )
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    throw Exception("DNS resolution failed for $domain: ${e.message}")
                }
            }
            
            val avgDuration = results.average()
            if (avgDuration > 1000) {
                throw Exception("DNS resolution too slow: ${avgDuration}ms average")
            }
        }
        
        runTest("Network - HTTP Connectivity") {
            val testUrls = listOf(
                "https://www.google.com",
                "https://www.github.com",
                "https://httpbin.org/get"
            )
            
            testUrls.forEach { urlString ->
                val startTime = System.currentTimeMillis()
                try {
                    val url = URL(urlString)
                    val connection = withContext(Dispatchers.IO) {
                        url.openConnection() as HttpURLConnection
                    }
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "SimpleXray-Test")
                    
                    val responseCode = withContext(Dispatchers.IO) {
                        connection.responseCode
                    }
                    val duration = System.currentTimeMillis() - startTime
                    
                    connection.disconnect()
                    
                    logTest(
                        "HTTP Connectivity",
                        if (responseCode in 200..299) TestStatus.PASSED else TestStatus.FAILED,
                        duration,
                        details = mapOf(
                            "url" to urlString,
                            "responseCode" to responseCode,
                            "durationMs" to duration
                        )
                    )
                    
                    if (responseCode !in 200..299) {
                        throw Exception("HTTP request failed with code $responseCode")
                    }
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    throw Exception("HTTP connectivity test failed for $urlString: ${e.message}")
                }
            }
        }
        
        runTest("Network - Latency Measurement") {
            val testHosts = listOf("8.8.8.8", "1.1.1.1", "google.com")
            val latencies = mutableListOf<Long>()
            
            testHosts.forEach { host ->
                val pingTimes = mutableListOf<Long>()
                repeat(3) {
                    try {
                        val startTime = System.currentTimeMillis()
                        val address = java.net.InetAddress.getByName(host)
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress(address, 80), 3000)
                        socket.close()
                        val duration = System.currentTimeMillis() - startTime
                        pingTimes.add(duration)
                    } catch (e: Exception) {
                        // Ignore individual ping failures
                    }
                }
                
                if (pingTimes.isNotEmpty()) {
                    val avgLatency = pingTimes.average().toLong()
                    latencies.add(avgLatency)
                    
                    logTest(
                        "Network Latency",
                        if (avgLatency < 500) TestStatus.PASSED else TestStatus.FAILED,
                        avgLatency,
                        details = mapOf("host" to host, "avgLatencyMs" to avgLatency, "samples" to pingTimes.size)
                    )
                }
            }
            
            if (latencies.isEmpty()) {
                throw Exception("No latency measurements successful")
            }
        }
        
        runTest("Network - Port Availability") {
            val testPorts = listOf(80, 443, 8080, 8443)
            
            testPorts.forEach { port ->
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress("google.com", port), 2000)
                    socket.close()
                    
                    logTest(
                        "Port Availability",
                        TestStatus.PASSED,
                        0,
                        details = mapOf("port" to port, "reachable" to true)
                    )
                } catch (e: Exception) {
                    logTest(
                        "Port Availability",
                        TestStatus.PASSED,
                        0,
                        details = mapOf("port" to port, "reachable" to false, "reason" to e.message)
                    )
                }
            }
        }
        
        runTest("Network - Multiple Concurrent Connections") {
            val urls = listOf(
                "https://www.google.com",
                "https://www.github.com",
                "https://httpbin.org/get"
            )
            
            val results = mutableListOf<Pair<String, Int>>()
            val threads = mutableListOf<Thread>()
            
            urls.forEach { urlString ->
                val thread = Thread {
                    try {
                        val url = URL(urlString)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.requestMethod = "HEAD"
                        val responseCode = connection.responseCode
                        connection.disconnect()
                        synchronized(results) {
                            results.add(urlString to responseCode)
                        }
                    } catch (e: Exception) {
                        synchronized(results) {
                            results.add(urlString to -1)
                        }
                    }
                }
                threads.add(thread)
                thread.start()
            }
            
            threads.forEach { it.join(10000) }
            
            val successCount = results.count { it.second in 200..299 }
            
            logTest(
                "Concurrent Connections",
                if (successCount >= urls.size / 2) TestStatus.PASSED else TestStatus.FAILED,
                0,
                details = mapOf(
                    "totalUrls" to urls.size,
                    "successCount" to successCount,
                    "results" to results.map { "${it.first}: ${it.second}" }
                )
            )
            
            if (successCount < urls.size / 2) {
                throw Exception("Too many concurrent connections failed: $successCount/${urls.size}")
            }
        }
        
        runTest("Network - Network Type Detection") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                
                val networkTypes = mutableListOf<String>()
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    networkTypes.add("WIFI")
                }
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                    networkTypes.add("CELLULAR")
                }
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) {
                    networkTypes.add("ETHERNET")
                }
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                    networkTypes.add("VPN")
                }
                
                logTest(
                    "Network Type Detection",
                    TestStatus.PASSED,
                    0,
                    details = mapOf(
                        "networkTypes" to networkTypes.joinToString(", "),
                        "isMetered" to (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true),
                        "isRoaming" to (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) != true)
                    )
                )
            }
        }
    }
    
    override suspend fun teardown() {
        // Cleanup
    }
}

