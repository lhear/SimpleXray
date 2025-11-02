package com.simplexray.an.asn

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AsnCdnMapTest {
    @Test
    fun knownCdnAsnsAreRecognized() {
        assertThat(AsnCdnMap.isCdn(AsnInfo(13335, "Cloudflare"))).isTrue()
        assertThat(AsnCdnMap.isCdn(AsnInfo(54113, "Fastly"))).isTrue()
        assertThat(AsnCdnMap.isCdn(AsnInfo(15169, "Google LLC"))).isTrue()
    }

    @Test
    fun nonCdnAsnReturnsFalse() {
        assertThat(AsnCdnMap.isCdn(AsnInfo(64512, "Private AS"))).isFalse()
    }

    @Test
    fun cdnNameReturnsExpectedProvider() {
        assertThat(AsnCdnMap.name(AsnInfo(13335, "Cloudflare"))).isEqualTo("Cloudflare")
        assertThat(AsnCdnMap.name(AsnInfo(64512, "Private"))).isNull()
    }
}

