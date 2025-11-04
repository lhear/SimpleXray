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
#include <android/log.h>
#include <atomic>
#include <vector>
#include <algorithm>

#define LOG_TAG "PerfEpoll"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MAX_EVENTS 256
#define EPOLL_TIMEOUT_MS 100

struct EpollContext {
    int epfd;
    std::atomic<bool> running;
    pthread_t thread;
    std::vector<int> registered_fds;
};

static EpollContext* g_epoll_ctx = nullptr;
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
    
    EpollContext* ctx = new EpollContext();
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
    if (!epoll_handle) return -1;
    
    EpollContext* ctx = reinterpret_cast<EpollContext*>(epoll_handle);
    
    struct epoll_event ev;
    ev.events = events;
    ev.data.fd = fd;
    
    // Set non-blocking
    int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    
    int result = epoll_ctl(ctx->epfd, EPOLL_CTL_ADD, fd, &ev);
    
    if (result == 0) {
        pthread_mutex_lock(&g_epoll_mutex);
        ctx->registered_fds.push_back(fd);
        pthread_mutex_unlock(&g_epoll_mutex);
        LOGD("Added fd %d to epoll", fd);
    } else {
        LOGE("Failed to add fd %d to epoll: %d", fd, errno);
    }
    
    return result;
}

/**
 * Remove file descriptor from epoll
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEpollRemove(JNIEnv *env, jclass clazz, jlong epoll_handle, jint fd) {
    (void)env; (void)clazz; // JNI required parameters, not used
    if (!epoll_handle) return -1;
    
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
    }
    
    return result;
}

/**
 * Wait for events (blocking)
 * Returns number of ready events
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEpollWait(JNIEnv *env, jclass clazz, jlong epoll_handle, jlongArray out_events) {
    (void)clazz; // JNI required parameter, not used
    if (!epoll_handle) return -1;
    
    EpollContext* ctx = reinterpret_cast<EpollContext*>(epoll_handle);
    
    struct epoll_event events[MAX_EVENTS];
    int nfds = epoll_wait(ctx->epfd, events, MAX_EVENTS, EPOLL_TIMEOUT_MS);
    
    if (nfds > 0 && out_events) {
        jsize size = env->GetArrayLength(out_events);
        jlong* arr = env->GetLongArrayElements(out_events, nullptr);
        
        for (int i = 0; i < nfds && i < size; i++) {
            // Pack fd and events into jlong
            arr[i] = ((jlong)events[i].data.fd << 32) | events[i].events;
        }
        
        env->ReleaseLongArrayElements(out_events, arr, 0);
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
    
    // Close all registered fds
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


