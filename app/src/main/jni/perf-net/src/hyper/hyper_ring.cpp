/*
 * Hyper Ring Buffer - Zero-copy packet transport with metadata
 * Power-of-two capacity, index masking, stable head/tail increments
 */

#include "hyper_backend.hpp"
#include <jni.h>
#include <atomic>
#include <cstring>
#include <android/log.h>
#include <malloc.h>

#define LOG_TAG "HyperRing"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Round up to next power of two
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

// Hyper ring buffer structure
struct alignas(64) HyperRing {
    // Write-side (separate cache line)
    std::atomic<uint64_t> write_pos;  // Write position
    std::atomic<uint32_t> write_seq; // Sequence number for ABA protection
    char write_padding[64 - sizeof(std::atomic<uint64_t>) - sizeof(std::atomic<uint32_t>)];
    
    // Read-side (separate cache line)
    std::atomic<uint64_t> read_pos;  // Read position
    std::atomic<uint32_t> read_seq;  // Sequence number
    char read_padding[64 - sizeof(std::atomic<uint64_t>) - sizeof(std::atomic<uint32_t>)];
    
    // Shared metadata (separate cache line)
    size_t capacity;      // Ring capacity (power of two)
    size_t capacity_mask; // capacity - 1 for mask-based indexing
    RingSlot* slots;      // Array of ring slots
    char* payload_pool;   // Payload buffer pool
    size_t payload_pool_size;
    char metadata_padding[64 - sizeof(size_t) * 3 - sizeof(RingSlot*) - sizeof(char*)];
};

extern "C" {

/**
 * Create hyper ring buffer with packet metadata support
 */
__attribute__((hot))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeCreateRing(
    JNIEnv *env, jclass clazz, jint capacity, jint payloadSize) {
    (void)env; (void)clazz;
    
    if (capacity <= 0 || capacity > 64 * 1024) {
        LOGE("Invalid capacity: %d (must be 1-65536)", capacity);
        return 0;
    }
    
    HyperRing* ring = new (std::align_val_t(64)) HyperRing();
    if (!ring) {
        LOGE("Failed to allocate HyperRing");
        return 0;
    }
    
    // Round capacity to power of two
    size_t pow2_capacity = round_up_power2(static_cast<size_t>(capacity));
    ring->capacity = pow2_capacity;
    ring->capacity_mask = pow2_capacity - 1;
    
    // Allocate ring slots (aligned)
    void* slots_ptr = nullptr;
    int align_result = posix_memalign(&slots_ptr, 64, pow2_capacity * sizeof(RingSlot));
    if (align_result != 0 || !slots_ptr) {
        LOGE("Failed to allocate ring slots: %zu bytes", pow2_capacity * sizeof(RingSlot));
        delete ring;
        return 0;
    }
    ring->slots = static_cast<RingSlot*>(slots_ptr);
    
    // Initialize slots
    memset(ring->slots, 0, pow2_capacity * sizeof(RingSlot));
    
    // Allocate payload pool (optional, for zero-copy)
    if (payloadSize > 0) {
        size_t pool_size = pow2_capacity * static_cast<size_t>(payloadSize);
        void* pool_ptr = nullptr;
        align_result = posix_memalign(&pool_ptr, 64, pool_size);
        if (align_result == 0 && pool_ptr) {
            ring->payload_pool = static_cast<char*>(pool_ptr);
            ring->payload_pool_size = pool_size;
        }
    }
    
    ring->write_pos.store(0, std::memory_order_relaxed);
    ring->write_seq.store(0, std::memory_order_relaxed);
    ring->read_pos.store(0, std::memory_order_relaxed);
    ring->read_seq.store(0, std::memory_order_relaxed);
    
    LOGD("Hyper ring created: capacity=%zu, payloadSize=%d", pow2_capacity, payloadSize);
    return reinterpret_cast<jlong>(ring);
}

/**
 * Write packet with metadata to ring (zero-copy)
 * Returns slot pointer as jlong handle
 */
__attribute__((hot))
__attribute__((flatten))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeRingWrite(
    JNIEnv *env, jclass clazz, jlong handle, jbyteArray data, jint offset, jint length,
    jlong timestampNs, jint flags, jint queue) {
    (void)clazz;
    
    if (!handle || !data || length < 0 || offset < 0) {
        return 0;
    }
    
    HyperRing* ring = reinterpret_cast<HyperRing*>(handle);
    if (!ring || !ring->slots) {
        return 0;
    }
    
    // Get source data
    jsize array_length = env->GetArrayLength(data);
    if (offset + length > array_length) {
        return 0;
    }
    
    jboolean isCopy = JNI_FALSE;
    jbyte* src = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(data, &isCopy));
    if (!src) {
        return 0;
    }
    
    // Load positions atomically
    uint64_t write_pos = ring->write_pos.load(std::memory_order_relaxed);
    uint64_t read_pos = ring->read_pos.load(std::memory_order_acquire);
    
    // Check space available
    uint64_t used = (write_pos - read_pos) & ring->capacity_mask;
    if (used >= ring->capacity) {
        env->ReleasePrimitiveArrayCritical(data, src, JNI_ABORT);
        return 0; // Full
    }
    
    // Get slot index (mask-based, no modulo)
    size_t slot_idx = write_pos & ring->capacity_mask;
    RingSlot* slot = &ring->slots[slot_idx];
    
    // Set metadata
    slot->meta.timestampNs = static_cast<uint64_t>(timestampNs);
    slot->meta.length = static_cast<uint32_t>(length);
    slot->meta.flags = static_cast<uint16_t>(flags);
    slot->meta.queue = static_cast<uint16_t>(queue);
    
    // Copy payload (or use payload pool for zero-copy)
    if (ring->payload_pool && length <= 1500) {
        // Use pre-allocated pool
        char* payload = ring->payload_pool + (slot_idx * 1500);
        memcpy(payload, src + offset, static_cast<size_t>(length));
        slot->payload = payload;
        slot->payloadSize = static_cast<uint64_t>(length);
    } else {
        // Allocate new buffer
        void* payload = malloc(static_cast<size_t>(length));
        if (payload) {
            memcpy(payload, src + offset, static_cast<size_t>(length));
            slot->payload = payload;
            slot->payloadSize = static_cast<uint64_t>(length);
        } else {
            env->ReleasePrimitiveArrayCritical(data, src, JNI_ABORT);
            return 0;
        }
    }
    
    env->ReleasePrimitiveArrayCritical(data, src, JNI_ABORT);
    
    // Update write position (release semantics)
    ring->write_pos.store(write_pos + 1, std::memory_order_release);
    
    // Return slot pointer as handle
    return reinterpret_cast<jlong>(slot);
}

