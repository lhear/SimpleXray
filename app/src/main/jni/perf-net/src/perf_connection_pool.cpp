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
#include <cstdio>
#include <cstdlib>
#include <sys/ioctl.h>
#include <poll.h>

// TCP_FASTOPEN may not be defined on all Android versions
#ifndef TCP_FASTOPEN
#define TCP_FASTOPEN 23
#endif

#define LOG_TAG "PerfConnPool"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MAX_POOL_SIZE 16
#define DEFAULT_POOL_SIZE 8
#define MIN_POOL_SIZE 4

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
static int g_connection_pool_size = DEFAULT_POOL_SIZE; // User-configured pool size

extern "C" {

/**
 * Initialize connection pool with user-configured size
 * @param pool_size_per_type Number of sockets per pool type (4-16)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeInitConnectionPool(JNIEnv *env, jclass clazz, jint pool_size_per_type) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    // Validate and clamp pool size (4-16)
    int pool_size = pool_size_per_type;
    if (pool_size < MIN_POOL_SIZE) {
        pool_size = MIN_POOL_SIZE;
        LOGD("Pool size too small, clamping to %d", MIN_POOL_SIZE);
    } else if (pool_size > MAX_POOL_SIZE) {
        pool_size = MAX_POOL_SIZE;
        LOGD("Pool size too large, clamping to %d", MAX_POOL_SIZE);
    }
    
    g_connection_pool_size = pool_size;
    
    // Distribute pool size across 3 pool types:
    // H2_STREAM gets 40%, VISION gets 35%, RESERVE gets 25%
    // Use rounding to minimize rounding errors
    int h2_size = (pool_size * 40 + 50) / 100;  // Round to nearest
    int vision_size = (pool_size * 35 + 50) / 100;  // Round to nearest
    int reserve_size = pool_size - h2_size - vision_size;
    
    // Ensure minimum 1 slot per pool
    if (h2_size < 1) h2_size = 1;
    if (vision_size < 1) vision_size = 1;
    if (reserve_size < 1) reserve_size = 1;
    
    int pool_sizes[3] = {h2_size, vision_size, reserve_size};
    
    for (int pool_idx = 0; pool_idx < 3; pool_idx++) {
        ConnectionPool* pool = &g_pools[pool_idx];
        std::lock_guard<std::mutex> lock(pool->mutex);
        
        // Clear existing slots if reinitializing
        for (auto& slot : pool->slots) {
            if (slot.fd >= 0) {
                close(slot.fd);
            }
        }
        pool->slots.clear();
        
        int current_pool_size = pool_sizes[pool_idx];
        pool->slots.resize(current_pool_size);
        for (int i = 0; i < current_pool_size; i++) {
            pool->slots[i].fd = -1;
            pool->slots[i].in_use = false;
            pool->slots[i].connected = false;
            pool->slots[i].type = static_cast<PoolType>(pool_idx);
        }
        
        pool->initialized = true;
        LOGD("Pool %d initialized with %d slots (total pool size: %d)", pool_idx, current_pool_size, pool_size);
    }
    
    return 0;
}

/**
 * Find slot index by file descriptor
 * Helper function to map fd to slot index
 */
static int findSlotIndexByFd(ConnectionPool* pool, int fd) {
    for (size_t i = 0; i < pool->slots.size(); i++) {
        if (pool->slots[i].fd == fd) {
            return static_cast<int>(i);
        }
    }
    return -1;
}

/**
 * Get a socket from pool
 * Returns fd (positive) on success, -1 on error
 * Note: Use nativeGetPooledSocketSlotIndex to get the slot index for the returned fd
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeGetPooledSocket(
    JNIEnv *env, jclass clazz, jint pool_type) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (pool_type < 0 || pool_type >= 3) {
        LOGE("Invalid pool type: %d", pool_type);
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
                if (flags < 0) {
                    LOGE("Failed to get socket flags: %d", errno);
                    close(fd);
                    return -1;
                }
                if (fcntl(fd, F_SETFL, flags | O_NONBLOCK) < 0) {
                    LOGE("Failed to set non-blocking: %d", errno);
                    close(fd);
                    return -1;
                }
                
                // Set socket options for performance
                int opt = 1;
                if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
                    LOGE("Failed to set SO_REUSEADDR: %d", errno);
                }
                // TODO: Add SO_REUSEPORT support for better connection distribution
                if (setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt)) < 0) {
                    LOGE("Failed to set TCP_NODELAY: %d", errno);
                }
                // Socket creation failure is handled by returning -1
                // The slot remains uninitialized (fd=-1) and will be retried on next allocation
                
                // Enable TCP Fast Open if supported
                #ifdef TCP_FASTOPEN
                int tfo_opt = 1;
                if (setsockopt(fd, IPPROTO_TCP, TCP_FASTOPEN, &tfo_opt, sizeof(tfo_opt)) == 0) {
                    LOGD("TCP Fast Open enabled for pooled socket fd %d", fd);
                }
                #endif
                
                // Set keep-alive for persistent connections
                if (setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &opt, sizeof(opt)) < 0) {
                    LOGE("Failed to set SO_KEEPALIVE: %d", errno);
                }
                
                pool->slots[i].fd = fd;
            }
            
            // THREAD: Race condition - in_use set AFTER socket creation
            // BUG: Another thread can get same socket between fd assignment and in_use=true
            // TODO: Use atomic compare-and-swap or set in_use before socket creation
            pool->slots[i].in_use = true;
            pool->slots[i].connected = false;
            LOGD("Got socket from pool %d, slot %zu, fd=%d", pool_type, i, pool->slots[i].fd);
            return pool->slots[i].fd;
        }
    }
    
    LOGE("Pool %d exhausted", pool_type);
    return -1;
}

/**
 * Get slot index for a given file descriptor
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeGetPooledSocketSlotIndex(
    JNIEnv *env, jclass clazz, jint pool_type, jint fd) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (pool_type < 0 || pool_type >= 3 || fd < 0) {
        return -1;
    }
    
    ConnectionPool* pool = &g_pools[pool_type];
    std::lock_guard<std::mutex> lock(pool->mutex);
    
    return findSlotIndexByFd(pool, fd);
}

/**
 * Connect pooled socket by slot index
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeConnectPooledSocket(
    JNIEnv *env, jclass clazz, jint pool_type, jint slot_index, jstring host, jint port) {
    (void)clazz; // JNI required parameter, not used
    
    if (pool_type < 0 || pool_type >= 3) {
        LOGE("Invalid pool type: %d", pool_type);
        return -1;
    }
    
    ConnectionPool* pool = &g_pools[pool_type];
    std::lock_guard<std::mutex> lock(pool->mutex);
    
    if (slot_index < 0 || slot_index >= static_cast<jint>(pool->slots.size())) {
        LOGE("Invalid slot index: %d (max: %zu)", slot_index, pool->slots.size());
        return -1;
    }
    
    ConnectionSlot* slot = &pool->slots[slot_index];
    
    if (slot->fd < 0 || !slot->in_use) {
        LOGE("Slot %d not in use or invalid fd", slot_index);
        return -1;
    }
    
    // Get host string - must release on all error paths
    const char* host_str = env->GetStringUTFChars(host, nullptr);
    if (!host_str) {
        LOGE("Failed to get host string");
        return -1;
    }
    
    // If already connected to same host:port, reuse
    if (slot->connected && 
        strcmp(slot->remote_addr, host_str) == 0 && 
        slot->remote_port == port) {
        LOGD("Socket already connected to %s:%d, reusing", host_str, port);
        env->ReleaseStringUTFChars(host, host_str);
        return 0;
    }
    
    // Disconnect if connected to different host
    if (slot->connected) {
        shutdown(slot->fd, SHUT_RDWR);
        slot->connected = false;
    }
    
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    
    int inet_result = inet_pton(AF_INET, host_str, &addr.sin_addr);
    if (inet_result <= 0) {
        LOGE("Invalid IP address: %s", host_str);
        env->ReleaseStringUTFChars(host, host_str);
        return -1;
    }
    
    int result = connect(slot->fd, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr));
    
    // Safe string copy with guaranteed null termination
    size_t host_len = strlen(host_str);
    size_t copy_len = (host_len < sizeof(slot->remote_addr) - 1) ? host_len : sizeof(slot->remote_addr) - 1;
    memcpy(slot->remote_addr, host_str, copy_len);
    slot->remote_addr[copy_len] = '\0';
    
    if (result == 0) {
        // Immediate connection
        slot->connected = true;
        slot->remote_port = port;
        LOGD("Pooled socket connected immediately: %s:%d", slot->remote_addr, port);
        env->ReleaseStringUTFChars(host, host_str);
        return 0;
    } else if (errno == EINPROGRESS) {
        // Connection in progress (non-blocking)
        slot->connected = false; // Will be set when connection completes
        slot->remote_port = port;
        LOGD("Pooled socket connecting (non-blocking): %s:%d", host_str, port);
        env->ReleaseStringUTFChars(host, host_str);
        return 0; // Return success, caller should check connection status
    } else {
        LOGE("Connect failed for %s:%d: %d (%s)", host_str, port, errno, strerror(errno));
        env->ReleaseStringUTFChars(host, host_str);
        return -1;
    }
}

/**
 * Connect pooled socket by file descriptor (alternative API)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeConnectPooledSocketByFd(
    JNIEnv *env, jclass clazz, jint pool_type, jint fd, jstring host, jint port) {
    (void)clazz; // JNI required parameter, not used
    
    if (pool_type < 0 || pool_type >= 3 || fd < 0) {
        return -1;
    }
    
    ConnectionPool* pool = &g_pools[pool_type];
    std::lock_guard<std::mutex> lock(pool->mutex);
    
    int slot_index = findSlotIndexByFd(pool, fd);
    if (slot_index < 0) {
        LOGE("FD %d not found in pool %d", fd, pool_type);
        return -1;
    }
    
    // Release lock before calling the other function (it will acquire it again)
    // Actually, we can't do that safely. Let's inline the logic:
    ConnectionSlot* slot = &pool->slots[slot_index];
    
    if (!slot->in_use) {
        LOGE("Slot %d not in use", slot_index);
        return -1;
    }
    
    const char* host_str = env->GetStringUTFChars(host, nullptr);
    if (!host_str) {
        // No need to release if GetStringUTFChars failed
        return -1;
    }
    
    if (slot->connected && 
        strcmp(slot->remote_addr, host_str) == 0 && 
        slot->remote_port == port) {
        env->ReleaseStringUTFChars(host, host_str);
        return 0;
    }
    
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    
    if (inet_pton(AF_INET, host_str, &addr.sin_addr) <= 0) {
        env->ReleaseStringUTFChars(host, host_str);
        return -1;
    }
    
    int result = connect(fd, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr));
    
    // Safe string copy with guaranteed null termination
    size_t host_len = strlen(host_str);
    size_t copy_len = (host_len < sizeof(slot->remote_addr) - 1) ? host_len : sizeof(slot->remote_addr) - 1;
    memcpy(slot->remote_addr, host_str, copy_len);
    slot->remote_addr[copy_len] = '\0';
    
    if (result == 0 || errno == EINPROGRESS) {
        if (result == 0) {
            slot->connected = true;
        }
        slot->remote_port = port;
        env->ReleaseStringUTFChars(host, host_str);
        return 0;
    }
    
    env->ReleaseStringUTFChars(host, host_str);
    return -1;
}

/**
 * Check if socket is still valid and healthy
 * Returns true if socket is valid, false if it should be closed
 */
static bool checkSocketHealth(int fd) {
    if (fd < 0) {
        return false;
    }
    
    // Use poll with zero timeout to check socket status without blocking
    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = 0; // We're just checking if socket is valid
    pfd.revents = 0;
    
    int result = poll(&pfd, 1, 0);
    if (result < 0) {
        // Error indicates socket is invalid
        return false;
    }
    
    // Check for errors on the socket
    int error = 0;
    socklen_t error_len = sizeof(error);
    if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &error_len) < 0) {
        // Failed to get socket option - socket is likely invalid
        return false;
    }
    
    if (error != 0) {
        // Socket has an error state
        return false;
    }
    
    return true;
}

