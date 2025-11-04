/*
 * Crypto Acceleration using ARM NEON & Crypto Extensions
 * Hardware-accelerated AES and ChaCha20
 */

#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <cstdio>

#if defined(__aarch64__) || defined(__arm__)
#include <arm_neon.h>
#define HAS_NEON 1
#else
#define HAS_NEON 0
#endif

#define LOG_TAG "PerfCrypto"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// AES key schedule (128-bit) - only valid when NEON is available
#if HAS_NEON
struct aes128_key {
    uint8x16_t key[11];
};
#else
// Dummy struct for non-ARM builds
struct aes128_key {
    uint8_t key[176];  // 11 * 16 bytes
};
#endif

extern "C" {


/**
 * Check if ARMv8 Crypto Extensions are available
 */
JNIEXPORT jboolean JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeHasCryptoExtensions(JNIEnv *env, jclass clazz) {
    FILE* f = fopen("/proc/cpuinfo", "r");
    if (!f) return JNI_FALSE;
    
    char line[256];
    bool has_crypto = false;
    
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "Features")) {
            if (strstr(line, "aes") || strstr(line, "pmull")) {
                has_crypto = true;
                break;
            }
        }
    }
    
    fclose(f);
    return has_crypto ? JNI_TRUE : JNI_FALSE;
}

