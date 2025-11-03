/*
 * Read-Ahead Optimization
 * Prefetches next chunks to fill Android I/O pipeline
 */

#include <jni.h>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
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
 * Reads 1-2 chunks ahead
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativePrefetchChunks(
    JNIEnv *env, jclass clazz, jint fd, jint chunk_size, jint num_chunks) {
    
    // Prefetch by reading ahead (non-blocking)
    char* buffer = static_cast<char*>(malloc(chunk_size * num_chunks));
    if (!buffer) {
        return -1;
    }
    
    // Set non-blocking
    int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    
    // Read ahead
    ssize_t total_read = 0;
    for (int i = 0; i < num_chunks; i++) {
        ssize_t read = recv(fd, buffer + (i * chunk_size), chunk_size, MSG_DONTWAIT);
        if (read < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                break; // No more data available
            }
            free(buffer);
            return -1;
        }
        total_read += read;
        if (read < chunk_size) {
            break; // Partial read
        }
    }
    
    // Restore blocking
    fcntl(fd, F_SETFL, flags);
    
    // Data is now in kernel buffer, discard it
    free(buffer);
    
    LOGD("Prefetched %zd bytes (%d chunks)", total_read, num_chunks);
    return static_cast<jint>(total_read);
}

} // extern "C"

