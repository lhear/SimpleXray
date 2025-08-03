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

    suspend fun getTraffic(): TrafficState = withContext(Dispatchers.IO) {
        val request = QueryStatsRequest.newBuilder()
            .setPattern("outbound")
            .setReset(false)
            .build()

        runCatching { blockingStub.queryStats(request) }
            .getOrNull()
            ?.statList
            ?.groupBy {
                when {
                    it.name.endsWith("uplink") -> "uplink"
                    it.name.endsWith("downlink") -> "downlink"
                    else -> "other"
                }
            }
            ?.let { groups ->
                val uplink = groups["uplink"]?.sumOf { it.value } ?: 0L
                val downlink = groups["downlink"]?.sumOf { it.value } ?: 0L
                TrafficState(uplink, downlink)
            }
            ?: TrafficState(0L, 0L)
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