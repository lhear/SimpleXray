/*
 * Hyper Burst Pacing - EWMA burst intensity tracking
 * Passes hints back to backend for pacing window adjustment
 */

#ifndef HYPER_BURST_HPP
#define HYPER_BURST_HPP

#include "hyper_backend.hpp"
#include <atomic>
#include <cstdint>

// Burst intensity tracker
struct alignas(64) BurstTracker {
    // EWMA parameters
    double alpha;           // 8 bytes - EWMA smoothing factor
    double currentBurst;    // 8 bytes - current burst intensity
    uint64_t packetCount;   // 8 bytes - packets in current window
    uint64_t byteCount;     // 8 bytes - bytes in current window
    uint64_t windowStartNs; // 8 bytes - window start timestamp
    BurstLevel level;       // 4 bytes - current burst level
    // Padding to 64 bytes
    uint8_t reserved[28];
};

static_assert(sizeof(BurstTracker) == 64, "BurstTracker must be 64 bytes");

// Update burst intensity with EWMA
inline void update_burst_intensity(BurstTracker* tracker, uint64_t bytes, uint64_t timestampNs) {
    const double alpha = 0.1; // EWMA smoothing factor
    const uint64_t windowNs = 10000000; // 10ms window
    
    if (timestampNs - tracker->windowStartNs > windowNs) {
        // New window - calculate intensity
        double intensity = static_cast<double>(tracker->byteCount) / 
                          (static_cast<double>(timestampNs - tracker->windowStartNs) / 1e9);
        
        // Update EWMA
        tracker->currentBurst = alpha * intensity + (1.0 - alpha) * tracker->currentBurst;
        
        // Determine level
        if (tracker->currentBurst > 100000000.0) { // > 100 Mbps
            tracker->level = BURST_EXTREME;
        } else if (tracker->currentBurst > 50000000.0) { // > 50 Mbps
            tracker->level = BURST_HIGH;
        } else if (tracker->currentBurst > 10000000.0) { // > 10 Mbps
            tracker->level = BURST_MEDIUM;
        } else if (tracker->currentBurst > 1000000.0) { // > 1 Mbps
            tracker->level = BURST_LOW;
        } else {
            tracker->level = BURST_NONE;
        }
        
        // Reset window
        tracker->packetCount = 0;
        tracker->byteCount = 0;
        tracker->windowStartNs = timestampNs;
    }
    
    tracker->packetCount++;
    tracker->byteCount += bytes;
}

#endif // HYPER_BURST_HPP

