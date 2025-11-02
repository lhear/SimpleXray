package com.simplexray.an.topology

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopologyLayoutStoreInstrumentedTest {
    @Test
    fun saveAndLoadPositions() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val map = mutableMapOf("n1" to (0.25f to 0.75f), "n2" to (0.5f to 0.5f))
        TopologyLayoutStore.save(ctx, map)
        val loaded = TopologyLayoutStore.load(ctx)
        assertThat(loaded["n1"]).isEqualTo(0.25f to 0.75f)
        assertThat(loaded["n2"]).isEqualTo(0.5f to 0.5f)
    }
}

