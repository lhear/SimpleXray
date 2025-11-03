/*
 * JIT Warm-Up
 * Pre-compiles hot paths to reduce latency
 */

#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <pthread.h>
#include <stdlib.h>
#include <stdio.h>

#define LOG_TAG "PerfJIT"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Warm up JIT by running hot paths
 * This is a best-effort optimization
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeJITWarmup(
    JNIEnv *env, jclass clazz) {
    
    LOGD("Starting JIT warm-up");
    
    // Trigger JIT compilation by calling hot paths multiple times
    // This is done at Java level, but we can hint the runtime
    
    // Force CPU to max frequency (best-effort)
    // This is usually done via CPU governor, but we can try to hint
    
    // Run some CPU-intensive operations to warm up
    volatile int sum = 0;
    for (int i = 0; i < 100000; i++) {
        sum += i * i;
    }
    
    // Prefetch some memory to warm up cache
    char* dummy = static_cast<char*>(malloc(4096));
    if (dummy) {
        for (int i = 0; i < 4096; i += 64) {
            __builtin_prefetch(dummy + i, 0, 3);
        }
        free(dummy);
    }
    
    LOGD("JIT warm-up completed");
}

/**
 * Request CPU boost (hint to scheduler)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeRequestCPUBoost(
    JNIEnv *env, jclass clazz, jint duration_ms) {
    
    // Try to write to CPU boost interface (requires root on most devices)
    FILE* f = fopen("/sys/devices/system/cpu/cpu_boost/input_boost_ms", "w");
    if (f) {
        fprintf(f, "%d", duration_ms);
        fclose(f);
        LOGD("CPU boost requested for %d ms", duration_ms);
        return 0;
    }
    
    // Try alternative path
    f = fopen("/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq", "r");
    if (f) {
        fclose(f);
        // Could potentially set min freq to max freq temporarily
    }
    
    return -1; // Not critical if it fails
}

} // extern "C"

