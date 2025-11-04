package com.simplexray.an.performance.analytics

import com.simplexray.an.data.repository.PerformanceMetricsRepository
import com.simplexray.an.performance.model.PerformanceMetrics
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates performance reports (daily, weekly, monthly)
 */
class PerformanceReportGenerator(
    private val repository: PerformanceMetricsRepository
) {
    
    data class PerformanceReport(
        val period: ReportPeriod,
        val startTime: Long,
        val endTime: Long,
        val metrics: List<PerformanceMetrics>,
        val summary: ReportSummary,
        val anomalies: List<AnomalyDetector.Anomaly>,
        val trends: TrendAnalysis
    )
    
    data class ReportSummary(
        val avgLatency: Double,
        val avgDownloadSpeed: Double,
        val avgUploadSpeed: Double,
        val avgPacketLoss: Double,
        val avgCpuUsage: Double,
        val avgMemoryUsage: Double,
        val totalDataDownloaded: Long,
        val totalDataUploaded: Long,
        val connectionQualityDistribution: Map<String, Int>,
        val profileDistribution: Map<String, Int>? = null
    )
    
    data class TrendAnalysis(
        val latencyTrend: TrendDirection,
        val throughputTrend: TrendDirection,
        val stabilityTrend: TrendDirection,
        val improvementPercentage: Float
    )
    
    enum class TrendDirection {
        Improving,
        Stable,
        Degrading
    }
    
    enum class ReportPeriod {
        Daily,
        Weekly,
        Monthly
    }
    
    /**
     * Generate daily report
     */
    suspend fun generateDailyReport(date: Date = Date()): PerformanceReport {
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = startTime + 24 * 60 * 60 * 1000L
        
        return generateReport(ReportPeriod.Daily, startTime, endTime)
    }
    
    /**
     * Generate weekly report
     */
    suspend fun generateWeeklyReport(): PerformanceReport {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        return generateReport(ReportPeriod.Weekly, startTime, endTime)
    }
    
    /**
     * Generate monthly report
     */
    suspend fun generateMonthlyReport(): PerformanceReport {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        return generateReport(ReportPeriod.Monthly, startTime, endTime)
    }
    
    /**
     * Generate report for custom period
     */
    suspend fun generateReport(
        period: ReportPeriod,
        startTime: Long,
        endTime: Long
    ): PerformanceReport {
        val metrics: List<PerformanceMetrics> = emptyList() // Placeholder - would need to collect from flow
        
        // For now, get metrics using suspend function
        val metricsList = repository.getMetricsForLastDays(
            ((endTime - startTime) / (24 * 60 * 60 * 1000L)).toInt() + 1
        ).filter { it.timestamp >= startTime && it.timestamp <= endTime }
        
        val summary = generateSummary(metricsList)
        val anomalies = detectAnomalies(metricsList)
        val trends = analyzeTrends(metricsList)
        
        return PerformanceReport(
            period = period,
            startTime = startTime,
            endTime = endTime,
            metrics = metricsList,
            summary = summary,
            anomalies = anomalies,
            trends = trends
        )
    }
    
    private fun generateSummary(metrics: List<PerformanceMetrics>): ReportSummary {
        if (metrics.isEmpty()) {
            return ReportSummary(
                avgLatency = 0.0,
                avgDownloadSpeed = 0.0,
                avgUploadSpeed = 0.0,
                avgPacketLoss = 0.0,
                avgCpuUsage = 0.0,
                avgMemoryUsage = 0.0,
                totalDataDownloaded = 0L,
                totalDataUploaded = 0L,
                connectionQualityDistribution = emptyMap()
            )
        }
        
        val avgLatency = metrics.map { it.latency }.average()
        val avgDownloadSpeed = metrics.map { it.downloadSpeed.toDouble() }.average()
        val avgUploadSpeed = metrics.map { it.uploadSpeed.toDouble() }.average()
        val avgPacketLoss = metrics.map { it.packetLoss.toDouble() }.average()
        val avgCpuUsage = metrics.map { it.cpuUsage.toDouble() }.average()
        val avgMemoryUsage = metrics.map { it.memoryUsage.toDouble() }.average()
        
        val totalDataDownloaded = metrics.maxOfOrNull { it.totalDownload } ?: 0L
        val totalDataUploaded = metrics.maxOfOrNull { it.totalUpload } ?: 0L
        
        val qualityDistribution = metrics.groupingBy { it.overallQuality.name }
            .eachCount()
        
        return ReportSummary(
            avgLatency = avgLatency,
            avgDownloadSpeed = avgDownloadSpeed,
            avgUploadSpeed = avgUploadSpeed,
            avgPacketLoss = avgPacketLoss,
            avgCpuUsage = avgCpuUsage,
            avgMemoryUsage = avgMemoryUsage,
            totalDataDownloaded = totalDataDownloaded,
            totalDataUploaded = totalDataUploaded,
            connectionQualityDistribution = qualityDistribution
        )
    }
    
    private fun detectAnomalies(metrics: List<PerformanceMetrics>): List<AnomalyDetector.Anomaly> {
        val detector = AnomalyDetector()
        return detector.detectAnomaliesInSequence(metrics)
    }
    
    private fun analyzeTrends(metrics: List<PerformanceMetrics>): TrendAnalysis {
        if (metrics.size < 2) {
            return TrendAnalysis(
                latencyTrend = TrendDirection.Stable,
                throughputTrend = TrendDirection.Stable,
                stabilityTrend = TrendDirection.Stable,
                improvementPercentage = 0f
            )
        }
        
        val firstHalf = metrics.take(metrics.size / 2)
        val secondHalf = metrics.takeLast(metrics.size / 2)
        
        val firstAvgLatency = firstHalf.map { it.latency }.average()
        val secondAvgLatency = secondHalf.map { it.latency }.average()
        val latencyTrend = when {
            secondAvgLatency < firstAvgLatency * 0.9 -> TrendDirection.Improving
            secondAvgLatency > firstAvgLatency * 1.1 -> TrendDirection.Degrading
            else -> TrendDirection.Stable
        }
        
        val firstAvgThroughput = firstHalf.map { it.downloadSpeed.toDouble() }.average()
        val secondAvgThroughput = secondHalf.map { it.downloadSpeed.toDouble() }.average()
        val throughputTrend = when {
            secondAvgThroughput > firstAvgThroughput * 1.1 -> TrendDirection.Improving
            secondAvgThroughput < firstAvgThroughput * 0.9 -> TrendDirection.Degrading
            else -> TrendDirection.Stable
        }
        
        val firstAvgStability = firstHalf.map { it.connectionStability.toDouble() }.average()
        val secondAvgStability = secondHalf.map { it.connectionStability.toDouble() }.average()
        val stabilityTrend = when {
            secondAvgStability > firstAvgStability * 1.05 -> TrendDirection.Improving
            secondAvgStability < firstAvgStability * 0.95 -> TrendDirection.Degrading
            else -> TrendDirection.Stable
        }
        
        val improvementPercentage = if (firstAvgLatency > 0) {
            (((firstAvgLatency - secondAvgLatency) / firstAvgLatency) * 100).toFloat()
        } else {
            0f
        }
        
        return TrendAnalysis(
            latencyTrend = latencyTrend,
            throughputTrend = throughputTrend,
            stabilityTrend = stabilityTrend,
            improvementPercentage = improvementPercentage
        )
    }
    
    /**
     * Export report to CSV
     */
    suspend fun exportToCsv(report: PerformanceReport): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        
        // Header
        sb.append("Period,Start Time,End Time,Avg Latency (ms),Avg Download (MB/s),Avg Upload (MB/s),Avg Packet Loss (%),Avg CPU (%),Avg Memory (MB),Total Downloaded (MB),Total Uploaded (MB)\n")
        
        // Data
        val startTimeStr = dateFormat.format(Date(report.startTime))
        val endTimeStr = dateFormat.format(Date(report.endTime))
        val avgDownloadMbps = report.summary.avgDownloadSpeed / (1024.0 * 1024.0)
        val avgUploadMbps = report.summary.avgUploadSpeed / (1024.0 * 1024.0)
        val avgMemoryMB = report.summary.avgMemoryUsage / (1024.0 * 1024.0)
        val totalDownloadMB = report.summary.totalDataDownloaded / (1024.0 * 1024.0)
        val totalUploadMB = report.summary.totalDataUploaded / (1024.0 * 1024.0)
        
        sb.append("${report.period.name},$startTimeStr,$endTimeStr,")
        sb.append("${String.format("%.2f", report.summary.avgLatency)},")
        sb.append("${String.format("%.2f", avgDownloadMbps)},")
        sb.append("${String.format("%.2f", avgUploadMbps)},")
        sb.append("${String.format("%.2f", report.summary.avgPacketLoss)},")
        sb.append("${String.format("%.2f", report.summary.avgCpuUsage)},")
        sb.append("${String.format("%.2f", avgMemoryMB)},")
        sb.append("${String.format("%.2f", totalDownloadMB)},")
        sb.append("${String.format("%.2f", totalUploadMB)}\n")
        
        return sb.toString()
    }
    
    /**
     * Export report to JSON
     */
    suspend fun exportToJson(report: PerformanceReport): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val json = """
        {
            "period": "${report.period.name}",
            "startTime": "${dateFormat.format(Date(report.startTime))}",
            "endTime": "${dateFormat.format(Date(report.endTime))}",
            "summary": {
                "avgLatency": ${report.summary.avgLatency},
                "avgDownloadSpeed": ${report.summary.avgDownloadSpeed},
                "avgUploadSpeed": ${report.summary.avgUploadSpeed},
                "avgPacketLoss": ${report.summary.avgPacketLoss},
                "avgCpuUsage": ${report.summary.avgCpuUsage},
                "avgMemoryUsage": ${report.summary.avgMemoryUsage},
                "totalDataDownloaded": ${report.summary.totalDataDownloaded},
                "totalDataUploaded": ${report.summary.totalDataUploaded}
            },
            "anomaliesCount": ${report.anomalies.size},
            "trends": {
                "latencyTrend": "${report.trends.latencyTrend}",
                "throughputTrend": "${report.trends.throughputTrend}",
                "stabilityTrend": "${report.trends.stabilityTrend}",
                "improvementPercentage": ${report.trends.improvementPercentage}
            }
        }
        """.trimIndent()
        
        return json
    }
}

