/*
 * Map/Unmap Batching
 * Reduces syscall overhead by batching memory operations
 */

#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>
#include <vector>
#include <mutex>
#include <algorithm>

#define LOG_TAG "PerfMMapBatch"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

struct MappedRegion {
    void* ptr;
    size_t size;
};

struct MMapBatch {
    std::vector<MappedRegion> mapped_regions;
    std::mutex mutex;
    size_t total_mapped;
};

static MMapBatch* g_batch = nullptr;

extern "C" {

/**
 * Initialize batch mapper
 */
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeInitBatchMapper(
    JNIEnv *env, jclass clazz) {
    
    MMapBatch* batch = new MMapBatch();
    batch->total_mapped = 0;
    g_batch = batch;
    
    LOGD("Batch mapper initialized");
    return reinterpret_cast<jlong>(batch);
}

/**
 * Batch map memory regions
 */
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeBatchMap(
    JNIEnv *env, jclass clazz, jlong handle, jlong size) {
    
    if (!handle) return 0;
    
    MMapBatch* batch = reinterpret_cast<MMapBatch*>(handle);
    
    // Map memory
    void* ptr = mmap(nullptr, size, PROT_READ | PROT_WRITE, 
                     MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    
    if (ptr == MAP_FAILED) {
        return 0;
    }
    
    std::lock_guard<std::mutex> lock(batch->mutex);
    MappedRegion region;
    region.ptr = ptr;
    region.size = size;
    batch->mapped_regions.push_back(region);
    batch->total_mapped += size;
    
    LOGD("Mapped %ld bytes, total: %zu", size, batch->total_mapped);
    return reinterpret_cast<jlong>(ptr);
}

/**
 * Batch unmap memory regions
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeBatchUnmap(
    JNIEnv *env, jclass clazz, jlong handle, jlongArray addresses, jlongArray sizes) {
    
    if (!handle) return -1;
    
    MMapBatch* batch = reinterpret_cast<MMapBatch*>(handle);
    
    jsize count = env->GetArrayLength(addresses);
    if (count != env->GetArrayLength(sizes)) {
        return -1;
    }
    
    jlong* addrs = env->GetLongArrayElements(addresses, nullptr);
    jlong* lens = env->GetLongArrayElements(sizes, nullptr);
    
    int unmapped = 0;
    for (int i = 0; i < count; i++) {
        void* ptr = reinterpret_cast<void*>(addrs[i]);
        size_t len = lens[i];
        
        if (munmap(ptr, len) == 0) {
            unmapped++;
            
            std::lock_guard<std::mutex> lock(batch->mutex);
            auto it = std::find_if(batch->mapped_regions.begin(), 
                                  batch->mapped_regions.end(),
                                  [ptr](const MappedRegion& r) { return r.ptr == ptr; });
            if (it != batch->mapped_regions.end()) {
                batch->total_mapped -= it->size;
                batch->mapped_regions.erase(it);
            }
        }
    }
    
    env->ReleaseLongArrayElements(addresses, addrs, JNI_ABORT);
    env->ReleaseLongArrayElements(sizes, lens, JNI_ABORT);
    
    LOGD("Unmapped %d regions", unmapped);
    return unmapped;
}

/**
 * Destroy batch mapper and unmap all
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyBatchMapper(
    JNIEnv *env, jclass clazz, jlong handle) {
    
    if (!handle) return;
    
    MMapBatch* batch = reinterpret_cast<MMapBatch*>(handle);
    
    std::lock_guard<std::mutex> lock(batch->mutex);
    
    // Unmap all remaining regions
    for (const auto& region : batch->mapped_regions) {
        munmap(region.ptr, region.size);
    }
    
    batch->mapped_regions.clear();
    batch->total_mapped = 0;
    
    delete batch;
    if (g_batch == batch) {
        g_batch = nullptr;
    }
    
    LOGD("Batch mapper destroyed");
}

} // extern "C"

