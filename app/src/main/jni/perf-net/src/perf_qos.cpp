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
#include <cstring>

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
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (fd < 0) {
        LOGE("Invalid file descriptor: %d", fd);
        return -1;
    }
    
    if (priority < 0 || priority > 6) {
        LOGE("Invalid priority: %d (must be 0-6)", priority);
        return -1;
    }
    
    // SO_PRIORITY (0-6, higher = more important)
    // On Android, this is honored more than commonly known
    int result = setsockopt(fd, SOL_SOCKET, SO_PRIORITY, &priority, sizeof(priority));
    
    if (result == 0) {
        LOGD("Socket priority set to %d for fd %d", priority, fd);
    } else {
        LOGE("Failed to set socket priority: %s", strerror(errno));
    }
    
    return result;
}

/**
 * Set IP TOS (Type of Service) for QoS
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeSetIPTOS(
    JNIEnv *env, jclass clazz, jint fd, jint tos) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (fd < 0) {
        LOGE("Invalid file descriptor: %d", fd);
        return -1;
    }
    
    if (tos < 0 || tos > 255) {
        LOGE("Invalid TOS value: %d (must be 0-255)", tos);
        return -1;
    }
    
    // IPTOS_LOWDELAY (0x10) for low latency
    // IPTOS_THROUGHPUT (0x08) for high throughput
    // IPTOS_RELIABILITY (0x04) for reliability
    int result = setsockopt(fd, IPPROTO_IP, IP_TOS, &tos, sizeof(tos));
    
    if (result == 0) {
        LOGD("IP TOS set to 0x%02x for fd %d", tos, fd);
    } else {
        LOGE("Failed to set IP TOS: %s", strerror(errno));
    }
    
    return result;
}

/**
 * Enable TCP Low Latency mode (if supported)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEnableTCPLowLatency(
    JNIEnv *env, jclass clazz, jint fd) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (fd < 0) {
        LOGE("Invalid file descriptor: %d", fd);
        return -1;
    }
    
    int opt = 1;
    
    // TCP_NODELAY (disable Nagle's algorithm)
    int result = setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
    
    // TCP_QUICKACK (quick ACK)
    #ifdef TCP_QUICKACK
    if (setsockopt(fd, IPPROTO_TCP, TCP_QUICKACK, &opt, sizeof(opt)) < 0) {
        LOGD("TCP_QUICKACK not supported: %s", strerror(errno));
    }
    #endif
    
    if (result == 0) {
        LOGD("TCP low latency enabled for fd %d", fd);
    }
    
    return result;
}

/**
 * Optimize TCP Keep-Alive settings
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeOptimizeKeepAlive(
    JNIEnv *env, jclass clazz, jint fd) {
    
    int keepalive = 1;
    int keepidle = 60;    // 60 seconds before first probe
    int keepintvl = 10;   // 10 seconds between probes
    int keepcnt = 3;      // 3 probes before timeout
    
    // Enable keep-alive
    int result = setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &keepalive, sizeof(keepalive));
    if (result != 0) {
        LOGE("Failed to enable SO_KEEPALIVE: %d", errno);
        return result;
    }
    
    // Set keep-alive parameters (Linux-specific)
    #ifdef TCP_KEEPIDLE
    setsockopt(fd, IPPROTO_TCP, TCP_KEEPIDLE, &keepidle, sizeof(keepidle));
    #endif
    
    #ifdef TCP_KEEPINTVL
    setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &keepintvl, sizeof(keepintvl));
    #endif
    
    #ifdef TCP_KEEPCNT
    setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &keepcnt, sizeof(keepcnt));
    #endif
    
    if (result == 0) {
        LOGD("TCP keep-alive optimized for fd %d (idle: %d, intvl: %d, cnt: %d)", 
             fd, keepidle, keepintvl, keepcnt);
    }
    
    return result;
}

/**
 * Optimize socket buffer sizes based on network type
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeOptimizeSocketBuffers(
    JNIEnv *env, jclass clazz, jint fd, jint networkType) {
    
    // Network type: 0=WiFi, 1=5G, 2=LTE, 3=Other
    // TODO: Make buffer sizes configurable via JNI parameters instead of hardcoded
    // TODO: Add adaptive buffer sizing based on measured network RTT and bandwidth
    int sendBuf, recvBuf;
    
    switch (networkType) {
        case 0: // WiFi
            sendBuf = 512 * 1024;  // 512 KB
            recvBuf = 512 * 1024;
            break;
        case 1: // 5G
            sendBuf = 1024 * 1024; // 1 MB
            recvBuf = 1024 * 1024;
            break;
        case 2: // LTE
            sendBuf = 256 * 1024;  // 256 KB
            recvBuf = 256 * 1024;
            break;
        case 3: // Other
            sendBuf = 256 * 1024;
            recvBuf = 256 * 1024;
            break;
        default: // Invalid network type
            LOGE("Invalid network type: %d (expected 0-3: WiFi=0, 5G=1, LTE=2, Other=3). Using default.", networkType);
            sendBuf = 256 * 1024;
            recvBuf = 256 * 1024;
            break;
    }
    
    int result1 = setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &sendBuf, sizeof(sendBuf));
    int result2 = setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &recvBuf, sizeof(recvBuf));
    
    if (result1 == 0 && result2 == 0) {
        LOGD("Socket buffers optimized for fd %d (send: %d, recv: %d)", fd, sendBuf, recvBuf);
        return 0;
    } else {
        LOGE("Failed to optimize socket buffers: %d, %d", errno, result2);
        return -1;
    }
}

} // extern "C"

