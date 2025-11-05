/*
 * Hyper JNI Bridge - Optimized JNI with cached method IDs
 * Zero-copy buffer contract, GetPrimitiveArrayCritical for microbursts
 */

#include "hyper_backend.hpp"
#include "hyper_burst.hpp"
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>

#define LOG_TAG "HyperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Cached JNI class and method IDs
struct alignas(64) HyperJNICache {
    jclass hyperBackendClass;
    jmethodID submitBurstHintMethod;
    jmethodID onPacketProcessedMethod;
    bool initialized;
};

static HyperJNICache g_jni_cache = {nullptr, nullptr, nullptr, false};

extern "C" {

/**
 * Configure hyper backend (batch size, chunk size, flags)
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeConfigure(
    JNIEnv *env, jclass clazz, jint batchSize, jint chunkSize, jint flags) {
    (void)clazz;
    
    // Store configuration (would be used by crypto workers)
    static HyperConfig config;
    config.batchSize = batchSize;
    config.chunkSize = chunkSize;
    config.flags = flags;
    
    LOGD("Hyper backend configured: batch=%d, chunk=%d, flags=0x%x", 
         batchSize, chunkSize, flags);
}

/**
 * Initialize JNI cache
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeInitJNI(
    JNIEnv *env, jclass clazz) {
    
    if (g_jni_cache.initialized) return;
    
    // Cache class reference
    jclass localClass = env->FindClass("com/simplexray/an/hyper/backend/HyperBackend");
    if (localClass) {
        g_jni_cache.hyperBackendClass = 
            reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        env->DeleteLocalRef(localClass);
        
        // Cache method IDs
        g_jni_cache.submitBurstHintMethod = env->GetStaticMethodID(
            g_jni_cache.hyperBackendClass, "onBurstHint", "(I)V");
        
        g_jni_cache.onPacketProcessedMethod = env->GetStaticMethodID(
            g_jni_cache.hyperBackendClass, "onPacketProcessed", "(JJ)V");
        
        g_jni_cache.initialized = true;
        LOGD("Hyper JNI cache initialized");
    }
}

/**
 * Get zero-copy buffer handle from jlong
 */
__attribute__((hot))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeGetDirectBufferHandle(
    JNIEnv *env, jclass clazz, jobject buffer) {
    (void)clazz;
    
    if (!buffer) return 0;
    
    void* ptr = env->GetDirectBufferAddress(buffer);
    if (!ptr) return 0;
    
    return reinterpret_cast<jlong>(ptr);
}

/**
 * Get buffer capacity
 */
__attribute__((hot))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeGetDirectBufferCapacity(
    JNIEnv *env, jclass clazz, jobject buffer) {
    (void)clazz;
    
    if (!buffer) return 0;
    
    return env->GetDirectBufferCapacity(buffer);
}

/**
 * Get primitive array critical (for microbursts)
 */
__attribute__((hot))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeGetPrimitiveArrayCritical(
    JNIEnv *env, jclass clazz, jbyteArray array) {
    (void)clazz;
    
    if (!array) return 0;
    
    jboolean isCopy = JNI_FALSE;
    jbyte* ptr = static_cast<jbyte*>(
        env->GetPrimitiveArrayCritical(array, &isCopy));
    
    if (!ptr) return 0;
    
    return reinterpret_cast<jlong>(ptr);
}

/**
 * Release primitive array critical
 */
__attribute__((hot))
JNIEXPORT void JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeReleasePrimitiveArrayCritical(
    JNIEnv *env, jclass clazz, jbyteArray array, jlong ptr, jint mode) {
    (void)clazz;
    
    if (!array || !ptr) return;
    
    jbyte* data = reinterpret_cast<jbyte*>(ptr);
    env->ReleasePrimitiveArrayCritical(array, data, mode);
}

} // extern "C"

