/*
 * Hyper CPU Feature Detection
 * Detects NEON, AES instructions, exposes flags to backend
 */

#include "hyper_backend.hpp"
#include <jni.h>
#include <android/log.h>

#if defined(__aarch64__) && defined(__ANDROID_API__) && __ANDROID_API__ >= 18
#include <sys/auxv.h>
// Define HWCAP constants if not available
#ifndef HWCAP_AES
#define HWCAP_AES (1 << 3)
#endif
#ifndef HWCAP_PMULL
#define HWCAP_PMULL (1 << 4)
#endif
#ifndef HWCAP_SHA1
#define HWCAP_SHA1 (1 << 5)
#endif
#ifndef HWCAP_SHA2
#define HWCAP_SHA2 (1 << 6)
#endif
#endif

#if defined(__aarch64__) || defined(__arm__)
#include <arm_neon.h>
#define HAS_NEON 1
#else
#define HAS_NEON 0
#endif

#define LOG_TAG "HyperCPU"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// CPU capability flags
#define CPU_CAP_NEON        (1 << 0)
#define CPU_CAP_AES         (1 << 1)
#define CPU_CAP_PMULL       (1 << 2)
#define CPU_CAP_SHA1         (1 << 3)
#define CPU_CAP_SHA2         (1 << 4)

static int g_cpu_caps = 0;
static bool g_caps_initialized = false;

static void detect_cpu_caps() {
    if (g_caps_initialized) return;
    
    #if HAS_NEON
    g_cpu_caps |= CPU_CAP_NEON;
    #endif
    
    #if defined(__aarch64__) && defined(__ANDROID_API__) && __ANDROID_API__ >= 18
    unsigned long hwcap = getauxval(AT_HWCAP);
    if (hwcap & HWCAP_AES) {
        g_cpu_caps |= CPU_CAP_AES;
    }
    if (hwcap & HWCAP_PMULL) {
        g_cpu_caps |= CPU_CAP_PMULL;
    }
    if (hwcap & HWCAP_SHA1) {
        g_cpu_caps |= CPU_CAP_SHA1;
    }
    if (hwcap & HWCAP_SHA2) {
        g_cpu_caps |= CPU_CAP_SHA2;
    }
    #endif
    
    g_caps_initialized = true;
    LOGD("CPU caps detected: 0x%x", g_cpu_caps);
}

extern "C" {

/**
 * Get CPU capabilities
 */
__attribute__((hot))
JNIEXPORT jint JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeCpuCaps(
    JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    
    detect_cpu_caps();
    return static_cast<jint>(g_cpu_caps);
}

/**
 * Check if NEON is available
 */
JNIEXPORT jboolean JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeHasNEON(
    JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    
    detect_cpu_caps();
    return (g_cpu_caps & CPU_CAP_NEON) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Check if AES is available
 */
JNIEXPORT jboolean JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeHasAES(
    JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    
    detect_cpu_caps();
    return (g_cpu_caps & CPU_CAP_AES) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

