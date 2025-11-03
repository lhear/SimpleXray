/*
 * CPU Core Affinity & Pinning
 * Pins threads to specific CPU cores for maximum performance
 */

#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <sched.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <android/log.h>

// gettid() helper for Android
#if defined(__ANDROID_API__) && __ANDROID_API__ < 30
#ifndef __NR_gettid
#ifdef __aarch64__
#define __NR_gettid 178
#elif defined(__arm__)
#define __NR_gettid 224
#else
#define __NR_gettid 224
#endif
#endif
static inline pid_t gettid() {
    return syscall(__NR_gettid);
}
#elif defined(__ANDROID_API__) && __ANDROID_API__ >= 30
// Android 30+ has gettid() available
#include <unistd.h>
#else
// Fallback for other platforms
static inline pid_t gettid() {
    return getpid();
}
#endif

#define LOG_TAG "PerfCPUAffinity"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Set CPU affinity for current thread
 * @param cpu_mask CPU mask (bit 0 = CPU 0, bit 1 = CPU 1, etc.)
 * @return 0 on success, -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeSetCPUAffinity(JNIEnv *env, jclass clazz, jlong cpu_mask) {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    
    // Convert bitmask to cpu_set_t
    for (int i = 0; i < 64; i++) {
        if (cpu_mask & (1ULL << i)) {
            CPU_SET(i, &cpuset);
        }
    }
    
    pid_t pid = gettid(); // Thread ID
    int result = sched_setaffinity(pid, sizeof(cpu_set_t), &cpuset);
    
    if (result == 0) {
        LOGD("CPU affinity set successfully for thread %d, mask: 0x%llx", pid, cpu_mask);
    } else {
        LOGE("Failed to set CPU affinity: %d", result);
    }
    
    return result;
}

/**
 * Pin thread to big cores (typical: cores 4-7 on 8-core devices)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativePinToBigCores(JNIEnv *env, jclass clazz) {
    // Common big core layout: 4,5,6,7 (adjust based on device)
    unsigned long big_cores = (1ULL << 4) | (1ULL << 5) | (1ULL << 6) | (1ULL << 7);
    return Java_com_simplexray_an_performance_PerformanceManager_nativeSetCPUAffinity(env, clazz, big_cores);
}

/**
 * Pin thread to little cores (typical: cores 0-3)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativePinToLittleCores(JNIEnv *env, jclass clazz) {
    unsigned long little_cores = (1ULL << 0) | (1ULL << 1) | (1ULL << 2) | (1ULL << 3);
    return Java_com_simplexray_an_performance_PerformanceManager_nativeSetCPUAffinity(env, clazz, little_cores);
}

/**
 * Get current CPU core
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeGetCurrentCPU(JNIEnv *env, jclass clazz) {
    return sched_getcpu();
}

/**
 * Request performance CPU governor
 * Note: Requires root on most devices, but some Android versions allow hints
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeRequestPerformanceGovernor(JNIEnv *env, jclass clazz) {
    // Try to write to scaling_governor (usually requires root)
    // This is best-effort; failure is acceptable
    FILE *f = fopen("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", "w");
    if (f) {
        fprintf(f, "performance");
        fclose(f);
        LOGD("Performance governor requested");
        return 0;
    }
    // Not critical if it fails
    return -1;
}

} // extern "C"

