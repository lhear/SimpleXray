package com.simplexray.an.common

import android.util.Log
import java.util.ArrayDeque
import com.simplexray.an.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Centralized logging utility for the SimpleXray application.
 * 
 * All logs are automatically disabled in release builds to prevent
 * exposing sensitive information and improve performance.
 * 
 * In production builds, errors and exceptions are automatically sent to
 * Firebase Crashlytics for monitoring and debugging.
 * 
 * Usage:
 * - AppLogger.d("Debug message")
 * - AppLogger.e("Error message", exception)
 * - AppLogger.w("Warning message")
 * - AppLogger.i("Info message")
 * - AppLogger.v("Verbose message")
 */
object AppLogger {
    const val LOG_TAG = "SimpleXray"
    
    // Rate limiting configuration
    private const val RATE_LIMIT_INTERVAL_MS = 5000L // 5 seconds
    private const val RATE_LIMIT_MAX_IDENTICAL = 10 // Max identical logs per interval
    private const val RING_BUFFER_SIZE = 100
    
    // Ring buffer for rate limiting
    private val logRingBuffer = ArrayDeque<LogEntry>(RING_BUFFER_SIZE)
    private val logCounts = mutableMapOf<String, Int>() // message -> count
    private var lastCleanupTime = System.currentTimeMillis()
    
    /**
     * Log entry for ring buffer
     */
    private data class LogEntry(
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Check if log should be rate-limited
     */
    private fun shouldRateLimit(message: String): Boolean {
        val now = System.currentTimeMillis()
        
        // Cleanup old entries periodically
        if (now - lastCleanupTime > RATE_LIMIT_INTERVAL_MS) {
            logRingBuffer.removeIf { now - it.timestamp > RATE_LIMIT_INTERVAL_MS }
            logCounts.clear()
            logRingBuffer.forEach { entry ->
                logCounts[entry.message] = (logCounts[entry.message] ?: 0) + 1
            }
            lastCleanupTime = now
        }
        
        val count = logCounts[message] ?: 0
        if (count >= RATE_LIMIT_MAX_IDENTICAL) {
            return true // Rate limit exceeded
        }
        
        // Add to ring buffer
        logRingBuffer.addLast(LogEntry(message))
        if (logRingBuffer.size > RING_BUFFER_SIZE) {
            logRingBuffer.removeFirst()
        }
        logCounts[message] = count + 1
        
        return false
    }
    
    /**
     * Firebase Crashlytics instance (null if not configured).
     * Lazy initialization to avoid crashes if Firebase is not set up.
     */
    private val crashlytics: FirebaseCrashlytics? by lazy(LazyThreadSafetyMode.NONE) {
        try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "Firebase Crashlytics not configured", e)
            }
            null
        }
    }

    /**
     * Log a debug message.
     * Only logs in debug builds.
     */
    @JvmStatic
    inline fun d(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, message())
        }
    }
    
    @JvmStatic
    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            if (shouldRateLimit(message)) {
                // Collapse into summary if rate limit exceeded
                val count = logCounts[message] ?: 0
                Log.d(LOG_TAG, "[RATE-LIMITED] $message (occurred ${count + 1} times in last ${RATE_LIMIT_INTERVAL_MS}ms)")
            } else {
                Log.d(LOG_TAG, message)
            }
        }
    }

    /**
     * Log an error message.
     * Only logs in debug builds.
     * In production, errors are sent to Firebase Crashlytics for monitoring.
     * 
     * @param message Error message
     * @param throwable Optional exception to log
     */
    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(LOG_TAG, message, throwable)
            } else {
                Log.e(LOG_TAG, message)
            }
        } else {
            // In production, send to Firebase Crashlytics
            try {
                crashlytics?.let { firebaseCrashlytics ->
                    firebaseCrashlytics.log("ERROR: $message")
                    if (throwable != null) {
                        firebaseCrashlytics.recordException(throwable)
                    } else {
                        // Create a custom exception for non-throwable errors
                        firebaseCrashlytics.recordException(Exception(message))
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "Failed to send error to Crashlytics", e)
                }
            }
        }
    }

    /**
     * Log a warning message.
     * Only logs in debug builds.
     * In production, warnings with throwables are sent to Crashlytics as non-fatal.
     */
    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                if (shouldRateLimit(message)) {
                    val count = logCounts[message] ?: 0
                    Log.w(LOG_TAG, "[RATE-LIMITED] $message (occurred ${count + 1} times)", throwable)
                } else {
                    Log.w(LOG_TAG, message, throwable)
                }
            } else {
                Log.w(LOG_TAG, message)
            }
        } else if (throwable != null) {
            // In production, send warnings with exceptions to Crashlytics as non-fatal
            try {
                crashlytics?.let { firebaseCrashlytics ->
                    firebaseCrashlytics.log("WARNING: $message")
                    firebaseCrashlytics.recordException(throwable)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "Failed to send warning to Crashlytics", e)
                }
            }
        }
    }

    /**
     * Log an info message.
     * Only logs in debug builds.
     */
    fun i(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(LOG_TAG, message)
        }
    }

    /**
     * Log a verbose message.
     * Only logs in debug builds.
     */
    fun v(message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(LOG_TAG, message)
        }
    }
    
    /**
     * Set a custom key-value pair for Crashlytics reporting.
     * This helps with debugging by adding context to crash reports.
     * 
     * @param key Custom key name
     * @param value Custom value
     */
    fun setCustomKey(key: String, value: String) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (e: Exception) {
            // Fail silently
        }
    }
    
    /**
     * Set a custom key-value pair for Crashlytics reporting (boolean).
     */
    fun setCustomKey(key: String, value: Boolean) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (e: Exception) {
            // Fail silently
        }
    }
    
    /**
     * Set a custom key-value pair for Crashlytics reporting (int).
     */
    fun setCustomKey(key: String, value: Int) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (e: Exception) {
            // Fail silently
        }
    }
    
    /**
     * Set user identifier for Crashlytics.
     * This helps identify which users are affected by crashes.
     * 
     * @param userId User identifier (should be anonymized for privacy)
     */
    fun setUserId(userId: String) {
        try {
            crashlytics?.setUserId(userId)
        } catch (e: Exception) {
            // Fail silently
        }
    }
    
    /**
     * Add a breadcrumb log to Crashlytics.
     * Breadcrumbs help track the sequence of events leading to a crash.
     * 
     * @param message Breadcrumb message
     */
    fun addBreadcrumb(message: String) {
        try {
            crashlytics?.log(message)
        } catch (e: Exception) {
            // Fail silently
        }
    }
}

