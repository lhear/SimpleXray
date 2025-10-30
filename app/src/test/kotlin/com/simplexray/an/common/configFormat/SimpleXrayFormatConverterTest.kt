package com.simplexray.an.common.configFormat

import android.content.Context
import com.simplexray.an.common.FilenameValidator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class SimpleXrayFormatConverterTest {

    private lateinit var converter: SimpleXrayFormatConverter
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        converter = SimpleXrayFormatConverter()
        mockContext = mockk(relaxed = true)
        
        // Mock FilenameValidator
        mockkObject(FilenameValidator)
        every { FilenameValidator.validateFilename(any(), any()) } returns null
    }

    @Test
    fun `detect should return true for simplexray links`() {
        val link = "simplexray://config/test/data"
        assertThat(converter.detect(link)).isTrue()
    }

    @Test
    fun `detect should return false for non-simplexray links`() {
        assertThat(converter.detect("vless://test")).isFalse()
        assertThat(converter.detect("https://example.com")).isFalse()
        assertThat(converter.detect("random text")).isFalse()
    }

    @Test
    fun `convert should decompress and decode valid simplexray link`() {
        val configName = "TestConfig"
        val configContent = """{"log": {"loglevel": "warning"}}"""
        
        // Compress and encode the content
        val deflater = Deflater()
        deflater.setInput(configContent.toByteArray())
        deflater.finish()
        
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()
        
        val compressed = outputStream.toByteArray()
        val encoded = Base64.getUrlEncoder().encodeToString(compressed)
        
        val simplexrayLink = "simplexray://config/$configName/$encoded"
        
        val result = converter.convert(mockContext, simplexrayLink)
        
        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        assertThat(config).isNotNull()
        assertThat(config!!.first).isEqualTo(configName)
        assertThat(config.second).isEqualTo(configContent)
    }

    @Test
    fun `convert should fail with invalid URI format - missing parts`() {
        val invalidLink = "simplexray://config/onlyonepart"
        
        val result = converter.convert(mockContext, invalidLink)
        
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid simplexray URI format")
    }

    @Test
    fun `convert should fail with invalid URI format - too many parts`() {
        val invalidLink = "simplexray://config/part1/part2/part3"
        
        val result = converter.convert(mockContext, invalidLink)
        
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `convert should fail when filename validation fails`() {
        every { FilenameValidator.validateFilename(any(), any()) } returns "Invalid character in filename"
        
        val configName = "Invalid<>Name"
        val configContent = "{}"
        val deflater = Deflater()
        deflater.setInput(configContent.toByteArray())
        deflater.finish()
        
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()
        
        val compressed = outputStream.toByteArray()
        val encoded = Base64.getUrlEncoder().encodeToString(compressed)
        val simplexrayLink = "simplexray://config/$configName/$encoded"
        
        val result = converter.convert(mockContext, simplexrayLink)
        
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid filename")
    }

    @Test
    fun `convert should handle URL encoded names`() {
        val configName = "Test Config With Spaces"
        val encodedName = "Test%20Config%20With%20Spaces"
        val configContent = "{}"
        
        val deflater = Deflater()
        deflater.setInput(configContent.toByteArray())
        deflater.finish()
        
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()
        
        val compressed = outputStream.toByteArray()
        val encoded = Base64.getUrlEncoder().encodeToString(compressed)
        val simplexrayLink = "simplexray://config/$encodedName/$encoded"
        
        val result = converter.convert(mockContext, simplexrayLink)
        
        assertThat(result.isSuccess).isTrue()
        val config = result.getOrNull()
        assertThat(config!!.first).isEqualTo(configName)
    }
}
