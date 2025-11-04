/*
 * Crypto Acceleration using ARM NEON & Crypto Extensions
 * Hardware-accelerated AES and ChaCha20
 */

#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <cstdio>

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
    LOGE("SECURITY ERROR: nativeAES128Encrypt requires OpenSSL!");
    LOGE("OpenSSL libraries not found. Please install OpenSSL in app/src/main/jni/openssl/");
    LOGE("See OPENSSL_SETUP_INSTRUCTIONS.md for setup guide");
    return -1;
#endif
    
    // Original broken code (DISABLED):
    /*
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
    
    // ⚠️ SECURITY WARNING: This implementation is BROKEN and INSECURE! ⚠️
    // 
    // CRITICAL ISSUES:
    // 1. Missing AES key expansion - using raw key directly (WRONG!)
    // 2. Only 1 round instead of 10 rounds (WRONG!)
    // 3. Insecure zero-padding instead of PKCS#7 (WRONG!)
    //
    // DO NOT USE THIS CODE FOR ANY REAL ENCRYPTION!
    // This is a placeholder that needs to be replaced with proper OpenSSL/BoringSSL.
    
    // Load key (128-bit = 16 bytes)
    // BUG: Using raw key without expansion - this is NOT cryptographically secure
    // TODO: Implement proper AES key expansion (AES_key_expansion) before encryption
    // TODO: Replace with full AES-128 implementation using OpenSSL or BoringSSL
    uint8x16_t key_vec = vld1q_u8(key_data);
    
    for (int i = 0; i < blocks; i++) {
        uint8x16_t data = vld1q_u8(in + i * 16);
        
        // BUG: Only doing 1 round instead of 10 - this is NOT secure encryption
        // Full AES-128 requires:
        // 1. Key expansion to generate 11 round keys (AES_key_expansion)
        // 2. 10 rounds: 9 rounds of vaeseq_u8 + vaesmcq_u8 + AddRoundKey
        // 3. Final round: vaeseq_u8 + AddRoundKey (no MixColumns)
        // TODO: Implement full 10-round AES-128 with proper key schedule
        
        data = vaeseq_u8(data, key_vec);  // AES encryption round (WRONG - needs 10 rounds!)
        data = vaesmcq_u8(data);          // AES MixColumns
        
        vst1q_u8(out + i * 16, data);
    }
    
    // Handle remainder (last incomplete block)
    if (remainder > 0) {
        // BUG: Padding with zeros is insecure - should use PKCS#7 padding
        // TODO: Implement proper PKCS#7 padding for incomplete blocks
        // TODO: Add padding validation on decryption side
        // 
        // Proper PKCS#7 padding:
        // - Pad with bytes equal to padding length (e.g., if 3 bytes needed, pad with 0x03 0x03 0x03)
        // - Always add padding (even if block is full, add a full block of padding)
        memcpy(out + blocks * 16, in + blocks * 16, remainder);
        // Pad remainder block (WRONG - using zeros, should use PKCS#7)
        memset(out + blocks * 16 + remainder, 0, 16 - remainder);
    }
#else
    // Fallback for non-ARM or non-ARM64: use software implementation
    // ⚠️ WARNING: This is NOT secure - just XOR encryption (like a Caesar cipher!)
    // ⚠️ DO NOT USE FOR PRODUCTION - Use OpenSSL/BoringSSL instead!
    LOGE("Using insecure software fallback - NOT cryptographically secure!");
    for (int i = 0; i < input_len; i++) {
        out[i] = in[i] ^ key_data[i % 16];
    }
#endif
    
    return input_len;
    */
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
    LOGE("SECURITY ERROR: nativeChaCha20NEON requires OpenSSL!");
    LOGE("OpenSSL libraries not found. Please install OpenSSL in app/src/main/jni/openssl/");
    LOGE("See OPENSSL_SETUP_INSTRUCTIONS.md for setup guide");
    return -1;
#endif
    
    // Original broken code (DISABLED):
    /*
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
    
    // ⚠️ SECURITY WARNING: This is NOT ChaCha20! ⚠️
    //
    // This implementation is completely broken:
    // - Just XORs data with key (like a Caesar cipher, NOT ChaCha20!)
    // - Missing proper ChaCha20 quarter round algorithm
    // - No state matrix initialization
    // - No proper counter handling
    //
    // DO NOT USE THIS CODE FOR ANY REAL ENCRYPTION!
    // This is a placeholder that needs to be replaced with proper OpenSSL/BoringSSL.
    
    // Note: Full ChaCha20 implementation requires:
    // 1. Initialize state matrix with constants (0x61707865, 0x3320646e, 0x79622d32, 0x6b206574),
    //    key (32 bytes), nonce (12 bytes), and counter (4 bytes)
    // 2. For each block: 20 rounds (10 double-rounds) of quarter rounds
    //    - Quarter round operates on 4 words: a, b, c, d
    //    - a += b; d ^= a; d = rotate_left(d, 16);
    //    - c += d; b ^= c; b = rotate_left(b, 12);
    //    - a += b; d ^= a; d = rotate_left(d, 8);
    //    - c += d; b ^= c; b = rotate_left(b, 7);
    // 3. Add original state to encrypted state (XOR)
    // 4. Increment counter for next block
    //
    // TODO: Implement proper ChaCha20 quarter round algorithm (ChaCha20_quarter_round)
    // TODO: Initialize proper state matrix with constants, key, nonce, counter
    // TODO: Add proper counter increment between blocks
    
    // BUG: This is NOT ChaCha20 - just XOR with key, completely insecure
    // BUG: ChaCha20 requires state mixing, not simple XOR - this is broken
    for (int b = 0; b < blocks; b++) {
        // Process 64-byte block
        for (int i = 0; i < 64; i += 16) {
            uint8x16_t data = vld1q_u8(in + b * 64 + i);
            
            // WRONG: This is just XOR, not ChaCha20!
            // Full implementation requires proper ChaCha20 quarter round with state mixing
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
    // ⚠️ WARNING: This is NOT ChaCha20 - just XOR encryption (completely insecure!)
    // ⚠️ DO NOT USE FOR PRODUCTION - Use OpenSSL/BoringSSL instead!
    LOGE("Using insecure software fallback - NOT cryptographically secure!");
    for (int i = 0; i < input_len; i++) {
        out[i] = in[i] ^ key_data[i % 32];
    }
    return input_len;
#endif
    */
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

