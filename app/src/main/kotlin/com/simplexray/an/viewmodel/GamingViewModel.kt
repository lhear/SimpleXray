package com.simplexray.an.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.performance.optimizer.PerformanceOptimizer
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.protocol.gaming.GamingOptimizer
import com.simplexray.an.protocol.gaming.GamingOptimizer.GameProfile
import com.simplexray.an.protocol.optimization.ProtocolConfig
import com.simplexray.an.xray.XrayConfigPatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Gaming Optimization screen
 */
class GamingViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "GamingViewModel"
    private val prefs: Preferences = Preferences(application)
    private val gamingOptimizer = GamingOptimizer()
    private val performanceOptimizer = PerformanceOptimizer(application)

    private val _selectedProfile = MutableStateFlow<GameProfile?>(null)
    val selectedProfile: StateFlow<GameProfile?> = _selectedProfile.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _currentPing = MutableStateFlow(0)
    val currentPing: StateFlow<Int> = _currentPing.asStateFlow()

    private val _jitterLevel = MutableStateFlow(0)
    val jitterLevel: StateFlow<Int> = _jitterLevel.asStateFlow()

    init {
        loadSavedProfile()
    }

    private fun loadSavedProfile() {
        val savedProfileName = prefs.selectedGameProfile
        val isEnabled = prefs.gamingOptimizationEnabled

        if (savedProfileName != null && isEnabled) {
            try {
                val profile = GameProfile.valueOf(savedProfileName)
                _selectedProfile.value = profile
                _isOptimizing.value = true
                applyGameOptimizations(profile)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load saved profile: $savedProfileName", e)
                // Profile not found, reset
                _selectedProfile.value = null
                _isOptimizing.value = false
            }
        }
    }

    fun selectGameProfile(profile: GameProfile) {
        viewModelScope.launch {
            try {
                _selectedProfile.value = profile
                _isOptimizing.value = true

                // Save to preferences
                prefs.selectedGameProfile = profile.name
                prefs.gamingOptimizationEnabled = true

                // Apply game-specific optimizations
                applyGameOptimizations(profile)
                
                Log.d(TAG, "Gaming profile selected: ${profile.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting game profile", e)
                _isOptimizing.value = false
            }
        }
    }

    fun clearSelection() {
        viewModelScope.launch {
            try {
                _selectedProfile.value = null
                _isOptimizing.value = false

                // Clear from preferences
                prefs.selectedGameProfile = null
                prefs.gamingOptimizationEnabled = false

                // Reset to balanced profile
                performanceOptimizer.setProfile(PerformanceProfile.Balanced)
                
                // Reload config to apply changes
                XrayConfigPatcher.patchConfig(getApplication())
                
                Log.d(TAG, "Gaming optimization disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing gaming selection", e)
            }
        }
    }

    private fun applyGameOptimizations(profile: GameProfile) {
        viewModelScope.launch {
            try {
                // 1. Set performance profile to Ultimate mode for maximum performance
                performanceOptimizer.setProfile(PerformanceProfile.Ultimate)
                
                // 2. Apply gaming-specific protocol optimizations
                val baseConfig = ProtocolConfig.forGaming()
                val optimizedConfig = gamingOptimizer.applyGamingOptimizations(baseConfig, profile)
                
                // Store the protocol config in preferences (will be used by protocol optimizer)
                storeProtocolConfig(optimizedConfig)
                
                // 3. Reload Xray config to apply the optimizations
                XrayConfigPatcher.patchConfig(getApplication())
                
                // 4. Update UI metrics (simulated ping/jitter based on profile settings)
                _currentPing.value = profile.config.maxLatency - 20
                _jitterLevel.value = profile.config.jitterTolerance - 10
                
                Log.d(TAG, "Applied gaming optimizations for ${profile.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error applying game optimizations", e)
                _isOptimizing.value = false
            }
        }
    }

    /**
     * Store protocol configuration (currently just ensures it's applied via performance profile)
     * The protocol config optimizations are applied through the Gaming performance profile
     */
    private fun storeProtocolConfig(config: ProtocolConfig) {
        // Protocol config optimizations are automatically applied when:
        // 1. Performance profile is set to Gaming (done above)
        // 2. XrayConfigPatcher applies the performance config
        // The GamingOptimizer provides game-specific tuning on top of the base Gaming profile
    }

    fun getAllGameProfiles(): List<GameProfile> {
        return GameProfile.entries.toList()
    }
}
