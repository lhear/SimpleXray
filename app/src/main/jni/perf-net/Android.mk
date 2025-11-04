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
    src/perf_tcp_fastopen.cpp

# Include directories
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include

# OpenSSL includes (if available)
# OpenSSL will be used if libraries are installed in app/src/main/jni/openssl/
OPENSSL_DIR := $(LOCAL_PATH)/../../openssl
ifneq ($(wildcard $(OPENSSL_DIR)/include/openssl/evp.h),)
    LOCAL_C_INCLUDES += $(OPENSSL_DIR)/include
    LOCAL_CPPFLAGS += -DUSE_OPENSSL=1
endif

# C++ flags
LOCAL_CPPFLAGS := \
    -std=c++17 \
    -Wall \
    -Wextra \
    -O3 \
    -ffast-math \
    -funroll-loops \
    -fomit-frame-pointer

# Architecture-specific flags
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    LOCAL_CPPFLAGS += -march=armv8-a+simd+crypto
    LOCAL_CFLAGS += -march=armv8-a+simd+crypto
else ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_CPPFLAGS += -march=armv7-a -mfpu=neon
    LOCAL_CFLAGS += -march=armv7-a -mfpu=neon
endif

# System libraries  
LOCAL_LDLIBS := \
    -llog \
    -latomic

# OpenSSL libraries (if available)
ifneq ($(wildcard $(OPENSSL_DIR)/include/openssl/evp.h),)
    LOCAL_LDLIBS += -L$(OPENSSL_DIR)/lib/$(TARGET_ARCH_ABI) -lcrypto -lssl
endif

# Enable NEON
LOCAL_ARM_NEON := true

# C++ standard library
LOCAL_CXX_STL := c++_shared

# Build as shared library
include $(BUILD_SHARED_LIBRARY)

