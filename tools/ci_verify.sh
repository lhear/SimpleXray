#!/bin/bash
# CI verification script - checks if OpenSSL is linked when WITH_OPENSSL=1
# Usage: ./tools/ci_verify.sh [binary_path]

set -e

BINARY_PATH="${1:-}"
WITH_OPENSSL="${WITH_OPENSSL:-0}"

echo "🔍 CI OpenSSL Verification"
echo "   WITH_OPENSSL: $WITH_OPENSSL"

# Find binary if not provided
if [ -z "$BINARY_PATH" ]; then
    # Try to find Xray binary in common locations
    POSSIBLE_PATHS=(
        "app/src/main/jniLibs/arm64-v8a/libxray.so"
        "app/src/main/jniLibs/armeabi-v7a/libxray.so"
        "build/bin/xray-linux-x86_64"
        "Xray-core/xray"
    )
    
    for path in "${POSSIBLE_PATHS[@]}"; do
        if [ -f "$path" ]; then
            BINARY_PATH="$path"
            break
        fi
    done
fi

if [ -z "$BINARY_PATH" ] || [ ! -f "$BINARY_PATH" ]; then
    echo "❌ Error: Binary not found"
    echo "   Searched in: ${POSSIBLE_PATHS[*]}"
    echo "   Please provide binary path: $0 <binary_path>"
    exit 1
fi

echo "   Binary: $BINARY_PATH"

# Run check_openssl_link.sh
if [ "$WITH_OPENSSL" = "1" ] || [ "$WITH_OPENSSL" = "true" ]; then
    echo ""
    echo "🔍 Verifying OpenSSL linkage (strict mode)..."
    if ./tools/check_openssl_link.sh "$BINARY_PATH" --strict; then
        echo ""
        echo "✅ Verification passed: OpenSSL is linked"
        exit 0
    else
        echo ""
        echo "❌ Verification failed: OpenSSL should be linked but was not found"
        echo "   This indicates a build configuration issue."
        exit 1
    fi
else
    echo ""
    echo "ℹ️  WITH_OPENSSL not set, skipping verification"
    echo "   OpenSSL linkage check would be skipped"
    exit 0
fi

