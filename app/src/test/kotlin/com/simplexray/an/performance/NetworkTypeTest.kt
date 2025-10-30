package com.simplexray.an.performance

import com.simplexray.an.performance.model.NetworkType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkTypeTest {

    @Test
    fun `WiFi has higher bandwidth than mobile`() {
        assertThat(NetworkType.WiFi.bandwidthEstimate)
            .isGreaterThan(NetworkType.Mobile4G.bandwidthEstimate)
    }

    @Test
    fun `5G has higher bandwidth than 4G`() {
        assertThat(NetworkType.Mobile5G.bandwidthEstimate)
            .isGreaterThan(NetworkType.Mobile4G.bandwidthEstimate)
    }

    @Test
    fun `Ethernet has lowest latency`() {
        val allTypes = listOf(
            NetworkType.WiFi,
            NetworkType.Ethernet,
            NetworkType.Mobile5G,
            NetworkType.Mobile4G
        )
        assertThat(NetworkType.Ethernet.latencyEstimate)
            .isEqualTo(allTypes.minOf { it.latencyEstimate })
    }

    @Test
    fun `WiFi is not metered`() {
        assertThat(NetworkType.WiFi.isMetered).isFalse()
    }

    @Test
    fun `Mobile connections are metered`() {
        assertThat(NetworkType.Mobile5G.isMetered).isTrue()
        assertThat(NetworkType.Mobile4G.isMetered).isTrue()
        assertThat(NetworkType.Mobile3G.isMetered).isTrue()
    }

    @Test
    fun `WiFi config adjustment is aggressive`() {
        assertThat(NetworkType.WiFi.configAdjustment.aggressiveOptimization).isTrue()
        assertThat(NetworkType.WiFi.configAdjustment.bufferMultiplier).isGreaterThan(1.0f)
    }

    @Test
    fun `2G config adjustment is conservative`() {
        assertThat(NetworkType.Mobile2G.configAdjustment.aggressiveOptimization).isFalse()
        assertThat(NetworkType.Mobile2G.configAdjustment.bufferMultiplier).isLessThan(1.0f)
    }

    @Test
    fun `slower networks have higher timeout multipliers`() {
        assertThat(NetworkType.Mobile3G.configAdjustment.timeoutMultiplier)
            .isGreaterThan(NetworkType.WiFi.configAdjustment.timeoutMultiplier)
    }
}
