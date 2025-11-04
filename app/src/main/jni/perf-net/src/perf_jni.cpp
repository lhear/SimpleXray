/*
 * JNI Bridge for Performance Module
 * Main entry point for Java/Kotlin integration
 */

#include <jni.h>
#include <android/log.h>

#define LOG_TAG "PerfJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global JavaVM pointer for thread attachment (shared across modules)
JavaVM* g_jvm = nullptr;

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
    jint Java_com_simplexray_an_performance_PerformanceManager_nativeOptimizeSocketBuffers(JNIEnv*, jclass, jint, jint);
    
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
    (void)reserved; // Reserved parameter, not used
    JNIEnv* env = nullptr;
    
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    // Store JavaVM pointer for thread attachment in other modules
    g_jvm = vm;
    
    LOGD("Performance module JNI loaded");
    return JNI_VERSION_1_6;
}