/**
 * Return socket to pool by slot index
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeReturnPooledSocket(
    JNIEnv *env, jclass clazz, jint pool_type, jint slot_index) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (pool_type < 0 || pool_type >= 3) {
        return;
    }
    
    ConnectionPool* pool = &g_pools[pool_type];
    std::lock_guard<std::mutex> lock(pool->mutex);
    
    if (slot_index >= 0 && slot_index < static_cast<jint>(pool->slots.size())) {
        ConnectionSlot* slot = &pool->slots[slot_index];
        
        // Health check: verify socket is still valid before returning to pool
        // CRITICAL: Use atomic compare-and-swap to prevent double-free
        int expected_fd = slot->fd;
        if (expected_fd < 0) {
            slot->in_use = false;
            return;
        }
        
        if (slot->fd >= 0 && !checkSocketHealth(slot->fd)) {
            // Use atomic compare-and-swap to atomically invalidate fd
            // __sync_val_compare_and_swap returns old value if swap succeeds
            int old_fd = __sync_val_compare_and_swap(&slot->fd, expected_fd, -1);
            if (old_fd == expected_fd) {
                // We successfully invalidated it, now safe to close
                slot->connected = false;
                slot->in_use = false;
                LOGD("Socket health check failed for fd %d, closing before returning to pool", old_fd);
                close(old_fd);
            }
            return;
        }
        
        slot->in_use = false;
        // Keep socket open for reuse (don't close)
        // Note: connected flag is kept - caller can check if socket is still valid
        LOGD("Returned socket to pool %d, slot %d, fd=%d", pool_type, slot_index, slot->fd);
    }
}

/**
 * Return socket to pool by file descriptor
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeReturnPooledSocketByFd(
    JNIEnv *env, jclass clazz, jint pool_type, jint fd) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    if (pool_type < 0 || pool_type >= 3 || fd < 0) {
        return;
    }
    
    ConnectionPool* pool = &g_pools[pool_type];
    std::lock_guard<std::mutex> lock(pool->mutex);
    
    int slot_index = findSlotIndexByFd(pool, fd);
    if (slot_index >= 0) {
        ConnectionSlot* slot = &pool->slots[slot_index];
        
        // Health check: verify socket is still valid before returning to pool
        // CRITICAL: Use atomic compare-and-swap to prevent double-free
        int expected_fd = slot->fd;
        if (expected_fd < 0 || expected_fd != fd) {
            // fd doesn't match or already invalidated
            slot->in_use = false;
            if (expected_fd != fd) {
                LOGD("Socket fd %d no longer matches slot (fd=%d), marking as not in use", fd, expected_fd);
            }
            return;
        }
        
        if (!checkSocketHealth(fd)) {
            // Use atomic compare-and-swap to atomically invalidate fd
            // __sync_val_compare_and_swap returns old value if swap succeeds
            int old_fd = __sync_val_compare_and_swap(&slot->fd, expected_fd, -1);
            if (old_fd == expected_fd) {
                // We successfully invalidated it, now safe to close
                slot->connected = false;
                slot->in_use = false;
                LOGD("Socket health check failed for fd %d, closing before returning to pool", old_fd);
                close(old_fd);
            } else {
                // Another thread already invalidated it
                slot->in_use = false;
                LOGD("Socket fd %d already invalidated by another thread", fd);
            }
            return;
        }
        
        slot->in_use = false;
        LOGD("Returned socket to pool %d by fd %d, slot %d", pool_type, fd, slot_index);
    }
}

/**
 * Destroy connection pool
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyConnectionPool(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz; // JNI required parameters, not used
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

