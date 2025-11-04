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

#define LOG_TAG "PerfRingBuffer"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Cache line size (typically 64 bytes)
#define CACHE_LINE_SIZE 64

// Align to cache line to avoid false sharing
struct alignas(CACHE_LINE_SIZE) RingBuffer {
    std::atomic<size_t> write_pos;
    std::atomic<size_t> read_pos;
    size_t capacity;
    char* data;
    char padding[CACHE_LINE_SIZE - sizeof(std::atomic<size_t>) * 2 - sizeof(size_t) - sizeof(char*)];
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
    rb->data = static_cast<char*>(malloc(capacity));
    if (!rb->data) {
        LOGE("Failed to allocate ring buffer data: %d bytes", capacity);
        delete rb;
        return 0;
    }
    
    rb->write_pos.store(0);
    rb->read_pos.store(0);
    
    // Data is already aligned if using posix_memalign
    // For simplicity, we'll use malloc and accept potential misalignment
    // In production, use posix_memalign for better cache alignment
    // TODO: Switch to posix_memalign() for better cache line alignment (performance improvement)
    // TODO: Consider using aligned_alloc() for C++11+ compatibility
    // BUG: malloc() may not align to cache line boundaries, causing false sharing
    
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
    
    jbyte* src = env->GetByteArrayElements(data, nullptr);
    if (!src) {
        LOGE("Failed to get byte array elements");
        return -1;
    }
    
    size_t write_pos = rb->write_pos.load(std::memory_order_relaxed);
    size_t read_pos = rb->read_pos.load(std::memory_order_acquire);
    // BUG: Potential integer overflow when write_pos wraps around - used calculation can be wrong
    // TODO: Use atomic compare-and-swap to prevent race conditions in multi-producer scenarios
    size_t used = write_pos - read_pos;
    size_t available = rb->capacity - used;
    
    if (available < static_cast<size_t>(length)) {
        env->ReleaseByteArrayElements(data, src, JNI_ABORT);
        return 0; // Buffer full
    }
    
    size_t pos = write_pos % rb->capacity;
    size_t to_end = rb->capacity - pos;
    size_t to_write = (static_cast<size_t>(length) < to_end) ? static_cast<size_t>(length) : to_end;
    
    memcpy(rb->data + pos, src + offset, to_write);
    
    if (static_cast<size_t>(length) > to_write) {
        // BUG: Buffer overflow risk - if write_pos + length > capacity, second memcpy can overflow
        // TODO: Add bounds checking before memcpy operations
        memcpy(rb->data, src + offset + to_write, length - to_write);
    }
    
    rb->write_pos.store(write_pos + length, std::memory_order_release);
    
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
    
    size_t write_pos = rb->write_pos.load(std::memory_order_acquire);
    size_t read_pos = rb->read_pos.load(std::memory_order_relaxed);
    size_t available = write_pos - read_pos;
    
    if (available == 0) {
        return 0; // Buffer empty
    }
    
    jint to_read = (maxLength < static_cast<jint>(available)) ? maxLength : static_cast<jint>(available);
    
    jbyte* dst = env->GetByteArrayElements(data, nullptr);
    if (!dst) {
        LOGE("Failed to get byte array elements");
        return -1;
    }
    
    size_t pos = read_pos % rb->capacity;
    size_t to_end = rb->capacity - pos;
    size_t copy_len = (to_read < static_cast<jint>(to_end)) ? to_read : to_end;
    
    memcpy(dst + offset, rb->data + pos, copy_len);
    
    if (to_read > static_cast<jint>(copy_len)) {
        memcpy(dst + offset + copy_len, rb->data, to_read - copy_len);
    }
    
    rb->read_pos.store(read_pos + to_read, std::memory_order_release);
    
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
            free(rb->data);
            rb->data = nullptr;
        }
        delete rb;
        LOGD("Ring buffer destroyed");
    }
}

} // extern "C"

