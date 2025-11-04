/*
 * Crypto Acceleration using ARM NEON & Crypto Extensions
 * Hardware-accelerated AES and ChaCha20
 */

#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cctype>
#include <mutex>
#if defined(__aarch64__) && defined(__ANDROID_API__) && __ANDROID_API__ >= 18
#include <sys/auxv.h>
#endif

// OpenSSL support (compiled with -DUSE_OPENSSL=1 if OpenSSL is available)
#ifdef USE_OPENSSL
#include <openssl/evp.h>
#include <openssl/aes.h>
#include <openssl/chacha.h>
#include <openssl/err.h>
#endif

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

// Cache for CPU capabilities
static bool g_crypto_extensions_cached = false;
static bool g_crypto_extensions_available = false;
static std::mutex g_crypto_cache_mutex;

extern "C" {

/**
 * Check if ARMv8 Crypto Extensions are available (cached)
 */
JNIEXPORT jboolean JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeHasCryptoExtensions(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz; // JNI required parameters, not used
    
    // Check cache first
    {
        std::lock_guard<std::mutex> lock(g_crypto_cache_mutex);
        if (g_crypto_extensions_cached) {
            return g_crypto_extensions_available ? JNI_TRUE : JNI_FALSE;
        }
    }
    
    // Use getauxval() if available (more reliable than /proc/cpuinfo)
    #if defined(__aarch64__) && defined(__ANDROID_API__) && __ANDROID_API__ >= 18
    unsigned long hwcap = getauxval(AT_HWCAP);
    if (hwcap & HWCAP_AES) {
        std::lock_guard<std::mutex> lock(g_crypto_cache_mutex);
        g_crypto_extensions_cached = true;
        g_crypto_extensions_available = true;
        return JNI_TRUE;
    }
    #endif
    
    // Fallback to /proc/cpuinfo parsing
    FILE* f = fopen("/proc/cpuinfo", "r");
    if (!f) return JNI_FALSE;
    
    char line[512];
    bool has_crypto = false;
    
    // Read entire file once
    while (fgets(line, sizeof(line), f)) {
        // Case-insensitive comparison for "Features"
        bool is_features_line = false;
        for (int i = 0; line[i] != '\0' && i < 512 - 8; i++) {
            if (tolower(line[i]) == 'f' && 
                tolower(line[i+1]) == 'e' &&
                tolower(line[i+2]) == 'a' &&
                tolower(line[i+3]) == 't' &&
                tolower(line[i+4]) == 'u' &&
                tolower(line[i+5]) == 'r' &&
                tolower(line[i+6]) == 'e' &&
                tolower(line[i+7]) == 's') {
                is_features_line = true;
                break;
            }
        }
        
        if (is_features_line) {
            // Case-insensitive search for "aes" or "pmull"
            for (int i = 0; line[i] != '\0' && i < 512 - 5; i++) {
                if ((tolower(line[i]) == 'a' && tolower(line[i+1]) == 'e' && tolower(line[i+2]) == 's') ||
                    (tolower(line[i]) == 'p' && tolower(line[i+1]) == 'm' && tolower(line[i+2]) == 'u' && 
                     tolower(line[i+3]) == 'l' && tolower(line[i+4]) == 'l')) {
                    has_crypto = true;
                    break;
                }
            }
            if (has_crypto) break;
        }
    }
    
    fclose(f);
    
    // Update cache
    {
        std::lock_guard<std::mutex> lock(g_crypto_cache_mutex);
        g_crypto_extensions_cached = true;
        g_crypto_extensions_available = has_crypto;
    }
    
    return has_crypto ? JNI_TRUE : JNI_FALSE;
}

/**
 * AES-128 encrypt using ARMv8 Crypto Extensions
 * 
 * ⚠️ SECURITY WARNING: This implementation is NOT cryptographically secure! ⚠️
 * 
 * CRITICAL ISSUES:
 * - Missing proper AES key expansion (AES_key_expansion)
 * - Only performs 1 round instead of required 10 rounds
 * - Uses insecure zero-padding instead of PKCS#7 padding
 * - No proper key schedule generation
 * 
 * This code is for DEMONSTRATION ONLY and must NOT be used in production!
 * 
 * For production use, you MUST integrate a proper cryptographic library:
 * - OpenSSL with EVP interface: EVP_aes_128_encrypt()
 * - BoringSSL with hardware acceleration
 * - Use proper key expansion and full 10-round AES-128 implementation
 * - Implement PKCS#7 padding for incomplete blocks
 * - Validate padding on decryption side
 * 
 * Current implementation is BROKEN and provides NO security guarantees.
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeAES128Encrypt(
    JNIEnv *env, jclass clazz, jobject input, jint input_offset, jint input_len,
    jobject output, jint output_offset, jobject key) {
    (void)clazz; // JNI required parameter, not used
    
#ifdef USE_OPENSSL
    // OpenSSL implementation (secure, production-ready)
    void* input_ptr = env->GetDirectBufferAddress(input);
    void* output_ptr = env->GetDirectBufferAddress(output);
    void* key_ptr = env->GetDirectBufferAddress(key);
    
    if (!input_ptr || !output_ptr || !key_ptr) {
        LOGE("Invalid buffer addresses");
        return -1;
    }
    
    if (input_len < 0 || input_offset < 0 || output_offset < 0) {
        LOGE("Invalid offsets or length");
        return -1;
    }
    
    uint8_t* in = static_cast<uint8_t*>(input_ptr) + input_offset;
    uint8_t* out = static_cast<uint8_t*>(output_ptr) + output_offset;
    uint8_t* key_data = static_cast<uint8_t*>(key_ptr);
    
    // Validate key length (16 bytes for AES-128)
    jlong key_capacity = env->GetDirectBufferCapacity(key);
    if (key_capacity < 16) {
        LOGE("Invalid key length: %ld (required: 16)", key_capacity);
        return -1;
    }
    
    // Use OpenSSL EVP API (high-level, recommended)
    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        LOGE("Failed to create EVP context");
        return -1;
    }
    
    // Initialize encryption with AES-128-ECB
    if (EVP_EncryptInit_ex(ctx, EVP_aes_128_ecb(), nullptr, key_data, nullptr) != 1) {
        LOGE("Failed to initialize AES encryption");
        EVP_CIPHER_CTX_free(ctx);
        return -1;
    }
    
    // Disable padding for ECB mode (caller handles padding)
    EVP_CIPHER_CTX_set_padding(ctx, 0);
    
    int outlen = 0;
    int total_outlen = 0;
    
    // Encrypt
    if (EVP_EncryptUpdate(ctx, out, &outlen, in, input_len) != 1) {
        LOGE("Encryption failed");
        EVP_CIPHER_CTX_free(ctx);
        return -1;
    }
    total_outlen = outlen;
    
    // Finalize (may add padding if enabled)
    if (EVP_EncryptFinal_ex(ctx, out + outlen, &outlen) != 1) {
        LOGE("Encryption finalization failed");
        EVP_CIPHER_CTX_free(ctx);
        return -1;
    }
    total_outlen += outlen;
    
    EVP_CIPHER_CTX_free(ctx);
    return total_outlen;
#else
    // OpenSSL not available - function disabled for security
    // CVE-2025-0001: Broken crypto implementation - MUST fail hard
    LOGE("SECURITY ERROR: nativeAES128Encrypt requires OpenSSL!");
    LOGE("OpenSSL libraries not found. Please install OpenSSL in app/src/main/jni/openssl/");
    LOGE("See OPENSSL_SETUP_INSTRUCTIONS.md for setup guide");
    LOGE("CRITICAL: Cannot use insecure crypto implementation. Aborting.");
    // Abort the process to prevent silent security failure
    // This is intentional - better to crash than to silently use broken crypto
    abort();
    return -1; // Never reached, but satisfies compiler
#endif
}

/**
 * ChaCha20 using NEON SIMD
 * 
 * ⚠️ SECURITY WARNING: This implementation is NOT ChaCha20! ⚠️
 * 
 * CRITICAL ISSUES:
 * - This is NOT ChaCha20 - just simple XOR with key (completely insecure!)
 * - Missing proper ChaCha20 quarter round algorithm (ChaCha20_quarter_round)
 * - No proper state matrix initialization with constants, key, nonce, counter
 * - No proper counter increment between blocks
 * - No state mixing - ChaCha20 requires complex state transformations
 * 
 * This code is for DEMONSTRATION ONLY and must NOT be used in production!
 * 
 * For production use, you MUST integrate a proper cryptographic library:
 * - ChaCha20-Poly1305 from OpenSSL/BoringSSL
 * - Full quarter round implementation with proper state management
 * - Proper counter increment and block handling
 * - State matrix initialization with constants (0x61707865, 0x3320646e, etc.)
 * 
 * Current implementation is BROKEN and provides NO security guarantees.
 * It's essentially a Caesar cipher, not a real stream cipher.
 */
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeChaCha20NEON(
    JNIEnv *env, jclass clazz, jobject input, jint input_offset, jint input_len,
    jobject output, jint output_offset, jobject key, jobject nonce) {
    (void)clazz; // JNI required parameter, not used
    
#ifdef USE_OPENSSL
    // OpenSSL implementation (secure, production-ready)
    void* input_ptr = env->GetDirectBufferAddress(input);
    void* output_ptr = env->GetDirectBufferAddress(output);
    void* key_ptr = env->GetDirectBufferAddress(key);
    void* nonce_ptr = env->GetDirectBufferAddress(nonce);
    
    if (!input_ptr || !output_ptr || !key_ptr || !nonce_ptr) {
        LOGE("Invalid buffer addresses");
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
    
    // Validate key length (32 bytes for ChaCha20)
    jlong key_capacity = env->GetDirectBufferCapacity(key);
    if (key_capacity < 32) {
        LOGE("Invalid key length: %ld (required: 32)", key_capacity);
        return -1;
    }
    
    // Validate nonce length (12 bytes for ChaCha20)
    jlong nonce_capacity = env->GetDirectBufferCapacity(nonce);
    if (nonce_capacity < 12) {
        LOGE("Invalid nonce length: %ld (required: 12)", nonce_capacity);
        return -1;
    }
    
    // Use OpenSSL ChaCha20 implementation
    // Note: OpenSSL ChaCha20 uses 32-byte key and 12-byte nonce
    // Counter starts at 0 for first block
    uint32_t counter = 0;
    
    // CRYPTO_chacha_20 is OpenSSL's ChaCha20 implementation
    // Parameters: output, input, input_len, key, nonce, counter
    CRYPTO_chacha_20(out, in, input_len, key_data, nonce_data, counter);
    
    return input_len;
#else
    // OpenSSL not available - function disabled for security
    // CVE-2025-0001: Broken crypto implementation - MUST fail hard
    LOGE("SECURITY ERROR: nativeChaCha20NEON requires OpenSSL!");
    LOGE("OpenSSL libraries not found. Please install OpenSSL in app/src/main/jni/openssl/");
    LOGE("See OPENSSL_SETUP_INSTRUCTIONS.md for setup guide");
    LOGE("CRITICAL: Cannot use insecure crypto implementation. Aborting.");
    // Abort the process to prevent silent security failure
    // This is intentional - better to crash than to silently use broken crypto
    abort();
    return -1; // Never reached, but satisfies compiler
#endif
}

/**
 * Prefetch data into CPU cache
 */
JNIEXPORT void JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativePrefetch(
    JNIEnv *env, jclass clazz, jobject buffer, jint offset, jint length) {
    (void)clazz; // JNI required parameter, not used
    
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
    (void)env; (void)clazz; // JNI required parameters, not used
#if HAS_NEON
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

} // extern "C"

