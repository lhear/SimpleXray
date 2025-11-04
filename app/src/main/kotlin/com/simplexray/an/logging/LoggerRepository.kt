package com.simplexray.an.logging

import android.os.Process
import androidx.annotation.VisibleForTesting
import com.simplexray.an.service.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Global logger repository that maintains logs across process lifecycle.
 * 
 * Features:
 * - Thread-safe log storage with ConcurrentLinkedQueue
 * - Hot SharedFlow with replay buffer (1000 logs)
 * - Process-death resilient (singleton survives Activity recreation)
 * - Supports parallel producers from multiple threads
 * - Automatic backpressure handling (drops oldest when full)
 * - Ring buffer behavior (keeps last N logs)
 * 
 * Usage:
 * - LoggerRepository.add(LogEvent.Info("message", "tag"))
 * - val logs by LoggerRepository.logs.collectAsState()
 */
object LoggerRepository {
    private const val MAX_BUFFER_SIZE = 2000
    private const val REPLAY_BUFFER = 1000
    private const val EXTRA_BUFFER = 1000
    
    // Thread-safe queue for log storage
    private val logBuffer = ConcurrentLinkedQueue<LogEvent>()
    
    // Counter for tracking buffer size (atomic, thread-safe)
    private val bufferSize = AtomicInteger(0)
    
    // Hot SharedFlow - maintains state and survives collectors
    private val _logEvents = MutableSharedFlow<LogEvent>(
        replay = REPLAY_BUFFER,
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    
    val logEvents: SharedFlow<LogEvent> = _logEvents.asSharedFlow()
    
    // Coroutine scope for background operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // VPN state tracking
    @Volatile
    private var vpnState: VpnState = VpnState.DISCONNECTED
    
    enum class VpnState {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
        ERROR
    }
    
    /**
     * Update VPN connection state (called from service)
     */
    fun updateVpnState(state: VpnState) {
        vpnState = state
    }
    
    /**
     * Get current VPN state
     */
    fun getVpnState(): VpnState = vpnState
    
    /**
     * Add a log event to the repository.
     * Thread-safe, can be called from any thread.
     * 
     * @param event Log event to add
     */
    fun add(event: LogEvent) {
        // Add to buffer with size limit (thread-safe using ConcurrentLinkedQueue)
        logBuffer.offer(event)
        val currentSize = bufferSize.incrementAndGet()
        
        // Remove oldest if buffer exceeds limit (ring buffer behavior)
        // Use atomic operations to avoid blocking
        if (currentSize > MAX_BUFFER_SIZE) {
            var removed = 0
            while (bufferSize.get() > MAX_BUFFER_SIZE && logBuffer.poll() != null) {
                removed++
            }
            bufferSize.addAndGet(-removed)
        }
        
        // Emit to SharedFlow (non-blocking, drops oldest if buffer full)
        _logEvents.tryEmit(event)
    }
    
    /**
     * Add a formatted log entry (for backward compatibility with string logs)
     */
    fun addFormatted(
        severity: LogEvent.Severity,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        val event = LogEvent.create(
            severity = severity,
            tag = tag,
            message = message,
            throwable = throwable,
            vpnState = vpnState
        )
        add(event)
    }
    
    /**
     * Add a traffic log event
     */
    fun addTraffic(
        uplink: Long,
        downlink: Long,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val event = LogEvent.Traffic(
            timestamp = timestamp,
            uplink = uplink,
            downlink = downlink,
            vpnState = vpnState
        )
        add(event)
    }
    
    /**
     * Add an instrumentation event (lifecycle, binder, etc.)
     */
    fun addInstrumentation(
        type: LogEvent.InstrumentationType,
        message: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        val event = LogEvent.Instrumentation(
            timestamp = System.currentTimeMillis(),
            type = type,
            message = message,
            data = data,
            vpnState = vpnState
        )
        add(event)
    }
    
    /**
     * Get all logs from buffer (for initial load)
     * Thread-safe, returns snapshot
     */
    fun getAllLogs(): List<LogEvent> {
        // ConcurrentLinkedQueue.toList() creates a snapshot (thread-safe)
        return logBuffer.toList()
    }
    
    /**
     * Clear all logs (for testing or user request)
     */
    fun clear() {
        logBuffer.clear()
        bufferSize.set(0)
    }
    
    /**
     * Get current buffer size
     */
    fun getBufferSize(): Int = bufferSize.get()
}

/**
 * Log event data class with all required metadata
 */
sealed class LogEvent {
    abstract val timestamp: Long
    abstract val vpnState: LoggerRepository.VpnState
    
