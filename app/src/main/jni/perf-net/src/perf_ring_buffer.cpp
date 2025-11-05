/*
 * Lock-free Ring Buffer with Cache Locality
 * Optimized for L1 cache hits
 */

#include <jni.h>
#include <atomic>
#include <cstring>
#include <android/log.h>
#include <stdlib.h>
#include <stdint.h>
#include <malloc.h>
#include <limits.h>
#include "perf_memcpy_helper.h"

#define LOG_TAG "PerfRingBuffer"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Cache line size (typically 64 bytes)
#define CACHE_LINE_SIZE 64

// Round up to next power of two for mask-based indexing
static inline size_t round_up_power2(size_t n) {
    if (n <= 1) return 1;
    n--;
    n |= n >> 1;
    n |= n >> 2;
    n |= n >> 4;
    n |= n >> 8;
    n |= n >> 16;
    if (sizeof(size_t) > 4) n |= n >> 32;
    return n + 1;
}

// Align to cache line to avoid false sharing
// Sequence numbers prevent ABA (Address-Before-After) problem
// Separate read/write counters into different cache lines to avoid false sharing
struct alignas(CACHE_LINE_SIZE) RingBuffer {
    // Write-side (one cache line)
    std::atomic<uint64_t> write_pos;  // Use uint64_t to prevent overflow
    std::atomic<uint32_t> write_seq;  // Sequence number for ABA protection
    char write_padding[CACHE_LINE_SIZE - sizeof(std::atomic<uint64_t>) - sizeof(std::atomic<uint32_t>)];
    
    // Read-side (separate cache line)
    std::atomic<uint64_t> read_pos;
    std::atomic<uint32_t> read_seq;  // Sequence number for ABA protection
    char read_padding[CACHE_LINE_SIZE - sizeof(std::atomic<uint64_t>) - sizeof(std::atomic<uint32_t>)];
    
    // Shared metadata (separate cache line)
    size_t capacity;
    size_t capacity_mask;  // capacity - 1 for power-of-two mask
    char* data;
    char metadata_padding[CACHE_LINE_SIZE - sizeof(size_t) * 2 - sizeof(char*)];
};

extern "C" {

/**
 * Create ring buffer
 */
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeCreateRingBuffer(
    JNIEnv *env, jclass clazz, jint capacity) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (capacity <= 0 || capacity > 64 * 1024 * 1024) { // Max 64MB
        LOGE("Invalid capacity: %d (must be 1-67108864)", capacity);
        return 0;
    }
    
    RingBuffer* rb = new RingBuffer();
    if (!rb) {
        LOGE("Failed to allocate RingBuffer structure");
        return 0;
    }
    
    // Round capacity to power of two for mask-based indexing
    size_t pow2_capacity = round_up_power2(static_cast<size_t>(capacity));
    rb->capacity = pow2_capacity;
    rb->capacity_mask = pow2_capacity - 1;
    
    // Use posix_memalign for cache line alignment to avoid false sharing
    void* aligned_ptr = nullptr;
    int align_result = posix_memalign(&aligned_ptr, CACHE_LINE_SIZE, pow2_capacity);
    if (align_result != 0 || !aligned_ptr) {
        LOGE("Failed to allocate aligned ring buffer data: %zu bytes (errno: %d)", pow2_capacity, align_result);
        delete rb;
        return 0;
    }
    rb->data = static_cast<char*>(aligned_ptr);
    
    rb->write_pos.store(0, std::memory_order_relaxed);
    rb->write_seq.store(0, std::memory_order_relaxed);
    rb->read_pos.store(0, std::memory_order_relaxed);
    rb->read_seq.store(0, std::memory_order_relaxed);
    
    LOGD("Ring buffer created: capacity=%d", capacity);
    return reinterpret_cast<jlong>(rb);
}

/**
 * Write to ring buffer (lock-free)
 */
