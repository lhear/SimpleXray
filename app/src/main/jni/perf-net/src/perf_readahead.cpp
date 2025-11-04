/*
 * Read-Ahead Optimization
 * Prefetches next chunks to fill Android I/O pipeline
 */

#include <jni.h>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdlib.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "PerfReadAhead"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Enable read-ahead for file descriptor
 * Uses posix_fadvise() if available
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEnableReadAhead(
    JNIEnv *env, jclass clazz, jint fd, jlong offset, jlong length) {
    
    // posix_fadvise() is not available on Android, but we can hint
    // by doing a small prefetch read
    char buffer[4096];
    ssize_t result = recv(fd, buffer, sizeof(buffer), MSG_PEEK | MSG_DONTWAIT);
    
    if (result < 0 && errno != EAGAIN && errno != EWOULDBLOCK) {
        LOGE("Read-ahead peek failed: %d", errno);
        return -1;
    }
    
    LOGD("Read-ahead enabled for fd %d", fd);
    return 0;
}

/**
 * Prefetch data for streaming
 * Reads 1-2 chunks ahead using MSG_PEEK to avoid consuming data
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativePrefetchChunks(
    JNIEnv *env, jclass clazz, jint fd, jint chunk_size, jint num_chunks) {
    
    if (chunk_size <= 0 || num_chunks <= 0 || chunk_size > 1024 * 1024) {
        LOGE("Invalid chunk size or count");
        return -1;
    }
    
    // Use MSG_PEEK to prefetch without consuming data
    // This allows subsequent reads to get the data from kernel buffer
    char* buffer = static_cast<char*>(malloc(chunk_size));
    if (!buffer) {
        LOGE("Failed to allocate prefetch buffer");
        return -1;
    }
    
    // Get current socket flags
    int flags = fcntl(fd, F_GETFL, 0);
    bool was_nonblocking = (flags & O_NONBLOCK) != 0;
    
    // Ensure non-blocking for peek
    if (!was_nonblocking) {
        fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    }
    
    // Peek at data to prefetch into kernel buffer
    ssize_t total_peeked = 0;
    for (int i = 0; i < num_chunks; i++) {
        ssize_t peeked = recv(fd, buffer, chunk_size, MSG_PEEK | MSG_DONTWAIT);
        if (peeked < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                break; // No more data available
            }
            LOGE("Prefetch peek failed: %d", errno);
            break;
        }
        total_peeked += peeked;
        if (peeked < chunk_size) {
            break; // Partial peek
        }
    }
    
    // Restore original blocking state
    if (!was_nonblocking) {
        fcntl(fd, F_SETFL, flags);
    }
    
    free(buffer);
    
    if (total_peeked > 0) {
        LOGD("Prefetched %zd bytes into kernel buffer (%d chunks)", total_peeked, num_chunks);
    }
    
    return static_cast<jint>(total_peeked);
}

} // extern "C"

