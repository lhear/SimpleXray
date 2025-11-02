package com.simplexray.an.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import com.xray.app.stats.command.StatsServiceGrpcKt

object GrpcChannelFactory {
    private val channelRef = AtomicReference<ManagedChannel?>(null)
    private val hostRef = AtomicReference("127.0.0.1")
    private val portRef = AtomicReference(10085)

    @Volatile private var profile: String = "balanced"

    private fun serviceConfigFor(profile: String): Map<String, Any> {
        val cfg = when (profile.lowercase()) {
            "aggressive" -> Triple(7.0, "0.2s" to "3s", 2.5)
            "conservative" -> Triple(3.0, "1s" to "10s", 1.8)
            else -> Triple(5.0, "0.5s" to "5s", 2.0)
        }
        return mapOf(
            "methodConfig" to listOf(
                mapOf(
                    "name" to listOf(mapOf<String, Any>()),
                    "retryPolicy" to mapOf(
                        "maxAttempts" to cfg.first,
                        "initialBackoff" to cfg.second.first,
                        "maxBackoff" to cfg.second.second,
                        "backoffMultiplier" to cfg.third,
                        "retryableStatusCodes" to listOf("UNAVAILABLE", "DEADLINE_EXCEEDED")
                    )
                )
            )
        )
    }

    @Synchronized
    private fun getOrCreateChannel(host: String, port: Int): ManagedChannel {
        val current = channelRef.get()
        if (current != null && !current.isShutdown && hostRef.get() == host && portRef.get() == port) {
            return current
        }
        current?.shutdownNow()
        hostRef.set(host)
        portRef.set(port)
        val ch = OkHttpChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .idleTimeout(5, TimeUnit.MINUTES)
            .enableRetry()
            .defaultServiceConfig(serviceConfigFor(profile))
            .build()
        channelRef.set(ch)
        return ch
    }

    fun statsStub(host: String = hostRef.get(), port: Int = portRef.get()): StatsServiceGrpcKt.StatsServiceCoroutineStub {
        val ch = getOrCreateChannel(host, port)
        return StatsServiceGrpcKt.StatsServiceCoroutineStub(ch)
    }

    @Synchronized
    fun setEndpoint(host: String, port: Int) {
        hostRef.set(host)
        portRef.set(port)
        // force rebuild on next request
        channelRef.getAndSet(null)?.shutdownNow()
    }

    fun currentEndpoint(): Pair<String, Int> = hostRef.get() to portRef.get()

    fun shutdown() {
        channelRef.getAndSet(null)?.shutdownNow()
    }

    @Synchronized
    fun setRetryProfile(newProfile: String) {
        profile = when (newProfile.lowercase()) { "aggressive", "balanced", "conservative" -> newProfile.lowercase() else -> "balanced" }
        // force rebuild on next request
        channelRef.getAndSet(null)?.shutdownNow()
    }
}
