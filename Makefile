# Makefile for building Xray-Core with OpenSSL support
# Usage: make <target>

.PHONY: help openssl-android openssl-linux build-android build-linux ci-check verify clean

# Default values
NDK_ROOT ?= $(shell find . -maxdepth 2 -type d -name "android-ndk-*" 2>/dev/null | head -1)
OPENSSL_VERSION ?= 3.3.0
API_LEVEL_ARM64 ?= 24
API_LEVEL_ARM ?= 21
WITH_OPENSSL ?= 1

help:
	@echo "Xray-Core OpenSSL Build Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  openssl-android    - Build OpenSSL for Android (arm64-v8a and armeabi-v7a)"
	@echo "  openssl-linux       - Build OpenSSL for Linux x86_64"
	@echo "  build-android      - Build Xray-Core for Android with OpenSSL"
	@echo "  build-linux        - Build Xray-Core for Linux x86_64 with OpenSSL"
	@echo "  ci-check           - Run CI verification checks"
	@echo "  verify             - Verify OpenSSL linkage in built binaries"
	@echo "  clean              - Clean build artifacts"
	@echo ""
	@echo "Environment Variables:"
	@echo "  NDK_ROOT          - Android NDK path (default: auto-detect)"
	@echo "  OPENSSL_VERSION  - OpenSSL version (default: 3.3.0)"
	@echo "  API_LEVEL_ARM64  - Android API level for arm64 (default: 24)"
	@echo "  API_LEVEL_ARM    - Android API level for arm (default: 21)"
	@echo "  WITH_OPENSSL     - Enable OpenSSL (default: 1)"
	@echo ""
	@echo "Examples:"
	@echo "  make openssl-android"
	@echo "  make build-android"
	@echo "  NDK_ROOT=/path/to/ndk make openssl-android"

openssl-android: check-ndk
	@echo "🔨 Building OpenSSL for Android..."
	@chmod +x scripts/build-openssl-android.sh
	@if [ -z "$(NDK_ROOT)" ]; then \
		echo "❌ Error: NDK_ROOT not set and not found automatically"; \
		echo "   Please set NDK_ROOT or run: ./scripts/setup-ndk.sh"; \
		exit 1; \
	fi
	@./scripts/build-openssl-android.sh --arch arm64 --api $(API_LEVEL_ARM64) --ndk "$(NDK_ROOT)" --openssl-version $(OPENSSL_VERSION)
	@./scripts/build-openssl-android.sh --arch arm --api $(API_LEVEL_ARM) --ndk "$(NDK_ROOT)" --openssl-version $(OPENSSL_VERSION)
	@echo "✅ OpenSSL for Android built successfully"

openssl-linux:
	@echo "🔨 Building OpenSSL for Linux x86_64..."
	@chmod +x scripts/build-openssl-linux.sh
	@./scripts/build-openssl-linux.sh --openssl-version $(OPENSSL_VERSION)
	@echo "✅ OpenSSL for Linux built successfully"

build-android: openssl-android
	@echo "🔨 Building Xray-Core for Android with OpenSSL..."
	@chmod +x scripts/build-xray-with-openssl.sh
	@if [ -z "$(NDK_ROOT)" ]; then \
		echo "❌ Error: NDK_ROOT not set and not found automatically"; \
		echo "   Please set NDK_ROOT or run: ./scripts/setup-ndk.sh"; \
		exit 1; \
	fi
	@WITH_OPENSSL=$(WITH_OPENSSL) ./scripts/build-xray-with-openssl.sh --target android --arch arm64 --ndk "$(NDK_ROOT)"
	@WITH_OPENSSL=$(WITH_OPENSSL) ./scripts/build-xray-with-openssl.sh --target android --arch arm --ndk "$(NDK_ROOT)"
	@echo "✅ Xray-Core for Android built successfully"

build-linux: openssl-linux
	@echo "🔨 Building Xray-Core for Linux x86_64 with OpenSSL..."
	@chmod +x scripts/build-xray-with-openssl.sh
	@WITH_OPENSSL=$(WITH_OPENSSL) ./scripts/build-xray-with-openssl.sh --target linux --arch x86_64
	@echo "✅ Xray-Core for Linux built successfully"

ci-check:
	@echo "🔍 Running CI verification checks..."
	@chmod +x tools/check_openssl_link.sh
	@chmod +x tools/ci_verify.sh
	@if [ "$(WITH_OPENSSL)" = "1" ]; then \
		if [ -f "app/src/main/jniLibs/arm64-v8a/libxray.so" ]; then \
			WITH_OPENSSL=1 ./tools/ci_verify.sh app/src/main/jniLibs/arm64-v8a/libxray.so; \
		else \
			echo "⚠️  Binary not found for verification"; \
		fi \
	else \
		echo "ℹ️  WITH_OPENSSL not set, skipping verification"; \
	fi

verify:
	@echo "🔍 Verifying OpenSSL linkage..."
	@chmod +x tools/check_openssl_link.sh
	@if [ -f "app/src/main/jniLibs/arm64-v8a/libxray.so" ]; then \
		./tools/check_openssl_link.sh app/src/main/jniLibs/arm64-v8a/libxray.so; \
	elif [ -f "build/bin/xray-linux-x86_64" ]; then \
		./tools/check_openssl_link.sh build/bin/xray-linux-x86_64; \
	else \
		echo "❌ No binary found for verification"; \
		exit 1; \
	fi

check-ndk:
	@if [ -z "$(NDK_ROOT)" ] && [ -z "$$(find . -maxdepth 2 -type d -name 'android-ndk-*' 2>/dev/null | head -1)" ]; then \
		echo "⚠️  Warning: NDK not found. Run 'make setup-ndk' or set NDK_ROOT"; \
	fi

setup-ndk:
	@echo "📦 Setting up Android NDK..."
	@chmod +x scripts/setup-ndk.sh
	@./scripts/setup-ndk.sh r28c
	@echo "✅ NDK setup complete"

clean:
	@echo "🧹 Cleaning build artifacts..."
	@rm -rf build/
	@rm -rf openssl-*/
	@rm -rf third_party/openssl/
	@rm -rf Xray-core/
	@echo "✅ Clean complete"

