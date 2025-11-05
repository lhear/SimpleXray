/*
 * JNI Bridge for Performance Module
 * Main entry point for Java/Kotlin integration
 */

#include <jni.h>
#include <android/log.h>
#include <atomic>

// Define JNI version constants if not available (for older NDK versions)
#ifndef JNI_VERSION_1_8
#define JNI_VERSION_1_8 0x00010008
#endif
#ifndef JNI_VERSION_1_6
#define JNI_VERSION_1_6 0x00010006
#endif

#define LOG_TAG "PerfJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Forward declaration for TLS session cleanup
extern "C" void perf_tls_session_cleanup();

// Global JavaVM pointer for thread attachment (shared across modules)
// CRITICAL: Use atomic to prevent data races when accessed from multiple threads
// JavaVM* is guaranteed stable for JVM lifetime, but atomic ensures correct memory ordering
// Note: Not static because it's accessed via extern from other modules
alignas(64) std::atomic<JavaVM*> g_jvm{nullptr};

// Cached JNI class and method IDs (reduces JNI lookups)
struct alignas(64) JNICache {
    jclass byteBufferClass{nullptr};
    jmethodID allocateDirectMethod{nullptr};
    bool initialized{false};
};
JNICache g_jni_cache;  // Not static - accessed via extern from other modules