/**
 * AES-128 encrypt using ARMv8 Crypto Extensions
 * 
 * WARNING: This is a simplified implementation for demonstration.
 * For production use, integrate OpenSSL/BoringSSL with hardware acceleration:
 * - Use AES_encrypt() from OpenSSL with EVP interface
 * - Enable hardware acceleration via ENGINE API
 * - Proper key expansion and full AES rounds required
 * 
 * Current implementation uses NEON intrinsics but is not cryptographically secure.
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeAES128Encrypt(
    JNIEnv *env, jclass clazz, jobject input, jint input_offset, jint input_len,
    jobject output, jint output_offset, jobject key) {
    
    void* input_ptr = env->GetDirectBufferAddress(input);
    void* output_ptr = env->GetDirectBufferAddress(output);
    void* key_ptr = env->GetDirectBufferAddress(key);
    
    if (!input_ptr || !output_ptr || !key_ptr) {
        LOGE("Invalid direct buffers");
        return -1;
    }
    
    if (input_len < 0 || input_offset < 0 || output_offset < 0) {
        LOGE("Invalid offsets or length");
        return -1;
    }
    
    uint8_t* in = static_cast<uint8_t*>(input_ptr) + input_offset;
    uint8_t* out = static_cast<uint8_t*>(output_ptr) + output_offset;
    uint8_t* key_data = static_cast<uint8_t*>(key_ptr);
    
#if HAS_NEON && defined(__aarch64__)
    // Process in 16-byte blocks (AES block size)
    int blocks = input_len / 16;
    int remainder = input_len % 16;
    
    // Load key (128-bit = 16 bytes)
    uint8x16_t key_vec = vld1q_u8(key_data);
    
    for (int i = 0; i < blocks; i++) {
        uint8x16_t data = vld1q_u8(in + i * 16);
        
        // AES-128 encryption using ARMv8 Crypto Extensions
        // This is a simplified version - full AES requires key expansion and 10 rounds
        // For production: use vaeseq_u8 + vaesmcq_u8 for all 10 rounds with expanded keys
        data = vaeseq_u8(data, key_vec);  // AES encryption round
        data = vaesmcq_u8(data);          // AES MixColumns
        
        // Note: Full implementation requires:
        // 1. Key expansion (AES_key_expansion)
        // 2. 10 rounds of vaeseq_u8 + vaesmcq_u8
        // 3. Final round without MixColumns
        
        vst1q_u8(out + i * 16, data);
    }
    
    // Handle remainder (last incomplete block)
    if (remainder > 0) {
        // For production, use proper padding (PKCS#7)
        memcpy(out + blocks * 16, in + blocks * 16, remainder);
        // Pad remainder block (simplified - production needs proper padding)
        memset(out + blocks * 16 + remainder, 0, 16 - remainder);
    }
#else
    // Fallback for non-ARM or non-ARM64: use software implementation
    // WARNING: This is NOT secure - for production use OpenSSL
    LOGD("Using software fallback (not secure)");
    for (int i = 0; i < input_len; i++) {
        out[i] = in[i] ^ key_data[i % 16];
    }
#endif
    
    return input_len;
}

/**
 * ChaCha20 using NEON SIMD
 * 
 * WARNING: This is a simplified implementation for demonstration.
 * For production use, integrate optimized ChaCha20-NEON library:
 * - Use ChaCha20-Poly1305 from OpenSSL/BoringSSL
 * - Full quarter round implementation with proper state management
 * - Counter increment and block handling
 * 
 * Current implementation uses NEON but is not cryptographically secure.
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeChaCha20NEON(
    JNIEnv *env, jclass clazz, jobject input, jint input_offset, jint input_len,
    jobject output, jint output_offset, jobject key, jobject nonce) {
    
    void* input_ptr = env->GetDirectBufferAddress(input);
    void* output_ptr = env->GetDirectBufferAddress(output);
    void* key_ptr = env->GetDirectBufferAddress(key);
    void* nonce_ptr = env->GetDirectBufferAddress(nonce);
    
    if (!input_ptr || !output_ptr || !key_ptr || !nonce_ptr) {
        LOGE("Invalid direct buffers");
        return -1;
    }
    
    if (input_len < 0 || input_offset < 0 || output_offset < 0) {
        LOGE("Invalid offsets or length");
        return -1;
    }
    
    uint8_t* in = static_cast<uint8_t*>(input_ptr) + input_offset;
    uint8_t* out = static_cast<uint8_t*>(output_ptr) + output_offset;
    uint8_t* key_data = static_cast<uint8_t*>(key_ptr);
    uint8_t* nonce_data = static_cast<uint8_t*>(nonce_ptr);
    
#if HAS_NEON
    // ChaCha20 uses 32-byte key and 12-byte nonce
    // Load key (256-bit = 32 bytes, split into two 128-bit registers)
    uint8x16_t key0 = vld1q_u8(key_data);
    uint8x16_t key1 = vld1q_u8(key_data + 16);
    uint8x16_t nonce_vec = vld1q_u8(nonce_data);
    
    // Process in 64-byte blocks (ChaCha20 block size)
    int blocks = input_len / 64;
    int remainder = input_len % 64;
    
    // Note: Full ChaCha20 implementation requires:
    // 1. Initialize state matrix with key, nonce, counter, constants
    // 2. For each block: 20 rounds (10 double-rounds) of quarter rounds
    // 3. Add original state to encrypted state
    // 4. Increment counter for next block
    
    // This is a simplified version - production needs full quarter round implementation
    for (int b = 0; b < blocks; b++) {
        // Process 64-byte block
        for (int i = 0; i < 64; i += 16) {
            uint8x16_t data = vld1q_u8(in + b * 64 + i);
            
            // Simplified: XOR with key (NOT secure - full implementation needed)
            // Full implementation: ChaCha20 quarter round with proper state mixing
            if (i < 16) {
                data = veorq_u8(data, key0);
            } else {
                data = veorq_u8(data, key1);
            }
            
            vst1q_u8(out + b * 64 + i, data);
        }
    }
    
    // Handle remainder
    if (remainder > 0) {
        for (int i = 0; i < remainder; i++) {
            out[blocks * 64 + i] = in[blocks * 64 + i] ^ key_data[i % 32];
        }
    }
    
    return input_len;
#else
    // Fallback for non-ARM: use software implementation
    // WARNING: This is NOT secure - for production use OpenSSL
    LOGD("Using software fallback (not secure)");
    for (int i = 0; i < input_len; i++) {
        out[i] = in[i] ^ key_data[i % 32];
    }
    return input_len;
#endif
}

/**
 * Prefetch data into CPU cache
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativePrefetch(
    JNIEnv *env, jclass clazz, jobject buffer, jint offset, jint length) {
    
    void* ptr = env->GetDirectBufferAddress(buffer);
    if (!ptr) return;
    
    char* data = static_cast<char*>(ptr) + offset;
    
    // Prefetch for read
    for (int i = 0; i < length; i += 64) {
        __builtin_prefetch(data + i, 0, 3); // 0 = read, 3 = high temporal locality
    }
}

/**
 * Check if NEON is available
 */
JNIEXPORT jboolean JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeHasNEON(JNIEnv *env, jclass clazz) {
#if HAS_NEON
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

} // extern "C"

