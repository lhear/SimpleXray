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
#include <stdio.h>
#include <errno.h>
#include <cstring>
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
static inline pid_t gettid_impl() {
    return syscall(__NR_gettid);
}
#define gettid gettid_impl
#elif defined(__ANDROID_API__) && __ANDROID_API__ >= 30
// Android 30+ has gettid() available in unistd.h
#else
// Fallback for other platforms
static inline pid_t gettid_impl() {
    return getpid();
}
#define gettid gettid_impl
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
    (void)env; (void)clazz; // JNI required parameters, not used
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    
    // Get actual CPU count from system to avoid hardcoded limit
    long cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
    if (cpu_count < 0) {
        LOGE("Failed to get CPU count, using default limit of 64");
        cpu_count = 64;
    }
    
    // Use CPU_COUNT macro to determine max supported CPUs
    // CPU_SETSIZE is typically 1024, but we validate against actual system CPU count
    int max_cpus = (cpu_count < CPU_SETSIZE) ? static_cast<int>(cpu_count) : CPU_SETSIZE;
    
    // Convert bitmask to cpu_set_t
    int cpus_set = 0;
    for (int i = 0; i < max_cpus; i++) {
        if (cpu_mask & (1ULL << i)) {
            CPU_SET(i, &cpuset);
            cpus_set++;
        }
    }
    
    // Validate that CPU set is not empty before setting affinity
    if (cpus_set == 0) {
        LOGE("CPU mask is empty - no CPUs selected");
        return -1;
    }
    
    // Warn if mask exceeds actual CPU count
    if (cpu_mask != 0 && (cpu_mask >> max_cpus) != 0) {
        LOGD("CPU mask contains bits beyond available CPUs (max: %d)", max_cpus);
    }
    
    pid_t pid = gettid(); // Thread ID
    int result = sched_setaffinity(pid, sizeof(cpu_set_t), &cpuset);
    
    if (result == 0) {
        LOGD("CPU affinity set successfully for thread %d, mask: 0x%lx, CPUs: %d", pid, (unsigned long)cpu_mask, cpus_set);
    } else {
        LOGE("Failed to set CPU affinity: %s", strerror(errno));
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
 * Returns CPU number (0-N) or -1 on error
 * Note: sched_getcpu() is available on Android API 21+
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeGetCurrentCPU(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    // sched_getcpu() is available on Android API 21+ (Android 5.0+)
    // Application.mk sets APP_PLATFORM := android-21, so this should be safe
    int cpu = sched_getcpu();
    if (cpu < 0) {
        LOGD("Failed to get current CPU: %s", strerror(errno));
        return -1;
    }
    return cpu;
}

/**
 * Request performance CPU governor
 * Note: Requires root on most devices, but some Android versions allow hints
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeRequestPerformanceGovernor(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz; // JNI required parameters, not used
    // Try to write to scaling_governor (usually requires root)
    // This is best-effort; failure is acceptable
    // TODO: Apply governor to all CPUs, not just cpu0
    FILE *f = fopen("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", "w");
    if (f) {
        int written = fprintf(f, "performance");
        if (written < 0) {
            LOGE("Failed to write performance governor: %s", strerror(errno));
            fclose(f);
            return -1;
        }
        
        // Flush to ensure write is committed
        if (fflush(f) != 0) {
            LOGE("Failed to flush governor file: %s", strerror(errno));
            fclose(f);
            return -1;
        }
        
        fclose(f);
        
        // Validate that governor was actually set by reading it back
        FILE *verify_f = fopen("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", "r");
        if (verify_f) {
            char current_governor[64];
            if (fgets(current_governor, sizeof(current_governor), verify_f)) {
                // Remove newline if present
                size_t len = strlen(current_governor);
                if (len > 0 && current_governor[len - 1] == '\n') {
                    current_governor[len - 1] = '\0';
                }
                if (strcmp(current_governor, "performance") == 0) {
                    LOGD("Performance governor set and verified successfully");
                    fclose(verify_f);
                    return 0;
                } else {
                    LOGD("Performance governor requested but current governor is: %s", current_governor);
                }
            }
            fclose(verify_f);
        }
        LOGD("Performance governor requested (verification unavailable)");
        return 0;
    }
    // Not critical if it fails
    return -1;
}

} // extern "C"

