package com.simplexray.an.common

import com.simplexray.an.viewmodel.TrafficState
import com.xray.app.stats.command.QueryStatsRequest
import com.xray.app.stats.command.StatsServiceGrpc
import com.xray.app.stats.command.SysStatsRequest
import com.xray.app.stats.command.SysStatsResponse
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.TimeUnit

class CoreStatsClient(private val channel: ManagedChannel) : Closeable {
    private val blockingStub: StatsServiceGrpc.StatsServiceBlockingStub =
        StatsServiceGrpc.newBlockingStub(channel)

    suspend fun getSystemStats(): SysStatsResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val request = SysStatsRequest.newBuilder().build()
            blockingStub.getSysStats(request)
        }.getOrNull()
    }

    suspend fun getTraffic(): TrafficState? = withContext(Dispatchers.IO) {
        // Query for all stats that contain "traffic" in their pattern
        // Xray stats format: inbound>>>tag>>>traffic>>>uplink/downlink or outbound>>>tag>>>traffic>>>uplink/downlink
        // Or: user>>>tag>>>traffic>>>uplink/downlink
        // From user's perspective:
        // - inbound>>>uplink = upload (data from user to server)
        // - inbound>>>downlink = download (data from server to user)
        // - outbound>>>uplink = upload (data from server to destination)
        // - outbound>>>downlink = download (data from destination to server)
        // For user display, we need to aggregate correctly:
        // - User upload = inbound uplink
        // - User download = inbound downlink
        val request = QueryStatsRequest.newBuilder()
            .setPattern("")  // Empty pattern to get all stats
            .setReset(false)
            .build()

        runCatching { blockingStub.queryStats(request) }
            .getOrNull()
            ?.statList
            ?.let { statList ->
                var uplink = 0L
                var downlink = 0L
                
                // Filter and sum traffic stats
                // Stat names should contain "traffic" and end with "uplink" or "downlink"
                // Only use inbound stats for user display, as they represent traffic from user's perspective
                // Inbound: uplink = user upload, downlink = user download
                for (stat in statList) {
                    val name = stat.name
                    // Check if it's a traffic stat and extract direction
                    if (name.contains("traffic") && name.contains(">>>")) {
                        val parts = name.split(">>>")
                        // Format: inbound/outbound>>>tag>>>traffic>>>uplink/downlink
                        // Or: user>>>tag>>>traffic>>>uplink/downlink
                        val firstPart = parts.firstOrNull() ?: ""
                        val direction = parts.lastOrNull()
                        
                        // Only count inbound stats for user traffic display
                        // Inbound stats directly represent user's upload (uplink) and download (downlink)
                        // Outbound stats represent the same traffic but from server's perspective
                        // User stats can also be used if available
                        when {
                            firstPart == "inbound" -> {
                                // Inbound: uplink = user upload, downlink = user download
                                when (direction) {
                                    "uplink" -> uplink += stat.value
                                    "downlink" -> downlink += stat.value
                                }
                            }
                            firstPart == "user" -> {
                                // User stats: use directly (per-user traffic)
                                when (direction) {
                                    "uplink" -> uplink += stat.value
                                    "downlink" -> downlink += stat.value
                                }
                            }
                            // Ignore outbound stats to avoid double-counting or confusion
                        }
                    }
                }
                
                // Always return TrafficState, even if values are 0
                // This indicates successful query, not failure
                TrafficState(uplink, downlink)
            }
    }

    /**
     * Get connection counts by querying user patterns
     */
    suspend fun getConnectionCounts(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            // Query for user stats patterns to count connections
            val request = QueryStatsRequest.newBuilder()
                .setPattern("user>>>")  // Pattern for user connections
                .setReset(false)
                .build()

            val stats = runCatching { blockingStub.queryStats(request) }
                .getOrNull()
                ?.statList
                ?: emptyList()

            // Count unique users (connections)
            val uniqueUsers = stats
                .mapNotNull { stat ->
                    // Extract user tag from stat name like "user>>>tag>>>traffic>>>uplink"
                    stat.name.split(">>>").getOrNull(1)
                }
                .distinct()
                .count()

            // Get system stats for active goroutines (active connections indicator)
            val sysStats = getSystemStats()
            val activeConnections = sysStats?.numGoroutine ?: 0

            Pair(uniqueUsers, activeConnections)
        } catch (e: Exception) {
            // Fallback: use system stats only
            val sysStats = getSystemStats()
            val goroutines = sysStats?.numGoroutine ?: 0
            Pair(goroutines, goroutines)
        }
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    companion object {
        fun create(host: String, port: Int): CoreStatsClient {
            val channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build()
            return CoreStatsClient(channel)
        }
    }
}