#!/bin/bash
# Setup Android NDK - downloads and extracts NDK if missing
# Usage: ./scripts/setup-ndk.sh [NDK_VERSION] [INSTALL_DIR]

set -e

NDK_VERSION="${1:-r28c}"
INSTALL_DIR="${2:-$(pwd)}"
NDK_DIR="$INSTALL_DIR/android-ndk-$NDK_VERSION"

# Check if NDK already exists
if [ -d "$NDK_DIR" ]; then
    echo "✅ NDK already exists at $NDK_DIR"
    echo "NDK_HOME=$NDK_DIR"
    exit 0
fi

echo "📦 Downloading Android NDK $NDK_VERSION..."

# Determine OS and download URL
OS_TYPE="linux"
if [[ "$OSTYPE" == "darwin"* ]]; then
    OS_TYPE="darwin"
elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
    echo "❌ Windows detected. Please download NDK manually or use WSL."
    exit 1
fi

NDK_URL="https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-${OS_TYPE}-x86_64.zip"
NDK_ZIP="$INSTALL_DIR/android-ndk-${NDK_VERSION}.zip"

echo "Downloading from: $NDK_URL"
if ! wget -q --show-progress "$NDK_URL" -O "$NDK_ZIP"; then
    echo "❌ Failed to download NDK"
    exit 1
fi

echo "📂 Extracting NDK..."
if ! unzip -q "$NDK_ZIP" -d "$INSTALL_DIR"; then
    echo "❌ Failed to extract NDK"
    rm -f "$NDK_ZIP"
    exit 1
fi

rm -f "$NDK_ZIP"

# Verify extraction
if [ ! -d "$NDK_DIR" ]; then
    echo "❌ NDK extraction failed - directory not found"
    exit 1
fi

echo "✅ NDK installed successfully at $NDK_DIR"
echo "NDK_HOME=$NDK_DIR"

