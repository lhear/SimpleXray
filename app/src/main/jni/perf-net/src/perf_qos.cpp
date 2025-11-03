/*
 * QoS Tricks for Critical Packets
 * High-priority socket flags for latency-sensitive traffic
 */

#include <jni.h>
#include <sys/socket.h>
#include <linux/socket.h>
#include <linux/ip.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <android/log.h>
#include <errno.h>

#define LOG_TAG "PerfQoS"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Set socket priority for QoS
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeSetSocketPriority(
    JNIEnv *env, jclass clazz, jint fd, jint priority) {
    
    // SO_PRIORITY (0-6, higher = more important)
    // On Android, this is honored more than commonly known
    int result = setsockopt(fd, SOL_SOCKET, SO_PRIORITY, &priority, sizeof(priority));
    
    if (result == 0) {
        LOGD("Socket priority set to %d for fd %d", priority, fd);
    } else {
        LOGE("Failed to set socket priority: %d", errno);
    }
    
    return result;
}

/**
 * Set IP TOS (Type of Service) for QoS
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeSetIPTOS(
    JNIEnv *env, jclass clazz, jint fd, jint tos) {
    
    // IPTOS_LOWDELAY (0x10) for low latency
    // IPTOS_THROUGHPUT (0x08) for high throughput
    // IPTOS_RELIABILITY (0x04) for reliability
    int result = setsockopt(fd, IPPROTO_IP, IP_TOS, &tos, sizeof(tos));
    
    if (result == 0) {
        LOGD("IP TOS set to 0x%02x for fd %d", tos, fd);
    } else {
        LOGE("Failed to set IP TOS: %d", errno);
    }
    
    return result;
}

/**
 * Enable TCP Low Latency mode (if supported)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEnableTCPLowLatency(
    JNIEnv *env, jclass clazz, jint fd) {
    
    int opt = 1;
    
    // TCP_NODELAY (disable Nagle's algorithm)
    int result = setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
    
    // TCP_QUICKACK (quick ACK)
    #ifdef TCP_QUICKACK
    setsockopt(fd, IPPROTO_TCP, TCP_QUICKACK, &opt, sizeof(opt));
    #endif
    
    if (result == 0) {
        LOGD("TCP low latency enabled for fd %d", fd);
    }
    
    return result;
}

} // extern "C"

