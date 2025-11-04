/*
 * Kernel Pacing Disable Simulation
 * Internal pacing FIFO to avoid kernel-level jitter
 */

#include <jni.h>
#include <android/log.h>
#include <sys/socket.h>
#include <unistd.h>
#include <errno.h>
#include <queue>
#include <mutex>
#include <thread>
#include <atomic>
#include <cstring>
#include <vector>
#include <chrono>
#include <time.h>
#include <stdlib.h>
#include <algorithm>

#define LOG_TAG "PerfKernelPacing"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

struct PacingPacket {
    char* data;
    size_t len;
    int fd;
    long timestamp;
};

struct PacingFIFO {
    std::queue<PacingPacket> queue;
    std::mutex mutex;
    std::atomic<bool> running;
    std::thread worker_thread;
    size_t max_size;
};

static PacingFIFO* g_pacing_fifo = nullptr;

extern "C" {

/**
 * Initialize internal pacing FIFO
 */
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeInitPacingFIFO(
    JNIEnv *env, jclass clazz, jint max_size) {
    
    PacingFIFO* fifo = new PacingFIFO();
    fifo->max_size = max_size;
    fifo->running.store(false);
    
    g_pacing_fifo = fifo;
    LOGD("Pacing FIFO initialized, max_size=%d", max_size);
    
    return reinterpret_cast<jlong>(fifo);
}

/**
 * Enqueue packet for pacing
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeEnqueuePacket(
    JNIEnv *env, jclass clazz, jlong handle, jint fd, jbyteArray data, jint offset, jint length) {
    
    if (!handle) return -1;
    
    PacingFIFO* fifo = reinterpret_cast<PacingFIFO*>(handle);
    
    std::lock_guard<std::mutex> lock(fifo->mutex);
    
    if (fifo->queue.size() >= fifo->max_size) {
        return -1; // Queue full
    }
    
    PacingPacket packet;
    packet.data = static_cast<char*>(malloc(length));
    packet.len = length;
    packet.fd = fd;
    
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    memcpy(packet.data, bytes + offset, length);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    packet.timestamp = ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    
    fifo->queue.push(packet);
    
    return 0;
}

/**
 * Process pacing queue (internal worker)
 */
static void pacing_worker(PacingFIFO* fifo) {
    const int BATCH_SIZE = 16; // Process 16 packets at once
    const int INTERVAL_MS = 1; // 1ms intervals
    
    while (fifo->running.load()) {
        std::vector<PacingPacket> batch;
        
        {
            std::lock_guard<std::mutex> lock(fifo->mutex);
            for (int i = 0; i < BATCH_SIZE && !fifo->queue.empty(); i++) {
                batch.push_back(fifo->queue.front());
                fifo->queue.pop();
            }
        }
        
        // Process batch
        for (auto& packet : batch) {
            // Send packet using socket
            ssize_t sent = send(packet.fd, packet.data, packet.len, MSG_DONTWAIT | MSG_NOSIGNAL);
            if (sent < 0 && errno != EAGAIN && errno != EWOULDBLOCK) {
                LOGD("Pacing send failed for fd %d: %d", packet.fd, errno);
            }
            free(packet.data);
        }
        
        // Microburst smoothing
        std::this_thread::sleep_for(std::chrono::milliseconds(INTERVAL_MS));
    }
}

/**
 * Start pacing worker thread
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeStartPacing(
    JNIEnv *env, jclass clazz, jlong handle) {
    
    if (!handle) return -1;
    
    PacingFIFO* fifo = reinterpret_cast<PacingFIFO*>(handle);
    
    if (fifo->running.load()) {
        return 0; // Already running
    }
    
    fifo->running.store(true);
    fifo->worker_thread = std::thread(pacing_worker, fifo);
    
    LOGD("Pacing worker started");
    return 0;
}

/**
 * Stop and destroy pacing FIFO
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeDestroyPacingFIFO(
    JNIEnv *env, jclass clazz, jlong handle) {
    
    if (!handle) return;
    
    PacingFIFO* fifo = reinterpret_cast<PacingFIFO*>(handle);
    
    fifo->running.store(false);
    if (fifo->worker_thread.joinable()) {
        fifo->worker_thread.join();
    }
    
    // Clear queue
    std::lock_guard<std::mutex> lock(fifo->mutex);
    while (!fifo->queue.empty()) {
        PacingPacket packet = fifo->queue.front();
        free(packet.data);
        fifo->queue.pop();
    }
    
    delete fifo;
    if (g_pacing_fifo == fifo) {
        g_pacing_fifo = nullptr;
    }
    
    LOGD("Pacing FIFO destroyed");
}

} // extern "C"

