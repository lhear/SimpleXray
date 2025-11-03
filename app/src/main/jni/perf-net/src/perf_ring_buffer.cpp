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
    
    RingBuffer* rb = new RingBuffer();
    rb->capacity = capacity;
    rb->data = static_cast<char*>(malloc(capacity));
    rb->write_pos.store(0);
    rb->read_pos.store(0);
    
    // Data is already aligned if using posix_memalign
    // For simplicity, we'll use malloc and accept potential misalignment
    // In production, use posix_memalign for better cache alignment
    
    LOGD("Ring buffer created: capacity=%d", capacity);
    return reinterpret_cast<jlong>(rb);
}

/**
 * Write to ring buffer (lock-free)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeRingBufferWrite(
    JNIEnv *env, jclass clazz, jlong handle, jbyteArray data, jint offset, jint length) {
    
    RingBuffer* rb = reinterpret_cast<RingBuffer*>(handle);
    if (!rb) return -1;
    
    jbyte* src = env->GetByteArrayElements(data, nullptr);
    if (!src) return -1;
    
    size_t write_pos = rb->write_pos.load(std::memory_order_relaxed);
    size_t read_pos = rb->read_pos.load(std::memory_order_acquire);
    size_t available = rb->capacity - (write_pos - read_pos);
    
    if (available < static_cast<size_t>(length)) {
        env->ReleaseByteArrayElements(data, src, JNI_ABORT);
        return 0; // Buffer full
    }
    
    size_t pos = write_pos % rb->capacity;
    size_t to_end = rb->capacity - pos;
    size_t to_write = (length < static_cast<jint>(to_end)) ? length : to_end;
    
    memcpy(rb->data + pos, src + offset, to_write);
    
    if (length > static_cast<jint>(to_write)) {
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
    
    RingBuffer* rb = reinterpret_cast<RingBuffer*>(handle);
    if (!rb) return -1;
    
    size_t write_pos = rb->write_pos.load(std::memory_order_acquire);
    size_t read_pos = rb->read_pos.load(std::memory_order_relaxed);
    size_t available = write_pos - read_pos;
    
    if (available == 0) {
        return 0; // Buffer empty
    }
    
    jint to_read = (maxLength < static_cast<jint>(available)) ? maxLength : available;
    
    jbyte* dst = env->GetByteArrayElements(data, nullptr);
    if (!dst) return -1;
    
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
    
    RingBuffer* rb = reinterpret_cast<RingBuffer*>(handle);
    if (rb) {
        free(rb->data);
        delete rb;
        LOGD("Ring buffer destroyed");
    }
}

} // extern "C"