__attribute__((hot))
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeRingBufferWrite(
    JNIEnv *env, jclass clazz, jlong handle, jbyteArray data, jint offset, jint length) {
    (void)clazz; // JNI required parameter, not used
    
    if (!handle || !data || length < 0 || offset < 0) {
        LOGE("Invalid parameters: handle=%p, data=%p, offset=%d, length=%d", 
             reinterpret_cast<void*>(handle), data, offset, length);
        return -1;
    }
    
    RingBuffer* rb = reinterpret_cast<RingBuffer*>(handle);
    if (!rb || !rb->data) {
        LOGE("Invalid ring buffer handle");
        return -1;
    }
    
    jsize array_length = env->GetArrayLength(data);
    if (env->ExceptionCheck() || offset + length > array_length) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("JNI exception occurred while getting array length");
        } else {
            LOGE("Array bounds exceeded: offset=%d, length=%d, array_size=%d", 
                 offset, length, array_length);
        }
        return -1;
    }
    
    // Use GetPrimitiveArrayCritical for better performance when possible
    // Note: This requires careful use - must not call JNI or allocate memory between Get and Release
    jboolean isCopy = JNI_FALSE;
    jbyte* src = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(data, &isCopy));
    if (!src) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("JNI exception occurred while getting byte array elements");
        } else {
            LOGE("Failed to get byte array elements");
        }
        return -1;
    }
    
    // Load positions and sequences atomically (ABA protection)
    uint64_t write_pos = rb->write_pos.load(std::memory_order_relaxed);
    uint32_t write_seq = rb->write_seq.load(std::memory_order_acquire);
    uint64_t read_pos = rb->read_pos.load(std::memory_order_acquire);
    uint32_t read_seq = rb->read_seq.load(std::memory_order_acquire);
    
    // Calculate used space with sequence-aware logic (prevents ABA)
    uint64_t used;
    if (write_seq == read_seq) {
        // Same generation - normal calculation
        if (write_pos >= read_pos) {
            used = write_pos - read_pos;
        } else {
            // Wrapped within same generation
            used = rb->capacity - (read_pos - write_pos);
        }
    } else {
        // Different generations - buffer has wrapped
        // write_seq > read_seq means writer has advanced past reader
        used = rb->capacity - (read_pos - (write_pos % rb->capacity));
    }
    
    // Ensure used doesn't exceed capacity
    if (used > rb->capacity) {
        used = rb->capacity;
    }
    
    uint64_t available = rb->capacity - used;
    
    if (available < static_cast<size_t>(length)) {
        env->ReleasePrimitiveArrayCritical(data, src, JNI_ABORT);
        return 0; // Buffer full
    }
    
    // Check for integer overflow before updating write_pos
    if (write_pos > UINT64_MAX - static_cast<uint64_t>(length)) {
        LOGE("Integer overflow: write_pos=%llu, length=%d", (unsigned long long)write_pos, length);
        env->ReleasePrimitiveArrayCritical(data, src, JNI_ABORT);
        return -1;
    }
    
    // Use mask instead of modulo for power-of-two capacity (faster)
    uint64_t pos = write_pos & rb->capacity_mask;
    uint64_t to_end = rb->capacity - pos;
    uint64_t to_write = (static_cast<uint64_t>(length) < to_end) ? static_cast<uint64_t>(length) : to_end;
    
    // Bounds check before memcpy (use size_t for size calculations)
    size_t pos_size = static_cast<size_t>(pos);
    size_t to_write_size = static_cast<size_t>(to_write);
    if (__builtin_expect(pos_size + to_write_size > rb->capacity, 0)) {
        LOGE("Buffer overflow: pos=%llu, to_write=%llu, capacity=%zu", 
             (unsigned long long)pos, (unsigned long long)to_write, rb->capacity);
        env->ReleasePrimitiveArrayCritical(data, src, JNI_ABORT);
        return -1;
    }
    // Use optimized memcpy for small copies
    void* __restrict__ dst = rb->data + pos_size;
    const void* __restrict__ src_ptr = src + offset;
    if (__builtin_expect(to_write_size < 128, 1)) {
        perf_fast_memcpy(dst, src_ptr, to_write_size);
    } else {
        memcpy(dst, src_ptr, to_write_size);
    }
    
    if (__builtin_expect(static_cast<uint64_t>(length) > to_write, 0)) {
        uint64_t remaining = static_cast<uint64_t>(length) - to_write;
        // Bounds check for second memcpy
        size_t remaining_size = static_cast<size_t>(remaining);
        if (__builtin_expect(remaining_size > rb->capacity, 0)) {
            LOGE("Buffer overflow: remaining=%zu > capacity=%zu", remaining_size, rb->capacity);
            env->ReleasePrimitiveArrayCritical(data, src, JNI_ABORT);
            return -1;
        }
        void* __restrict__ dst2 = rb->data;
        const void* __restrict__ src_ptr2 = src + offset + to_write;
        if (__builtin_expect(remaining_size < 128, 1)) {
            perf_fast_memcpy(dst2, src_ptr2, remaining_size);
        } else {
            memcpy(dst2, src_ptr2, remaining_size);
        }
    }
    
    // Update position and sequence atomically (ABA protection)
    uint64_t new_write_pos = write_pos + static_cast<uint64_t>(length);
    uint32_t new_write_seq = write_seq;
    
    // Increment sequence on wraparound (when position exceeds UINT64_MAX - capacity)
    // This prevents ABA: reader sees same position but different generation
    if (__builtin_expect(new_write_pos >= UINT64_MAX - rb->capacity, 0)) {
        // Wraparound - increment sequence to prevent ABA
        new_write_seq = (write_seq + 1) % 0xFFFFFFFF;
        new_write_pos = new_write_pos & rb->capacity_mask;  // Use mask instead of modulo
    }
    
    // Atomic update: sequence first, then position (release semantics)
    if (new_write_seq != write_seq) {
        rb->write_seq.store(new_write_seq, std::memory_order_release);
    }
    rb->write_pos.store(new_write_pos, std::memory_order_release);
    
    env->ReleasePrimitiveArrayCritical(data, src, JNI_ABORT);
    return length;
}

