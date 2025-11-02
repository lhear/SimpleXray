package com.simplexray.an.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TrafficSnapshot data class and its companion functions.
 */
class TrafficSnapshotTest {

    @Test
    fun `totalBytes calculates sum of rx and tx bytes`() {
        val snapshot = TrafficSnapshot(
            rxBytes = 1000L,
            txBytes = 500L
        )

        assertEquals(1500L, snapshot.totalBytes)
    }

    @Test
    fun `totalRateMbps calculates sum of rx and tx rates`() {
        val snapshot = TrafficSnapshot(
            rxRateMbps = 10.5f,
            txRateMbps = 5.2f
        )

        assertEquals(15.7f, snapshot.totalRateMbps, 0.01f)
    }

    @Test
    fun `formatDownloadSpeed formats Kbps correctly`() {
        val snapshot = TrafficSnapshot(rxRateMbps = 0.5f)

        assertEquals("500 Kbps", snapshot.formatDownloadSpeed())
    }

    @Test
    fun `formatDownloadSpeed formats Mbps correctly`() {
        val snapshot = TrafficSnapshot(rxRateMbps = 25.75f)

        assertEquals("25.75 Mbps", snapshot.formatDownloadSpeed())
    }

    @Test
    fun `formatDownloadSpeed formats Gbps correctly`() {
        val snapshot = TrafficSnapshot(rxRateMbps = 1500f)

        assertEquals("1.50 Gbps", snapshot.formatDownloadSpeed())
    }

    @Test
    fun `formatUploadSpeed formats correctly`() {
        val snapshot = TrafficSnapshot(txRateMbps = 10.25f)

        assertEquals("10.25 Mbps", snapshot.formatUploadSpeed())
    }

    @Test
    fun `formatTotalData formats bytes correctly`() {
        val snapshot = TrafficSnapshot(
            rxBytes = 500L,
            txBytes = 300L
        )

        assertEquals("800 B", snapshot.formatTotalData())
    }

    @Test
    fun `formatTotalData formats KB correctly`() {
        val snapshot = TrafficSnapshot(
            rxBytes = 512_000L,
            txBytes = 512_000L
        )

        assertEquals("1000.00 KB", snapshot.formatTotalData())
    }

    @Test
    fun `formatTotalData formats MB correctly`() {
        val snapshot = TrafficSnapshot(
            rxBytes = 5_242_880L,  // 5 MB
            txBytes = 5_242_880L   // 5 MB
        )

        assertEquals("10.00 MB", snapshot.formatTotalData())
    }

    @Test
    fun `formatTotalData formats GB correctly`() {
        val snapshot = TrafficSnapshot(
            rxBytes = 1_073_741_824L,  // 1 GB
            txBytes = 1_073_741_824L   // 1 GB
        )

        assertEquals("2.00 GB", snapshot.formatTotalData())
    }

    @Test
    fun `calculateDelta returns same snapshot when time delta is zero`() {
        val snapshot1 = TrafficSnapshot(
            timestamp = 1000L,
            rxBytes = 1000L,
            txBytes = 500L
        )
        val snapshot2 = TrafficSnapshot(
            timestamp = 1000L,  // Same timestamp
            rxBytes = 2000L,
            txBytes = 1000L
        )

        val result = TrafficSnapshot.calculateDelta(snapshot1, snapshot2)

        assertEquals(snapshot2, result)
    }

    @Test
    fun `calculateDelta calculates rates correctly for 1 second interval`() {
        val snapshot1 = TrafficSnapshot(
            timestamp = 0L,
            rxBytes = 0L,
            txBytes = 0L
        )
        val snapshot2 = TrafficSnapshot(
            timestamp = 1000L,  // 1 second later
            rxBytes = 1_000_000L,  // 1 MB downloaded
            txBytes = 500_000L     // 0.5 MB uploaded
        )

        val result = TrafficSnapshot.calculateDelta(snapshot1, snapshot2)

        // (1_000_000 bytes/sec * 8) / 1_000_000 = 8 Mbps
        assertEquals(8.0f, result.rxRateMbps, 0.01f)
        // (500_000 bytes/sec * 8) / 1_000_000 = 4 Mbps
        assertEquals(4.0f, result.txRateMbps, 0.01f)
    }

