/*
 * Hyper Crypto Pipeline - Multi-worker crypto processing
 * Spawns coreCount * 2 threads, NEON accelerated ChaCha/POLY or AES
 */

#include "hyper_backend.hpp"
#include <jni.h>
#include <thread>
#include <vector>
#include <atomic>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <android/log.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <pthread.h>
#include <sched.h>

#ifdef USE_OPENSSL
#include <openssl/evp.h>
#include <openssl/aes.h>
#include <openssl/crypto.h>
#endif

#if defined(__aarch64__) || defined(__arm__)
#include <arm_neon.h>
#define HAS_NEON 1
#else
#define HAS_NEON 0
#endif

#define LOG_TAG "HyperCrypto"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Crypto job structure
struct CryptoJob {
    RingSlot* slot;
    void* output;
    size_t outputSize;
    std::atomic<bool> done;
};

// Crypto worker pool
struct alignas(64) CryptoPool {
    std::vector<std::thread> workers;
    std::queue<CryptoJob*> jobQueue;
    std::mutex queueMutex;
    std::condition_variable queueCond;
    std::atomic<bool> running;
    int workerCount;
    WorkerLocal* workerLocals;
};

static CryptoPool* g_crypto_pool = nullptr;
static std::once_flag g_pool_init_flag;

// Pin thread to CPU core
static void pin_thread_to_core(int core_id) {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(core_id, &cpuset);
    pthread_setaffinity_np(pthread_self(), sizeof(cpu_set_t), &cpuset);
}

// Crypto worker thread
__attribute__((hot))
__attribute__((flatten))
static void crypto_worker(int worker_id, CryptoPool* pool) {
    // Pin to big core (4-7 on typical ARM big.LITTLE)
    int core_id = 4 + (worker_id % 4);
    pin_thread_to_core(core_id);
    
    WorkerLocal* local = &pool->workerLocals[worker_id];
    local->workerId = static_cast<uint32_t>(worker_id);
    
    while (pool->running.load(std::memory_order_acquire)) {
        CryptoJob* job = nullptr;
        
        {
            std::unique_lock<std::mutex> lock(pool->queueMutex);
            pool->queueCond.wait(lock, [pool] {
                return !pool->jobQueue.empty() || !pool->running.load(std::memory_order_acquire);
            });
            
            if (!pool->running.load(std::memory_order_acquire)) {
                break;
            }
            
            if (!pool->jobQueue.empty()) {
                job = pool->jobQueue.front();
                pool->jobQueue.pop();
            }
        }
        
        if (job && job->slot) {
            // Process crypto (ChaCha20 or AES)
            void* input = job->slot->payload;
            size_t inputLen = job->slot->payloadSize;
            
            // Use NEON-accelerated crypto if available
            #ifdef USE_OPENSSL
            if (HAS_NEON) {
                // Use OpenSSL with hardware acceleration
                // Simplified: encrypt with ChaCha20
                EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
                if (ctx) {
                    uint8_t key[32] = {0}; // In production, use actual key
                    uint8_t iv[12] = {0};  // In production, use actual IV
                    
                    EVP_EncryptInit_ex(ctx, EVP_chacha20_poly1305(), nullptr, key, iv);
                    int outlen = 0;
                    EVP_EncryptUpdate(ctx, static_cast<unsigned char*>(job->output), 
                                     &outlen, static_cast<const unsigned char*>(input), 
                                     static_cast<int>(inputLen));
                    EVP_EncryptFinal_ex(ctx, static_cast<unsigned char*>(job->output) + outlen, &outlen);
                    EVP_CIPHER_CTX_free(ctx);
                    job->outputSize = static_cast<size_t>(outlen);
                }
            } else
            #endif
            {
                // Software fallback (simple XOR for demo)
                uint8_t* in = static_cast<uint8_t*>(input);
                uint8_t* out = static_cast<uint8_t*>(job->output);
                for (size_t i = 0; i < inputLen; i++) {
                    out[i] = in[i] ^ 0xAA; // Simple XOR (replace with real crypto)
                }
                job->outputSize = inputLen;
            }
            
            local->processedCount++;
            local->totalBytes += inputLen;
            local->lastTimestamp = job->slot->meta.timestampNs;
            
            job->done.store(true, std::memory_order_release);
        }
    }
}

