/*
 * Dedicated epoll loop for ultra-fast I/O
 * Replaces Java Selector with native epoll_wait()
 */

#include <jni.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <cstring>
#include <android/log.h>
#include <atomic>
#include <vector>
#include <algorithm>

#define LOG_TAG "PerfEpoll"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MAX_EVENTS 256
#define EPOLL_TIMEOUT_MS_DEFAULT 100

struct EpollContext {
    int epfd;
    std::atomic<bool> running;
    pthread_t thread;
    std::vector<int> registered_fds;
};

static EpollContext* g_epoll_ctx = nullptr;
extern std::atomic<JavaVM*> g_jvm; // Defined in perf_jni.cpp (atomic for thread safety)
static pthread_mutex_t g_epoll_mutex = PTHREAD_MUTEX_INITIALIZER;

extern "C" {

/**
 * Initialize epoll loop
 */
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeInitEpoll(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz; // JNI required parameters, not used
    pthread_mutex_lock(&g_epoll_mutex);
    
    if (g_epoll_ctx) {
        pthread_mutex_unlock(&g_epoll_mutex);
        return reinterpret_cast<jlong>(g_epoll_ctx);
    }
    
    EpollContext* ctx = new (std::nothrow) EpollContext();
    if (!ctx) {
        LOGE("Failed to allocate EpollContext");
        pthread_mutex_unlock(&g_epoll_mutex);
        return 0;
    }
    ctx->epfd = epoll_create1(EPOLL_CLOEXEC);
    ctx->running.store(false);
    
    if (ctx->epfd < 0) {
        LOGE("Failed to create epoll: %d", errno);
        delete ctx;
        pthread_mutex_unlock(&g_epoll_mutex);
        return 0;
    }
    
    g_epoll_ctx = ctx;
    LOGD("Epoll initialized: fd=%d", ctx->epfd);
    
    pthread_mutex_unlock(&g_epoll_mutex);
    return reinterpret_cast<jlong>(ctx);
}

/**
 * Add file descriptor to epoll
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEpollAdd(JNIEnv *env, jclass clazz, jlong epoll_handle, jint fd, jint events) {
    (void)env; (void)clazz; // JNI required parameters, not used
    if (!epoll_handle || fd < 0) {
        LOGE("Invalid parameters: handle=%p, fd=%d", reinterpret_cast<void*>(epoll_handle), fd);
        return -1;
    }
    
    EpollContext* ctx = reinterpret_cast<EpollContext*>(epoll_handle);
    
    struct epoll_event ev;
    ev.events = events;
    ev.data.fd = fd;
    
    // Set non-blocking
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        LOGE("Failed to get fd flags: %s", strerror(errno));
        return -1;
    }
    if (fcntl(fd, F_SETFL, flags | O_NONBLOCK) < 0) {
        LOGE("Failed to set non-blocking: %s", strerror(errno));
        return -1;
    }
    
    int result = epoll_ctl(ctx->epfd, EPOLL_CTL_ADD, fd, &ev);
    
    if (result == 0) {
        pthread_mutex_lock(&g_epoll_mutex);
        // Check for duplicate fd before adding
        auto it = std::find(ctx->registered_fds.begin(), ctx->registered_fds.end(), fd);
        if (it == ctx->registered_fds.end()) {
            ctx->registered_fds.push_back(fd);
        } else {
            LOGD("FD %d already registered, skipping duplicate", fd);
        }
        pthread_mutex_unlock(&g_epoll_mutex);
        LOGD("Added fd %d to epoll", fd);
    } else {
        LOGE("Failed to add fd %d to epoll: %s", fd, strerror(errno));
    }
    
    return result;
}

/**
 * Remove file descriptor from epoll
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEpollRemove(JNIEnv *env, jclass clazz, jlong epoll_handle, jint fd) {
    (void)env; (void)clazz; // JNI required parameters, not used
    if (!epoll_handle || fd < 0) {
        LOGE("Invalid parameters: handle=%p, fd=%d", reinterpret_cast<void*>(epoll_handle), fd);
        return -1;
    }
    
    EpollContext* ctx = reinterpret_cast<EpollContext*>(epoll_handle);
    
    int result = epoll_ctl(ctx->epfd, EPOLL_CTL_DEL, fd, nullptr);
    
    if (result == 0) {
        pthread_mutex_lock(&g_epoll_mutex);
        auto it = std::find(ctx->registered_fds.begin(), ctx->registered_fds.end(), fd);
        if (it != ctx->registered_fds.end()) {
            ctx->registered_fds.erase(it);
        }
        pthread_mutex_unlock(&g_epoll_mutex);
        LOGD("Removed fd %d from epoll", fd);
    } else {
        LOGE("Failed to remove fd %d from epoll: %s", fd, strerror(errno));
    }
    
    return result;
}

/**
 * Wait for events (blocking)
 * Returns number of ready events
 * @param timeout_ms Timeout in milliseconds (-1 for infinite, 0 for non-blocking)
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEpollWait(JNIEnv *env, jclass clazz, jlong epoll_handle, jlongArray out_events, jint timeout_ms) {
    (void)clazz; // JNI required parameter, not used
    if (!epoll_handle) {
        LOGE("Invalid epoll handle");
        return -1;
    }
    
    // Ensure thread is attached to JVM (critical for background threads)
    // Use atomic load with acquire semantics to ensure we see the initialized JavaVM*
    JavaVM* jvm = g_jvm.load(std::memory_order_acquire);
    JNIEnv* thread_env = env;
    int attached = 0;
    jint attach_status = JNI_ERR;
    if (jvm && jvm->GetEnv(reinterpret_cast<void**>(&thread_env), JNI_VERSION_1_6) != JNI_OK) {
        attach_status = jvm->AttachCurrentThread(&thread_env, nullptr);
        if (attach_status != JNI_OK) {
            LOGE("Failed to attach thread to JVM");
            return -1;
        }
        attached = 1;
    } else {
        // Thread already attached, use provided env
        thread_env = env;
    }
    env = thread_env;
    
    EpollContext* ctx = reinterpret_cast<EpollContext*>(epoll_handle);
    
    struct epoll_event events[MAX_EVENTS];
    // Use provided timeout, default to EPOLL_TIMEOUT_MS_DEFAULT if timeout_ms is -2 (for backward compatibility)
    // Note: Consider using epoll_pwait2() for better timeout precision on newer kernels
    int timeout = (timeout_ms == -2) ? EPOLL_TIMEOUT_MS_DEFAULT : timeout_ms;
    int nfds = epoll_wait(ctx->epfd, events, MAX_EVENTS, timeout);
    
    if (nfds < 0) {
        if (errno == EINTR) {
            // Interrupted by signal, return 0 (no events) instead of error
            return 0;
        }
        LOGE("epoll_wait failed: %s", strerror(errno));
        return -1;
    }
    
    if (nfds > 0 && out_events) {
        jsize size = env->GetArrayLength(out_events);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("JNI exception occurred while getting array length");
            return -1;
        }
        if (size < nfds) {
            LOGE("Output array too small: %d < %d", size, nfds);
            nfds = size; // Limit to available space
        }
        
        jlong* arr = env->GetLongArrayElements(out_events, nullptr);
        if (!arr || env->ExceptionCheck()) {
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                LOGE("JNI exception occurred while getting long array elements");
            } else {
                LOGE("Failed to get array elements");
            }
            return -1;
        }
        
        for (int i = 0; i < nfds; i++) {
            // Pack fd and events into jlong
            // Validate fd fits in 32 bits
            jlong fd = events[i].data.fd;
            if (fd < 0 || fd > 0xFFFFFFFFL) {
                LOGE("Invalid fd value: %ld", fd);
                fd = -1;
            }
            arr[i] = (fd << 32) | (events[i].events & 0xFFFFFFFFL);
        }
        
        env->ReleaseLongArrayElements(out_events, arr, 0);
    }
    
    // Detach thread if we attached it
    // Use atomic load to ensure we see the current JavaVM* value
    if (attached == 1) {
        JavaVM* jvm = g_jvm.load(std::memory_order_acquire);
        if (jvm) {
            jvm->DetachCurrentThread();
        }
    }
    
    return nfds;
}

/**
 * Destroy epoll loop
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyEpoll(JNIEnv *env, jclass clazz, jlong epoll_handle) {
    (void)env; (void)clazz; // JNI required parameters, not used
    if (!epoll_handle) return;
    
    pthread_mutex_lock(&g_epoll_mutex);
    
    EpollContext* ctx = reinterpret_cast<EpollContext*>(epoll_handle);
    ctx->running.store(false);
    
    // Remove all registered fds from epoll
    // Note: We don't close fds here as they are managed by the caller
    // The caller is responsible for closing file descriptors
    for (int fd : ctx->registered_fds) {
        epoll_ctl(ctx->epfd, EPOLL_CTL_DEL, fd, nullptr);
    }
    
    close(ctx->epfd);
    ctx->registered_fds.clear();
    
    if (g_epoll_ctx == ctx) {
        g_epoll_ctx = nullptr;
    }
    
    delete ctx;
    
    pthread_mutex_unlock(&g_epoll_mutex);
    LOGD("Epoll destroyed");
}

} // extern "C"


