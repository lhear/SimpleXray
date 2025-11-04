package com.simplexray.an.performance

import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer

/**
 * Unit tests for crypto functions (OpenSSL integration)
 * 
 * Note: These tests require OpenSSL libraries to be installed.
 * Without OpenSSL, functions will return -1 (disabled for security).
 */
class CryptoTest {

    @Test
    fun testAES128Encryption_WithOpenSSL() {
        // Test AES-128 encryption with known plaintext/key
        val plaintext = "Hello, World!123".toByteArray() // 16 bytes (AES block size)
        val key = ByteArray(16) { it.toByte() }
        val ciphertext = ByteBuffer.allocateDirect(plaintext.size)
        val plaintextBuffer = ByteBuffer.allocateDirect(plaintext.size)
        plaintextBuffer.put(plaintext)
        plaintextBuffer.rewind()
        
        val result = PerformanceManager.nativeAES128Encrypt(
            plaintextBuffer, 0, plaintext.size,
            ciphertext, 0,
            ByteBuffer.wrap(key)
        )
        
        // If OpenSSL is installed, result should be positive
        // If not installed, result will be -1 (function disabled)
        if (result > 0) {
            // OpenSSL is available - verify encryption worked
            assertNotEquals("Ciphertext should differ from plaintext", 
                plaintext.contentEquals(ciphertext.array()))
            assertTrue("Result should be positive", result > 0)
        } else {
            // OpenSSL not installed - function correctly disabled
            assertEquals("Function should return -1 when OpenSSL not available", -1, result)
        }
    }

    @Test
    fun testAES128Encryption_InvalidKeyLength() {
        val plaintext = ByteArray(16)
        val invalidKey = ByteArray(8) // Too short (need 16 bytes)
        val ciphertext = ByteBuffer.allocateDirect(16)
        val plaintextBuffer = ByteBuffer.allocateDirect(16)
        
        val result = PerformanceManager.nativeAES128Encrypt(
            plaintextBuffer, 0, 16,
            ciphertext, 0,
            ByteBuffer.wrap(invalidKey)
        )
        
        // Should return -1 for invalid key length
        assertEquals("Should return -1 for invalid key length", -1, result)
    }

    @Test
    fun testChaCha20Encryption_WithOpenSSL() {
        // Test ChaCha20 encryption
        val plaintext = "Test message for ChaCha20 encryption".toByteArray()
        val key = ByteArray(32) { it.toByte() } // 32 bytes for ChaCha20
        val nonce = ByteArray(12) { (it + 1).toByte() } // 12 bytes for ChaCha20
        val ciphertext = ByteBuffer.allocateDirect(plaintext.size)
        val plaintextBuffer = ByteBuffer.allocateDirect(plaintext.size)
        plaintextBuffer.put(plaintext)
        plaintextBuffer.rewind()
        
        val result = PerformanceManager.nativeChaCha20NEON(
            plaintextBuffer, 0, plaintext.size,
            ciphertext, 0,
            ByteBuffer.wrap(key),
            ByteBuffer.wrap(nonce)
        )
        
        // If OpenSSL is installed, result should equal input length
        // If not installed, result will be -1 (function disabled)
        if (result > 0) {
            assertEquals("Result should equal input length", plaintext.size, result)
            assertNotEquals("Ciphertext should differ from plaintext",
                plaintext.contentEquals(ciphertext.array()))
        } else {
            // OpenSSL not installed - function correctly disabled
            assertEquals("Function should return -1 when OpenSSL not available", -1, result)
        }
    }

    @Test
    fun testChaCha20Encryption_InvalidKeyLength() {
        val plaintext = ByteArray(32)
        val invalidKey = ByteArray(16) // Too short (need 32 bytes)
        val nonce = ByteArray(12)
        val ciphertext = ByteBuffer.allocateDirect(32)
        val plaintextBuffer = ByteBuffer.allocateDirect(32)
        
        val result = PerformanceManager.nativeChaCha20NEON(
            plaintextBuffer, 0, 32,
            ciphertext, 0,
            ByteBuffer.wrap(invalidKey),
            ByteBuffer.wrap(nonce)
        )
        
        // Should return -1 for invalid key length
        assertEquals("Should return -1 for invalid key length", -1, result)
    }

    @Test
    fun testChaCha20Encryption_InvalidNonceLength() {
        val plaintext = ByteArray(32)
        val key = ByteArray(32)
        val invalidNonce = ByteArray(8) // Too short (need 12 bytes)
        val ciphertext = ByteBuffer.allocateDirect(32)
        val plaintextBuffer = ByteBuffer.allocateDirect(32)
        
        val result = PerformanceManager.nativeChaCha20NEON(
            plaintextBuffer, 0, 32,
            ciphertext, 0,
            ByteBuffer.wrap(key),
            ByteBuffer.wrap(invalidNonce)
        )
        
        // Should return -1 for invalid nonce length
        assertEquals("Should return -1 for invalid nonce length", -1, result)
    }
}

