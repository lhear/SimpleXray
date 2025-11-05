# Building Xray-Core with OpenSSL Support

This guide explains how to build Xray-Core with OpenSSL support for Linux x86_64 and Android (arm64-v8a and armeabi-v7a) using the Android NDK.

## Prerequisites

- Go 1.25.3 or later
- Android NDK r21+ (r28c recommended)
- OpenSSL 3.3.0 source code
- Make and build tools (gcc, clang, etc.)

## Quick Start

### Option 1: Using Helper Scripts (Recommended)

```bash
# 1. Setup NDK (if not already installed)
./scripts/setup-ndk.sh r28c

# 2. Build OpenSSL for Android architectures
export NDK_ROOT=$(pwd)/android-ndk-r28c
./scripts/build-openssl-android.sh --arch arm64 --api 24 --ndk "$NDK_ROOT"
./scripts/build-openssl-android.sh --arch arm --api 24 --ndk "$NDK_ROOT"

# 3. Build OpenSSL for Linux x86_64
./scripts/build-openssl-linux.sh

# 4. Build Xray-Core with OpenSSL
export WITH_OPENSSL=1
./scripts/build-xray-with-openssl.sh --target android --arch arm64
./scripts/build-xray-with-openssl.sh --target android --arch arm
./scripts/build-xray-with-openssl.sh --target linux --arch x86_64
```

### Option 2: Manual Build

#### Step 1: Download OpenSSL

```bash
# Download OpenSSL 3.3.0
wget https://www.openssl.org/source/openssl-3.3.0.tar.gz
tar -xzf openssl-3.3.0.tar.gz
cd openssl-3.3.0
```

#### Step 2: Build OpenSSL for Android

##### For arm64-v8a (Android):

```bash
export NDK_ROOT=/path/to/android-ndk-r28c
export API_LEVEL=24
export TOOLCHAIN_DIR="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"

export PATH=$TOOLCHAIN_DIR/bin:$PATH
export CC=aarch64-linux-android${API_LEVEL}-clang
export CXX=aarch64-linux-android${API_LEVEL}-clang++
export AR=$TOOLCHAIN_DIR/bin/llvm-ar
export RANLIB=$TOOLCHAIN_DIR/bin/llvm-ranlib
export STRIP=$TOOLCHAIN_DIR/bin/llvm-strip

./Configure android-arm64 \
  --prefix=/tmp/openssl-arm64-v8a \
  CC=$CC \
  CXX=$CXX \
  AR=$AR \
  RANLIB=$RANLIB \
  STRIP=$STRIP \
  no-shared \
  no-ssl3 \
  no-comp \
  -D__ANDROID_API__=$API_LEVEL

make -j$(nproc)
make install_sw

# Copy libraries to project
mkdir -p ../app/src/main/jni/openssl/lib/arm64-v8a
cp /tmp/openssl-arm64-v8a/lib/libcrypto.a ../app/src/main/jni/openssl/lib/arm64-v8a/
cp /tmp/openssl-arm64-v8a/lib/libssl.a ../app/src/main/jni/openssl/lib/arm64-v8a/

# Copy headers (only once)
mkdir -p ../app/src/main/jni/openssl/include
cp -r /tmp/openssl-arm64-v8a/include/openssl ../app/src/main/jni/openssl/include/
```

##### For armeabi-v7a (Android):

```bash
export CC=armv7a-linux-androideabi${API_LEVEL}-clang
export CXX=armv7a-linux-androideabi${API_LEVEL}-clang++

./Configure android-arm \
  --prefix=/tmp/openssl-armeabi-v7a \
  CC=$CC \
  CXX=$CXX \
  AR=$AR \
  RANLIB=$RANLIB \
  STRIP=$STRIP \
  no-shared \
  no-ssl3 \
  no-comp \
  -D__ANDROID_API__=$API_LEVEL

make -j$(nproc)
make install_sw

# Copy libraries
mkdir -p ../app/src/main/jni/openssl/lib/armeabi-v7a
cp /tmp/openssl-armeabi-v7a/lib/libcrypto.a ../app/src/main/jni/openssl/lib/armeabi-v7a/
cp /tmp/openssl-armeabi-v7a/lib/libssl.a ../app/src/main/jni/openssl/lib/armeabi-v7a/
```

#### Step 3: Build OpenSSL for Linux x86_64

```bash
cd openssl-3.3.0

./config \
  --prefix=/tmp/openssl-linux-x86_64 \
  no-shared \
  no-ssl3 \
  no-comp \
  -fPIC

make -j$(nproc)
make install_sw

# Copy to project
mkdir -p ../third_party/openssl/linux/x86_64
cp -r /tmp/openssl-linux-x86_64/lib ../third_party/openssl/linux/x86_64/
cp -r /tmp/openssl-linux-x86_64/include ../third_party/openssl/linux/x86_64/
```

