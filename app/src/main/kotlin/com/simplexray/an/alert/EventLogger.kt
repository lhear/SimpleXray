package com.simplexray.an.alert

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Event logger for alert system
 */
object EventLogger {
    private const val TAG = "EventLogger"
    private const val MAX_EVENTS_IN_MEMORY = 1000
    private val events = ConcurrentLinkedQueue<AlertEvent>()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class AlertEvent(
        val timestamp: Long = System.currentTimeMillis(),
        val type: EventType,
        val severity: Severity,
        val message: String,
        val data: Map<String, Any?> = emptyMap()
    ) {
        enum class EventType {
            BURST,
            THROTTLE,
            CDN_SPIKE,
            SUSPICIOUS_PATTERN,
            ERROR,
            INFO
        }

        enum class Severity {
            LOW,
            MEDIUM,
            HIGH,
            CRITICAL
        }
    }

    /**
     * Log an event
     */
    fun log(
        type: AlertEvent.EventType,
        severity: AlertEvent.Severity,
        message: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        val event = AlertEvent(
            type = type,
            severity = severity,
            message = message,
            data = data
        )
        events.offer(event)
        
        // Prune if too many events
        while (events.size > MAX_EVENTS_IN_MEMORY) {
            events.poll()
        }
        
        Log.d(TAG, "[${severity.name}] $type: $message")
    }

    /**
     * Get recent events
     */
    fun getRecentEvents(count: Int = 100): List<AlertEvent> {
        return events.toList().takeLast(count)
    }

    /**
     * Get events in time range
     */
    fun getEventsInRange(startMs: Long, endMs: Long): List<AlertEvent> {
        return events.filter { it.timestamp in startMs..endMs }
    }

    /**
     * Export events to file
     */
    fun exportToFile(context: Context, format: ExportFormat = ExportFormat.JSON): File? {
        return try {
            val file = File(context.filesDir, "alert_events.${format.extension}")
            val content = when (format) {
                ExportFormat.JSON -> gson.toJson(events.toList())
                ExportFormat.CSV -> {
                    val sb = StringBuilder()
                    sb.append("timestamp,type,severity,message,data\n")
                    events.forEach { e ->
                        sb.append("${e.timestamp},${e.type},${e.severity},${e.message.replace(",", ";")},\"${e.data}\"\n")
                    }
                    sb.toString()
                }
            }
            file.writeText(content)
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export events", e)
            null
        }
    }

    /**
     * Clear all events
     */
    fun clear() {
        events.clear()
    }

    /**
     * Get statistics
     */
    fun getStatistics(): EventStatistics {
        val now = System.currentTimeMillis()
        val last24h = events.filter { now - it.timestamp < 24 * 60 * 60 * 1000L }
        
        return EventStatistics(
            totalEvents = events.size,
            last24hCount = last24h.size,
            byType = events.groupBy { it.type }.mapValues { it.value.size },
            bySeverity = events.groupBy { it.severity }.mapValues { it.value.size }
        )
    }

    data class EventStatistics(
        val totalEvents: Int,
        val last24hCount: Int,
        val byType: Map<AlertEvent.EventType, Int>,
        val bySeverity: Map<AlertEvent.Severity, Int>
    )

    enum class ExportFormat(val extension: String) {
        JSON("json"),
        CSV("csv")
    }
}
