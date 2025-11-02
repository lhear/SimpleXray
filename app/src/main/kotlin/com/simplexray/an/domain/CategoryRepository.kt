package com.simplexray.an.domain

import android.content.Context
import com.simplexray.an.config.ApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryRepository(
    private val context: Context,
    externalScope: CoroutineScope? = null
) {
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _distribution = MutableStateFlow<Map<Category, Float>>(emptyMap())
    val distribution: Flow<Map<Category, Float>> = _distribution.asStateFlow()

    fun start() {
        if (ApiConfig.isMock(context)) startMock() else startLive()
    }

    private fun startMock() {
        scope.launch {
            _distribution.emit(
                linkedMapOf(
                    Category.CDN to 0.35f,
                    Category.Social to 0.15f,
                    Category.Gaming to 0.10f,
                    Category.Video to 0.25f,
                    Category.Other to 0.15f
                )
            )
        }
    }

    private fun startLive() {
        scope.launch {
            // Placeholder until domain activity is available.
            _distribution.emit(emptyMap())
        }
    }
}

