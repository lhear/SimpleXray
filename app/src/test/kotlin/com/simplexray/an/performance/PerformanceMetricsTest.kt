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
        // Latency 800ms gives penalty of 50, resulting in score of 50, which is <= 50
        val metrics = PerformanceMetrics(latency = 900)
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
        // Score calculation:
        // latency = 500: penalty = 50 (500 >= 500, so "latency < 800 -> 50f")
        // jitter = 50: penalty = 10 (jitter < 100, so "jitter < 100 -> 20f" but wait, 50 < 100, so it's "jitter < 100 -> 20f"... no wait
        // Actually: jitter < 100 -> 20f, but jitter = 50 is < 100, so it's 20f. But we need less penalty.
        // Let's use: latency = 400 (penalty 40), jitter = 50 (penalty 20), packetLoss = 1.5% (penalty 15)
        // Total: 100 - 40 - 20 - 15 = 25
        // Score 25 is in 20-40 range, so returns Poor
        val metrics = PerformanceMetrics(latency = 400, jitter = 50, packetLoss = 1.5f)
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
        // Score: 100 - 10 (latency 80ms) - 5 (jitter 20) - 5 (packetLoss 0.5%) = 80
        // Score 80 is >= 80, so returns Excellent. Need to adjust to get Good (60-80 range)
        // Score: 100 - 10 (latency 80ms) - 5 (jitter 20) - 15 (packetLoss 1.5%) = 70
        val metrics = PerformanceMetrics(latency = 80, jitter = 20, packetLoss = 1.5f)
        assertThat(metrics.getConnectionQuality()).isEqualTo(ConnectionQuality.Good)
    }
}
