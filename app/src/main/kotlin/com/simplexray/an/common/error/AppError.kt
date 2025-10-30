package com.simplexray.an.common.error

sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null
) {
    data class NetworkError(
        override val message: String = "Network connection failed",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class ConfigurationError(
        override val message: String = "Invalid configuration",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class VpnError(
        override val message: String = "VPN connection error",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class FileError(
        override val message: String = "File operation failed",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class ParseError(
        override val message: String = "Failed to parse data",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class PermissionError(
        override val message: String = "Permission denied",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class UnknownError(
        override val message: String = "An unknown error occurred",
        override val cause: Throwable? = null
    ) : AppError(message, cause)
}

fun Throwable.toAppError(): AppError {
    return when (this) {
        is java.net.UnknownHostException,
        is java.net.SocketException,
        is java.net.SocketTimeoutException -> AppError.NetworkError(
            message = this.message ?: "Network error",
            cause = this
        )
        is java.io.IOException -> AppError.FileError(
            message = this.message ?: "File operation failed",
            cause = this
        )
        is SecurityException -> AppError.PermissionError(
            message = this.message ?: "Permission denied",
            cause = this
        )
        is org.json.JSONException,
        is IllegalArgumentException -> AppError.ParseError(
            message = this.message ?: "Failed to parse data",
            cause = this
        )
        else -> AppError.UnknownError(
            message = this.message ?: "An unknown error occurred",
            cause = this
        )
    }
}
