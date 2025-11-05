#!/bin/bash
# Check if binary is linked with OpenSSL
# Usage: ./tools/check_openssl_link.sh <binary_path> [--strict]

set -e

BINARY_PATH="$1"
STRICT_MODE="${2:-}"

if [ -z "$BINARY_PATH" ] || [ ! -f "$BINARY_PATH" ]; then
    echo "❌ Error: Binary not found: $BINARY_PATH"
    echo "Usage: $0 <binary_path> [--strict]"
    exit 1
fi

echo "🔍 Checking OpenSSL linkage in: $BINARY_PATH"

# Check file type
FILE_TYPE=$(file "$BINARY_PATH" | head -1)
echo "   File type: $FILE_TYPE"

# Method 1: Check symbols using readelf (for ELF binaries)
if command -v readelf >/dev/null 2>&1; then
    echo "   Checking symbols with readelf..."
    OPENSSL_SYMBOLS=$(readelf -s "$BINARY_PATH" 2>/dev/null | grep -E 'EVP_|SSL_|TLS_|OPENSSL_' | head -10 || true)
    
    if [ -n "$OPENSSL_SYMBOLS" ]; then
        echo "   ✅ Found OpenSSL symbols:"
        echo "$OPENSSL_SYMBOLS" | sed 's/^/      /'
        FOUND_SYMBOLS=1
    else
        echo "   ⚠️  No OpenSSL symbols found with readelf"
        FOUND_SYMBOLS=0
    fi
else
    echo "   ⚠️  readelf not available"
    FOUND_SYMBOLS=0
fi

# Method 2: Check strings
if command -v strings >/dev/null 2>&1; then
    echo "   Checking strings..."
    OPENSSL_STRINGS=$(strings "$BINARY_PATH" 2>/dev/null | grep -iE 'openssl|libcrypto|libssl' | head -10 || true)
    
    if [ -n "$OPENSSL_STRINGS" ]; then
        echo "   ✅ Found OpenSSL strings:"
        echo "$OPENSSL_STRINGS" | sed 's/^/      /'
        FOUND_STRINGS=1
    else
        echo "   ⚠️  No OpenSSL strings found"
        FOUND_STRINGS=0
    fi
else
    echo "   ⚠️  strings not available"
    FOUND_STRINGS=0
fi

# Method 3: Check dynamic libraries (for shared libraries)
if command -v readelf >/dev/null 2>&1; then
    echo "   Checking dynamic libraries..."
    DYNLIB=$(readelf -d "$BINARY_PATH" 2>/dev/null | grep -iE 'NEEDED.*ssl|NEEDED.*crypto' || true)
    
    if [ -n "$DYNLIB" ]; then
        echo "   ✅ Found OpenSSL in dynamic libraries:"
        echo "$DYNLIB" | sed 's/^/      /'
        FOUND_DYNLIB=1
    else
        # For static linking, this is expected to be empty
        echo "   ℹ️  No OpenSSL in dynamic libraries (may be statically linked)"
        FOUND_DYNLIB=0
    fi
fi

# Method 4: Check with nm (if available)
if command -v nm >/dev/null 2>&1; then
    echo "   Checking with nm..."
    OPENSSL_NM=$(nm "$BINARY_PATH" 2>/dev/null | grep -E 'EVP_|SSL_|TLS_' | head -5 || true)
    
    if [ -n "$OPENSSL_NM" ]; then
        echo "   ✅ Found OpenSSL symbols with nm:"
        echo "$OPENSSL_NM" | sed 's/^/      /'
        FOUND_NM=1
    else
        FOUND_NM=0
    fi
else
    FOUND_NM=0
fi

# Summary
echo ""
echo "📊 Summary:"

TOTAL_FOUND=$((FOUND_SYMBOLS + FOUND_STRINGS + FOUND_DYNLIB + FOUND_NM))

if [ "$TOTAL_FOUND" -gt 0 ]; then
    echo "   ✅ OpenSSL appears to be linked (found $TOTAL_FOUND indicators)"
    exit 0
else
    if [ "$STRICT_MODE" = "--strict" ]; then
        echo "   ❌ OpenSSL not found (strict mode)"
        exit 1
    else
        echo "   ⚠️  OpenSSL linkage not confirmed (may be statically linked or not using OpenSSL)"
        exit 0
    fi
fi

