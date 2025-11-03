/*
 * MTU Tuning & Jumbo Frame Support
 * Optimizes MTU for LTE (1380-1436) and 5G (1420-1460)
 */

#include <jni.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <linux/if.h>
#include <linux/if_tun.h>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>
#include <errno.h>
#include <cstring>

#define LOG_TAG "PerfMTU"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Set optimal MTU based on network type
 * @param fd TUN interface file descriptor
 * @param networkType 0=LTE, 1=5G, 2=WiFi
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeSetOptimalMTU(
    JNIEnv *env, jclass clazz, jint fd, jint networkType) {
    
    int optimal_mtu;
    
    switch (networkType) {
        case 0: // LTE
            optimal_mtu = 1436; // 1500 - 40 (IPv6 + options) - 24 (overhead)
            break;
        case 1: // 5G
            optimal_mtu = 1460; // Larger for 5G
            break;
        case 2: // WiFi
            optimal_mtu = 1500; // Standard Ethernet
            break;
        default:
            optimal_mtu = 1436;
    }
    
    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, "tun0", IFNAMSIZ - 1);
    ifr.ifr_mtu = optimal_mtu;
    
    int result = ioctl(fd, SIOCSIFMTU, &ifr);
    
    if (result == 0) {
        LOGD("MTU set to %d for network type %d", optimal_mtu, networkType);
        return optimal_mtu;
    } else {
        LOGE("Failed to set MTU: %d", errno);
        return -1;
    }
}

/**
 * Get current MTU
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeGetMTU(
    JNIEnv *env, jclass clazz, jint fd) {
    
    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, "tun0", IFNAMSIZ - 1);
    
    int result = ioctl(fd, SIOCGIFMTU, &ifr);
    
    if (result == 0) {
        return ifr.ifr_mtu;
    } else {
        LOGE("Failed to get MTU: %d", errno);
        return -1;
    }
}

/**
 * Set socket buffer sizes for high throughput
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeSetSocketBuffers(
    JNIEnv *env, jclass clazz, jint fd, jint sendBuffer, jint recvBuffer) {
    
    int result = 0;
    
    // Set send buffer
    if (setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &sendBuffer, sizeof(sendBuffer)) < 0) {
        LOGE("Failed to set send buffer: %d", errno);
        result = -1;
    }
    
    // Set receive buffer
    if (setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &recvBuffer, sizeof(recvBuffer)) < 0) {
        LOGE("Failed to set recv buffer: %d", errno);
        result = -1;
    }
    
    if (result == 0) {
        LOGD("Socket buffers set: send=%d, recv=%d", sendBuffer, recvBuffer);
    }
    
    return result;
}

} // extern "C"

