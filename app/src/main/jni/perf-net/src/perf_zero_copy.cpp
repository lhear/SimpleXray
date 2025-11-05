/*
 * Zero-Copy I/O operations
 * Direct kernel-to-user-space transfers with minimal copying
 */

#include <jni.h>
#include <sys/socket.h>
#include <sys/uio.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include <android/log.h>
#include <cstring>
#include <limits.h>
#include <linux/version.h>
#include <fcntl.h>

#define LOG_TAG "PerfZeroCopy"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// MSG_ZEROCOPY was introduced in Linux 4.14
// Check if it's available at compile time
#ifndef MSG_ZEROCOPY
#define MSG_ZEROCOPY 0x4000000
#endif

// Cache for MSG_ZEROCOPY support detection
static int g_zerocopy_supported = -1; // -1 = not checked, 0 = not supported, 1 = supported

/**
 * Check if MSG_ZEROCOPY is supported by the kernel
 * This requires kernel 4.14+ and proper socket configuration
 */
static bool checkZeroCopySupport() {
    // Check cache first
    if (g_zerocopy_supported >= 0) {
        return g_zerocopy_supported == 1;
    }
    
    // Try to create a test socket and check if MSG_ZEROCOPY is available
    int testFd = socket(AF_INET, SOCK_STREAM, 0);
    if (testFd < 0) {
        g_zerocopy_supported = 0;
        return false;
    }
    
    // Enable SO_ZEROCOPY option (required for MSG_ZEROCOPY)
    int opt = 1;
    int result = setsockopt(testFd, SOL_SOCKET, SO_ZEROCOPY, &opt, sizeof(opt));
    
    close(testFd);
    
    // If SO_ZEROCOPY is supported, MSG_ZEROCOPY should work
    g_zerocopy_supported = (result == 0) ? 1 : 0;
    
    if (g_zerocopy_supported) {
        LOGD("MSG_ZEROCOPY support detected");
    } else {
        LOGD("MSG_ZEROCOPY not supported (kernel may be < 4.14 or feature not enabled)");
    }
    
    return g_zerocopy_supported == 1;
}

