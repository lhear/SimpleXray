/*
 * Optimized memcpy helper for small copies (< 128 bytes)
 * Hand-unrolled loops and compiler hints for hot paths
 */

#ifndef PERF_MEMCPY_HELPER_H
#define PERF_MEMCPY_HELPER_H

#include <cstddef>
#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// Inline optimized memcpy for small sizes (< 128 bytes)
// Uses hand-unrolled 32/64-bit copies for better performance
__attribute__((hot, always_inline))
static inline void perf_fast_memcpy(void* __restrict__ dst, const void* __restrict__ src, size_t len) {
    if (__builtin_expect(len == 0, 0)) return;
    
    uint8_t* d = static_cast<uint8_t*>(dst);
    const uint8_t* s = static_cast<const uint8_t*>(src);
    
    // Use __builtin_memcpy_inline for very small copies (compiler can optimize)
    if (__builtin_expect(len <= 8, 1)) {
        switch (len) {
            case 8: *reinterpret_cast<uint64_t*>(d) = *reinterpret_cast<const uint64_t*>(s); return;
            case 7: d[6] = s[6]; // fallthrough
            case 6: *reinterpret_cast<uint32_t*>(d + 2) = *reinterpret_cast<const uint32_t*>(s + 2); // fallthrough
            case 5: d[4] = s[4]; // fallthrough
            case 4: *reinterpret_cast<uint32_t*>(d) = *reinterpret_cast<const uint32_t*>(s); return;
            case 3: d[2] = s[2]; // fallthrough
            case 2: *reinterpret_cast<uint16_t*>(d) = *reinterpret_cast<const uint16_t*>(s); return;
            case 1: *d = *s; return;
            default: break;
        }
        return;
    }
    
    // Aligned 64-bit copies for medium sizes
    if (__builtin_expect(len <= 64, 1)) {
        size_t i = 0;
        // Copy 8 bytes at a time
        for (; i + 8 <= len; i += 8) {
            *reinterpret_cast<uint64_t*>(d + i) = *reinterpret_cast<const uint64_t*>(s + i);
        }
        // Handle remainder
        if (i < len) {
            __builtin_memcpy_inline(d + i, s + i, len - i);
        }
        return;
    }
    
    // For larger sizes, use standard memcpy (let libc handle it)
    __builtin_memcpy(d, s, len);
}

#ifdef __cplusplus
}
#endif

#endif // PERF_MEMCPY_HELPER_H

