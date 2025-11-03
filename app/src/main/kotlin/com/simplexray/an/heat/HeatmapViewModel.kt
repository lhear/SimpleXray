package com.simplexray.an.heat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HeatmapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HeatmapRepository(app, viewModelScope)
    val grid: StateFlow<List<List<Float>>> = repo.grid
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { repo.start() }
    
    override fun onCleared() {
        super.onCleared()
        repo.stop()
    }
}

