package com.simplexray.an.performance

import com.simplexray.an.performance.model.PerformanceProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerformanceProfileTest {

    @Test
    fun `fromId returns correct profile for turbo`() {
        val profile = PerformanceProfile.fromId("turbo")
        assertThat(profile).isEqualTo(PerformanceProfile.Turbo)
    }

    @Test
    fun `fromId returns balanced for unknown id`() {
        val profile = PerformanceProfile.fromId("unknown")
        assertThat(profile).isEqualTo(PerformanceProfile.Balanced)
    }

    @Test
    fun `getAll returns all profiles`() {
        val profiles = PerformanceProfile.getAll()
        assertThat(profiles).hasSize(5)
        assertThat(profiles).contains(PerformanceProfile.Turbo)
        assertThat(profiles).contains(PerformanceProfile.Balanced)
        assertThat(profiles).contains(PerformanceProfile.BatterySaver)
        assertThat(profiles).contains(PerformanceProfile.Gaming)
        assertThat(profiles).contains(PerformanceProfile.Streaming)
    }

    @Test
    fun `turbo mode has largest buffer size`() {
        val turbo = PerformanceProfile.Turbo.config
        val balanced = PerformanceProfile.Balanced.config
        val battery = PerformanceProfile.BatterySaver.config

        assertThat(turbo.bufferSize).isGreaterThan(balanced.bufferSize)
        assertThat(balanced.bufferSize).isGreaterThan(battery.bufferSize)
    }

    @Test
    fun `gaming mode has lowest timeouts`() {
        val gaming = PerformanceProfile.Gaming.config
        val balanced = PerformanceProfile.Balanced.config

        assertThat(gaming.connectionTimeout).isLessThan(balanced.connectionTimeout)
        assertThat(gaming.handshakeTimeout).isLessThan(balanced.handshakeTimeout)
    }

    @Test
    fun `battery saver has longest timeouts`() {
        val battery = PerformanceProfile.BatterySaver.config
        val balanced = PerformanceProfile.Balanced.config

        assertThat(battery.connectionTimeout).isGreaterThan(balanced.connectionTimeout)
        assertThat(battery.handshakeTimeout).isGreaterThan(balanced.handshakeTimeout)
    }

    @Test
    fun `streaming mode has largest buffer for bandwidth`() {
        val streaming = PerformanceProfile.Streaming.config
        val turbo = PerformanceProfile.Turbo.config

        assertThat(streaming.bufferSize).isGreaterThan(turbo.bufferSize)
    }

    @Test
    fun `gaming mode disables compression for lower latency`() {
        val gaming = PerformanceProfile.Gaming.config
        assertThat(gaming.enableCompression).isFalse()
        assertThat(gaming.tcpNoDelay).isTrue()
    }

    @Test
    fun `battery saver reduces parallel connections`() {
        val battery = PerformanceProfile.BatterySaver.config
        val turbo = PerformanceProfile.Turbo.config

        assertThat(battery.parallelConnections).isLessThan(turbo.parallelConnections)
    }

    @Test
    fun `all profiles have valid configurations`() {
        PerformanceProfile.getAll().forEach { profile ->
            val config = profile.config

            assertThat(config.bufferSize).isGreaterThan(0)
            assertThat(config.connectionTimeout).isGreaterThan(0)
            assertThat(config.handshakeTimeout).isGreaterThan(0)
            assertThat(config.idleTimeout).isGreaterThan(0)
            assertThat(config.keepAliveInterval).isGreaterThan(0)
            assertThat(config.dnsCacheTtl).isGreaterThan(0)
            assertThat(config.parallelConnections).isGreaterThan(0)
            assertThat(config.statsUpdateInterval).isGreaterThan(0)
        }
    }
}