#### Step 4: Build Xray-Core with OpenSSL

```bash
# Clone Xray-Core
git clone https://github.com/XTLS/Xray-core.git
cd Xray-core
git checkout v25.10.15  # or your desired version
COMMIT=$(git rev-parse HEAD | cut -c 1-7)

# For Android arm64-v8a
export GOOS=android
export GOARCH=arm64
export CGO_ENABLED=1
export CC=$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang
export CGO_CFLAGS="-I$(pwd)/../app/src/main/jni/openssl/include"
export CGO_LDFLAGS="-L$(pwd)/../app/src/main/jni/openssl/lib/arm64-v8a -static -lcrypto -lssl"

go build -tags openssl -o xray -trimpath -buildvcs=false \
  -ldflags="-X github.com/xtls/xray-core/core.build=${COMMIT} -s -w -buildid=" \
  -v ./main

mkdir -p ../app/src/main/jniLibs/arm64-v8a
mv xray ../app/src/main/jniLibs/arm64-v8a/libxray.so
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `WITH_OPENSSL` | Enable OpenSSL build | `0` |
| `NDK_ROOT` | Android NDK path | Auto-detected |
| `API_LEVEL` | Android API level | `24` (arm64), `21` (arm) |
| `OPENSSL_VERSION` | OpenSSL version | `3.3.0` |
| `CGO_ENABLED` | Enable CGO | `1` (required) |
| `CGO_CFLAGS` | C compiler flags | `-I<openssl_include>` |
| `CGO_LDFLAGS` | Linker flags | `-L<openssl_lib> -static -lcrypto -lssl` |

## Verification

### Check OpenSSL Linkage

```bash
# Check if binary is linked with OpenSSL
./tools/check_openssl_link.sh app/src/main/jniLibs/arm64-v8a/libxray.so

# CI verification (strict mode)
export WITH_OPENSSL=1
./tools/ci_verify.sh app/src/main/jniLibs/arm64-v8a/libxray.so
```

### Expected Output

The verification script should show:
- ✅ OpenSSL symbols found (EVP_, SSL_, TLS_)
- ✅ OpenSSL strings found
- ✅ Static linking (no dynamic library dependencies)

## Troubleshooting

### Build Fails with "openssl/evp.h: No such file"

**Solution:** Ensure OpenSSL headers are in the correct location:
```bash
ls -la app/src/main/jni/openssl/include/openssl/evp.h
```

### Linker Errors: "cannot find -lcrypto"

**Solution:** Verify OpenSSL libraries are built and in the correct path:
```bash
ls -la app/src/main/jni/openssl/lib/arm64-v8a/libcrypto.a
ls -la app/src/main/jni/openssl/lib/arm64-v8a/libssl.a
```

### Go Build Fails with "CGO not enabled"

**Solution:** Ensure `CGO_ENABLED=1` is set:
```bash
export CGO_ENABLED=1
```

### Android Binary Not Working on Device

**Solution:** Ensure static linking is used:
- Verify `-static` flag in `CGO_LDFLAGS`
- Check that `no-shared` was used when building OpenSSL

## CI/CD Integration

The build process is integrated into GitHub Actions workflows:

- **Workflow:** `.github/workflows/build.yml`
- **Job:** `build-openssl-android` (builds OpenSSL for all architectures)
- **Job:** `build-xray-openssl` (builds Xray-Core with OpenSSL)
- **Job:** `verify-openssl-linkage` (verifies OpenSSL is linked)

## Directory Structure

After building, the following structure should exist:

```
app/src/main/jni/openssl/
├── include/
│   └── openssl/
│       ├── evp.h
│       ├── ssl.h
│       └── ...
└── lib/
    ├── arm64-v8a/
    │   ├── libcrypto.a
    │   └── libssl.a
    ├── armeabi-v7a/
    │   ├── libcrypto.a
    │   └── libssl.a
    └── ...

third_party/openssl/
└── linux/
    └── x86_64/
        ├── lib/
        │   ├── libcrypto.a
        │   └── libssl.a
        └── include/
            └── openssl/
```

## Assumptions

- **NDK Version:** r28c (compatible with r21+)
- **OpenSSL Version:** 3.3.0 (any 3.x should work)
- **API Level:** 24 for arm64, 21 for arm (configurable)
- **Static Linking:** All builds use static linking to avoid runtime dependencies

## References

- [OpenSSL Wiki - Android](https://wiki.openssl.org/index.php/FIPS_Library_and_Android)
- [Xray-Core Repository](https://github.com/XTLS/Xray-core)
- [Android NDK Documentation](https://developer.android.com/ndk)

