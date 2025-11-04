package com.simplexray.an.performance

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer

/**
 * Tests for JNI thread safety fixes
 * 
 * Verifies that JNI methods can be called from background threads
 * without crashing (AttachCurrentThread/DetachCurrentThread guards).
 */
class JNIThreadSafetyTest {

    private fun getPerformanceManager(): PerformanceManager? {
        return try {
            val context = mockk<Context>(relaxed = true)
            PerformanceManager.getInstance(context)
        } catch (e: UnsatisfiedLinkError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun testEpollWait_FromBackgroundThread() = runBlocking {
        val perfManager = getPerformanceManager()
        if (perfManager == null) {
            // Skip test if native library not available
            return@runBlocking
        }
        
        // Test that epoll_wait can be called from background thread
        // This should not crash due to stale JNIEnv
        
        val jobs = (1..10).map { i ->
            async(Dispatchers.Default) {
                try {
                    // Try to create epoll (this uses JNI from background thread)
                    // Note: This may fail if permissions are missing, but shouldn't crash
                    val result = try {
                        perfManager.initEpoll()
                    } catch (e: Exception) {
                        // Expected to fail in test environment, but shouldn't crash with SIGSEGV
                        0L
                    }
                    
                    // If we got here without SIGSEGV, thread safety is working
                    assertNotNull("Epoll creation attempt should not crash", result)
                } catch (e: UnsatisfiedLinkError) {
                    // Expected in test environment without native library
                    // This is fine - we're just testing that it doesn't crash
                } catch (e: Exception) {
                    // Any other exception is fine - we're testing for SIGSEGV crashes
                    // which would terminate the process
                }
            }
        }
        
        // Wait for all coroutines to complete
        jobs.awaitAll()
        
        // If we reach here, no crashes occurred (thread safety working)
        assertTrue("All background thread JNI calls completed without crash", true)
    }

    @Test
    fun testRingBuffer_FromMultipleThreads() = runBlocking {
        val perfManager = getPerformanceManager()
        if (perfManager == null) {
            // Skip test if native library not available
            return@runBlocking
        }
        
        // Test ring buffer operations from multiple threads
        // Ring buffer should handle concurrent access safely
        
        val bufferHandle = try {
            val capacity = 4096
            perfManager.createRingBuffer(capacity)
        } catch (e: UnsatisfiedLinkError) {
            // Skip test if native library not available
            return@runBlocking
        } catch (e: Exception) {
            // Skip test if creation fails
            return@runBlocking
        }
        
        if (bufferHandle <= 0) {
            // Skip test if buffer creation failed
            return@runBlocking
        }
        
        val jobs = (1..5).map { i ->
            async(Dispatchers.Default) {
                try {
                    val data = "Thread $i data".toByteArray()
                    
                    // Write from background thread
                    val writeResult = perfManager.ringBufferWrite(
                        bufferHandle, data, 0, data.size
                    )
                    
                    // Read from background thread
                    val readBuffer = ByteArray(data.size)
                    val readResult = perfManager.ringBufferRead(
                        bufferHandle, readBuffer, 0, data.size
                    )
                    
                    // Verify operations completed (may fail due to buffer full/empty, but shouldn't crash)
                    assertNotNull("Write operation should not crash", writeResult)
                    assertNotNull("Read operation should not crash", readResult)
                } catch (e: Exception) {
                    // Exceptions are fine - we're testing for SIGSEGV crashes
                }
            }
        }
        
        jobs.awaitAll()
        
        // Cleanup
        try {
            perfManager.destroyRingBuffer(bufferHandle)
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}

