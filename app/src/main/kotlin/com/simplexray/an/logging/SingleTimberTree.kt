package com.simplexray.an.logging

import com.simplexray.an.service.TProxyService
import timber.log.Timber

/**
 * Custom Timber tree that forwards all logs to LoggerRepository.
 * 
 * This tree:
 * - Captures all Timber log calls (from any thread)
 * - Forwards to LoggerRepository for global aggregation
 * - Includes VPN state in all logs
 * - Handles exceptions properly
 * - Works with worker threads
 * 
 * Installation:
 * - Call Timber.plant(SingleTimberTree()) in Application.onCreate()
 */
class SingleTimberTree : Timber.Tree() {
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Convert Android Log priority to LogEvent severity
        val severity = when (priority) {
            android.util.Log.VERBOSE -> LogEvent.Severity.VERBOSE
            android.util.Log.DEBUG -> LogEvent.Severity.DEBUG
            android.util.Log.INFO -> LogEvent.Severity.INFO
            android.util.Log.WARN -> LogEvent.Severity.WARNING
            android.util.Log.ERROR -> LogEvent.Severity.ERROR
            else -> LogEvent.Severity.DEBUG
        }
        
        // Get VPN state
        val vpnState = LoggerRepository.getVpnState()
        
        // Create log event
        val event = LogEvent.create(
            severity = severity,
            tag = tag ?: "Timber",
            message = message,
            throwable = t,
            vpnState = vpnState
        )
        
        // Add to repository (non-blocking, thread-safe)
        LoggerRepository.add(event)
    }
    
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Log everything - LoggerRepository handles filtering
        return true
    }
}

