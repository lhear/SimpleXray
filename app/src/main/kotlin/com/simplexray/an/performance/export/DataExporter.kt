package com.simplexray.an.performance.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.simplexray.an.performance.model.MetricsHistory
import com.simplexray.an.performance.model.PerformanceMetrics
import com.simplexray.an.performance.statistics.PerformanceStatistics
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Export performance data to various formats
 */
class DataExporter(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val statistics = PerformanceStatistics()

    /**
     * Export to CSV format
     */
    fun exportToCsv(history: MetricsHistory): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "simplexray_performance_$timestamp.csv"
        val file = File(context.cacheDir, fileName)

        file.bufferedWriter().use { writer ->
            // Write CSV header
            writer.write("Timestamp,Download Speed (MB/s),Upload Speed (MB/s),Total Download (MB),Total Upload (MB),")
            writer.write("Latency (ms),Jitter (ms),Packet Loss (%),CPU Usage (%),Memory Usage (MB),")
            writer.write("Stability (%),Quality Score,Quality Grade\n")

            // Write data rows
            history.metrics.forEach { metric ->
                val downloadSpeedMBps = metric.downloadSpeed / (1024.0 * 1024.0)
                val uploadSpeedMBps = metric.uploadSpeed / (1024.0 * 1024.0)
                val totalDownloadMB = metric.totalDownload / (1024.0 * 1024.0)
                val totalUploadMB = metric.totalUpload / (1024.0 * 1024.0)
                val memoryUsageMB = metric.memoryUsage / (1024.0 * 1024.0)
                val qualityScore = metric.calculateQualityScore()
                val quality = metric.getConnectionQuality()

                writer.write("${formatTimestamp(metric.timestamp)},")
                writer.write("${String.format("%.2f", downloadSpeedMBps)},")
                writer.write("${String.format("%.2f", uploadSpeedMBps)},")
                writer.write("${String.format("%.2f", totalDownloadMB)},")
                writer.write("${String.format("%.2f", totalUploadMB)},")
                writer.write("${metric.latency},")
                writer.write("${metric.jitter},")
                writer.write("${String.format("%.2f", metric.packetLoss)},")
                writer.write("${String.format("%.2f", metric.cpuUsage)},")
                writer.write("${String.format("%.2f", memoryUsageMB)},")
                writer.write("${String.format("%.2f", metric.connectionStability)},")
                writer.write("${String.format("%.2f", qualityScore)},")
                writer.write("${quality.displayName}\n")
            }
        }

        return file
    }

    /**
     * Export to JSON format with detailed statistics
     */
    fun exportToJson(history: MetricsHistory): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "simplexray_performance_$timestamp.json"
        val file = File(context.cacheDir, fileName)

        val report = statistics.generateReport(history.metrics)

        val exportData = mapOf(
            "export_info" to mapOf(
                "timestamp" to System.currentTimeMillis(),
                "date" to formatTimestamp(System.currentTimeMillis()),
                "app_version" to "1.0.0", // TODO: Get from BuildConfig
                "total_metrics" to history.metrics.size
            ),
            "statistics" to mapOf(
                "download_speed" to mapOf(
                    "min_mbps" to String.format("%.2f", report.downloadStats.min),
                    "max_mbps" to String.format("%.2f", report.downloadStats.max),
                    "mean_mbps" to String.format("%.2f", report.downloadStats.mean),
                    "median_mbps" to String.format("%.2f", report.downloadStats.median),
                    "p95_mbps" to String.format("%.2f", report.downloadStats.p95),
                    "p99_mbps" to String.format("%.2f", report.downloadStats.p99),
                    "std_dev" to String.format("%.2f", report.downloadStats.stdDev)
                ),
                "upload_speed" to mapOf(
                    "min_mbps" to String.format("%.2f", report.uploadStats.min),
                    "max_mbps" to String.format("%.2f", report.uploadStats.max),
                    "mean_mbps" to String.format("%.2f", report.uploadStats.mean),
                    "median_mbps" to String.format("%.2f", report.uploadStats.median),
                    "p95_mbps" to String.format("%.2f", report.uploadStats.p95),
                    "std_dev" to String.format("%.2f", report.uploadStats.stdDev)
                ),
                "latency" to mapOf(
                    "min_ms" to report.latencyStats.min.toInt(),
                    "max_ms" to report.latencyStats.max.toInt(),
                    "mean_ms" to report.latencyStats.mean.toInt(),
                    "median_ms" to report.latencyStats.median.toInt(),
                    "p95_ms" to report.latencyStats.p95.toInt(),
                    "p99_ms" to report.latencyStats.p99.toInt(),
                    "std_dev" to String.format("%.2f", report.latencyStats.stdDev)
                ),
                "cpu_usage" to mapOf(
                    "min_percent" to String.format("%.2f", report.cpuStats.min),
                    "max_percent" to String.format("%.2f", report.cpuStats.max),
                    "mean_percent" to String.format("%.2f", report.cpuStats.mean),
                    "median_percent" to String.format("%.2f", report.cpuStats.median)
                ),
                "memory_usage" to mapOf(
                    "min_mb" to String.format("%.2f", report.memoryStats.min),
                    "max_mb" to String.format("%.2f", report.memoryStats.max),
                    "mean_mb" to String.format("%.2f", report.memoryStats.mean),
                    "median_mb" to String.format("%.2f", report.memoryStats.median)
                ),
                "session" to mapOf(
                    "total_data_points" to report.totalDataPoints,
                    "average_quality" to String.format("%.2f", report.averageQuality),
                    "uptime_ms" to report.uptime
                )
            ),
            "raw_metrics" to history.metrics.map { metric ->
                mapOf(
                    "timestamp" to metric.timestamp,
                    "date" to formatTimestamp(metric.timestamp),
                    "download_speed_bytes_per_sec" to metric.downloadSpeed,
                    "upload_speed_bytes_per_sec" to metric.uploadSpeed,
                    "total_download_bytes" to metric.totalDownload,
                    "total_upload_bytes" to metric.totalUpload,
                    "latency_ms" to metric.latency,
                    "jitter_ms" to metric.jitter,
                    "packet_loss_percent" to metric.packetLoss,
                    "connection_count" to metric.connectionCount,
                    "active_connection_count" to metric.activeConnectionCount,
                    "cpu_usage_percent" to metric.cpuUsage,
                    "memory_usage_bytes" to metric.memoryUsage,
                    "native_memory_usage_bytes" to metric.nativeMemoryUsage,
                    "connection_stability" to metric.connectionStability,
                    "quality_score" to metric.calculateQualityScore(),
                    "quality_grade" to metric.getConnectionQuality().displayName
                )
            }
        )

        file.bufferedWriter().use { writer ->
            writer.write(gson.toJson(exportData))
        }

        return file
    }

    /**
     * Share exported file
     */
    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when {
                file.name.endsWith(".csv") -> "text/csv"
                file.name.endsWith(".json") -> "application/json"
                else -> "application/octet-stream"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SimpleXray Performance Data")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share Performance Data"))
    }

    /**
     * Format timestamp for human-readable display
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
