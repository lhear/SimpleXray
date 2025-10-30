package com.simplexray.an.performance

import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.model.ConnectionQuality
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerformanceMetricsTest {

    @Test
    fun `calculateQualityScore returns 100 for perfect metrics`() {
        val metrics = PerformanceMetrics(
            latency = 10,
            jitter = 5,
            packetLoss = 0f
        )
        assertThat(metrics.calculateQualityScore()).isEqualTo(100f)
    }

    @Test
    fun `calculateQualityScore penalizes high latency`() {
        val metrics = PerformanceMetrics(latency = 600)
        assertThat(metrics.calculateQualityScore()).isLessThan(50f)
    }

    @Test
    fun `calculateQualityScore penalizes packet loss`() {
        val metrics = PerformanceMetrics(packetLoss = 10f)
        assertThat(metrics.calculateQualityScore()).isEqualTo(0f)
    }

    @Test
    fun `getConnectionQuality returns Excellent for high score`() {
        val metrics = PerformanceMetrics(latency = 20, jitter = 5, packetLoss = 0f)
        assertThat(metrics.getConnectionQuality()).isEqualTo(ConnectionQuality.Excellent)
    }

    @Test
    fun `getConnectionQuality returns Poor for low score`() {
        val metrics = PerformanceMetrics(latency = 500, jitter = 100, packetLoss = 3f)
        assertThat(metrics.getConnectionQuality()).isEqualTo(ConnectionQuality.Poor)
    }

    @Test
    fun `getConnectionQuality returns VeryPoor for very low score`() {
        val metrics = PerformanceMetrics(latency = 1000, jitter = 200, packetLoss = 10f)
        assertThat(metrics.getConnectionQuality()).isEqualTo(ConnectionQuality.VeryPoor)
    }

    @Test
    fun `quality score never goes below zero`() {
        val metrics = PerformanceMetrics(latency = 10000, jitter = 1000, packetLoss = 100f)
        assertThat(metrics.calculateQualityScore()).isAtLeast(0f)
    }

    @Test
    fun `quality score never goes above 100`() {
        val metrics = PerformanceMetrics(latency = 0, jitter = 0, packetLoss = 0f)
        assertThat(metrics.calculateQualityScore()).isAtMost(100f)
    }

    @Test
    fun `connection quality enum has correct colors`() {
        assertThat(ConnectionQuality.Excellent.color).isEqualTo(0xFF4CAF50)
        assertThat(ConnectionQuality.VeryPoor.color).isEqualTo(0xFFF44336)
    }

    @Test
    fun `metrics with moderate latency get Good quality`() {
        val metrics = PerformanceMetrics(latency = 80, jitter = 20, packetLoss = 0.5f)
        assertThat(metrics.getConnectionQuality()).isEqualTo(ConnectionQuality.Good)
    }
}