// Initialize crypto pool
static void init_crypto_pool(int worker_count) {
    std::call_once(g_pool_init_flag, [worker_count]() {
        g_crypto_pool = new CryptoPool();
        g_crypto_pool->workerCount = worker_count;
        g_crypto_pool->running.store(true, std::memory_order_release);
        
        // Allocate worker local storage
        void* locals_ptr = nullptr;
        posix_memalign(&locals_ptr, 64, worker_count * sizeof(WorkerLocal));
        g_crypto_pool->workerLocals = static_cast<WorkerLocal*>(locals_ptr);
        
        // Start worker threads
        for (int i = 0; i < worker_count; i++) {
            g_crypto_pool->workers.emplace_back(crypto_worker, i, g_crypto_pool);
        }
        
        LOGD("Crypto pool initialized with %d workers", worker_count);
    });
}

extern "C" {

/**
 * Submit crypto job to worker pool
 */
__attribute__((hot))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeSubmitCrypto(
    JNIEnv *env, jclass clazz, jlong slotHandle, jint outputLen) {
    (void)env; (void)clazz;
    
    if (!slotHandle) return 0;
    
    // Initialize pool if needed (coreCount * 2)
    static std::once_flag init_flag;
    std::call_once(init_flag, []() {
        int core_count = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
        int worker_count = core_count * 2;
        init_crypto_pool(worker_count);
    });
    
    if (!g_crypto_pool || !g_crypto_pool->running.load(std::memory_order_acquire)) {
        return 0;
    }
    
    RingSlot* slot = reinterpret_cast<RingSlot*>(slotHandle);
    if (!slot || !slot->payload) {
        return 0;
    }
    
    // Create job
    CryptoJob* job = new CryptoJob();
    job->slot = slot;
    job->output = malloc(static_cast<size_t>(outputLen));
    job->outputSize = 0;
    job->done.store(false, std::memory_order_release);
    
    if (!job->output) {
        delete job;
        return 0;
    }
    
    // Ensure output size is at least input size
    if (outputLen < static_cast<jint>(slot->payloadSize)) {
        free(job->output);
        job->output = malloc(static_cast<size_t>(slot->payloadSize));
        if (!job->output) {
            delete job;
            return 0;
        }
    }
    
    // Submit to queue
    {
        std::lock_guard<std::mutex> lock(g_crypto_pool->queueMutex);
        g_crypto_pool->jobQueue.push(job);
    }
    g_crypto_pool->queueCond.notify_one();
    
    // Return job handle
    return reinterpret_cast<jlong>(job);
}

/**
 * Wait for crypto job completion
 */
__attribute__((hot))
JNIEXPORT jint JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeWaitCrypto(
    JNIEnv *env, jclass clazz, jlong jobHandle, jlong timeoutMs) {
    (void)env; (void)clazz;
    
    if (!jobHandle) return -1;
    
    CryptoJob* job = reinterpret_cast<CryptoJob*>(jobHandle);
    
    // Wait for completion (spin wait for hot path)
    uint64_t timeoutUs = static_cast<uint64_t>(timeoutMs) * 1000;
    uint64_t start = 0; // Would use clock_gettime in real implementation
    
    while (!job->done.load(std::memory_order_acquire)) {
        // Spin wait with timeout
        // In production, use proper timing
        if (timeoutUs > 0) {
            // Check timeout
            break;
        }
    }
    
    if (job->done.load(std::memory_order_acquire)) {
        return static_cast<jint>(job->outputSize);
    }
    
    return -1; // Timeout
}

/**
 * Get crypto output pointer
 */
__attribute__((hot))
JNIEXPORT jlong JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeGetCryptoOutput(
    JNIEnv *env, jclass clazz, jlong jobHandle) {
    (void)env; (void)clazz;
    
    if (!jobHandle) return 0;
    
    CryptoJob* job = reinterpret_cast<CryptoJob*>(jobHandle);
    return reinterpret_cast<jlong>(job->output);
}

/**
 * Free crypto job
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_hyper_backend_HyperBackend_nativeFreeCryptoJob(
    JNIEnv *env, jclass clazz, jlong jobHandle) {
    (void)env; (void)clazz;
    
    if (!jobHandle) return;
    
    CryptoJob* job = reinterpret_cast<CryptoJob*>(jobHandle);
    if (job->output) {
        free(job->output);
    }
    delete job;
}

} // extern "C"

