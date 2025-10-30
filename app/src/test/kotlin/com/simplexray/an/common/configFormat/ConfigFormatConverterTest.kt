package com.simplexray.an.common.configFormat

import android.content.Context
import io.mockk.mockk
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class ConfigFormatConverterTest {

    private val mockContext: Context = mockk(relaxed = true)

    @Test
    fun `knownImplementations should contain all converters`() {
        val implementations = ConfigFormatConverter.knownImplementations
        
        assertThat(implementations).hasSize(2)
        assertThat(implementations.any { it is SimpleXrayFormatConverter }).isTrue()
        assertThat(implementations.any { it is VlessLinkConverter }).isTrue()
    }

    @Test
    fun `convertOrNull should return null for unknown format`() {
        val unknownContent = "unknown://test"
        
        val result = ConfigFormatConverter.convertOrNull(mockContext, unknownContent)
        
        assertThat(result).isNull()
    }

    @Test
    fun `convertOrNull should detect vless links`() {
        val vlessLink = "vless://test-uuid@example.com:443#TestServer"
        
        val result = ConfigFormatConverter.convertOrNull(mockContext, vlessLink)
        
        assertThat(result).isNotNull()
    }

    @Test
    fun `convert should use default format for unknown content`() {
        val unknownContent = "some random config content"
        
        val result = ConfigFormatConverter.convert(mockContext, unknownContent)
        
        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        assertThat(config).isNotNull()
        assertThat(config!!.first).startsWith("imported_share_")
        assertThat(config.second).isEqualTo(unknownContent)
    }

    @Test
    fun `convert should delegate to appropriate converter for known format`() {
        val vlessLink = "vless://test-uuid@example.com:443?type=tcp#KnownFormat"

        val result = ConfigFormatConverter.convert(mockContext, vlessLink)

        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        assertThat(config).isNotNull()
        // For vless links, the name should be from fragment, not "imported_share_"
        assertThat(config!!.first).isEqualTo("KnownFormat")
    }
}