/**
 * Read from ring buffer (lock-free)
 */
__attribute__((hot))
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeRingBufferRead(
    JNIEnv *env, jclass clazz, jlong handle, jbyteArray data, jint offset, jint maxLength) {
    (void)clazz; // JNI required parameter, not used
    
    if (!handle || !data || maxLength < 0 || offset < 0) {
        LOGE("Invalid parameters: handle=%p, data=%p, offset=%d, maxLength=%d", 
             reinterpret_cast<void*>(handle), data, offset, maxLength);
        return -1;
    }
    
    RingBuffer* rb = reinterpret_cast<RingBuffer*>(handle);
    if (!rb || !rb->data) {
        LOGE("Invalid ring buffer handle");
        return -1;
    }
    
    jsize array_length = env->GetArrayLength(data);
    if (env->ExceptionCheck() || offset + maxLength > array_length) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("JNI exception occurred while getting array length");
        } else {
            LOGE("Array bounds exceeded: offset=%d, maxLength=%d, array_size=%d", 
                 offset, maxLength, array_length);
        }
        return -1;
    }
    
    // Load positions and sequences atomically (ABA protection)
    uint64_t write_pos = rb->write_pos.load(std::memory_order_acquire);
    uint32_t write_seq = rb->write_seq.load(std::memory_order_acquire);
    uint64_t read_pos = rb->read_pos.load(std::memory_order_relaxed);
    uint32_t read_seq = rb->read_seq.load(std::memory_order_relaxed);
    
    // Calculate available space with sequence-aware logic (prevents ABA)
    uint64_t available;
    if (write_seq == read_seq) {
        // Same generation - normal calculation
        if (write_pos >= read_pos) {
            available = write_pos - read_pos;
        } else {
            // Wrapped within same generation
            available = rb->capacity - (read_pos - write_pos);
        }
    } else {
        // Different generations - buffer has wrapped
        // write_seq > read_seq means writer has advanced past reader
        available = rb->capacity - (read_pos - (write_pos % rb->capacity));
    }
    
    // Ensure available doesn't exceed capacity
    if (available > rb->capacity) {
        available = rb->capacity;
    }
    
    if (available == 0) {
        return 0; // Buffer empty
    }
    
    jint to_read = (maxLength < static_cast<jint>(available)) ? maxLength : static_cast<jint>(available);
    
    // Use GetPrimitiveArrayCritical for better performance
    jboolean isCopy = JNI_FALSE;
    jbyte* dst = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(data, &isCopy));
    if (!dst) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("JNI exception occurred while getting byte array elements");
        } else {
            LOGE("Failed to get byte array elements");
        }
        return -1;
    }
    
    // Use mask instead of modulo for power-of-two capacity (faster)
    uint64_t pos = read_pos & rb->capacity_mask;
    uint64_t to_end = rb->capacity - pos;
    uint64_t copy_len = (to_read < static_cast<jint>(to_end)) ? to_read : to_end;
    
    // Use optimized memcpy for small copies
    if (__builtin_expect(copy_len < 128, 1)) {
        perf_fast_memcpy(dst + offset, rb->data + pos, copy_len);
    } else {
        memcpy(dst + offset, rb->data + pos, copy_len);
    }
    
    if (__builtin_expect(to_read > static_cast<jint>(copy_len), 0)) {
        size_t remaining = to_read - copy_len;
        if (__builtin_expect(remaining < 128, 1)) {
            perf_fast_memcpy(dst + offset + copy_len, rb->data, remaining);
        } else {
            memcpy(dst + offset + copy_len, rb->data, remaining);
        }
    }
    
    // Update position and sequence atomically (ABA protection)
    uint64_t new_read_pos = read_pos + to_read;
    uint32_t new_read_seq = read_seq;
    
    // Increment sequence on wraparound
    if (__builtin_expect(new_read_pos >= UINT64_MAX - rb->capacity, 0)) {
        // Wraparound - increment sequence to prevent ABA
        new_read_seq = (read_seq + 1) % 0xFFFFFFFF;
        new_read_pos = new_read_pos & rb->capacity_mask;  // Use mask instead of modulo
    }
    
    // Atomic update: sequence first, then position (release semantics)
    if (new_read_seq != read_seq) {
        rb->read_seq.store(new_read_seq, std::memory_order_release);
    }
    rb->read_pos.store(new_read_pos, std::memory_order_release);
    
    env->ReleasePrimitiveArrayCritical(data, dst, 0);
    return to_read;
}

/**
 * Destroy ring buffer
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyRingBuffer(
    JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (!handle) {
        LOGE("Invalid ring buffer handle");
        return;
    }
    
    RingBuffer* rb = reinterpret_cast<RingBuffer*>(handle);
    if (rb) {
        if (rb->data) {
            // Use free() for posix_memalign allocated memory
            free(rb->data);
            rb->data = nullptr;
        }
        delete rb;
        LOGD("Ring buffer destroyed");
    }
}

} // extern "C"