extern "C" {

/**
 * Receive with zero-copy (MSG_ZEROCOPY if available)
 * Falls back to regular recv if not supported
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeRecvZeroCopy(
    JNIEnv *env, jclass clazz, jint fd, jobject buffer, jint offset, jint length) {
    (void)clazz; // JNI required parameter, not used
    
    if (fd < 0 || length < 0 || offset < 0) {
        LOGE("Invalid parameters: fd=%d, offset=%d, length=%d", fd, offset, length);
        return -1;
    }
    
    void* buf_ptr = env->GetDirectBufferAddress(buffer);
    if (!buf_ptr) {
        LOGE("Not a direct buffer");
        return -1;
    }
    
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (capacity < 0 || offset + length > capacity) {
        LOGE("Buffer overflow: capacity=%lld, offset=%d, length=%d", (long long)capacity, offset, length);
        return -1;
    }
    
    char* data = static_cast<char*>(buf_ptr) + offset;
    
    // Note: MSG_ZEROCOPY is primarily for send operations, not recv
    // For receive operations, zero-copy is typically achieved through:
    // - recvmsg with MSG_ZEROCOPY (requires kernel 5.20+)
    // - mmap-based receive buffers
    // For now, we use regular recv with MSG_DONTWAIT as MSG_ZEROCOPY for recv
    // is not widely available on Android kernels
    
    // Check if MSG_ZEROCOPY is available for recv (kernel 5.20+)
    bool use_zerocopy = false;
    #ifdef MSG_ZEROCOPY
    // For recv, MSG_ZEROCOPY support is limited and may not be available
    // We'll attempt it but fall back to regular recv if needed
    use_zerocopy = checkZeroCopySupport();
    #endif
    
    ssize_t received;
    if (use_zerocopy) {
        // Attempt zero-copy receive (requires kernel 5.20+)
        // Note: This may not be supported on all Android devices
        received = recv(fd, data, length, MSG_DONTWAIT | MSG_ZEROCOPY);
        if (received < 0 && errno == EOPNOTSUPP) {
            // Zero-copy not supported, fall back to regular recv
            LOGD("MSG_ZEROCOPY not supported for recv, falling back to regular recv");
            received = recv(fd, data, length, MSG_DONTWAIT);
        }
    } else {
        // Use regular recv (standard on all systems)
        received = recv(fd, data, length, MSG_DONTWAIT);
    }
    
    if (received < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0; // No data available
        }
        LOGE("recv failed: %s", strerror(errno));
        return -1;
    }
    
    // Validate received bytes don't exceed buffer capacity
    if (static_cast<size_t>(received) > static_cast<size_t>(length)) {
        LOGE("Received more bytes than requested: received=%zd, requested=%d", received, length);
        return -1;
    }
    return static_cast<jint>(received);
}

/**
 * Send with zero-copy
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeSendZeroCopy(
    JNIEnv *env, jclass clazz, jint fd, jobject buffer, jint offset, jint length) {
    (void)clazz; // JNI required parameter, not used
    
    if (fd < 0 || length < 0 || offset < 0) {
        LOGE("Invalid parameters: fd=%d, offset=%d, length=%d", fd, offset, length);
        return -1;
    }
    
    void* buf_ptr = env->GetDirectBufferAddress(buffer);
    if (!buf_ptr) {
        LOGE("Not a direct buffer");
        return -1;
    }
    
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    // Check for integer overflow before comparison
    if (capacity < 0 || offset < 0 || length < 0) {
        LOGE("Invalid parameters: capacity=%lld, offset=%d, length=%d", (long long)capacity, offset, length);
        return -1;
    }
    if (offset > capacity || length > capacity || offset > capacity - length) {
        LOGE("Buffer overflow: capacity=%lld, offset=%d, length=%d", (long long)capacity, offset, length);
        return -1;
    }
    
    char* data = static_cast<char*>(buf_ptr) + offset;
    
    // Enable SO_ZEROCOPY on socket if not already enabled (required for MSG_ZEROCOPY)
    static thread_local int zerocopy_enabled = -1; // -1 = not checked
    if (zerocopy_enabled < 0) {
        int opt = 1;
        if (setsockopt(fd, SOL_SOCKET, SO_ZEROCOPY, &opt, sizeof(opt)) == 0) {
            zerocopy_enabled = 1;
            LOGD("SO_ZEROCOPY enabled for socket fd %d", fd);
        } else {
            zerocopy_enabled = 0;
            LOGD("SO_ZEROCOPY not available for socket fd %d: %s", fd, strerror(errno));
        }
    }
    
    // Use MSG_ZEROCOPY if supported (kernel 4.14+)
    bool use_zerocopy = checkZeroCopySupport() && (zerocopy_enabled == 1);
    ssize_t sent;
    
    if (use_zerocopy) {
        // Attempt zero-copy send (requires kernel 4.14+ and SO_ZEROCOPY enabled)
        sent = send(fd, data, length, MSG_DONTWAIT | MSG_NOSIGNAL | MSG_ZEROCOPY);
        if (sent < 0 && errno == EOPNOTSUPP) {
            // Zero-copy not supported, fall back to regular send
            LOGD("MSG_ZEROCOPY not supported for send, falling back to regular send");
            sent = send(fd, data, length, MSG_DONTWAIT | MSG_NOSIGNAL);
        }
        // Note: With MSG_ZEROCOPY, completion notifications can be received via
        // SO_EE_CODE_ZEROCOPY_COPIED error queue messages (requires epoll/select)
    } else {
        // Use regular send (fallback for older kernels)
        sent = send(fd, data, length, MSG_DONTWAIT | MSG_NOSIGNAL);
    }
    
    if (sent < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0; // Would block
        }
        LOGE("send failed: %s", strerror(errno));
        return -1;
    }
    
    return static_cast<jint>(sent);
}

/**
 * Scatter-gather receive (recvmsg)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeRecvMsg(
    JNIEnv *env, jclass clazz, jint fd, jobjectArray buffers, jintArray lengths) {
    (void)clazz; // JNI required parameter, not used
    
    if (fd < 0 || !buffers || !lengths) {
        LOGE("Invalid parameters: fd=%d, buffers=%p, lengths=%p", fd, buffers, lengths);
        return -1;
    }
    
    jsize num_buffers = env->GetArrayLength(buffers);
    jsize num_lengths = env->GetArrayLength(lengths);
    
    if (num_buffers == 0 || num_buffers > IOV_MAX || num_buffers != num_lengths) {
        LOGE("Invalid array sizes: buffers=%d, lengths=%d, max=%d", 
             num_buffers, num_lengths, IOV_MAX);
        return -1;
    }
    
    struct iovec iov[IOV_MAX];
    jint* len_arr = env->GetIntArrayElements(lengths, nullptr);
    if (!len_arr) {
        LOGE("Failed to get lengths array");
        return -1;
    }
    
    for (int i = 0; i < num_buffers; i++) {
        if (len_arr[i] < 0) {
            LOGE("Invalid length at index %d: %d", i, len_arr[i]);
            env->ReleaseIntArrayElements(lengths, len_arr, JNI_ABORT);
            return -1;
        }
        
        jobject buffer = env->GetObjectArrayElement(buffers, i);
        if (!buffer) {
            LOGE("Null buffer at index %d", i);
            env->ReleaseIntArrayElements(lengths, len_arr, JNI_ABORT);
            return -1;
        }
        
        void* buf_ptr = env->GetDirectBufferAddress(buffer);
        if (!buf_ptr) {
            LOGE("Not a direct buffer at index %d", i);
            env->ReleaseIntArrayElements(lengths, len_arr, JNI_ABORT);
            return -1;
        }
        
        iov[i].iov_base = buf_ptr;
        iov[i].iov_len = static_cast<size_t>(len_arr[i]);
    }
    
    struct msghdr msg;
    memset(&msg, 0, sizeof(msg));
    msg.msg_iov = iov;
    msg.msg_iovlen = num_buffers;
    
    ssize_t received = recvmsg(fd, &msg, MSG_DONTWAIT);
    
    env->ReleaseIntArrayElements(lengths, len_arr, JNI_ABORT);
    
    if (received < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0;
        }
        LOGE("recvmsg failed: %s", strerror(errno));
        return -1;
    }
    
    return static_cast<jint>(received);
}

/**
 * Allocate direct ByteBuffer in native memory
 * Note: JVM manages the memory, we just call the Java API
 */
JNIEXPORT jobject JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeAllocateDirectBuffer(
    JNIEnv *env, jclass clazz, jint capacity) {
    (void)clazz; // JNI required parameter, not used
    
    if (capacity <= 0) {
        return nullptr;
    }
    
    // Create DirectByteBuffer using JVM's native allocation
    jclass byteBufferClass = env->FindClass("java/nio/ByteBuffer");
    if (!byteBufferClass) {
        return nullptr;
    }
    
    jmethodID allocateDirectMethod = env->GetStaticMethodID(
        byteBufferClass, "allocateDirect", "(I)Ljava/nio/ByteBuffer;");
    if (!allocateDirectMethod) {
        return nullptr;
    }
    
    jobject buffer = env->CallStaticObjectMethod(byteBufferClass, allocateDirectMethod, capacity);
    
    return buffer;
}

} // extern "C"

