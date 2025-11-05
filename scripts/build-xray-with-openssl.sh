#!/bin/bash
# Build Xray-Core with OpenSSL support
# Usage: ./scripts/build-xray-with-openssl.sh [--target <target>] [--arch <arch>] [--xray-version <version>]

set -e

TARGET=""
ARCH=""
XRAY_VERSION=""
WITH_OPENSSL="${WITH_OPENSSL:-1}"
NDK_ROOT=""
OPENSSL_DIR=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --target)
            TARGET="$2"
            shift 2
            ;;
        --arch)
            ARCH="$2"
            shift 2
            ;;
        --xray-version)
            XRAY_VERSION="$2"
            shift 2
            ;;
        --ndk)
            NDK_ROOT="$2"
            shift 2
            ;;
        --openssl-dir)
            OPENSSL_DIR="$2"
            shift 2
            ;;
        --without-openssl)
            WITH_OPENSSL=0
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--target <target>] [--arch <arch>] [--xray-version <version>]"
            exit 1
            ;;
    esac
done

# Determine target and architecture
if [ -z "$TARGET" ]; then
    if [ -n "$ARCH" ]; then
        case "$ARCH" in
            arm64|arm64-v8a)
                TARGET="android"
                ARCH="arm64"
                ;;
            arm|armeabi-v7a)
                TARGET="android"
                ARCH="arm"
                ;;
            x86_64)
                TARGET="linux"
                ARCH="amd64"
                ;;
            x86)
                TARGET="android"
                ARCH="386"
                ;;
            *)
                TARGET="linux"
                ARCH="amd64"
                ;;
        esac
    else
        TARGET="linux"
        ARCH="amd64"
    fi
fi

# Map architecture
case "$ARCH" in
    arm64|arm64-v8a)
        GOARCH="arm64"
        OUTPUT_ARCH="arm64-v8a"
        ANDROID_API=24
        ;;
    arm|armeabi-v7a)
        GOARCH="arm"
        GOARM=7
        OUTPUT_ARCH="armeabi-v7a"
        ANDROID_API=24
        ;;
    x86_64|amd64)
        GOARCH="amd64"
        OUTPUT_ARCH="x86_64"
        ;;
    x86|386)
        GOARCH="386"
        OUTPUT_ARCH="x86"
        ANDROID_API=24
        ;;
    *)
        GOARCH="amd64"
        OUTPUT_ARCH="x86_64"
        ;;
esac

# Set Xray version
if [ -z "$XRAY_VERSION" ]; then
    if [ -f "version.properties" ]; then
        XRAY_VERSION=$(grep 'XRAY_CORE_VERSION' version.properties | cut -d '=' -f 2)
    else
        XRAY_VERSION="v25.10.15"
    fi
fi

echo "🔨 Building Xray-Core $XRAY_VERSION"
echo "   Target: $TARGET"
echo "   Architecture: $GOARCH ($OUTPUT_ARCH)"
echo "   OpenSSL: $([ "$WITH_OPENSSL" = "1" ] && echo "enabled" || echo "disabled")"

# Clone Xray-Core if needed
if [ ! -d "Xray-core" ]; then
    echo "📥 Cloning Xray-Core..."
    git clone https://github.com/XTLS/Xray-core.git
fi

cd Xray-core
git fetch --tags
git checkout "$XRAY_VERSION" 2>/dev/null || git checkout "master"
COMMIT=$(git rev-parse HEAD | cut -c 1-7)

# Setup environment
export CGO_ENABLED=1
export GOOS="$TARGET"

if [ "$TARGET" = "android" ]; then
    # Find NDK if not provided
    if [ -z "$NDK_ROOT" ]; then
        if [ -n "$ANDROID_NDK_HOME" ]; then
            NDK_ROOT="$ANDROID_NDK_HOME"
        elif [ -n "$ANDROID_NDK_ROOT" ]; then
            NDK_ROOT="$ANDROID_NDK_ROOT"
        else
            NDK_ROOT=$(find .. -maxdepth 2 -type d -name "android-ndk-*" 2>/dev/null | head -1)
            if [ -z "$NDK_ROOT" ]; then
                echo "❌ Error: NDK not found. Please set --ndk or ANDROID_NDK_HOME"
                exit 1
            fi
        fi
    fi

    # Set Android compiler
    case "$GOARCH" in
        arm64)
            export CC="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${ANDROID_API}-clang"
            ;;
        arm)
            export CC="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi${ANDROID_API}-clang"
            export GOARM=7
            ;;
        386)
            export CC="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android${ANDROID_API}-clang"
            ;;
        *)
            export CC="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${ANDROID_API}-clang"
            ;;
    esac
fi

# Set OpenSSL paths if enabled
if [ "$WITH_OPENSSL" = "1" ]; then
    if [ -z "$OPENSSL_DIR" ]; then
        if [ "$TARGET" = "android" ]; then
            OPENSSL_DIR="../third_party/openssl/android/$OUTPUT_ARCH"
        else
            OPENSSL_DIR="../third_party/openssl/linux/x86_64"
        fi
    fi

    if [ ! -d "$OPENSSL_DIR" ]; then
        echo "❌ Error: OpenSSL not found at $OPENSSL_DIR"
        echo "   Please build OpenSSL first using scripts/build-openssl-android.sh or scripts/build-openssl-linux.sh"
        exit 1
    fi

    export CGO_CFLAGS="-I$OPENSSL_DIR/include"
    export CGO_LDFLAGS="-L$OPENSSL_DIR/lib -static -lcrypto -lssl"
    
    echo "   OpenSSL Dir: $OPENSSL_DIR"
    echo "   CGO_CFLAGS: $CGO_CFLAGS"
    echo "   CGO_LDFLAGS: $CGO_LDFLAGS"
fi

# Build flags
BUILD_FLAGS="-trimpath -buildvcs=false"
LDFLAGS="-X github.com/xtls/xray-core/core.build=${COMMIT} -s -w -buildid="

if [ "$WITH_OPENSSL" = "1" ]; then
    BUILD_FLAGS="$BUILD_FLAGS -tags openssl"
fi

# Build
echo "🔨 Building Xray-Core..."
export GOARCH="$GOARCH"
go build $BUILD_FLAGS -ldflags="$LDFLAGS" -o xray -v ./main

# Output
OUTPUT_DIR="../app/src/main/jniLibs/$OUTPUT_ARCH"
if [ "$TARGET" = "android" ]; then
    mkdir -p "$OUTPUT_DIR"
    mv xray "$OUTPUT_DIR/libxray.so"
    echo "✅ Built: $OUTPUT_DIR/libxray.so"
else
    mkdir -p "../build/bin"
    mv xray "../build/bin/xray-linux-x86_64"
    echo "✅ Built: ../build/bin/xray-linux-x86_64"
fi

cd ..

