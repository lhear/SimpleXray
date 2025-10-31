package com.simplexray.an.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.protocol.gaming.GamingOptimizer
import com.simplexray.an.protocol.gaming.GamingOptimizer.GameProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Gaming Optimization screen
 */
class GamingViewModel : ViewModel() {

    private val gamingOptimizer = GamingOptimizer()

    private val _selectedProfile = MutableStateFlow<GameProfile?>(null)
    val selectedProfile: StateFlow<GameProfile?> = _selectedProfile.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _currentPing = MutableStateFlow(0)
    val currentPing: StateFlow<Int> = _currentPing.asStateFlow()

    private val _jitterLevel = MutableStateFlow(0)
    val jitterLevel: StateFlow<Int> = _jitterLevel.asStateFlow()

    fun selectGameProfile(profile: GameProfile) {
        viewModelScope.launch {
            _selectedProfile.value = profile
            _isOptimizing.value = true

            // Apply game-specific optimizations
            applyGameOptimizations(profile)
        }
    }

    fun clearSelection() {
        _selectedProfile.value = null
        _isOptimizing.value = false
    }

    private fun applyGameOptimizations(profile: GameProfile) {
        // Here you would actually apply the optimizations to the network stack
        // For now, we'll just set the profile as active
        viewModelScope.launch {
            // Simulate applying optimizations
            _currentPing.value = profile.config.maxLatency - 20
            _jitterLevel.value = profile.config.jitterTolerance - 10
        }
    }

    fun getAllGameProfiles(): List<GameProfile> {
        return GameProfile.entries.toList()
    }
}
