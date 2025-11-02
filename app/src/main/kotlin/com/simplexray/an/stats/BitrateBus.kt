package com.simplexray.an.stats

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object BitrateBus {
    private val _flow = MutableSharedFlow<BitratePoint>(replay = 1)
    val flow = _flow.asSharedFlow()

    suspend fun emit(p: BitratePoint) {
        _flow.emit(p)
    }
}

