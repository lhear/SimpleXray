# 🔐 OpenSSL Integration Roadmap

## Current Status

✅ **Crypto Functions Safely Disabled**
- `nativeAES128Encrypt()` - Returns -1 with security warning
- `nativeChaCha20NEON()` - Returns -1 with security warning
- Functions fail fast, preventing false security

## Integration Plan

### Phase 1: OpenSSL Library Setup (4-6 hours)

#### Step 1.1: Choose OpenSSL Distribution
**Options:**
1. **Prebuilt Libraries** (Recommended for speed)
   - https://github.com/leenjewel/openssl_for_ios_and_android
   - Pre-compiled for Android ABIs
   - Download and extract to `app/src/main/jni/openssl/`

2. **Build from Source** (More control)
   - Use NDK standalone toolchain
   - Configure with `no-shared` and `no-ssl3`
   - Build for `arm64-v8a` and `armeabi-v7a`

**Recommendation:** Start with prebuilt, switch to source build if needed.

#### Step 1.2: Directory Structure
```
app/src/main/jni/
├── openssl/
│   ├── include/
│   │   └── openssl/
│   └── lib/
│       ├── arm64-v8a/
│       │   ├── libcrypto.a
│       │   └── libssl.a
│       └── armeabi-v7a/
│           ├── libcrypto.a
│           └── libssl.a
└── perf-net/
    ├── Android.mk
    └── src/
        └── perf_crypto_neon.cpp
```

#### Step 1.3: Update Android.mk

**File:** `app/src/main/jni/perf-net/Android.mk`

Add after line 28 (LOCAL_C_INCLUDES):

```makefile
# OpenSSL includes
OPENSSL_DIR := $(LOCAL_PATH)/../../openssl
LOCAL_C_INCLUDES += $(OPENSSL_DIR)/include
```

Add after line 52 (LOCAL_LDLIBS):

```makefile
# OpenSSL libraries
LOCAL_LDLIBS += -L$(OPENSSL_DIR)/lib/$(TARGET_ARCH_ABI) -lcrypto -lssl
```

**Note:** OpenSSL static libraries (`.a`) are linked at build time.

### Phase 2: Code Implementation (2-3 hours)

#### Step 2.1: Update perf_crypto_neon.cpp

**Replace includes:**
```cpp
// Remove broken NEON crypto includes
// Add OpenSSL includes
#include <openssl/evp.h>
#include <openssl/aes.h>
#include <openssl/chacha.h>
#include <openssl/err.h>
```

#### Step 2.2: Implement AES-128-ECB

**Replace `nativeAES128Encrypt()`:**

```cpp
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeAES128Encrypt(
    JNIEnv *env, jclass clazz, jobject input, jint input_offset, jint input_len,
    jobject output, jint output_offset, jobject key) {
    
    void* input_ptr = env->GetDirectBufferAddress(input);
    void* output_ptr = env->GetDirectBufferAddress(output);
    void* key_ptr = env->GetDirectBufferAddress(key);
    
    if (!input_ptr || !output_ptr || !key_ptr) {
        LOGE("Invalid buffer addresses");
        return -1;
    }
    
    uint8_t* in = static_cast<uint8_t*>(input_ptr) + input_offset;
    uint8_t* out = static_cast<uint8_t*>(output_ptr) + output_offset;
    uint8_t* key_data = static_cast<uint8_t*>(key_ptr);
    
    // Validate key length (16 bytes for AES-128)
    if (env->GetDirectBufferCapacity(key) < 16) {
        LOGE("Invalid key length");
        return -1;
    }
    
    // Use OpenSSL EVP API (high-level, recommended)
    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        LOGE("Failed to create EVP context");
        return -1;
    }
    
    // Initialize encryption
    if (EVP_EncryptInit_ex(ctx, EVP_aes_128_ecb(), nullptr, key_data, nullptr) != 1) {
        LOGE("Failed to initialize AES encryption");
        EVP_CIPHER_CTX_free(ctx);
        return -1;
    }
    
    // Disable padding for ECB mode (if needed)
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
    
    // Finalize
    if (EVP_EncryptFinal_ex(ctx, out + outlen, &outlen) != 1) {
        LOGE("Encryption finalization failed");
        EVP_CIPHER_CTX_free(ctx);
        return -1;
    }
    total_outlen += outlen;
    
    EVP_CIPHER_CTX_free(ctx);
    return total_outlen;
}
```

#### Step 2.3: Implement ChaCha20

**Replace `nativeChaCha20NEON()`:**

