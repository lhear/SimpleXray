package com.simplexray.an.common.error

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val error: AppError, val userMessage: String = ErrorHandler.getUserFriendlyMessage(error)) : UiState<Nothing>()
    object Idle : UiState<Nothing>()
}

fun <T> Result<T>.toUiState(): UiState<T> {
    return fold(
        onSuccess = { UiState.Success(it) },
        onFailure = { error ->
            val appError = if (error is AppError) error else error.toAppError()
            UiState.Error(appError)
        }
    )
}
