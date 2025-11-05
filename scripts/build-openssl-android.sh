#!/bin/bash
# Build OpenSSL for Android architectures
# Usage: ./scripts/build-openssl-android.sh --arch <arch> [--api <level>] [--ndk <path>] [--openssl-version <version>]

set -e

ARCH=""
API_LEVEL=""
NDK_ROOT=""
OPENSSL_VERSION="3.3.0"
BUILD_DIR=""
INSTALL_DIR=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --arch)
            ARCH="$2"
            shift 2
            ;;
        --api)
            API_LEVEL="$2"
            shift 2
            ;;
        --ndk)
            NDK_ROOT="$2"
            shift 2
            ;;
        --openssl-version)
            OPENSSL_VERSION="$2"
            shift 2
            ;;
        --build-dir)
            BUILD_DIR="$2"
            shift 2
            ;;
        --install-dir)
            INSTALL_DIR="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 --arch <arch> [--api <level>] [--ndk <path>] [--openssl-version <version>]"
            exit 1
            ;;
    esac
done

# Validate required arguments
if [ -z "$ARCH" ]; then
    echo "❌ Error: --arch is required"
    echo "Supported architectures: arm64, arm, x86_64, x86"
    exit 1
fi

# Set defaults based on architecture
if [ -z "$API_LEVEL" ]; then
    case "$ARCH" in
        arm64)
            API_LEVEL=24
            ;;
        arm)
            API_LEVEL=21
            ;;
        x86_64|x86)
            API_LEVEL=24
            ;;
        *)
            API_LEVEL=24
            ;;
    esac
fi

# Map architecture to OpenSSL target and NDK prefix
case "$ARCH" in
    arm64)
        OPENSSL_TARGET="android-arm64"
        NDK_PREFIX="aarch64-linux-android${API_LEVEL}"
        OUTPUT_ARCH="arm64-v8a"
        ;;
    arm)
        OPENSSL_TARGET="android-arm"
        NDK_PREFIX="armv7a-linux-androideabi${API_LEVEL}"
        OUTPUT_ARCH="armeabi-v7a"
        ;;
    x86_64)
        OPENSSL_TARGET="android-x86_64"
        NDK_PREFIX="x86_64-linux-android${API_LEVEL}"
        OUTPUT_ARCH="x86_64"
        ;;
    x86)
        OPENSSL_TARGET="android-x86"
        NDK_PREFIX="i686-linux-android${API_LEVEL}"
        OUTPUT_ARCH="x86"
        ;;
    *)
        echo "❌ Unsupported architecture: $ARCH"
        exit 1
        ;;
esac

# Find NDK if not provided
if [ -z "$NDK_ROOT" ]; then
    if [ -n "$ANDROID_NDK_HOME" ]; then
        NDK_ROOT="$ANDROID_NDK_HOME"
    elif [ -n "$ANDROID_NDK_ROOT" ]; then
        NDK_ROOT="$ANDROID_NDK_ROOT"
    else
        # Try to find in common locations
        POSSIBLE_NDK=$(find . -maxdepth 2 -type d -name "android-ndk-*" 2>/dev/null | head -1)
        if [ -n "$POSSIBLE_NDK" ]; then
            NDK_ROOT="$POSSIBLE_NDK"
        else
            echo "❌ Error: NDK not found. Please set --ndk or ANDROID_NDK_HOME"
            exit 1
        fi
    fi
fi

if [ ! -d "$NDK_ROOT" ]; then
    echo "❌ Error: NDK directory not found: $NDK_ROOT"
    exit 1
fi

# Set build and install directories
if [ -z "$BUILD_DIR" ]; then
    BUILD_DIR="$(pwd)/build/openssl-$OUTPUT_ARCH"
fi

if [ -z "$INSTALL_DIR" ]; then
    INSTALL_DIR="$(pwd)/third_party/openssl/android/$OUTPUT_ARCH"
fi

# Check if already built (idempotency)
if [ -f "$INSTALL_DIR/lib/libcrypto.a" ] && [ -f "$INSTALL_DIR/lib/libssl.a" ]; then
    echo "✅ OpenSSL already built for $OUTPUT_ARCH at $INSTALL_DIR"
    exit 0
fi

echo "🔨 Building OpenSSL $OPENSSL_VERSION for $OUTPUT_ARCH"
echo "   Target: $OPENSSL_TARGET"
echo "   API Level: $API_LEVEL"
echo "   NDK: $NDK_ROOT"
echo "   Build Dir: $BUILD_DIR"
echo "   Install Dir: $INSTALL_DIR"

# Setup toolchain
TOOLCHAIN_DIR="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"
if [ ! -d "$TOOLCHAIN_DIR" ]; then
    echo "❌ Error: Toolchain not found at $TOOLCHAIN_DIR"
    exit 1
fi

export PATH="$TOOLCHAIN_DIR/bin:$PATH"
export CC="$NDK_PREFIX-clang"
export CXX="$NDK_PREFIX-clang++"
export AR="$TOOLCHAIN_DIR/bin/llvm-ar"
export RANLIB="$TOOLCHAIN_DIR/bin/llvm-ranlib"
export STRIP="$TOOLCHAIN_DIR/bin/llvm-strip"
export ANDROID_NDK_HOME="$NDK_ROOT"
export ANDROID_NDK_ROOT="$NDK_ROOT"

# Download OpenSSL if needed
OPENSSL_TAR="openssl-$OPENSSL_VERSION.tar.gz"
OPENSSL_SRC_DIR="openssl-$OPENSSL_VERSION"

if [ ! -d "$OPENSSL_SRC_DIR" ]; then
    echo "📥 Downloading OpenSSL $OPENSSL_VERSION..."
    if ! wget -q "https://www.openssl.org/source/$OPENSSL_TAR"; then
        echo "❌ Failed to download OpenSSL"
        exit 1
    fi
    tar -xzf "$OPENSSL_TAR"
    rm -f "$OPENSSL_TAR"
fi

# Create build directory
mkdir -p "$BUILD_DIR"
cd "$OPENSSL_SRC_DIR"

# Configure OpenSSL
echo "⚙️  Configuring OpenSSL..."
./Configure "$OPENSSL_TARGET" \
    --prefix="$INSTALL_DIR" \
    CC="$CC" \
    CXX="$CXX" \
    AR="$AR" \
    RANLIB="$RANLIB" \
    STRIP="$STRIP" \
    no-shared \
    no-ssl3 \
    no-comp \
    -D__ANDROID_API__="$API_LEVEL"

# Build
echo "🔨 Building OpenSSL (this may take a while)..."
make clean >/dev/null 2>&1 || true
if ! make -j$(nproc); then
    echo "❌ OpenSSL build failed"
    exit 1
fi

# Install
echo "📦 Installing OpenSSL..."
if ! make install_sw; then
    echo "❌ OpenSSL install failed"
    exit 1
fi

# Verify
if [ ! -f "$INSTALL_DIR/lib/libcrypto.a" ] || [ ! -f "$INSTALL_DIR/lib/libssl.a" ]; then
    echo "❌ OpenSSL libraries not found after install"
    exit 1
fi

echo "✅ OpenSSL built successfully for $OUTPUT_ARCH"
echo "   Libraries: $INSTALL_DIR/lib/"
echo "   Headers: $INSTALL_DIR/include/"

# Clean build directory
cd ..
rm -rf "$BUILD_DIR"