    @Test
    fun `calculateDelta calculates rates correctly for 500ms interval`() {
        val snapshot1 = TrafficSnapshot(
            timestamp = 0L,
            rxBytes = 0L,
            txBytes = 0L
        )
        val snapshot2 = TrafficSnapshot(
            timestamp = 500L,  // 500ms later
            rxBytes = 500_000L,  // 0.5 MB downloaded
            txBytes = 250_000L   // 0.25 MB uploaded
        )

        val result = TrafficSnapshot.calculateDelta(snapshot1, snapshot2)

        // (500_000 / 0.5 sec * 8) / 1_000_000 = 8 Mbps
        assertEquals(8.0f, result.rxRateMbps, 0.01f)
        // (250_000 / 0.5 sec * 8) / 1_000_000 = 4 Mbps
        assertEquals(4.0f, result.txRateMbps, 0.01f)
    }

    @Test
    fun `calculateDelta handles negative byte delta (counter reset)`() {
        val snapshot1 = TrafficSnapshot(
            timestamp = 0L,
            rxBytes = 1_000_000L,
            txBytes = 500_000L
        )
        val snapshot2 = TrafficSnapshot(
            timestamp = 1000L,
            rxBytes = 100_000L,  // Decreased (counter reset)
            txBytes = 50_000L    // Decreased (counter reset)
        )

        val result = TrafficSnapshot.calculateDelta(snapshot1, snapshot2)

        // Should use maxOf(0, delta) to prevent negative rates
        assertEquals(0.0f, result.rxRateMbps, 0.01f)
        assertEquals(0.0f, result.txRateMbps, 0.01f)
    }

    @Test
    fun `calculateDelta preserves other snapshot properties`() {
        val snapshot1 = TrafficSnapshot(
            timestamp = 0L,
            rxBytes = 0L,
            txBytes = 0L
        )
        val snapshot2 = TrafficSnapshot(
            timestamp = 1000L,
            rxBytes = 1_000_000L,
            txBytes = 500_000L,
            latencyMs = 42L,
            isConnected = true
        )

        val result = TrafficSnapshot.calculateDelta(snapshot1, snapshot2)

        assertEquals(1000L, result.timestamp)
        assertEquals(1_000_000L, result.rxBytes)
        assertEquals(500_000L, result.txBytes)
        assertEquals(42L, result.latencyMs)
        assertTrue(result.isConnected)
    }

    @Test
    fun `TrafficHistory from empty list returns default values`() {
        val history = TrafficHistory.from(emptyList())

        assertTrue(history.snapshots.isEmpty())
        assertEquals(0f, history.maxRxRate, 0.01f)
        assertEquals(0f, history.maxTxRate, 0.01f)
        assertEquals(0f, history.avgRxRate, 0.01f)
        assertEquals(0f, history.avgTxRate, 0.01f)
    }

    @Test
    fun `TrafficHistory from snapshots calculates stats correctly`() {
        val snapshots = listOf(
            TrafficSnapshot(rxRateMbps = 10f, txRateMbps = 5f),
            TrafficSnapshot(rxRateMbps = 20f, txRateMbps = 10f),
            TrafficSnapshot(rxRateMbps = 15f, txRateMbps = 7.5f)
        )

        val history = TrafficHistory.from(snapshots)

        assertEquals(3, history.snapshots.size)
        assertEquals(20f, history.maxRxRate, 0.01f)
        assertEquals(10f, history.maxTxRate, 0.01f)
        assertEquals(15f, history.avgRxRate, 0.01f)
        assertEquals(7.5f, history.avgTxRate, 0.01f)
    }

    @Test
    fun `TrafficHistory from single snapshot`() {
        val snapshots = listOf(
            TrafficSnapshot(rxRateMbps = 25.5f, txRateMbps = 12.3f)
        )

        val history = TrafficHistory.from(snapshots)

        assertEquals(1, history.snapshots.size)
        assertEquals(25.5f, history.maxRxRate, 0.01f)
        assertEquals(12.3f, history.maxTxRate, 0.01f)
        assertEquals(25.5f, history.avgRxRate, 0.01f)
        assertEquals(12.3f, history.avgTxRate, 0.01f)
    }

    @Test
    fun `default TrafficSnapshot has zero values`() {
        val snapshot = TrafficSnapshot()

        assertEquals(0L, snapshot.rxBytes)
        assertEquals(0L, snapshot.txBytes)
        assertEquals(0f, snapshot.rxRateMbps, 0.01f)
        assertEquals(0f, snapshot.txRateMbps, 0.01f)
        assertEquals(-1L, snapshot.latencyMs)
        assertFalse(snapshot.isConnected)
    }

    @Test
    fun `timestamp defaults to current time`() {
        val before = System.currentTimeMillis()
        val snapshot = TrafficSnapshot()
        val after = System.currentTimeMillis()

        assertTrue(snapshot.timestamp >= before)
        assertTrue(snapshot.timestamp <= after)
    }
}
