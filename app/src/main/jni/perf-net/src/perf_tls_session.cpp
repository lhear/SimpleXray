/*
 * TLS Session Ticket Hoarding
 * Reuses TLS sessions to avoid handshake overhead (-60% latency)
 */

#include <jni.h>
#include <android/log.h>
#include <unordered_map>
#include <mutex>
#include <string>
#include <cstring>
#include <time.h>
#include <stdlib.h>

#define LOG_TAG "PerfTLSSession"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct TlsSessionTicket {
    unsigned char* ticket_data;
    size_t ticket_len;
    long timestamp;
    int ref_count;
};

static std::unordered_map<std::string, TlsSessionTicket*> g_session_cache;
static std::mutex g_cache_mutex;
static const size_t MAX_CACHE_SIZE = 100;
static const long TICKET_TTL_MS = 3600000; // 1 hour

extern "C" {

/**
 * Store TLS session ticket
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeStoreTLSTicket(
    JNIEnv *env, jclass clazz, jstring host, jbyteArray ticket_data) {
    
    const char* host_str = env->GetStringUTFChars(host, nullptr);
    if (!host_str) return -1;
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("JNI exception occurred while getting host string");
        return -1;
    }
    
    jsize ticket_len = env->GetArrayLength(ticket_data);
    if (ticket_len <= 0 || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("JNI exception occurred while getting array length");
        }
        env->ReleaseStringUTFChars(host, host_str);
        return -1;
    }
    
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    // Check cache size and remove expired/old entries
    struct timespec check_ts;
    clock_gettime(CLOCK_REALTIME, &check_ts);
    long current_time = check_ts.tv_sec * 1000 + check_ts.tv_nsec / 1000000;
    
    // First, remove expired entries
    auto it = g_session_cache.begin();
    while (it != g_session_cache.end()) {
        if (current_time - it->second->timestamp > TICKET_TTL_MS) {
            free(it->second->ticket_data);
            delete it->second;
            it = g_session_cache.erase(it);
        } else {
            ++it;
        }
    }
    
    // If still full, remove oldest entry
    if (g_session_cache.size() >= MAX_CACHE_SIZE) {
        auto oldest = g_session_cache.begin();
        for (auto it = g_session_cache.begin(); it != g_session_cache.end(); ++it) {
            if (it->second->timestamp < oldest->second->timestamp) {
                oldest = it;
            }
        }
        if (oldest != g_session_cache.end()) {
            free(oldest->second->ticket_data);
            delete oldest->second;
            g_session_cache.erase(oldest);
        }
    }
    
    // Allocate and store ticket
    TlsSessionTicket* ticket = new TlsSessionTicket();
    ticket->ticket_len = ticket_len;
    ticket->ticket_data = static_cast<unsigned char*>(malloc(ticket_len));
    if (!ticket->ticket_data) {
        delete ticket;
        env->ReleaseStringUTFChars(host, host_str);
        LOGE("Failed to allocate memory for TLS ticket");
        return -1;
    }
    // Get current time (milliseconds since epoch)
    struct timespec store_ts;
    clock_gettime(CLOCK_REALTIME, &store_ts);
    ticket->timestamp = store_ts.tv_sec * 1000 + store_ts.tv_nsec / 1000000;
    ticket->ref_count = 1;
    
    jbyte* bytes = env->GetByteArrayElements(ticket_data, nullptr);
    if (!bytes || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("JNI exception occurred while getting byte array elements");
        }
        free(ticket->ticket_data);
        delete ticket;
        env->ReleaseStringUTFChars(host, host_str);
        return -1;
    }
    memcpy(ticket->ticket_data, bytes, ticket_len);
    env->ReleaseByteArrayElements(ticket_data, bytes, JNI_ABORT);
    
    std::string host_key(host_str);
    g_session_cache[host_key] = ticket;
    
    env->ReleaseStringUTFChars(host, host_str);
    LOGD("Stored TLS ticket for %s, size: %zu", host_str, ticket_len);
    
    return 0;
}

/**
 * Get TLS session ticket
 */
JNIEXPORT jbyteArray JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeGetTLSTicket(
    JNIEnv *env, jclass clazz, jstring host) {
    
    const char* host_str = env->GetStringUTFChars(host, nullptr);
    if (!host_str) return nullptr;
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("JNI exception occurred while getting host string");
        return nullptr;
    }
    
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    std::string host_key(host_str);
    auto it = g_session_cache.find(host_key);
    
    if (it == g_session_cache.end()) {
        env->ReleaseStringUTFChars(host, host_str);
        return nullptr;
    }
    
    TlsSessionTicket* ticket = it->second;
    
    // Check if expired
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    long current_time = ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    
    if (current_time - ticket->timestamp > TICKET_TTL_MS) {
        // Expired, remove from cache
        free(ticket->ticket_data);
        delete ticket;
        g_session_cache.erase(it);
        env->ReleaseStringUTFChars(host, host_str);
        LOGD("TLS ticket expired for %s", host_str);
        return nullptr;
    }
    
    jbyteArray result = env->NewByteArray(ticket->ticket_len);
    if (!result || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("JNI exception occurred while creating byte array");
        }
        env->ReleaseStringUTFChars(host, host_str);
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, ticket->ticket_len, 
                           reinterpret_cast<jbyte*>(ticket->ticket_data));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("JNI exception occurred while setting byte array region");
        env->ReleaseStringUTFChars(host, host_str);
        return nullptr;
    }
    
    env->ReleaseStringUTFChars(host, host_str);
    LOGD("Retrieved TLS ticket for %s", host_str);
    
    return result;
}

/**
 * Clear TLS session cache
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeClearTLSCache(JNIEnv *env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    for (auto& pair : g_session_cache) {
        free(pair.second->ticket_data);
        delete pair.second;
    }
    
    g_session_cache.clear();
    LOGD("TLS session cache cleared");
}

} // extern "C"

/**
 * Cleanup on JNI unload to prevent memory leaks
 * Note: JNI_OnUnload must be outside extern "C" block but with C linkage
 */
extern "C" void JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)vm; (void)reserved;
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    
    for (auto& pair : g_session_cache) {
        free(pair.second->ticket_data);
        delete pair.second;
    }
    
    g_session_cache.clear();
    LOGD("TLS session cache cleaned up on unload");
}

