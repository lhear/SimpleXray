package com.simplexray.an.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.simplexray.an.db.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat {
    JSON, CSV, JSON_PRETTY
}

data class TimeRange(
    val startMs: Long,
    val endMs: Long = System.currentTimeMillis()
)

class ExportRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).trafficDao()
    private val gson = Gson()
    private val gsonPretty = GsonBuilder().setPrettyPrinting().create()

    /**
     * Export with time range and format selection
     */
    suspend fun export(
        timeRange: TimeRange,
        format: ExportFormat,
        customFilename: String? = null
    ): Uri? {
        // Get all data since start, then filter to end
        val allData = dao.getSince(timeRange.startMs)
        val data = allData.filter { it.timestampMs <= timeRange.endMs }
        
        val (content, extension) = when (format) {
            ExportFormat.JSON -> gson.toJson(data) to "json"
            ExportFormat.JSON_PRETTY -> gsonPretty.toJson(data) to "json"
            ExportFormat.CSV -> {
                val sb = StringBuilder()
                sb.append("timestampMs,uplinkBps,downlinkBps\n")
                for (s in data) sb.append("${s.timestampMs},${s.uplinkBps},${s.downlinkBps}\n")
                sb.toString() to "csv"
            }
        }
        
        val filename = customFilename ?: generateFilename(timeRange, format)
        return writeAndShare(content, "$filename.$extension")
    }

    /**
     * Legacy methods for backwards compatibility
     */
    suspend fun exportJson(lastMillis: Long): Uri? {
        return export(
            TimeRange(System.currentTimeMillis() - lastMillis),
            ExportFormat.JSON
        )
    }

    suspend fun exportCsv(lastMillis: Long): Uri? {
        return export(
            TimeRange(System.currentTimeMillis() - lastMillis),
            ExportFormat.CSV
        )
    }

    /**
     * Generate filename based on time range and format
     */
    private fun generateFilename(timeRange: TimeRange, format: ExportFormat): String {
        val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val dfReadable = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
        val startStr = dfReadable.format(Date(timeRange.startMs))
        val endStr = dfReadable.format(Date(timeRange.endMs))
        
        val base = when {
            timeRange.endMs - timeRange.startMs < 24 * 60 * 60 * 1000L -> // < 24h
                "export_${startStr}_to_${endStr}"
            else -> {
                val duration = (timeRange.endMs - timeRange.startMs) / (24 * 60 * 60 * 1000L)
                "export_last_${duration}_days"
            }
        }
        
        return "${base}_${df.format(Date())}"
    }

    /**
     * Get available time range presets
     */
    fun getTimeRangePresets(): Map<String, TimeRange> {
        val now = System.currentTimeMillis()
        return mapOf(
            "Last Hour" to TimeRange(now - 60 * 60 * 1000L, now),
            "Last 24 Hours" to TimeRange(now - 24 * 60 * 60 * 1000L, now),
            "Last 7 Days" to TimeRange(now - 7 * 24 * 60 * 60 * 1000L, now),
            "Last 30 Days" to TimeRange(now - 30 * 24 * 60 * 60 * 1000L, now),
            "All Time" to TimeRange(0, now)
        )
    }

    private fun writeAndShare(content: String, filename: String): Uri? {
        val file = File(context.cacheDir, filename)
        file.writeText(content)
        return FileProvider.getUriForFile(context, "com.simplexray.an.fileprovider", file)
    }
}


