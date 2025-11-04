package com.simplexray.an.common

import android.util.Log
import com.simplexray.an.BuildConfig

/**
 * Centralized logging utility for the SimpleXray application.
 * 
 * All logs are automatically disabled in release builds to prevent
 * exposing sensitive information and improve performance.
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
     * In production builds, errors are silently handled to prevent
     * exposing sensitive information. For production crash reporting,
     * integrate with Firebase Crashlytics or similar service.
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
        }
        // Production: Errors are logged only in debug builds
        // To enable crash reporting, integrate Firebase Crashlytics:
        // if (!BuildConfig.DEBUG && throwable != null) {
        //     FirebaseCrashlytics.getInstance().recordException(throwable)
        // }
    }

    /**
     * Log a warning message.
     * Only logs in debug builds.
     */
    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w(LOG_TAG, message, throwable)
            } else {
                Log.w(LOG_TAG, message)
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
}

