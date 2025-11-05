/*
 * Hyper Backend - Core Packet Metadata Layout
 * Zero-allocation, cache-aligned packet metadata for hot loops
 */

#ifndef HYPER_BACKEND_HPP
#define HYPER_BACKEND_HPP

#include <cstdint>
#include <cstddef>

// Packet metadata structure - aligned to 64 bytes for cache line optimization
// No padding holes, predictable ordering for NEON loads
struct alignas(64) PacketMeta {
    uint64_t timestampNs;  // 8 bytes - nanoseconds since epoch
    uint32_t length;       // 4 bytes - packet payload length
    uint16_t flags;        // 2 bytes - packet flags (crypto, priority, etc.)
    uint16_t queue;        // 2 bytes - queue identifier
    // Padding to 64 bytes (8 + 4 + 2 + 2 = 16, need 48 more)
    uint8_t reserved[48];  // Reserved for future use, maintains alignment
};

static_assert(sizeof(PacketMeta) == 64, "PacketMeta must be exactly 64 bytes");
static_assert(offsetof(PacketMeta, timestampNs) == 0, "timestampNs must be at offset 0");
static_assert(offsetof(PacketMeta, length) == 8, "length must be at offset 8");
static_assert(offsetof(PacketMeta, flags) == 12, "flags must be at offset 12");
static_assert(offsetof(PacketMeta, queue) == 14, "queue must be at offset 14");

// Ring buffer slot - contains metadata and pointer to payload
struct alignas(64) RingSlot {
    PacketMeta meta;       // 64 bytes - packet metadata
    void* payload;         // 8 bytes - pointer to payload buffer
    uint64_t payloadSize;  // 8 bytes - size of payload buffer
    // Padding to maintain 64-byte alignment
    uint8_t reserved[48];  // Reserved for future use
};

static_assert(sizeof(RingSlot) == 128, "RingSlot must be cache-aligned");
static_assert(offsetof(RingSlot, meta) == 0, "meta must be at offset 0");
static_assert(offsetof(RingSlot, payload) == 64, "payload must be at offset 64");

// Worker thread local storage - aligned for cache locality
struct alignas(64) WorkerLocal {
    uint32_t workerId;      // 4 bytes - worker thread ID
    uint32_t processedCount; // 4 bytes - packets processed by this worker
    uint64_t totalBytes;     // 8 bytes - total bytes processed
    uint64_t lastTimestamp; // 8 bytes - last packet timestamp
    // Padding to 64 bytes
    uint8_t reserved[40];
};

static_assert(sizeof(WorkerLocal) == 64, "WorkerLocal must be 64 bytes");

// Configuration structure for hyper backend
struct alignas(64) HyperConfig {
    int32_t batchSize;      // 4 bytes - packets per batch (16-32)
    int32_t chunkSize;     // 4 bytes - crypto chunk size
    int32_t flags;         // 4 bytes - feature flags
    int32_t workerCount;   // 4 bytes - number of crypto workers
    // Padding to 64 bytes
    uint8_t reserved[48];
};

static_assert(sizeof(HyperConfig) == 64, "HyperConfig must be 64 bytes");

// Burst hint levels
enum BurstLevel : int32_t {
    BURST_NONE = 0,
    BURST_LOW = 1,
    BURST_MEDIUM = 2,
    BURST_HIGH = 3,
    BURST_EXTREME = 4
};

#endif // HYPER_BACKEND_HPP

