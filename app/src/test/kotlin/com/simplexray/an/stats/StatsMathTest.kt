package com.simplexray.an.stats

import com.google.common.truth.Truth.assertThat
import com.xray.app.stats.command.Stat
import org.junit.Test

class StatsMathTest {
    @Test
    fun computeDelta_accumulatesUpDown() {
        val prev = mapOf(
            "inbound>>>x>>>traffic>>>uplink" to 100L,
            "inbound>>>x>>>traffic>>>downlink" to 200L,
            "outbound>>>y>>>traffic>>>uplink" to 300L,
            "outbound>>>y>>>traffic>>>downlink" to 400L,
        )
        val stats = listOf(
            Stat.newBuilder().setName("inbound>>>x>>>traffic>>>uplink").setValue(150).build(),
            Stat.newBuilder().setName("inbound>>>x>>>traffic>>>downlink").setValue(260).build(),
            Stat.newBuilder().setName("outbound>>>y>>>traffic>>>uplink").setValue(330).build(),
            Stat.newBuilder().setName("outbound>>>y>>>traffic>>>downlink").setValue(500).build(),
        )

        val delta = StatsMath.computeDelta(prev, stats)
        // uplink: (150-100) + (330-300) = 80
        // downlink: (260-200) + (500-400) = 160
        assertThat(delta.upBytes).isEqualTo(80)
        assertThat(delta.downBytes).isEqualTo(160)
        assertThat(delta.nextMap["outbound>>>y>>>traffic>>>downlink"]).isEqualTo(500)
    }

    @Test
    fun bitsPerSecond_handlesDtAndZeroClamp() {
        val bps = StatsMath.bitsPerSecond(bytes = 1000, dtMs = 500)
        assertThat(bps).isEqualTo(16000)
        // When dtMs is 0, it clamps to 1ms: (1000 * 1000 / 1) * 8 = 8000000 bits/s
        val bpsClamp = StatsMath.bitsPerSecond(bytes = 1000, dtMs = 0)
        assertThat(bpsClamp).isEqualTo(8000000)
    }
}

