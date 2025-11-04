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

#define LOG_TAG "PerfRingBuffer"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Cache line size (typically 64 bytes)
#define CACHE_LINE_SIZE 64

// Align to cache line to avoid false sharing
// Sequence numbers prevent ABA (Address-Before-After) problem
struct alignas(CACHE_LINE_SIZE) RingBuffer {
    std::atomic<uint64_t> write_pos;  // Use uint64_t to prevent overflow
    std::atomic<uint32_t> write_seq;  // Sequence number for ABA protection
    std::atomic<uint64_t> read_pos;
    std::atomic<uint32_t> read_seq;  // Sequence number for ABA protection
    size_t capacity;
    char* data;
    char padding[CACHE_LINE_SIZE - sizeof(std::atomic<uint64_t>) * 2 
                  - sizeof(std::atomic<uint32_t>) * 2 - sizeof(size_t) - sizeof(char*)];
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
    
    rb->capacity = capacity;
    // Use posix_memalign for cache line alignment to avoid false sharing
    void* aligned_ptr = nullptr;
    int align_result = posix_memalign(&aligned_ptr, CACHE_LINE_SIZE, capacity);
    if (align_result != 0 || !aligned_ptr) {
        LOGE("Failed to allocate aligned ring buffer data: %d bytes (errno: %d)", capacity, align_result);
        delete rb;
        return 0;
    }
    rb->data = static_cast<char*>(aligned_ptr);
    
    rb->write_pos.store(0);
    rb->write_seq.store(0);
    rb->read_pos.store(0);
    rb->read_seq.store(0);
    
    LOGD("Ring buffer created: capacity=%d", capacity);
    return reinterpret_cast<jlong>(rb);
}

/**
 * Write to ring buffer (lock-free)
 */
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
    if (offset + length > array_length) {
        LOGE("Array bounds exceeded: offset=%d, length=%d, array_size=%d", 
             offset, length, array_length);
        return -1;
    }
    
    // NDK: GetByteArrayElements may copy array - should check isCopy and use GetPrimitiveArrayCritical for performance
    jbyte* src = env->GetByteArrayElements(data, nullptr);
    if (!src) {
        LOGE("Failed to get byte array elements");
        return -1;
    }
    
    // Load positions and sequences atomically (ABA protection)
    // NDK: Memory ordering is correct - relaxed for write_pos, acquire for sequence numbers
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
        env->ReleaseByteArrayElements(data, src, JNI_ABORT);
        return 0; // Buffer full
    }
    
    // Check for integer overflow before updating write_pos
    if (write_pos > UINT64_MAX - static_cast<uint64_t>(length)) {
        LOGE("Integer overflow: write_pos=%llu, length=%d", (unsigned long long)write_pos, length);
        env->ReleaseByteArrayElements(data, src, JNI_ABORT);
        return -1;
    }
    
    uint64_t pos = write_pos % rb->capacity;
    uint64_t to_end = rb->capacity - pos;
    uint64_t to_write = (static_cast<uint64_t>(length) < to_end) ? static_cast<uint64_t>(length) : to_end;
    
    // Bounds check before memcpy
    // NDK: Bounds check is correct but should use size_t for all size calculations
    if (pos + to_write > rb->capacity) {
        LOGE("Buffer overflow: pos=%llu, to_write=%llu, capacity=%zu", 
             (unsigned long long)pos, (unsigned long long)to_write, rb->capacity);
        env->ReleaseByteArrayElements(data, src, JNI_ABORT);
        return -1;
    }
    // PERF: memcpy without restrict - compiler can't optimize - should use memcpy with __restrict__
    memcpy(rb->data + pos, src + offset, to_write);
    
    if (static_cast<uint64_t>(length) > to_write) {
        uint64_t remaining = static_cast<uint64_t>(length) - to_write;
        // Bounds check for second memcpy
        if (remaining > rb->capacity) {
            LOGE("Buffer overflow: remaining=%zu > capacity=%zu", remaining, rb->capacity);
            env->ReleaseByteArrayElements(data, src, JNI_ABORT);
            return -1;
        }
        memcpy(rb->data, src + offset + to_write, remaining);
    }
    
    // Update position and sequence atomically (ABA protection)
    uint64_t new_write_pos = write_pos + static_cast<uint64_t>(length);
    uint32_t new_write_seq = write_seq;
    
    // Increment sequence on wraparound (when position exceeds UINT64_MAX - capacity)
    // This prevents ABA: reader sees same position but different generation
    if (new_write_pos >= UINT64_MAX - rb->capacity) {
        // Wraparound - increment sequence to prevent ABA
        new_write_seq = (write_seq + 1) % 0xFFFFFFFF;
        new_write_pos = new_write_pos % rb->capacity;
    }
    
    // Atomic update: sequence first, then position (release semantics)
    if (new_write_seq != write_seq) {
        rb->write_seq.store(new_write_seq, std::memory_order_release);
    }
    rb->write_pos.store(new_write_pos, std::memory_order_release);
    
    env->ReleaseByteArrayElements(data, src, JNI_ABORT);
    return length;
}

/**
 * Read from ring buffer (lock-free)
 */
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
    if (offset + maxLength > array_length) {
        LOGE("Array bounds exceeded: offset=%d, maxLength=%d, array_size=%d", 
             offset, maxLength, array_length);
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
    
    jbyte* dst = env->GetByteArrayElements(data, nullptr);
    if (!dst) {
        LOGE("Failed to get byte array elements");
        return -1;
    }
    
    uint64_t pos = read_pos % rb->capacity;
    uint64_t to_end = rb->capacity - pos;
    uint64_t copy_len = (to_read < static_cast<jint>(to_end)) ? to_read : to_end;
    
    memcpy(dst + offset, rb->data + pos, copy_len);
    
    if (to_read > static_cast<jint>(copy_len)) {
        memcpy(dst + offset + copy_len, rb->data, to_read - copy_len);
    }
    
    // Update position and sequence atomically (ABA protection)
    uint64_t new_read_pos = read_pos + to_read;
    uint32_t new_read_seq = read_seq;
    
    // Increment sequence on wraparound
    if (new_read_pos >= UINT64_MAX - rb->capacity) {
        // Wraparound - increment sequence to prevent ABA
        new_read_seq = (read_seq + 1) % 0xFFFFFFFF;
        new_read_pos = new_read_pos % rb->capacity;
    }
    
    // Atomic update: sequence first, then position (release semantics)
    if (new_read_seq != read_seq) {
        rb->read_seq.store(new_read_seq, std::memory_order_release);
    }
    rb->read_pos.store(new_read_pos, std::memory_order_release);
    
    env->ReleaseByteArrayElements(data, dst, 0);
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

