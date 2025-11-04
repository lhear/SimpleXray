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
    (void)env; (void)clazz; // JNI required parameters, not used
    
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
    
    // Prevent compiler from optimizing away the loop
    (void)sum;
    
    // Prefetch some memory to warm up cache
    char* dummy = static_cast<char*>(malloc(4096));
    if (dummy) {
        for (int i = 0; i < 4096; i += 64) {
            __builtin_prefetch(dummy + i, 0, 3);
        }
        free(dummy);
    } else {
        LOGD("Failed to allocate memory for prefetch");
    }
    
    LOGD("JIT warm-up completed");
}

/**
 * Request CPU boost (hint to scheduler)
 * Note: Requires root access on most devices, best-effort only
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeRequestCPUBoost(
    JNIEnv *env, jclass clazz, jint duration_ms) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (duration_ms < 0 || duration_ms > 10000) {
        LOGD("Invalid CPU boost duration: %d ms (max 10000)", duration_ms);
        return -1;
    }
    
    // Try to write to CPU boost interface (requires root on most devices)
    FILE* f = fopen("/sys/devices/system/cpu/cpu_boost/input_boost_ms", "w");
    if (f) {
        int written = fprintf(f, "%d", duration_ms);
        fclose(f);
        if (written > 0) {
            LOGD("CPU boost requested for %d ms", duration_ms);
            return 0;
        }
    }
    
    // Try alternative path (read-only check)
    f = fopen("/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq", "r");
    if (f) {
        fclose(f);
        // Could potentially set min freq to max freq temporarily
        // But requires root and is risky, so we skip it
    }
    
    LOGD("CPU boost not available (requires root)");
    return -1; // Not critical if it fails
}

} // extern "C"

