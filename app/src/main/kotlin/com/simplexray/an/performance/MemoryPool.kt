package com.simplexray.an.performance

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Memory Pool for ByteBuffer reuse
 * Reduces GC pressure by reusing allocated buffers
 */
class MemoryPool(private val bufferSize: Int, private val poolSize: Int = 16) {
    
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()
    private val allocated = AtomicInteger(0)
    
    /**
     * Get a buffer from pool or allocate new one
     */
    fun acquire(): ByteBuffer {
        val buffer = pool.poll()
        return if (buffer != null) {
            buffer.clear()
            buffer
        } else {
            allocated.incrementAndGet()
            ByteBuffer.allocateDirect(bufferSize)
        }
    }
    
    /**
     * Return buffer to pool
     */
    fun release(buffer: ByteBuffer) {
        if (buffer.capacity() == bufferSize && pool.size < poolSize) {
            buffer.clear()
            pool.offer(buffer)
        }
        // If pool is full or buffer size mismatch, let GC handle it
    }
    
    /**
     * Get current pool statistics
     */
    fun getStats(): PoolStats {
        return PoolStats(
            poolSize = poolSize,
            available = pool.size,
            allocated = allocated.get()
        )
    }
    
    /**
     * Clear pool
     */
    fun clear() {
        pool.clear()
    }
    
    data class PoolStats(
        val poolSize: Int,
        val available: Int,
        val allocated: Int
    )
}

/**
 * Object Pool for generic objects
 */
class ObjectPool<T>(private val factory: () -> T, private val poolSize: Int = 16) {
    
    private val pool = ConcurrentLinkedQueue<T>()
    
    fun acquire(): T {
        return pool.poll() ?: factory()
    }
    
    fun release(obj: T) {
        if (pool.size < poolSize) {
            pool.offer(obj)
        }
    }
    
    fun clear() {
        pool.clear()
    }
}



