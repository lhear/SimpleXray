# 🔐 OpenSSL Setup Instructions

## Quick Start

OpenSSL entegrasyonu için hazırlık yapıldı. Şimdi OpenSSL kütüphanelerini indirip kurmanız gerekiyor.

## Step 1: OpenSSL Kütüphanelerini İndir

### Seçenek 1: Prebuilt Libraries (Önerilen - Hızlı)

1. **GitHub Repository:**
   ```
   https://github.com/leenjewel/openssl_for_ios_and_android
   ```

2. **Veya Android Prebuilt:**
   ```
   https://github.com/viperforge/android-openssl-prebuilt
   ```

3. **İndirilen dosyaları şu dizine kopyala:**
   ```
   app/src/main/jni/openssl/
   ├── include/
   │   └── openssl/
   └── lib/
       ├── arm64-v8a/
       │   ├── libcrypto.a
       │   └── libssl.a
       └── armeabi-v7a/
           ├── libcrypto.a
           └── libssl.a
   ```

### Seçenek 2: Source'dan Build (Daha Fazla Kontrol)

NDK standalone toolchain ile build edin:

```bash
# NDK standalone toolchain oluştur
$NDK/build/tools/make_standalone_toolchain.py \
  --arch arm64 --api 21 --install-dir=/tmp/ndk-arm64

# OpenSSL source'u indir ve build et
cd openssl-3.0.x
./Configure android-arm64 \
  --prefix=/path/to/app/src/main/jni/openssl \
  no-shared no-ssl3

make
make install
```

## Step 2: Android.mk'yi Aktif Et

**File:** `app/src/main/jni/perf-net/Android.mk`

Satır 31-33'teki yorumları kaldır:

```makefile
# OpenSSL includes (if available)
OPENSSL_DIR := $(LOCAL_PATH)/../../openssl
LOCAL_C_INCLUDES += $(OPENSSL_DIR)/include
```

Satır 59-61'deki yorumları kaldır:

```makefile
# OpenSSL libraries (if available)
LOCAL_LDLIBS += -L$(OPENSSL_DIR)/lib/$(TARGET_ARCH_ABI) -lcrypto -lssl
```

## Step 3: Crypto Kodunu Aktif Et

**File:** `app/src/main/jni/perf-net/src/perf_crypto_neon.cpp`

Satır 3-4'teki yorumları kaldır:

```cpp
// OpenSSL support (uncomment when OpenSSL is integrated)
#define USE_OPENSSL 1
#ifdef USE_OPENSSL
#include <openssl/evp.h>
#include <openssl/aes.h>
#include <openssl/chacha.h>
#include <openssl/err.h>
#endif
```

## Step 4: Crypto Fonksiyonlarını Implement Et

`nativeAES128Encrypt()` ve `nativeChaCha20NEON()` fonksiyonlarını OpenSSL API'leri ile değiştir.

Detaylı implementasyon için: `OPENSSL_INTEGRATION_ROADMAP.md`

## Step 5: Build ve Test

```bash
./gradlew clean
./gradlew assembleDebug
```

## Verification

OpenSSL'in çalıştığını doğrulamak için:

```kotlin
val hasCrypto = PerformanceManager.nativeHasCryptoExtensions()
if (hasCrypto) {
    // OpenSSL is available
}
```

## Troubleshooting

### Error: "openssl/evp.h: No such file or directory"
- OpenSSL kütüphaneleri doğru dizinde mi?
- `OPENSSL_DIR` path'i doğru mu?
- Android.mk'de yorumlar kaldırıldı mı?

### Error: "undefined reference to EVP_EncryptInit_ex"
- OpenSSL library link edildi mi?
- `LOCAL_LDLIBS` yorumları kaldırıldı mı?
- Static library (.a) dosyaları var mı?

### Build Fails
- NDK version kontrol et (r21+)
- OpenSSL version kontrol et (1.1.1+ veya 3.0+)
- Architecture (arm64-v8a, armeabi-v7a) uyumlu mu?

---

**Status:** ✅ Hazırlık tamamlandı  
**Next:** OpenSSL kütüphanelerini indir ve kur


