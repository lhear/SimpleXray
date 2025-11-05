/*
 * Hyper Backend - Kotlin Bridge for Zero-Allocation Packet Pipeline
 * Provides JNI interface to C++ hyper backend components
 */

package com.simplexray.an.hyper.backend

import android.util.Log
import java.nio.ByteBuffer

/**
 * Hyper Backend - High-performance packet processing bridge
 * 
 * Features:
 * - Zero-copy ring buffer transport
 * - Multi-worker crypto pipeline
 * - Burst pacing hints
 * - CPU feature detection
 */
class HyperBackend private constructor() {
    
    companion object {
        private const val TAG = "HyperBackend"
        
        @Volatile
        private var INSTANCE: HyperBackend? = null
        
        fun getInstance(): HyperBackend {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HyperBackend().also { INSTANCE = it }
            }
        }
        
        init {
            System.loadLibrary("perf-net")
        }
    }
    
    private var ringHandle: Long = 0
    private var initialized = false
    
    /**
     * Initialize hyper backend
     */
    fun initialize(ringCapacity: Int = 1024, payloadSize: Int = 1500): Boolean {
        if (initialized) return true
        
        try {
            nativeInitJNI()
            nativeInitBurst()
            ringHandle = nativeCreateRing(ringCapacity, payloadSize)
            
            if (ringHandle == 0L) {
                Log.e(TAG, "Failed to create hyper ring")
                return false
            }
            
            initialized = true
            Log.d(TAG, "Hyper backend initialized: ring=$ringHandle")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize hyper backend", e)
            return false
        }
    }
    
    /**
     * Write packet to ring buffer with metadata
     */
    fun writePacket(
        data: ByteArray,
        offset: Int,
        length: Int,
        timestampNs: Long,
        flags: Int = 0,
        queue: Int = 0
    ): Long {
        if (!initialized || ringHandle == 0L) return 0
        
        return nativeRingWrite(ringHandle, data, offset, length, timestampNs, flags, queue)
    }
    
    /**
     * Read packet from ring buffer
     */
    fun readPacket(): Long {
        if (!initialized || ringHandle == 0L) return 0
        
        return nativeRingRead(ringHandle)
    }
    
    /**
     * Get packet pointer from slot handle (zero-copy)
     */
    fun getPacketPtr(slotHandle: Long): Long {
        return nativeGetPacketPtr(slotHandle)
    }
    
    /**
     * Get packet metadata from slot handle
     */
    fun getPacketMeta(slotHandle: Long): Long {
        return nativeGetPacketMeta(slotHandle)
    }
    
    /**
     * Submit crypto job to worker pool
     */
    fun submitCrypto(slotHandle: Long, outputLen: Int): Long {
        return nativeSubmitCrypto(slotHandle, outputLen)
    }
    
    /**
     * Wait for crypto job completion
     */
    fun waitCrypto(jobHandle: Long, timeoutMs: Long): Int {
        return nativeWaitCrypto(jobHandle, timeoutMs)
    }
    
    /**
     * Get crypto output pointer
     */
    fun getCryptoOutput(jobHandle: Long): Long {
        return nativeGetCryptoOutput(jobHandle)
    }
    
    /**
     * Free crypto job
     */
    fun freeCryptoJob(jobHandle: Long) {
        nativeFreeCryptoJob(jobHandle)
    }
    
    /**
     * Update burst tracker
     */
    fun updateBurst(bytes: Long, timestampNs: Long) {
        nativeUpdateBurst(bytes, timestampNs)
    }
    
    /**
     * Get current burst level
     */
    fun getBurstLevel(): Int {
        return nativeGetBurstLevel()
    }
    
    /**
     * Submit burst hint to backend
     */
    fun submitBurstHint(level: Int) {
        nativeSubmitBurstHint(level)
    }
    
    /**
     * Get CPU capabilities
     */
    fun getCpuCaps(): Int {
        return nativeCpuCaps()
    }
    
    /**
     * Check if NEON is available
     */
    fun hasNEON(): Boolean {
        return nativeHasNEON()
    }
    
    /**
     * Check if AES is available
     */
    fun hasAES(): Boolean {
        return nativeHasAES()
    }
    
    /**
     * Configure hyper backend
     */
    fun configure(batchSize: Int, chunkSize: Int, flags: Int) {
        nativeConfigure(batchSize, chunkSize, flags)
    }
    
    /**
     * Cleanup
     */
    fun destroy() {
        if (ringHandle != 0L) {
            nativeDestroyRing(ringHandle)
            ringHandle = 0
        }
        initialized = false
    }
    
    /**
     * Callback for burst hints (called from C++)
     */
    @JvmStatic
    fun onBurstHint(level: Int) {
        Log.d(TAG, "Burst hint: level=$level")
    }
    
    /**
     * Callback for packet processed (called from C++)
     */
    @JvmStatic
    fun onPacketProcessed(bytes: Long, timestampNs: Long) {
        // Backend can track statistics
    }
    
    // Native methods
    private external fun nativeInitJNI()
    private external fun nativeInitBurst()
    private external fun nativeCreateRing(capacity: Int, payloadSize: Int): Long
    private external fun nativeRingWrite(
        handle: Long, data: ByteArray, offset: Int, length: Int,
        timestampNs: Long, flags: Int, queue: Int
    ): Long
    private external fun nativeRingRead(handle: Long): Long
    private external fun nativeGetPacketPtr(slotHandle: Long): Long
    private external fun nativeGetPacketMeta(slotHandle: Long): Long
    private external fun nativeSubmitCrypto(slotHandle: Long, outputLen: Int): Long
    private external fun nativeWaitCrypto(jobHandle: Long, timeoutMs: Long): Int
    private external fun nativeGetCryptoOutput(jobHandle: Long): Long
    private external fun nativeFreeCryptoJob(jobHandle: Long)
    private external fun nativeUpdateBurst(bytes: Long, timestampNs: Long)
    private external fun nativeGetBurstLevel(): Int
    private external fun nativeSubmitBurstHint(level: Int)
    private external fun nativeCpuCaps(): Int
    private external fun nativeHasNEON(): Boolean
    private external fun nativeHasAES(): Boolean
    private external fun nativeConfigure(batchSize: Int, chunkSize: Int, flags: Int)
    private external fun nativeDestroyRing(handle: Long)
}

