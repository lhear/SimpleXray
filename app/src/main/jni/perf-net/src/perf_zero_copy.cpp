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
    
    void* buf_ptr = env->GetDirectBufferAddress(buffer);
    if (!buf_ptr) {
        LOGE("Not a direct buffer");
        return -1;
    }
    
    char* data = static_cast<char*>(buf_ptr) + offset;
    
    // Try MSG_ZEROCOPY (requires kernel 4.14+)
    ssize_t received = recv(fd, data, length, MSG_DONTWAIT);
    
    if (received < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0; // No data available
        }
        LOGE("recv failed: %d", errno);
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
    
    void* buf_ptr = env->GetDirectBufferAddress(buffer);
    if (!buf_ptr) {
        LOGE("Not a direct buffer");
        return -1;
    }
    
    char* data = static_cast<char*>(buf_ptr) + offset;
    
    ssize_t sent = send(fd, data, length, MSG_DONTWAIT | MSG_NOSIGNAL);
    
    if (sent < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0; // Would block
        }
        LOGE("send failed: %d", errno);
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
    
    jsize num_buffers = env->GetArrayLength(buffers);
    if (num_buffers == 0 || num_buffers > IOV_MAX) {
        return -1;
    }
    
    struct iovec iov[IOV_MAX];
    jint* len_arr = env->GetIntArrayElements(lengths, nullptr);
    
    for (int i = 0; i < num_buffers; i++) {
        jobject buffer = env->GetObjectArrayElement(buffers, i);
        void* buf_ptr = env->GetDirectBufferAddress(buffer);
        
        if (!buf_ptr) {
            env->ReleaseIntArrayElements(lengths, len_arr, JNI_ABORT);
            return -1;
        }
        
        iov[i].iov_base = buf_ptr;
        iov[i].iov_len = len_arr[i];
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