```cpp
JNIEXPORT jint JNICALL
Java_com_simplexray_an_performance_PerformanceManager_nativeChaCha20NEON(
    JNIEnv *env, jclass clazz, jobject input, jint input_offset, jint input_len,
    jobject output, jint output_offset, jobject key, jobject nonce) {
    
    void* input_ptr = env->GetDirectBufferAddress(input);
    void* output_ptr = env->GetDirectBufferAddress(output);
    void* key_ptr = env->GetDirectBufferAddress(key);
    void* nonce_ptr = env->GetDirectBufferAddress(nonce);
    
    if (!input_ptr || !output_ptr || !key_ptr || !nonce_ptr) {
        LOGE("Invalid buffer addresses");
        return -1;
    }
    
    uint8_t* in = static_cast<uint8_t*>(input_ptr) + input_offset;
    uint8_t* out = static_cast<uint8_t*>(output_ptr) + output_offset;
    uint8_t* key_data = static_cast<uint8_t*>(key_ptr);
    uint8_t* nonce_data = static_cast<uint8_t*>(nonce_ptr);
    
    // Validate key length (32 bytes for ChaCha20)
    if (env->GetDirectBufferCapacity(key) < 32) {
        LOGE("Invalid key length");
        return -1;
    }
    
    // Validate nonce length (12 bytes)
    if (env->GetDirectBufferCapacity(nonce) < 12) {
        LOGE("Invalid nonce length");
        return -1;
    }
    
    // Use OpenSSL ChaCha20 implementation
    // Note: OpenSSL ChaCha20 uses 32-byte key and 12-byte nonce
    uint32_t counter = 0;
    
    CRYPTO_chacha_20(out, in, input_len, key_data, nonce_data, counter);
    
    return input_len;
}
```

#### Step 2.4: Remove Security Warnings

Remove all `LOGE("SECURITY ERROR...")` warnings after OpenSSL is integrated.

### Phase 3: Testing (2-3 hours)

#### Step 3.1: Unit Tests

**Create:** `app/src/test/kotlin/com/simplexray/an/performance/CryptoTest.kt`

```kotlin
@Test
fun testAES128Encryption() {
    val key = ByteArray(16) { it.toByte() }
    val plaintext = "Hello, World!".toByteArray()
    val ciphertext = ByteArray(plaintext.size)
    
    val result = PerformanceManager.nativeAES128Encrypt(
        ByteBuffer.wrap(plaintext), 0, plaintext.size,
        ByteBuffer.wrap(ciphertext), 0,
        ByteBuffer.wrap(key)
    )
    
    assert(result > 0)
    // Verify ciphertext is not same as plaintext
    assert(!ciphertext.contentEquals(plaintext))
}
```

#### Step 3.2: Test Vectors

- Use NIST test vectors for AES
- Use RFC 7539 test vectors for ChaCha20
- Verify against known-good implementations

#### Step 3.3: Performance Benchmarks

- Compare OpenSSL vs broken implementation (should be faster)
- Measure CPU usage
- Measure latency

### Phase 4: Documentation (1 hour)

- Update `README.md` with OpenSSL dependency
- Remove security warnings from code
- Update `CRYPTO_FIX_PLAN.md` with completion status
- Add OpenSSL license acknowledgment

## Estimated Timeline

| Phase | Duration | Priority |
|-------|----------|----------|
| Phase 1: Setup | 4-6 hours | P0 |
| Phase 2: Implementation | 2-3 hours | P0 |
| Phase 3: Testing | 2-3 hours | P0 |
| Phase 4: Documentation | 1 hour | P1 |
| **Total** | **9-13 hours** | |

## Risk Assessment

**Risk:** LOW
- Replacing broken code with proven library
- OpenSSL is battle-tested
- No breaking API changes

**Impact:** HIGH
- Security critical
- Required for production use
- Blocks crypto feature enablement

**Priority:** P0 - Blocking Release

## Dependencies

- OpenSSL 1.1.1+ or 3.0+ (recommended: 3.0+)
- NDK r21+ (for C++17 support)
- Android API 21+ (already supported)

## Alternative: AndroidKeyStore

If OpenSSL proves too complex, consider AndroidKeyStore:
- Built into Android
- Hardware-backed on supported devices
- Limited to AES-GCM (no ChaCha20)
- Requires Java/Kotlin API usage

**Recommendation:** Proceed with OpenSSL for flexibility.

---

**Status:** 📋 Planned  
**Next Action:** Download/configure OpenSSL prebuilt libraries  
**Owner:** Development Team  
**Sprint:** Next Sprint (P0)


