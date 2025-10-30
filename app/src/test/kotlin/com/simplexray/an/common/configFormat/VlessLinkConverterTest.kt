package com.simplexray.an.common.configFormat

import android.content.Context
import com.simplexray.an.prefs.Preferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class VlessLinkConverterTest {

    private lateinit var converter: VlessLinkConverter
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        converter = VlessLinkConverter()
        mockContext = mockk(relaxed = true)
        
        // Mock Preferences
        mockkConstructor(Preferences::class)
        every { anyConstructed<Preferences>().socksPort } returns 10808
    }

    @Test
    fun `detect should return true for vless links`() {
        val vlessLink = "vless://uuid@example.com:443?type=tcp&security=reality#TestServer"
        assertThat(converter.detect(vlessLink)).isTrue()
    }

    @Test
    fun `detect should return false for non-vless links`() {
        assertThat(converter.detect("vmess://test")).isFalse()
        assertThat(converter.detect("https://example.com")).isFalse()
        assertThat(converter.detect("random text")).isFalse()
    }

    @Test
    fun `convert should parse valid vless link with all parameters`() {
        val vlessLink = "vless://test-uuid-123@example.com:443?" +
                "type=tcp&security=reality&sni=example.com&fp=chrome&" +
                "flow=xtls-rprx-vision&pbk=publickey123&sid=shortid456&spx=/path#MyServer"

        val result = converter.convert(mockContext, vlessLink)

        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        assertThat(config).isNotNull()
        assertThat(config!!.name).isEqualTo("MyServer")
        
        // Verify JSON structure
        val jsonConfig = JSONObject(config.content)
        assertThat(jsonConfig.has("log")).isTrue()
        assertThat(jsonConfig.has("inbounds")).isTrue()
        assertThat(jsonConfig.has("outbounds")).isTrue()
    }

    @Test
    fun `convert should use default port 443 when not specified`() {
        val vlessLink = "vless://test-uuid@example.com?type=tcp#TestServer"

        val result = converter.convert(mockContext, vlessLink)

        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        val jsonConfig = JSONObject(config!!.content)
        
        val outbounds = jsonConfig.getJSONArray("outbounds")
        val firstOutbound = outbounds.getJSONObject(0)
        val settings = firstOutbound.getJSONObject("settings")
        val vnext = settings.getJSONArray("vnext")
        val server = vnext.getJSONObject(0)
        
        assertThat(server.getInt("port")).isEqualTo(443)
    }

    @Test
    fun `convert should generate name when fragment is missing`() {
        val vlessLink = "vless://test-uuid@example.com:443?type=tcp"

        val result = converter.convert(mockContext, vlessLink)

        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        assertThat(config!!.name).startsWith("imported_vless_")
    }

    @Test
    fun `convert should fail when host is missing`() {
        val vlessLink = "vless://test-uuid@?type=tcp"

        val result = converter.convert(mockContext, vlessLink)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `convert should fail when user info is missing`() {
        val vlessLink = "vless://example.com:443?type=tcp"

        val result = converter.convert(mockContext, vlessLink)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `convert should use default values for optional parameters`() {
        val vlessLink = "vless://test-uuid@example.com#MinimalServer"

        val result = converter.convert(mockContext, vlessLink)

        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        val jsonConfig = JSONObject(config!!.content)
        
        val outbounds = jsonConfig.getJSONArray("outbounds")
        val firstOutbound = outbounds.getJSONObject(0)
        val streamSettings = firstOutbound.getJSONObject("streamSettings")
        
        // Check default values
        assertThat(streamSettings.getString("network")).isEqualTo("tcp")
        assertThat(streamSettings.getString("security")).isEqualTo("reality")
        
        val realitySettings = streamSettings.getJSONObject("realitySettings")
        assertThat(realitySettings.getString("fingerprint")).isEqualTo("chrome")
        assertThat(realitySettings.getString("spiderX")).isEqualTo("/")
    }

    @Test
    fun `convert should convert h2 type to http`() {
        val vlessLink = "vless://test-uuid@example.com?type=h2#H2Server"

        val result = converter.convert(mockContext, vlessLink)

        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        val jsonConfig = JSONObject(config!!.content)
        
        val outbounds = jsonConfig.getJSONArray("outbounds")
        val firstOutbound = outbounds.getJSONObject(0)
        val streamSettings = firstOutbound.getJSONObject("streamSettings")
        
        assertThat(streamSettings.getString("network")).isEqualTo("http")
    }

    @Test
    fun `convert should use sni parameter for serverName`() {
        val vlessLink = "vless://test-uuid@example.com?sni=custom.sni.com#SNIServer"

        val result = converter.convert(mockContext, vlessLink)

        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        val jsonConfig = JSONObject(config!!.content)
        
        val outbounds = jsonConfig.getJSONArray("outbounds")
        val firstOutbound = outbounds.getJSONObject(0)
        val streamSettings = firstOutbound.getJSONObject("streamSettings")
        val realitySettings = streamSettings.getJSONObject("realitySettings")
        
        assertThat(realitySettings.getString("serverName")).isEqualTo("custom.sni.com")
    }
}
