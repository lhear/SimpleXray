package com.simplexray.an.performance

import android.content.Context
import com.simplexray.an.common.AppLogger
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * High-Performance Network Manager
 * 
 * Aggressive performance optimizations for Android networking:
 * - CPU core affinity & pinning
 * - Native epoll loop
 * - Zero-copy I/O
 * - Connection pooling
 * - Crypto acceleration (NEON/ARMv8)
 * - Memory pool management
 * 
 * WARNING: This is a "performance laboratory" mode, not production-ready.
 * Use with caution and proper testing.
 */
class PerformanceManager private constructor(context: Context) {
    
    companion object {
        private const val TAG = "PerformanceManager"
        
        @Volatile
        private var INSTANCE: PerformanceManager? = null
        
        fun getInstance(context: Context): PerformanceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        init {
            try {
                System.loadLibrary("perf-net")
                AppLogger.d(TAG, "Native library 'perf-net' loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                AppLogger.e(TAG, "Failed to load native library 'perf-net'", e)
                // Continue without performance optimizations
            } catch (e: Exception) {
                AppLogger.e(TAG, "Unexpected error loading native library", e)
            }
        }
    }
    
    private val initialized = AtomicBoolean(false)
    private val epollHandle = AtomicLong(0)
    
    // Pool types
    enum class PoolType(val value: Int) {
        H2_STREAM(0),
        VISION(1),
        RESERVE(2)
    }
    
    /**
     * Initialize performance module
     */
    fun initialize(): Boolean {
        if (initialized.compareAndSet(false, true)) {
            try {
                // Initialize connection pool
                nativeInitConnectionPool()
                
                // Request performance CPU governor (best-effort)
                nativeRequestPerformanceGovernor()
                
                AppLogger.d(TAG, "Performance module initialized")
                return true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to initialize performance module", e)
                initialized.set(false)
                return false
            }
        }
        return true
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        if (initialized.compareAndSet(true, false)) {
            try {
                nativeDestroyConnectionPool()
                if (epollHandle.get() != 0L) {
                    nativeDestroyEpoll(epollHandle.get())
                    epollHandle.set(0)
                }
                AppLogger.d(TAG, "Performance module cleaned up")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error during cleanup", e)
            }
        }
    }
    
    // ==================== CPU Affinity ====================
    
    /**
     * Set CPU affinity for current thread
     * @param cpuMask Bitmask (bit 0 = CPU 0, bit 1 = CPU 1, etc.)
     */
    fun setCPUAffinity(cpuMask: Long): Int {
        return nativeSetCPUAffinity(cpuMask)
    }
    
    /**
     * Pin current thread to big cores (typically 4-7)
     */
    fun pinToBigCores(): Int {
        return nativePinToBigCores()
    }
    
    /**
     * Pin current thread to little cores (typically 0-3)
     */
    fun pinToLittleCores(): Int {
        return nativePinToLittleCores()
    }
    
    /**
     * Get current CPU core
     */
    fun getCurrentCPU(): Int {
        return nativeGetCurrentCPU()
    }
    
    // ==================== Epoll Loop ====================
    
    /**
     * Initialize epoll loop
     */
    fun initEpoll(): Long {
        val handle = nativeInitEpoll()
        if (handle != 0L) {
            epollHandle.set(handle)
        }
        return handle
    }
    
    /**
     * Add file descriptor to epoll
     */
    fun epollAdd(fd: Int, events: Int): Int {
        val handle = epollHandle.get()
        return if (handle != 0L) {
            nativeEpollAdd(handle, fd, events)
        } else {
            -1
        }
    }
    
    /**
     * Remove file descriptor from epoll
     */
    fun epollRemove(fd: Int): Int {
        val handle = epollHandle.get()
        return if (handle != 0L) {
            nativeEpollRemove(handle, fd)
        } else {
            -1
        }
    }
    
    /**
     * Wait for events (blocking)
     * @return Array of packed events: [fd << 32 | events, ...]
     */
    fun epollWait(maxEvents: Int = 256): LongArray {
        val handle = epollHandle.get()
        return if (handle != 0L) {
            val events = LongArray(maxEvents)
            val count = nativeEpollWait(handle, events)
            if (count > 0) {
                events.sliceArray(0 until count)
            } else {
                LongArray(0)
            }
        } else {
            LongArray(0)
        }
    }
    
    // ==================== Zero-Copy I/O ====================
    
    /**
     * Receive with zero-copy
     */
    fun recvZeroCopy(fd: Int, buffer: ByteBuffer, offset: Int, length: Int): Int {
        return nativeRecvZeroCopy(fd, buffer, offset, length)
    }
    
    /**
     * Send with zero-copy
     */
    fun sendZeroCopy(fd: Int, buffer: ByteBuffer, offset: Int, length: Int): Int {
        return nativeSendZeroCopy(fd, buffer, offset, length)
    }
    
    /**
     * Scatter-gather receive
     */
    fun recvMsg(fd: Int, buffers: Array<ByteBuffer>, lengths: IntArray): Int {
        return nativeRecvMsg(fd, buffers, lengths)
    }
    
    /**
     * Allocate direct ByteBuffer
     */
    fun allocateDirectBuffer(capacity: Int): ByteBuffer? {
        return nativeAllocateDirectBuffer(capacity)
    }
    
    // ==================== Connection Pool ====================
    
    /**
     * Get a socket from pool
     */
    fun getPooledSocket(poolType: PoolType): Int {
        return nativeGetPooledSocket(poolType.value)
    }
    
    /**
     * Connect pooled socket
     */
    fun connectPooledSocket(poolType: PoolType, slotIndex: Int, host: String, port: Int): Int {
        return nativeConnectPooledSocket(poolType.value, slotIndex, host, port)
    }
    
    /**
     * Return socket to pool
     */
    fun returnPooledSocket(poolType: PoolType, slotIndex: Int) {
        nativeReturnPooledSocket(poolType.value, slotIndex)
    }
    
    // ==================== Crypto Acceleration ====================
    
    /**
     * Check if NEON is available
     */
    fun hasNEON(): Boolean {
        return nativeHasNEON()
    }
    
    /**
     * Check if ARMv8 Crypto Extensions are available
     */
    fun hasCryptoExtensions(): Boolean {
        return nativeHasCryptoExtensions()
    }
    
    /**
     * AES-128 encrypt (hardware accelerated)
     */
    fun aes128Encrypt(
        input: ByteBuffer, inputOffset: Int, inputLen: Int,
        output: ByteBuffer, outputOffset: Int, key: ByteBuffer
    ): Int {
        return nativeAES128Encrypt(input, inputOffset, inputLen, output, outputOffset, key)
    }
    
    /**
     * ChaCha20 encrypt using NEON
     */
    fun chaCha20NEON(
        input: ByteBuffer, inputOffset: Int, inputLen: Int,
        output: ByteBuffer, outputOffset: Int, key: ByteBuffer, nonce: ByteBuffer
    ): Int {
        return nativeChaCha20NEON(input, inputOffset, inputLen, output, outputOffset, key, nonce)
    }
    
    /**
     * Prefetch data into CPU cache
     */
    fun prefetch(buffer: ByteBuffer, offset: Int, length: Int) {
        nativePrefetch(buffer, offset, length)
    }
    
    // ==================== TLS Session Management ====================
    
    /**
     * Store TLS session ticket for reuse
     */
    fun storeTLSTicket(host: String, ticket: ByteArray): Int {
        return nativeStoreTLSTicket(host, ticket)
    }
    
    /**
     * Get stored TLS session ticket
     */
    fun getTLSTicket(host: String): ByteArray? {
        return nativeGetTLSTicket(host)
    }
    
    /**
     * Clear TLS session cache
     */
    fun clearTLSCache() {
        nativeClearTLSCache()
    }
    
    // ==================== MTU Tuning ====================
    
    enum class NetworkType(val value: Int) {
        LTE(0),
        FIVE_G(1),
        WIFI(2)
    }
    
    /**
     * Set optimal MTU for network type
     */
    fun setOptimalMTU(fd: Int, networkType: NetworkType): Int {
        return nativeSetOptimalMTU(fd, networkType.value)
    }
    
    /**
     * Get current MTU
     */
    fun getMTU(fd: Int): Int {
        return nativeGetMTU(fd)
    }
    
    /**
     * Set socket buffer sizes for high throughput
     */
    fun setSocketBuffers(fd: Int, sendBuffer: Int, recvBuffer: Int): Int {
        return nativeSetSocketBuffers(fd, sendBuffer, recvBuffer)
    }
    
    // ==================== Ring Buffer ====================
    
    /**
     * Create lock-free ring buffer
     */
    fun createRingBuffer(capacity: Int): Long {
        return nativeCreateRingBuffer(capacity)
    }
    
    /**
     * Write to ring buffer
     */
    fun ringBufferWrite(handle: Long, data: ByteArray, offset: Int, length: Int): Int {
        return nativeRingBufferWrite(handle, data, offset, length)
    }
    
    /**
     * Read from ring buffer
     */
    fun ringBufferRead(handle: Long, data: ByteArray, offset: Int, maxLength: Int): Int {
        return nativeRingBufferRead(handle, data, offset, maxLength)
    }
    
    /**
     * Destroy ring buffer
     */
    fun destroyRingBuffer(handle: Long) {
        nativeDestroyRingBuffer(handle)
    }
    
    // ==================== JIT Warm-Up ====================
    
    /**
     * Warm up JIT compiler
     */
    fun jitWarmup() {
        nativeJITWarmup()
    }
    
    /**
     * Request CPU boost
     */
    fun requestCPUBoost(durationMs: Int): Int {
        return nativeRequestCPUBoost(durationMs)
    }
    
    // ==================== Kernel Pacing ====================
    
    /**
     * Initialize internal pacing FIFO
     */
    fun initPacingFIFO(maxSize: Int): Long {
        return nativeInitPacingFIFO(maxSize)
    }
    
    /**
     * Enqueue packet for pacing
     */
    fun enqueuePacket(handle: Long, fd: Int, data: ByteArray, offset: Int, length: Int): Int {
        return nativeEnqueuePacket(handle, fd, data, offset, length)
    }
    
    /**
     * Start pacing worker
     */
    fun startPacing(handle: Long): Int {
        return nativeStartPacing(handle)
    }
    
    /**
     * Destroy pacing FIFO
     */
    fun destroyPacingFIFO(handle: Long) {
        nativeDestroyPacingFIFO(handle)
    }
    
    // ==================== Read-Ahead ====================
    
    /**
     * Enable read-ahead for file descriptor
     */
    fun enableReadAhead(fd: Int, offset: Long, length: Long): Int {
        return nativeEnableReadAhead(fd, offset, length)
    }
    
    /**
     * Prefetch chunks for streaming
     */
    fun prefetchChunks(fd: Int, chunkSize: Int, numChunks: Int): Int {
        return nativePrefetchChunks(fd, chunkSize, numChunks)
    }
    
    // ==================== QoS ====================
    
    /**
     * Set socket priority (0-6, higher = more important)
     */
    fun setSocketPriority(fd: Int, priority: Int): Int {
        return nativeSetSocketPriority(fd, priority)
    }
    
    /**
     * Set IP TOS (Type of Service)
     */
    fun setIPTOS(fd: Int, tos: Int): Int {
        return nativeSetIPTOS(fd, tos)
    }
    
    /**
     * Enable TCP Low Latency mode
     */
    fun enableTCPLowLatency(fd: Int): Int {
        return nativeEnableTCPLowLatency(fd)
    }
    
    // ==================== Map/Unmap Batching ====================
    
    /**
     * Initialize batch mapper
     */
    fun initBatchMapper(): Long {
        return nativeInitBatchMapper()
    }
    
    /**
     * Batch map memory
     */
    fun batchMap(handle: Long, size: Long): Long {
        return nativeBatchMap(handle, size)
    }
    
    /**
     * Batch unmap memory
     */
    fun batchUnmap(handle: Long, addresses: LongArray, sizes: LongArray): Int {
        return nativeBatchUnmap(handle, addresses, sizes)
    }
    
    /**
     * Destroy batch mapper
     */
    fun destroyBatchMapper(handle: Long) {
        nativeDestroyBatchMapper(handle)
    }
    
    // ==================== Native Methods ====================
    
    // CPU Affinity
    private external fun nativeSetCPUAffinity(cpuMask: Long): Int
    private external fun nativePinToBigCores(): Int
    private external fun nativePinToLittleCores(): Int
    private external fun nativeGetCurrentCPU(): Int
    private external fun nativeRequestPerformanceGovernor(): Int
    
    // Epoll
    private external fun nativeInitEpoll(): Long
    private external fun nativeEpollAdd(handle: Long, fd: Int, events: Int): Int
    private external fun nativeEpollRemove(handle: Long, fd: Int): Int
    private external fun nativeEpollWait(handle: Long, outEvents: LongArray): Int
    private external fun nativeDestroyEpoll(handle: Long)
    
    // Zero-Copy
    private external fun nativeRecvZeroCopy(fd: Int, buffer: ByteBuffer, offset: Int, length: Int): Int
    private external fun nativeSendZeroCopy(fd: Int, buffer: ByteBuffer, offset: Int, length: Int): Int
    private external fun nativeRecvMsg(fd: Int, buffers: Array<ByteBuffer>, lengths: IntArray): Int
    private external fun nativeAllocateDirectBuffer(capacity: Int): ByteBuffer?
    
    // Connection Pool
    private external fun nativeInitConnectionPool(): Int
    private external fun nativeGetPooledSocket(poolType: Int): Int
    private external fun nativeConnectPooledSocket(poolType: Int, slotIndex: Int, host: String, port: Int): Int
    private external fun nativeReturnPooledSocket(poolType: Int, slotIndex: Int)
    private external fun nativeDestroyConnectionPool()
    
    // Crypto
    private external fun nativeHasNEON(): Boolean
    private external fun nativeHasCryptoExtensions(): Boolean
    private external fun nativeAES128Encrypt(
        input: ByteBuffer, inputOffset: Int, inputLen: Int,
        output: ByteBuffer, outputOffset: Int, key: ByteBuffer
    ): Int
    private external fun nativeChaCha20NEON(
        input: ByteBuffer, inputOffset: Int, inputLen: Int,
        output: ByteBuffer, outputOffset: Int, key: ByteBuffer, nonce: ByteBuffer
    ): Int
    private external fun nativePrefetch(buffer: ByteBuffer, offset: Int, length: Int)
    
    // TLS Session
    private external fun nativeStoreTLSTicket(host: String, ticket: ByteArray): Int
    private external fun nativeGetTLSTicket(host: String): ByteArray?
    private external fun nativeClearTLSCache()
    
    // MTU Tuning
    private external fun nativeSetOptimalMTU(fd: Int, networkType: Int): Int
    private external fun nativeGetMTU(fd: Int): Int
    private external fun nativeSetSocketBuffers(fd: Int, sendBuffer: Int, recvBuffer: Int): Int
    
    // Ring Buffer
    private external fun nativeCreateRingBuffer(capacity: Int): Long
    private external fun nativeRingBufferWrite(handle: Long, data: ByteArray, offset: Int, length: Int): Int
    private external fun nativeRingBufferRead(handle: Long, data: ByteArray, offset: Int, maxLength: Int): Int
    private external fun nativeDestroyRingBuffer(handle: Long)
    
    // JIT Warm-Up
    private external fun nativeJITWarmup()
    private external fun nativeRequestCPUBoost(durationMs: Int): Int
    
    // Kernel Pacing
    private external fun nativeInitPacingFIFO(maxSize: Int): Long
    private external fun nativeEnqueuePacket(handle: Long, fd: Int, data: ByteArray, offset: Int, length: Int): Int
    private external fun nativeStartPacing(handle: Long): Int
    private external fun nativeDestroyPacingFIFO(handle: Long)
    
    // Read-Ahead
    private external fun nativeEnableReadAhead(fd: Int, offset: Long, length: Long): Int
    private external fun nativePrefetchChunks(fd: Int, chunkSize: Int, numChunks: Int): Int
    
    // QoS
    private external fun nativeSetSocketPriority(fd: Int, priority: Int): Int
    private external fun nativeSetIPTOS(fd: Int, tos: Int): Int
    private external fun nativeEnableTCPLowLatency(fd: Int): Int
    
    // Map/Unmap Batching
    private external fun nativeInitBatchMapper(): Long
    private external fun nativeBatchMap(handle: Long, size: Long): Long
    private external fun nativeBatchUnmap(handle: Long, addresses: LongArray, sizes: LongArray): Int
    private external fun nativeDestroyBatchMapper(handle: Long)
}

