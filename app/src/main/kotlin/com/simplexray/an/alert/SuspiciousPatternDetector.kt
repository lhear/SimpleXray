package com.simplexray.an.alert

import android.content.Context
import android.util.Log
import com.simplexray.an.stats.BitrateBus
import com.simplexray.an.stats.BitratePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * Detects suspicious traffic patterns (e.g., DDoS-like behavior, unusual patterns)
 */
class SuspiciousPatternDetector(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "SuspiciousPatternDetector"
    private var job: Job? = null
    private val bitrateHistory = ArrayDeque<BitratePoint>(120) // 2 minutes at 1s intervals
    
    fun start() {
        NotificationEngine.ensureChannel(context)
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            BitrateBus.flow.collect { p ->
                bitrateHistory.addLast(p)
                if (bitrateHistory.size > 120) {
                    bitrateHistory.removeFirst()
                }
                
                if (bitrateHistory.size >= 60) {
                    detectPatterns()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun detectPatterns() {
        val points = bitrateHistory.toList()
        
        // Pattern 1: Rapid oscillation (possible DDoS or attack)
        detectRapidOscillation(points)
        
        // Pattern 2: Sudden drop to zero then recovery
        detectSuddenDrop(points)
        
        // Pattern 3: Unusual symmetry (uplink ≈ downlink at high rates)
        detectUnusualSymmetry(points)
    }

    /**
     * Detect rapid oscillation (high variance)
     */
    private fun detectRapidOscillation(points: List<BitratePoint>) {
        if (points.size < 30) return
        
        val downlinkValues = points.map { it.downlinkBps.toFloat() }
        val variance = calculateVariance(downlinkValues)
        val mean = downlinkValues.average().toFloat()
        
        // High coefficient of variation suggests oscillation
        if (mean > 0 && variance / mean > 0.5f) {
            val now = System.currentTimeMillis()
            EventLogger.log(
                EventLogger.AlertEvent.EventType.SUSPICIOUS_PATTERN,
                EventLogger.AlertEvent.Severity.HIGH,
                "Rapid traffic oscillation detected (possible attack pattern)",
                mapOf(
                    "variance" to variance,
                    "mean" to mean,
                    "coefficientOfVariation" to (variance / mean)
                )
            )
        }
    }

    /**
     * Detect sudden drop to near zero
     */
    private fun detectSuddenDrop(points: List<BitratePoint>) {
        if (points.size < 20) return
        
        val recent = points.takeLast(10).map { it.downlinkBps.toFloat() }
        val previous = points.dropLast(10).takeLast(10).map { it.downlinkBps.toFloat() }
        
        val recentAvg = recent.average().toFloat()
        val previousAvg = previous.average().toFloat()
        
        // Drop from high to near zero
        if (previousAvg > 1_000_000 && recentAvg < previousAvg * 0.1f && recentAvg < 100_000) {
            EventLogger.log(
                EventLogger.AlertEvent.EventType.SUSPICIOUS_PATTERN,
                EventLogger.AlertEvent.Severity.MEDIUM,
                "Sudden traffic drop detected",
                mapOf(
                    "previousAvg" to previousAvg,
                    "recentAvg" to recentAvg,
                    "dropPercent" to ((previousAvg - recentAvg) / previousAvg * 100).toInt()
                )
            )
        }
    }

    /**
     * Detect unusual symmetry (equal high uplink/downlink)
     */
    private fun detectUnusualSymmetry(points: List<BitratePoint>) {
        val recent = points.takeLast(30)
        val avgUplink = recent.map { it.uplinkBps.toFloat() }.average().toFloat()
        val avgDownlink = recent.map { it.downlinkBps.toFloat() }.average().toFloat()
        
        // Both high and similar (within 20%)
        val similarity = if (avgDownlink > 0) {
            kotlin.math.abs(avgUplink - avgDownlink) / avgDownlink
        } else 1f
        
        if (avgUplink > 5_000_000 && avgDownlink > 5_000_000 && similarity < 0.2f) {
            EventLogger.log(
                EventLogger.AlertEvent.EventType.SUSPICIOUS_PATTERN,
                EventLogger.AlertEvent.Severity.LOW,
                "Unusual traffic symmetry detected (high bidirectional traffic)",
                mapOf(
                    "uplink" to avgUplink,
                    "downlink" to avgDownlink,
                    "similarity" to similarity
                )
            )
        }
    }

    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val squaredDiffs = values.map { (it - mean) * (it - mean) }
        return squaredDiffs.average().toFloat()
    }
}
