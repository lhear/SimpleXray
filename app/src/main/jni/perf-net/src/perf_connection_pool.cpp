/*
 * Pinned Connection Pool
 * Pre-allocated persistent sockets for zero handshake overhead
 */

#include <jni.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <android/log.h>
#include <vector>
#include <mutex>
#include <algorithm>
#include <cstring>

#define LOG_TAG "PerfConnPool"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MAX_POOL_SIZE 8
#define H2_STREAM_POOL_SIZE 3
#define VISION_POOL_SIZE 3
#define RESERVE_POOL_SIZE 2

enum PoolType {
    POOL_H2_STREAM = 0,
    POOL_VISION = 1,
    POOL_RESERVE = 2
};

struct ConnectionSlot {
    int fd;
    bool in_use;
    bool connected;
    char remote_addr[64];
    int remote_port;
    PoolType type;
};

struct ConnectionPool {
    std::vector<ConnectionSlot> slots;
    std::mutex mutex;
    bool initialized;
};

static ConnectionPool g_pools[3]; // H2, Vision, Reserve

extern "C" {

/**
 * Initialize connection pool
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeInitConnectionPool(JNIEnv *env, jclass clazz) {
    for (int pool_idx = 0; pool_idx < 3; pool_idx++) {
        ConnectionPool* pool = &g_pools[pool_idx];
        std::lock_guard<std::mutex> lock(pool->mutex);
        
        int pool_size = (pool_idx == 0) ? H2_STREAM_POOL_SIZE :
                       (pool_idx == 1) ? VISION_POOL_SIZE : RESERVE_POOL_SIZE;
        
        pool->slots.resize(pool_size);
        for (int i = 0; i < pool_size; i++) {
            pool->slots[i].fd = -1;
            pool->slots[i].in_use = false;
            pool->slots[i].connected = false;
            pool->slots[i].type = static_cast<PoolType>(pool_idx);
        }
        
        pool->initialized = true;
        LOGD("Pool %d initialized with %d slots", pool_idx, pool_size);
    }
    
    return 0;
}

/**
 * Get a socket from pool
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeGetPooledSocket(
    JNIEnv *env, jclass clazz, jint pool_type) {
    
    if (pool_type < 0 || pool_type >= 3) {
        return -1;
    }
    
    ConnectionPool* pool = &g_pools[pool_type];
    std::lock_guard<std::mutex> lock(pool->mutex);
    
    // Find available slot
    for (size_t i = 0; i < pool->slots.size(); i++) {
        if (!pool->slots[i].in_use) {
            if (pool->slots[i].fd < 0) {
                // Create new socket
                int fd = socket(AF_INET, SOCK_STREAM, 0);
                if (fd < 0) {
                    LOGE("Failed to create socket: %d", errno);
                    return -1;
                }
                
                // Set non-blocking
                int flags = fcntl(fd, F_GETFL, 0);
                fcntl(fd, F_SETFL, flags | O_NONBLOCK);
                
                // Set socket options for performance
                int opt = 1;
                setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
                setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
                
                pool->slots[i].fd = fd;
            }
            
            pool->slots[i].in_use = true;
            LOGD("Got socket from pool %d, slot %zu, fd=%d", pool_type, i, pool->slots[i].fd);
            return pool->slots[i].fd;
        }
    }
    
    LOGE("Pool %d exhausted", pool_type);
    return -1;
}

/**
 * Connect pooled socket
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeConnectPooledSocket(
    JNIEnv *env, jclass clazz, jint pool_type, jint slot_index, jstring host, jint port) {
    
    if (pool_type < 0 || pool_type >= 3) {
        return -1;
    }
    
    ConnectionPool* pool = &g_pools[pool_type];
    std::lock_guard<std::mutex> lock(pool->mutex);
    
    if (slot_index < 0 || slot_index >= static_cast<jint>(pool->slots.size())) {
        return -1;
    }
    
    ConnectionSlot* slot = &pool->slots[slot_index];
    
    if (slot->fd < 0) {
        return -1;
    }
    
    // Get host string
    const char* host_str = env->GetStringUTFChars(host, nullptr);
    if (!host_str) {
        return -1;
    }
    
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    inet_pton(AF_INET, host_str, &addr.sin_addr);
    
    int result = connect(slot->fd, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr));
    
    if (result == 0 || errno == EINPROGRESS) {
        slot->connected = true;
        strncpy(slot->remote_addr, host_str, sizeof(slot->remote_addr) - 1);
        slot->remote_addr[sizeof(slot->remote_addr) - 1] = '\0';
        slot->remote_port = port;
        LOGD("Pooled socket connected: %s:%d", slot->remote_addr, port);
        env->ReleaseStringUTFChars(host, host_str);
        return 0;
    }
    
    env->ReleaseStringUTFChars(host, host_str);
    
    LOGE("Connect failed: %d", errno);
    return -1;
}

/**
 * Return socket to pool
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeReturnPooledSocket(
    JNIEnv *env, jclass clazz, jint pool_type, jint slot_index) {
    
    if (pool_type < 0 || pool_type >= 3) {
        return;
    }
    
    ConnectionPool* pool = &g_pools[pool_type];
    std::lock_guard<std::mutex> lock(pool->mutex);
    
    if (slot_index >= 0 && slot_index < static_cast<jint>(pool->slots.size())) {
        pool->slots[slot_index].in_use = false;
        // Keep socket open for reuse (don't close)
        LOGD("Returned socket to pool %d, slot %d", pool_type, slot_index);
    }
}

/**
 * Destroy connection pool
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyConnectionPool(JNIEnv *env, jclass clazz) {
    for (int pool_idx = 0; pool_idx < 3; pool_idx++) {
        ConnectionPool* pool = &g_pools[pool_idx];
        std::lock_guard<std::mutex> lock(pool->mutex);
        
        for (auto& slot : pool->slots) {
            if (slot.fd >= 0) {
                close(slot.fd);
                slot.fd = -1;
            }
        }
        
        pool->slots.clear();
        pool->initialized = false;
        LOGD("Pool %d destroyed", pool_idx);
    }
}

} // extern "C"

