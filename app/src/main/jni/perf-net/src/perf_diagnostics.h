/*
 * Optional Performance Diagnostics
 * Enabled via PERF_DIAG macro for development/debugging
 */

#ifndef PERF_DIAGNOSTICS_H
#define PERF_DIAGNOSTICS_H

// Enable diagnostics by defining PERF_DIAG before including this header
#ifdef PERF_DIAG

#include <cstdint>
#include <atomic>
#include <android/log.h>

#define LOG_TAG "PerfDiag"
#define LOGD_DIAG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Performance counters (relaxed memory order for telemetry)
struct alignas(64) PerfCounters {
    std::atomic<uint64_t> packets_processed{0};
    std::atomic<uint64_t> cycles_total{0};
    std::atomic<uint64_t> branch_misses{0};
    std::atomic<uint64_t> cache_misses{0};
    std::atomic<uint64_t> jni_calls{0};
    
    // Rate-limited logging (every N packets)
    static constexpr uint64_t LOG_INTERVAL = 10000;
    std::atomic<uint64_t> last_log_packet{0};
    
    void record_packet(uint64_t cycles) {
        packets_processed.fetch_add(1, std::memory_order_relaxed);
        cycles_total.fetch_add(cycles, std::memory_order_relaxed);
        
        // Rate-limited diagnostic log
        uint64_t count = packets_processed.load(std::memory_order_relaxed);
        if (count - last_log_packet.load(std::memory_order_relaxed) >= LOG_INTERVAL) {
            uint64_t avg_cycles = cycles_total.load(std::memory_order_relaxed) / 
                                  (count > 0 ? count : 1);
            LOGD_DIAG("Perf: %llu packets, avg %llu cycles/packet",
                     (unsigned long long)count, (unsigned long long)avg_cycles);
            last_log_packet.store(count, std::memory_order_relaxed);
        }
    }
    
    void record_branch_miss() {
        branch_misses.fetch_add(1, std::memory_order_relaxed);
    }
    
    void record_cache_miss() {
        cache_misses.fetch_add(1, std::memory_order_relaxed);
    }
    
    void record_jni_call() {
        jni_calls.fetch_add(1, std::memory_order_relaxed);
    }
};

// Global diagnostics instance
extern PerfCounters g_perf_counters;

#define PERF_RECORD_PACKET(cycles) g_perf_counters.record_packet(cycles)
#define PERF_RECORD_BRANCH_MISS() g_perf_counters.record_branch_miss()
#define PERF_RECORD_CACHE_MISS() g_perf_counters.record_cache_miss()
#define PERF_RECORD_JNI_CALL() g_perf_counters.record_jni_call()

#else

// Diagnostics disabled - no-op macros
#define PERF_RECORD_PACKET(cycles) ((void)0)
#define PERF_RECORD_BRANCH_MISS() ((void)0)
#define PERF_RECORD_CACHE_MISS() ((void)0)
#define PERF_RECORD_JNI_CALL() ((void)0)

#endif // PERF_DIAG

#endif // PERF_DIAGNOSTICS_H

