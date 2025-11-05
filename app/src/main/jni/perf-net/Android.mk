LOCAL_PATH := $(call my-dir)

# Performance network module
include $(CLEAR_VARS)

LOCAL_MODULE := perf-net

# Source files
LOCAL_SRC_FILES := \
    src/perf_jni.cpp \
    src/perf_cpu_affinity.cpp \
    src/perf_epoll_loop.cpp \
    src/perf_zero_copy.cpp \
    src/perf_connection_pool.cpp \
    src/perf_crypto_neon.cpp \
    src/perf_tls_session.cpp \
    src/perf_mtu_tuning.cpp \
    src/perf_ring_buffer.cpp \
    src/perf_jit_warmup.cpp \
    src/perf_kernel_pacing.cpp \
    src/perf_readahead.cpp \
    src/perf_qos.cpp \
    src/perf_mmap_batch.cpp \
    src/perf_tcp_fastopen.cpp \
    src/hyper/hyper_ring.cpp \
    src/hyper/hyper_crypto.cpp \
    src/hyper/hyper_burst.cpp \
    src/hyper/hyper_cpu.cpp \
    src/hyper/hyper_jni.cpp

# Include directories
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/src/hyper

# OpenSSL includes (if available)
# OpenSSL will be used if libraries are installed in app/src/main/jni/openssl/
OPENSSL_DIR := $(LOCAL_PATH)/../../openssl
OPENSSL_HEADER := $(OPENSSL_DIR)/include/openssl/evp.h
OPENSSL_LIB_CRYPTO := $(OPENSSL_DIR)/lib/$(TARGET_ARCH_ABI)/libcrypto.a
OPENSSL_LIB_SSL := $(OPENSSL_DIR)/lib/$(TARGET_ARCH_ABI)/libssl.a

# Check if OpenSSL is available
ifneq ($(wildcard $(OPENSSL_HEADER)),)
    ifneq ($(wildcard $(OPENSSL_LIB_CRYPTO)),)
        ifneq ($(wildcard $(OPENSSL_LIB_SSL)),)
            # OpenSSL is fully available
            LOCAL_C_INCLUDES += $(OPENSSL_DIR)/include
            LOCAL_CPPFLAGS += -DUSE_OPENSSL=1
            $(info OpenSSL found: $(OPENSSL_DIR))
        else
            $(warning OpenSSL header found but libssl.a missing for $(TARGET_ARCH_ABI))
            $(warning Expected: $(OPENSSL_LIB_SSL))
        endif
    else
        $(warning OpenSSL header found but libcrypto.a missing for $(TARGET_ARCH_ABI))
        $(warning Expected: $(OPENSSL_LIB_CRYPTO))
    endif
else
    $(warning OpenSSL not found at $(OPENSSL_DIR))
    $(warning Run: ./scripts/download-openssl.sh to download OpenSSL libraries)
    $(warning Or manually install OpenSSL to: $(OPENSSL_DIR))
endif

# C++ flags
LOCAL_CPPFLAGS += \
    -std=c++17 \
    -Wall \
    -Wextra \
    -O3 \
    -ffast-math \
    -funroll-loops \
    -fno-omit-frame-pointer \
    -frtti \
    -fexceptions

# Architecture-specific flags
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    LOCAL_CPPFLAGS += -march=armv8-a+simd
    LOCAL_CFLAGS += -march=armv8-a+simd
else ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_CPPFLAGS += -march=armv7-a -mfpu=neon
    LOCAL_CFLAGS += -march=armv7-a -mfpu=neon
else ifeq ($(TARGET_ARCH_ABI),x86_64)
    LOCAL_CPPFLAGS += -march=x86-64 -msse4.2
else ifeq ($(TARGET_ARCH_ABI),x86)
    LOCAL_CPPFLAGS += -march=i686 -msse3
endif

# System libraries  
LOCAL_LDLIBS := \
    -llog \
    -latomic

# OpenSSL static libraries (if available)
ifneq ($(wildcard $(OPENSSL_HEADER)),)
    ifneq ($(wildcard $(OPENSSL_LIB_CRYPTO)),)
        ifneq ($(wildcard $(OPENSSL_LIB_SSL)),)
            # Link OpenSSL static libraries directly
            LOCAL_LDLIBS += $(OPENSSL_DIR)/lib/$(TARGET_ARCH_ABI)/libssl.a
            LOCAL_LDLIBS += $(OPENSSL_DIR)/lib/$(TARGET_ARCH_ABI)/libcrypto.a
        endif
    endif
endif

# Enable NEON
LOCAL_ARM_NEON := true

# C++ standard library
LOCAL_CXX_STL := c++_shared

# Build as shared library
include $(BUILD_SHARED_LIBRARY)

