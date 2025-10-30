package com.simplexray.an.common.error

import android.util.Log

object ErrorHandler {
    private const val TAG = "ErrorHandler"

    fun handleError(error: AppError, tag: String = TAG): String {
        val errorMessage = formatErrorMessage(error)
        Log.e(tag, errorMessage, error.cause)
        return errorMessage
    }

    fun formatErrorMessage(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> "Network Error: ${error.message}"
            is AppError.ConfigurationError -> "Configuration Error: ${error.message}"
            is AppError.VpnError -> "VPN Error: ${error.message}"
            is AppError.FileError -> "File Error: ${error.message}"
            is AppError.ParseError -> "Parse Error: ${error.message}"
            is AppError.PermissionError -> "Permission Error: ${error.message}"
            is AppError.UnknownError -> "Error: ${error.message}"
        }
    }

    fun getUserFriendlyMessage(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> "Unable to connect to the network. Please check your internet connection."
            is AppError.ConfigurationError -> "Invalid configuration. Please check your settings."
            is AppError.VpnError -> "Failed to establish VPN connection. Please try again."
            is AppError.FileError -> "Unable to read or write file. Please check permissions."
            is AppError.ParseError -> "Invalid data format. Please check the configuration."
            is AppError.PermissionError -> "Permission denied. Please grant the required permissions."
            is AppError.UnknownError -> "Something went wrong. Please try again later."
        }
    }
}

inline fun <T> runCatchingWithError(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(e.toAppError())
    }
}

suspend inline fun <T> runSuspendCatchingWithError(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(e.toAppError())
    }
}
