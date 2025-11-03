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
    src/perf_mmap_batch.cpp

# Include directories
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include

# C++ flags
LOCAL_CPPFLAGS := \
    -std=c++17 \
    -Wall \
    -Wextra \
    -O3 \
    -ffast-math \
    -funroll-loops \
    -fomit-frame-pointer \
    -march=armv8-a+simd+crypto \
    -mfpu=neon-fp-armv8

# Linker flags
LOCAL_LDFLAGS := \
    -llog \
    -latomic

# Enable NEON
LOCAL_ARM_NEON := true

# Build as shared library
include $(BUILD_SHARED_LIBRARY)