/**
 * Get packet pointer from slot handle (zero-copy access)
 */
__attribute__((hot))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeGetPacketPtr(
    JNIEnv *env, jclass clazz, jlong slotHandle) {
    (void)env; (void)clazz;
    
    if (!slotHandle) return 0;
    
    RingSlot* slot = reinterpret_cast<RingSlot*>(slotHandle);
    return reinterpret_cast<jlong>(slot->payload);
}

/**
 * Get packet metadata from slot handle
 */
__attribute__((hot))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeGetPacketMeta(
    JNIEnv *env, jclass clazz, jlong slotHandle) {
    (void)env; (void)clazz;
    
    if (!slotHandle) return 0;
    
    RingSlot* slot = reinterpret_cast<RingSlot*>(slotHandle);
    return reinterpret_cast<jlong>(&slot->meta);
}

/**
 * Read packet from ring (advances read position)
 */
__attribute__((hot))
__attribute__((flatten))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeRingRead(
    JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    
    if (!handle) return 0;
    
    HyperRing* ring = reinterpret_cast<HyperRing*>(handle);
    if (!ring || !ring->slots) return 0;
    
    uint64_t read_pos = ring->read_pos.load(std::memory_order_relaxed);
    uint64_t write_pos = ring->write_pos.load(std::memory_order_acquire);
    
    if (read_pos >= write_pos) {
        return 0; // Empty
    }
    
    size_t slot_idx = read_pos & ring->capacity_mask;
    RingSlot* slot = &ring->slots[slot_idx];
    
    // Advance read position
    ring->read_pos.store(read_pos + 1, std::memory_order_release);
    
    return reinterpret_cast<jlong>(slot);
}

/**
 * Destroy ring buffer
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeDestroyRing(
    JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    
    if (!handle) return;
    
    HyperRing* ring = reinterpret_cast<HyperRing*>(handle);
    if (ring) {
        // Free payload buffers
        if (ring->slots) {
            for (size_t i = 0; i < ring->capacity; i++) {
                if (ring->slots[i].payload && 
                    (ring->payload_pool == nullptr || 
                     ring->slots[i].payload < ring->payload_pool ||
                     ring->slots[i].payload >= ring->payload_pool + ring->payload_pool_size)) {
                    free(ring->slots[i].payload);
                }
            }
            free(ring->slots);
        }
        if (ring->payload_pool) {
            free(ring->payload_pool);
        }
        delete ring;
        LOGD("Hyper ring destroyed");
    }
}

} // extern "C"

