package com.simplexray.an.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.common.error.AppError
import com.simplexray.an.common.error.ErrorHandler
import com.simplexray.an.common.error.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Extension function to handle errors in ViewModels with user-friendly messages
 */
fun ViewModel.launchWithErrorHandling(
    onError: (AppError, String) -> Unit,
    block: suspend CoroutineScope.() -> Unit
) {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        val appError = if (throwable is AppError) throwable else throwable.toAppError()
        val userMessage = ErrorHandler.getUserFriendlyMessage(appError)
        ErrorHandler.handleError(appError)
        onError(appError, userMessage)
    }

    viewModelScope.launch(exceptionHandler) {
        try {
            block()
        } catch (e: CancellationException) {
            // Re-throw cancellation to properly handle coroutine cancellation
            throw e
        } catch (e: Throwable) {
            val appError = if (e is AppError) e else e.toAppError()
            val userMessage = ErrorHandler.getUserFriendlyMessage(appError)
            ErrorHandler.handleError(appError)
            onError(appError, userMessage)
        }
    }
}

/**
 * Handle Result type with error callbacks
 */
inline fun <T> Result<T>.onAppError(
    crossinline onError: (AppError, String) -> Unit
): Result<T> {
    this.onFailure { throwable ->
        val appError = if (throwable is AppError) throwable else throwable.toAppError()
        val userMessage = ErrorHandler.getUserFriendlyMessage(appError)
        ErrorHandler.handleError(appError)
        onError(appError, userMessage)
    }
    return this
}
