#!/bin/bash
# Build OpenSSL for Linux x86_64
# Usage: ./scripts/build-openssl-linux.sh [--openssl-version <version>] [--install-dir <dir>]

set -e

OPENSSL_VERSION="3.3.0"
INSTALL_DIR=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --openssl-version)
            OPENSSL_VERSION="$2"
            shift 2
            ;;
        --install-dir)
            INSTALL_DIR="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--openssl-version <version>] [--install-dir <dir>]"
            exit 1
            ;;
    esac
done

# Set default install directory
if [ -z "$INSTALL_DIR" ]; then
    INSTALL_DIR="$(pwd)/third_party/openssl/linux/x86_64"
fi

# Check if already built (idempotency)
if [ -f "$INSTALL_DIR/lib/libcrypto.a" ] && [ -f "$INSTALL_DIR/lib/libssl.a" ]; then
    echo "✅ OpenSSL already built for Linux x86_64 at $INSTALL_DIR"
    exit 0
fi

echo "🔨 Building OpenSSL $OPENSSL_VERSION for Linux x86_64"
echo "   Install Dir: $INSTALL_DIR"

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

cd "$OPENSSL_SRC_DIR"

# Configure OpenSSL for Linux x86_64
echo "⚙️  Configuring OpenSSL..."
./config \
    --prefix="$INSTALL_DIR" \
    no-shared \
    no-ssl3 \
    no-comp \
    -fPIC

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

echo "✅ OpenSSL built successfully for Linux x86_64"
echo "   Libraries: $INSTALL_DIR/lib/"
echo "   Headers: $INSTALL_DIR/include/"

