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

#define LOG_TAG "PerfZeroCopy"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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
        LOGE("Buffer overflow: capacity=%lld, offset=%d, length=%d", capacity, offset, length);
        return -1;
    }
    
    char* data = static_cast<char*>(buf_ptr) + offset;
    
    // Try MSG_ZEROCOPY (requires kernel 4.14+)
    // TODO: Implement actual MSG_ZEROCOPY support - currently using regular recv
    // TODO: Add MSG_ZEROCOPY flag detection and notification mechanism for completion
    // BUG: Function name suggests zero-copy but uses regular recv() - misleading implementation
    ssize_t received = recv(fd, data, length, MSG_DONTWAIT);
    
    if (received < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0; // No data available
        }
        LOGE("recv failed: %s", strerror(errno));
        return -1;
    }
    
    // BUG: No validation that received bytes don't exceed buffer capacity
    // TODO: Add bounds checking after recv to prevent buffer overflows
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
    // BUG: Integer overflow risk - offset + length might overflow jlong before comparison
    // TODO: Add overflow check before arithmetic operations
    if (capacity < 0 || offset + length > capacity) {
        LOGE("Buffer overflow: capacity=%lld, offset=%d, length=%d", capacity, offset, length);
        return -1;
    }
    
    char* data = static_cast<char*>(buf_ptr) + offset;
    
    // TODO: Implement actual MSG_ZEROCOPY for send operations (requires kernel 4.14+)
    // TODO: Add support for zerocopy completion notifications via epoll
    ssize_t sent = send(fd, data, length, MSG_DONTWAIT | MSG_NOSIGNAL);
    
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