// Forward declarations
extern "C" {
    // CPU Affinity
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeSetCPUAffinity(JNIEnv*, jclass, jlong);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativePinToBigCores(JNIEnv*, jclass);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativePinToLittleCores(JNIEnv*, jclass);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeGetCurrentCPU(JNIEnv*, jclass);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeRequestPerformanceGovernor(JNIEnv*, jclass);
    
    // Epoll
    jlong Java_com_simplexray_an_performance_PerformanceManager_nativeInitEpoll(JNIEnv*, jclass);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeEpollAdd(JNIEnv*, jclass, jlong, jint, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeEpollRemove(JNIEnv*, jclass, jlong, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeEpollWait(JNIEnv*, jclass, jlong, jlongArray);
    void Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyEpoll(JNIEnv*, jclass, jlong);
    
    // Zero-Copy
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeRecvZeroCopy(JNIEnv*, jclass, jint, jobject, jint, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeSendZeroCopy(JNIEnv*, jclass, jint, jobject, jint, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeRecvMsg(JNIEnv*, jclass, jint, jobjectArray, jintArray);
    jobject Java_com_simplexray_an_performance_PerformanceManager_nativeAllocateDirectBuffer(JNIEnv*, jclass, jint);
    
    // Connection Pool
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeInitConnectionPool(JNIEnv*, jclass, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeGetPooledSocket(JNIEnv*, jclass, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeGetPooledSocketSlotIndex(JNIEnv*, jclass, jint, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeConnectPooledSocket(JNIEnv*, jclass, jint, jint, jstring, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeConnectPooledSocketByFd(JNIEnv*, jclass, jint, jint, jstring, jint);
    void Java_com_simplexray_an_performance_PerformanceManager_nativeReturnPooledSocket(JNIEnv*, jclass, jint, jint);
    void Java_com_simplexray_an_performance_PerformanceManager_nativeReturnPooledSocketByFd(JNIEnv*, jclass, jint, jint);
    void Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyConnectionPool(JNIEnv*, jclass);
    jfloat Java_com_simplexray_an_performance_PerformanceManager_nativeGetConnectionPoolUtilization(JNIEnv*, jclass);
    
    // Crypto
    jboolean Java_com_simplexray_an_performance_PerformanceManager_nativeHasNEON(JNIEnv*, jclass);
    jboolean Java_com_simplexray_an_performance_PerformanceManager_nativeHasCryptoExtensions(JNIEnv*, jclass);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeAES128Encrypt(JNIEnv*, jclass, jobject, jint, jint, jobject, jint, jobject);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeChaCha20NEON(JNIEnv*, jclass, jobject, jint, jint, jobject, jint, jobject, jobject);
    void Java_com_simplexray_an_performance_PerformanceManager_nativePrefetch(JNIEnv*, jclass, jobject, jint, jint);
    
    // TLS Session
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeStoreTLSTicket(JNIEnv*, jclass, jstring, jbyteArray);
    jbyteArray Java_com_simplexray_an_performance_PerformanceManager_nativeGetTLSTicket(JNIEnv*, jclass, jstring);
    void Java_com_simplexray_an_performance_PerformanceManager_nativeClearTLSCache(JNIEnv*, jclass);
    
    // MTU Tuning
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeSetOptimalMTU(JNIEnv*, jclass, jint, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeGetMTU(JNIEnv*, jclass, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeSetSocketBuffers(JNIEnv*, jclass, jint, jint, jint);
    
    // Ring Buffer
    jlong Java_com_simplexray_an_performance_PerformanceManager_nativeCreateRingBuffer(JNIEnv*, jclass, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeRingBufferWrite(JNIEnv*, jclass, jlong, jbyteArray, jint, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeRingBufferRead(JNIEnv*, jclass, jlong, jbyteArray, jint, jint);
    void Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyRingBuffer(JNIEnv*, jclass, jlong);
    
    // JIT Warm-Up
    void Java_com_simplexray_an_performance_PerformanceManager_nativeJITWarmup(JNIEnv*, jclass);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeRequestCPUBoost(JNIEnv*, jclass, jint);
    
    // Kernel Pacing
    jlong Java_com_simplexray_an_performance_PerformanceManager_nativeInitPacingFIFO(JNIEnv*, jclass, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeEnqueuePacket(JNIEnv*, jclass, jlong, jint, jbyteArray, jint, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeStartPacing(JNIEnv*, jclass, jlong);
    void Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyPacingFIFO(JNIEnv*, jclass, jlong);
    
    // Read-Ahead
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeEnableReadAhead(JNIEnv*, jclass, jint, jlong, jlong);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativePrefetchChunks(JNIEnv*, jclass, jint, jint, jint);
    
    // QoS
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeSetSocketPriority(JNIEnv*, jclass, jint, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeSetIPTOS(JNIEnv*, jclass, jint, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeEnableTCPLowLatency(JNIEnv*, jclass, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeOptimizeKeepAlive(JNIEnv*, jclass, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeOptimizeSocketBuffers(JNIEnv*, jclass, jint, jint, jint, jint);
    
    // Map/Unmap Batching
    jlong Java_com_simplexray_an_performance_PerformanceManager_nativeInitBatchMapper(JNIEnv*, jclass);
    jlong Java_com_simplexray_an_performance_PerformanceManager_nativeBatchMap(JNIEnv*, jclass, jlong, jlong);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeBatchUnmap(JNIEnv*, jclass, jlong, jlongArray, jlongArray);
    void Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyBatchMapper(JNIEnv*, jclass, jlong);
    
    // TCP Fast Open
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeEnableTCPFastOpen(JNIEnv*, jclass, jint);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeIsTCPFastOpenSupported(JNIEnv*, jclass);
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeSetTCPFastOpenQueueSize(JNIEnv*, jclass, jint);
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;
    JNIEnv* env = nullptr;
    
    // Try JNI_VERSION_1_8 first, fallback to 1_6 for compatibility
    jint version = JNI_VERSION_1_8;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), version) != JNI_OK) {
        version = JNI_VERSION_1_6;
        if (vm->GetEnv(reinterpret_cast<void**>(&env), version) != JNI_OK) {
            return JNI_ERR;
        }
    }
    
    // Use release semantics to ensure all previous writes are visible
    g_jvm.store(vm, std::memory_order_release);
    
    // Cache frequently used JNI class and method IDs
    g_jni_cache.byteBufferClass = env->FindClass("java/nio/ByteBuffer");
    if (g_jni_cache.byteBufferClass) {
        g_jni_cache.byteBufferClass = reinterpret_cast<jclass>(env->NewGlobalRef(g_jni_cache.byteBufferClass));
        g_jni_cache.allocateDirectMethod = env->GetStaticMethodID(
            g_jni_cache.byteBufferClass, "allocateDirect", "(I)Ljava/nio/ByteBuffer;");
        g_jni_cache.initialized = (g_jni_cache.allocateDirectMethod != nullptr);
    }
    
    LOGD("Performance module JNI loaded");
    return version;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)reserved;
    (void)vm; // Parameter not used, but required by JNI spec
    
    // Cleanup TLS session cache
    perf_tls_session_cleanup();
    
    // Cleanup cached JNI references
    if (g_jni_cache.byteBufferClass) {
        JNIEnv* env = nullptr;
        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(g_jni_cache.byteBufferClass);
        }
        g_jni_cache.byteBufferClass = nullptr;
        g_jni_cache.allocateDirectMethod = nullptr;
        g_jni_cache.initialized = false;
    }
    
    // Reset to nullptr with release semantics for proper cleanup
    g_jvm.store(nullptr, std::memory_order_release);
    LOGD("Performance module JNI unloaded");
}

