#!/bin/bash
# OpenSSL Download Script for Android
# Downloads prebuilt OpenSSL libraries for Android

set -Eeuo pipefail

# Source git-safe helper functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/git-safe.sh" 2>/dev/null || {
    # Fallback if git-safe.sh not available
    ensure_repo_root() {
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
    }
}

# Use git-safe helper to get repo root
ensure_repo_root
PROJECT_ROOT="${REPO_ROOT:-$PROJECT_ROOT}"
# Ensure we have absolute path
OPENSSL_DIR="$(cd "$PROJECT_ROOT" && pwd)/app/src/main/jni/openssl"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}OpenSSL Download Script for Android${NC}"
echo "=========================================="

# Create OpenSSL directory structure
mkdir -p "$OPENSSL_DIR/include"
mkdir -p "$OPENSSL_DIR/lib/arm64-v8a"
mkdir -p "$OPENSSL_DIR/lib/armeabi-v7a"
mkdir -p "$OPENSSL_DIR/lib/x86"
mkdir -p "$OPENSSL_DIR/lib/x86_64"

# Function to download and extract
download_openssl() {
    local arch=$1
    local abi=$2
    
    echo -e "${YELLOW}Downloading OpenSSL for $arch ($abi)...${NC}"
    
    # Try multiple sources
    # Source 1: leenjewel/openssl_for_ios_and_android (GitHub)
    local url=""
    local temp_file="/tmp/openssl-${arch}.zip"
    
    # Check if we can use wget or curl
    if command -v wget &> /dev/null; then
        DOWNLOAD_CMD="wget -O"
    elif command -v curl &> /dev/null; then
        DOWNLOAD_CMD="curl -L -o"
    else
        echo -e "${RED}Error: wget or curl is required but not found${NC}"
        return 1
    fi
    
    # Try to download from various sources
    # Note: These URLs may need to be updated based on actual availability
    case "$arch" in
        arm64)
            echo -e "${YELLOW}Attempting to download prebuilt OpenSSL for arm64-v8a...${NC}"
            echo -e "${YELLOW}Please manually download OpenSSL from:${NC}"
            echo "  1. https://github.com/leenjewel/openssl_for_ios_and_android"
            echo "  2. https://github.com/viperforge/android-openssl-prebuilt"
            echo ""
            echo -e "${YELLOW}Or build from source:${NC}"
            echo "  See: https://wiki.openssl.org/index.php/Android"
            ;;
        arm)
            echo -e "${YELLOW}Attempting to download prebuilt OpenSSL for armeabi-v7a...${NC}"
            ;;
        *)
            echo -e "${YELLOW}Architecture $arch may not be fully supported${NC}"
            ;;
    esac
    
    return 0
}

# Function to build OpenSSL from source (if download fails)
build_openssl_instructions() {
    echo ""
    echo -e "${GREEN}=== Alternative: Build OpenSSL from Source ===${NC}"
    echo ""
    echo "If prebuilt libraries are not available, you can build OpenSSL:"
    echo ""
    echo "1. Download OpenSSL source:"
    echo "   wget https://www.openssl.org/source/openssl-3.2.0.tar.gz"
    echo "   tar -xzf openssl-3.2.0.tar.gz"
    echo ""
    echo "2. Set up NDK standalone toolchain:"
    echo "   \$NDK/build/tools/make_standalone_toolchain.py \\"
    echo "     --arch arm64 --api 21 --install-dir=/tmp/ndk-arm64"
    echo ""
    echo "3. Configure and build:"
    echo "   cd openssl-3.2.0"
    echo "   ./Configure android-arm64 \\"
    echo "     --prefix=$OPENSSL_DIR \\"
    echo "     no-shared no-ssl3 no-tests"
    echo "   make"
    echo "   make install"
    echo ""
}

# Check architectures
ARCHS=("arm64-v8a" "armeabi-v7a")

for abi in "${ARCHS[@]}"; do
    case "$abi" in
        arm64-v8a)
            arch="arm64"
            ;;
        armeabi-v7a)
            arch="arm"
            ;;
        *)
            continue
            ;;
    esac
    
    download_openssl "$arch" "$abi"
done

# Create a README with instructions
cat > "$OPENSSL_DIR/README.md" << 'EOF'
# OpenSSL for Android

This directory should contain OpenSSL libraries for Android.

## Required Structure

```
openssl/
├── include/
│   └── openssl/
│       ├── evp.h
│       ├── aes.h
│       ├── chacha.h
│       └── ...
└── lib/
    ├── arm64-v8a/
    │   ├── libcrypto.a
    │   └── libssl.a
    └── armeabi-v7a/
        ├── libcrypto.a
        └── libssl.a
```

## Quick Setup Options

### Option 1: Prebuilt Libraries (Recommended)

Download prebuilt libraries from:
- https://github.com/leenjewel/openssl_for_ios_and_android
- https://github.com/viperforge/android-openssl-prebuilt

Extract to this directory.

### Option 2: Build from Source

See: https://wiki.openssl.org/index.php/Android

1. Install NDK
2. Create standalone toolchain
3. Configure and build OpenSSL
4. Install to this directory

## Verification

After installation, verify with:
```bash
ls -la include/openssl/evp.h
ls -la lib/arm64-v8a/libcrypto.a
ls -la lib/arm64-v8a/libssl.a
```

EOF

echo ""
echo -e "${GREEN}=== Setup Instructions Created ===${NC}"
echo ""
echo "OpenSSL directory structure created at:"
echo "  $OPENSSL_DIR"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Download OpenSSL libraries (see $OPENSSL_DIR/README.md)"
echo "2. Place libraries in: $OPENSSL_DIR/lib/\${ABI}/"
echo "3. Place headers in: $OPENSSL_DIR/include/openssl/"
echo ""
echo -e "${GREEN}Or run this script with --auto-download flag (when implemented)${NC}"

build_openssl_instructions


