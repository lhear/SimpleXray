/*
 * Hyper Burst Pacing - Implementation
 */

#include "hyper_burst.hpp"
#include <jni.h>
#include <android/log.h>
#include <time.h>

#define LOG_TAG "HyperBurst"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static BurstTracker g_burst_tracker;

static inline uint64_t get_nanoseconds() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<uint64_t>(ts.tv_sec) * 1000000000ULL + 
           static_cast<uint64_t>(ts.tv_nsec);
}

extern "C" {

/**
 * Submit burst hint to backend
 */
__attribute__((hot))
JNIEXPORT void JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeSubmitBurstHint(
    JNIEnv *env, jclass clazz, jint level) {
    (void)env; (void)clazz;
    
    g_burst_tracker.level = static_cast<BurstLevel>(level);
    // Backend can query this via JNI
}

/**
 * Update burst tracker with packet
 */
__attribute__((hot))
JNIEXPORT void JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeUpdateBurst(
    JNIEnv *env, jclass clazz, jlong bytes, jlong timestampNs) {
    (void)env; (void)clazz;
    
    update_burst_intensity(&g_burst_tracker, static_cast<uint64_t>(bytes), 
                          static_cast<uint64_t>(timestampNs));
}

/**
 * Get current burst level
 */
__attribute__((hot))
JNIEXPORT jint JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeGetBurstLevel(
    JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    
    return static_cast<jint>(g_burst_tracker.level);
}

/**
 * Initialize burst tracker
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeInitBurst(
    JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    
    g_burst_tracker.alpha = 0.1;
    g_burst_tracker.currentBurst = 0.0;
    g_burst_tracker.packetCount = 0;
    g_burst_tracker.byteCount = 0;
    g_burst_tracker.windowStartNs = get_nanoseconds();
    g_burst_tracker.level = BURST_NONE;
}

} // extern "C"