    enum class Severity {
        VERBOSE,
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        FATAL
    }
    
    enum class InstrumentationType {
        ACTIVITY_LIFECYCLE,
        SERVICE_BINDING,
        BINDER_DEATH,
        BINDER_RECONNECT,
        VPN_TUNNEL_STATE,
        FILE_DESCRIPTOR_ERROR,
        PROCESS_DEATH,
        CONFIG_RELOAD
    }
    
    data class Standard(
        override val timestamp: Long,
        val severity: Severity,
        val tag: String,
        val message: String,
        val threadName: String,
        val pid: Int,
        val tid: Int,
        val throwable: Throwable? = null,
        override val vpnState: LoggerRepository.VpnState
    ) : LogEvent() {
        fun toFormattedString(): String {
            val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val timeStr = dateFormat.format(Date(timestamp))
            val severityChar = when (severity) {
                Severity.VERBOSE -> "V"
                Severity.DEBUG -> "D"
                Severity.INFO -> "I"
                Severity.WARNING -> "W"
                Severity.ERROR -> "E"
                Severity.FATAL -> "F"
            }
            val vpnStateStr = when (vpnState) {
                LoggerRepository.VpnState.CONNECTED -> "[VPN:ON]"
                LoggerRepository.VpnState.DISCONNECTED -> "[VPN:OFF]"
                LoggerRepository.VpnState.CONNECTING -> "[VPN:...]"
                LoggerRepository.VpnState.ERROR -> "[VPN:ERR]"
            }
            val threadInfo = "$pid/$tid"
            val throwableStr = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
            return "$timeStr $threadInfo $severityChar $tag: $vpnStateStr $message$throwableStr"
        }
    }
    
    data class Traffic(
        override val timestamp: Long,
        val uplink: Long,
        val downlink: Long,
        override val vpnState: LoggerRepository.VpnState
    ) : LogEvent() {
        fun toFormattedString(): String {
            val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val timeStr = dateFormat.format(Date(timestamp))
            val vpnStateStr = when (vpnState) {
                LoggerRepository.VpnState.CONNECTED -> "[VPN:ON]"
                LoggerRepository.VpnState.DISCONNECTED -> "[VPN:OFF]"
                LoggerRepository.VpnState.CONNECTING -> "[VPN:...]"
                LoggerRepository.VpnState.ERROR -> "[VPN:ERR]"
            }
            val upKB = String.format(Locale.US, "%.2f", uplink / 1024.0)
            val downKB = String.format(Locale.US, "%.2f", downlink / 1024.0)
            return "$timeStr $vpnStateStr TRAFFIC: UP=${upKB}KB DOWN=${downKB}KB"
        }
    }
    
    data class Instrumentation(
        override val timestamp: Long,
        val type: InstrumentationType,
        val message: String,
        val data: Map<String, Any?> = emptyMap(),
        override val vpnState: LoggerRepository.VpnState
    ) : LogEvent() {
        fun toFormattedString(): String {
            val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val timeStr = dateFormat.format(Date(timestamp))
            val vpnStateStr = when (vpnState) {
                LoggerRepository.VpnState.CONNECTED -> "[VPN:ON]"
                LoggerRepository.VpnState.DISCONNECTED -> "[VPN:OFF]"
                LoggerRepository.VpnState.CONNECTING -> "[VPN:...]"
                LoggerRepository.VpnState.ERROR -> "[VPN:ERR]"
            }
            val dataStr = if (data.isNotEmpty()) {
                " ${data.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
            } else {
                ""
            }
            return "$timeStr $vpnStateStr [${type.name}] $message$dataStr"
        }
    }
    
    companion object {
        /**
         * Create a standard log event with current thread/process info
         */
        fun create(
            severity: Severity,
            tag: String,
            message: String,
            throwable: Throwable? = null,
            vpnState: LoggerRepository.VpnState = LoggerRepository.getVpnState()
        ): Standard {
            val thread = Thread.currentThread()
            return Standard(
                timestamp = System.currentTimeMillis(),
                severity = severity,
                tag = tag,
                message = message,
                threadName = thread.name,
                pid = Process.myPid(),
                tid = Process.myTid(),
                throwable = throwable,
                vpnState = vpnState
            )
        }
    }
    
    /**
     * Convert to formatted string for display
     */
    fun toFormattedString(): String = when (this) {
        is Standard -> toFormattedString()
        is Traffic -> toFormattedString()
        is Instrumentation -> toFormattedString()
    }
}

