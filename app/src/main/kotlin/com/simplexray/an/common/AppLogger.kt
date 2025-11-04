package com.simplexray.an.common

import android.util.Log
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
    private const val LOG_TAG = "SimpleXray"
    
    /**
     * Firebase Crashlytics instance (null if not configured).
     * Lazy initialization to avoid crashes if Firebase is not set up.
     */
    private val crashlytics: FirebaseCrashlytics? by lazy {
        try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            // Firebase not configured, that's okay
            null
        }
    }

    /**
     * Log a debug message.
     * Only logs in debug builds.
     */
    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, message)
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
                // Fail silently to avoid crashes from crash reporting
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
                Log.w(LOG_TAG, message, throwable)
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
                // Fail silently to avoid crashes from crash reporting
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

