package com.simplexray.an.performance.analytics

import com.simplexray.an.data.repository.PerformanceMetricsRepository
import com.simplexray.an.performance.model.PerformanceMetrics

/**
 * Comparison tools for performance metrics
 */
class PerformanceComparator(
    private val repository: PerformanceMetricsRepository
) {
    
    data class ComparisonResult(
        val before: ComparisonStats,
        val after: ComparisonStats,
        val improvements: Improvements,
        val regressions: Regressions
    )
    
    data class ComparisonStats(
        val avgLatency: Double,
        val avgDownloadSpeed: Double,
        val avgUploadSpeed: Double,
        val avgPacketLoss: Double,
        val avgCpuUsage: Double,
        val avgMemoryUsage: Double,
        val avgConnectionStability: Double,
        val sampleCount: Int
    )
    
    data class Improvements(
        val latencyImprovement: Float, // Percentage
        val downloadSpeedImprovement: Float,
        val uploadSpeedImprovement: Float,
        val packetLossImprovement: Float,
        val cpuImprovement: Float,
        val memoryImprovement: Float,
        val stabilityImprovement: Float
    )
    
    data class Regressions(
        val latencyRegression: Float,
        val downloadSpeedRegression: Float,
        val uploadSpeedRegression: Float,
        val packetLossRegression: Float,
        val cpuRegression: Float,
        val memoryRegression: Float,
        val stabilityRegression: Float
    )
    
    /**
     * Compare before/after performance mode
     */
    suspend fun compareBeforeAfter(
        beforeStartTime: Long,
        beforeEndTime: Long,
        afterStartTime: Long,
        afterEndTime: Long
    ): ComparisonResult {
        val beforeMetrics = repository.getMetricsForLastDays(
            ((beforeEndTime - beforeStartTime) / (24 * 60 * 60 * 1000L)).toInt() + 1
        ).filter { it.timestamp >= beforeStartTime && it.timestamp <= beforeEndTime }
        
        val afterMetrics = repository.getMetricsForLastDays(
            ((afterEndTime - afterStartTime) / (24 * 60 * 60 * 1000L)).toInt() + 1
        ).filter { it.timestamp >= afterStartTime && it.timestamp <= afterEndTime }
        
        return compareMetrics(beforeMetrics, afterMetrics)
    }
    
    /**
     * Compare two profiles
     */
    suspend fun compareProfiles(
        profile1Id: String,
        profile2Id: String,
        days: Int = 7
    ): ComparisonResult {
        val profile1Metrics = repository.getMetricsByProfile(profile1Id, days)
        val profile2Metrics = repository.getMetricsByProfile(profile2Id, days)
        
        return compareMetrics(profile1Metrics, profile2Metrics)
    }
    
    /**
     * Compare network types
     */
    suspend fun compareNetworkTypes(
        networkType1: String,
        networkType2: String,
        days: Int = 7
    ): ComparisonResult {
        // This would require filtering by networkType in the repository
        // For now, return empty comparison
        val emptyStats = ComparisonStats(
            avgLatency = 0.0,
            avgDownloadSpeed = 0.0,
            avgUploadSpeed = 0.0,
            avgPacketLoss = 0.0,
            avgCpuUsage = 0.0,
            avgMemoryUsage = 0.0,
            avgConnectionStability = 0.0,
            sampleCount = 0
        )
        
        return ComparisonResult(
            before = emptyStats,
            after = emptyStats,
            improvements = Improvements(0f, 0f, 0f, 0f, 0f, 0f, 0f),
            regressions = Regressions(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )
    }
    
    /**
     * Compare two sets of metrics
     */
    private fun compareMetrics(
        before: List<PerformanceMetrics>,
        after: List<PerformanceMetrics>
    ): ComparisonResult {
        val beforeStats = calculateStats(before)
        val afterStats = calculateStats(after)
        
        val improvements = calculateImprovements(beforeStats, afterStats)
        val regressions = calculateRegressions(beforeStats, afterStats)
        
        return ComparisonResult(
            before = beforeStats,
            after = afterStats,
            improvements = improvements,
            regressions = regressions
        )
    }
    
    private fun calculateStats(metrics: List<PerformanceMetrics>): ComparisonStats {
        if (metrics.isEmpty()) {
            return ComparisonStats(
                avgLatency = 0.0,
                avgDownloadSpeed = 0.0,
                avgUploadSpeed = 0.0,
                avgPacketLoss = 0.0,
                avgCpuUsage = 0.0,
                avgMemoryUsage = 0.0,
                avgConnectionStability = 0.0,
                sampleCount = 0
            )
        }
        
        return ComparisonStats(
            avgLatency = metrics.map { it.latency }.average(),
            avgDownloadSpeed = metrics.map { it.downloadSpeed.toDouble() }.average(),
            avgUploadSpeed = metrics.map { it.uploadSpeed.toDouble() }.average(),
            avgPacketLoss = metrics.map { it.packetLoss.toDouble() }.average(),
            avgCpuUsage = metrics.map { it.cpuUsage.toDouble() }.average(),
            avgMemoryUsage = metrics.map { it.memoryUsage.toDouble() }.average(),
            avgConnectionStability = metrics.map { it.connectionStability.toDouble() }.average(),
            sampleCount = metrics.size
        )
    }
    
    private fun calculateImprovements(
        before: ComparisonStats,
        after: ComparisonStats
    ): Improvements {
        val latencyImprovement = if (before.avgLatency > 0) {
            ((before.avgLatency - after.avgLatency) / before.avgLatency * 100).toFloat()
        } else 0f
        
        val downloadSpeedImprovement = if (before.avgDownloadSpeed > 0) {
            ((after.avgDownloadSpeed - before.avgDownloadSpeed) / before.avgDownloadSpeed * 100).toFloat()
        } else 0f
        
        val uploadSpeedImprovement = if (before.avgUploadSpeed > 0) {
            ((after.avgUploadSpeed - before.avgUploadSpeed) / before.avgUploadSpeed * 100).toFloat()
        } else 0f
        
        val packetLossImprovement = if (before.avgPacketLoss > 0) {
            ((before.avgPacketLoss - after.avgPacketLoss) / before.avgPacketLoss * 100).toFloat()
        } else 0f
        
        val cpuImprovement = if (before.avgCpuUsage > 0) {
            ((before.avgCpuUsage - after.avgCpuUsage) / before.avgCpuUsage * 100).toFloat()
        } else 0f
        
        val memoryImprovement = if (before.avgMemoryUsage > 0) {
            ((before.avgMemoryUsage - after.avgMemoryUsage) / before.avgMemoryUsage * 100).toFloat()
        } else 0f
        
        val stabilityImprovement = if (before.avgConnectionStability > 0) {
            ((after.avgConnectionStability - before.avgConnectionStability) / before.avgConnectionStability * 100).toFloat()
        } else 0f
        
        return Improvements(
            latencyImprovement = latencyImprovement,
            downloadSpeedImprovement = downloadSpeedImprovement,
            uploadSpeedImprovement = uploadSpeedImprovement,
            packetLossImprovement = packetLossImprovement,
            cpuImprovement = cpuImprovement,
            memoryImprovement = memoryImprovement,
            stabilityImprovement = stabilityImprovement
        )
    }
    
    private fun calculateRegressions(
        before: ComparisonStats,
        after: ComparisonStats
    ): Regressions {
        val latencyRegression = if (before.avgLatency > 0 && after.avgLatency > before.avgLatency) {
            ((after.avgLatency - before.avgLatency) / before.avgLatency * 100).toFloat()
        } else 0f
        
        val downloadSpeedRegression = if (before.avgDownloadSpeed > 0 && after.avgDownloadSpeed < before.avgDownloadSpeed) {
            ((before.avgDownloadSpeed - after.avgDownloadSpeed) / before.avgDownloadSpeed * 100).toFloat()
        } else 0f
        
        val uploadSpeedRegression = if (before.avgUploadSpeed > 0 && after.avgUploadSpeed < before.avgUploadSpeed) {
            ((before.avgUploadSpeed - after.avgUploadSpeed) / before.avgUploadSpeed * 100).toFloat()
        } else 0f
        
        val packetLossRegression = if (before.avgPacketLoss >= 0 && after.avgPacketLoss > before.avgPacketLoss) {
            ((after.avgPacketLoss - before.avgPacketLoss) / (before.avgPacketLoss + 0.1) * 100).toFloat()
        } else 0f
        
        val cpuRegression = if (before.avgCpuUsage > 0 && after.avgCpuUsage > before.avgCpuUsage) {
            ((after.avgCpuUsage - before.avgCpuUsage) / before.avgCpuUsage * 100).toFloat()
        } else 0f
        
        val memoryRegression = if (before.avgMemoryUsage > 0 && after.avgMemoryUsage > before.avgMemoryUsage) {
            ((after.avgMemoryUsage - before.avgMemoryUsage) / before.avgMemoryUsage * 100).toFloat()
        } else 0f
        
        val stabilityRegression = if (before.avgConnectionStability > 0 && after.avgConnectionStability < before.avgConnectionStability) {
            ((before.avgConnectionStability - after.avgConnectionStability) / before.avgConnectionStability * 100).toFloat()
        } else 0f
        
        return Regressions(
            latencyRegression = latencyRegression,
            downloadSpeedRegression = downloadSpeedRegression,
            uploadSpeedRegression = uploadSpeedRegression,
            packetLossRegression = packetLossRegression,
            cpuRegression = cpuRegression,
            memoryRegression = memoryRegression,
            stabilityRegression = stabilityRegression
        )
    }
}


