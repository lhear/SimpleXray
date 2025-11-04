/*
 * TCP Fast Open (TFO) Support
 * Reduces latency for first connection by combining SYN and data
 */

#include <jni.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <android/log.h>
#include <errno.h>
#include <cstring>
#include <cstdio>
#include <unistd.h>

#define LOG_TAG "PerfTFO"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Enable TCP Fast Open on a socket
 * Returns 0 on success, negative on error
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEnableTCPFastOpen(
    JNIEnv *env, jclass clazz, jint fd) {
    
    // TCP_FASTOPEN option (23) - available on Linux 3.7+
    int opt = 1;
    int result = setsockopt(fd, IPPROTO_TCP, TCP_FASTOPEN, &opt, sizeof(opt));
    
    if (result == 0) {
        LOGD("TCP Fast Open enabled for fd %d", fd);
    } else {
        // TFO may not be supported on all devices/Android versions
        // Log as debug, not error, as it's best-effort
        LOGD("TCP Fast Open not available for fd %d (errno: %d, %s)", 
             fd, errno, strerror(errno));
    }
    
    return result;
}

/**
 * Check if TCP Fast Open is supported
 * Returns 1 if supported, 0 if not
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeIsTCPFastOpenSupported(
    JNIEnv *env, jclass clazz) {
    
    // Try to create a test socket and enable TFO
    int testFd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (testFd < 0) {
        return 0;
    }
    
    int opt = 1;
    int result = setsockopt(testFd, IPPROTO_TCP, TCP_FASTOPEN, &opt, sizeof(opt));
    close(testFd);
    
    return (result == 0) ? 1 : 0;
}

/**
 * Set TCP Fast Open queue size
 * Controls how many TFO requests can be queued
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeSetTCPFastOpenQueueSize(
    JNIEnv *env, jclass clazz, jint queueSize) {
    
    // Write to /proc/sys/net/ipv4/tcp_fastopen
    // This requires root access, so this is best-effort
    // Returns 0 if successful, negative on error
    
    FILE* fp = fopen("/proc/sys/net/ipv4/tcp_fastopen", "w");
    if (fp == nullptr) {
        LOGE("Cannot open tcp_fastopen sysctl (requires root): %s", strerror(errno));
        return -1;
    }
    
    fprintf(fp, "%d", queueSize);
    fclose(fp);
    
    LOGD("TCP Fast Open queue size set to %d", queueSize);
    return 0;
}

} // extern "C"

