package com.simplexray.an.viewmodel

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.common.AppLogger
import com.simplexray.an.performance.CustomProfileManager
import com.simplexray.an.performance.model.PerformanceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ViewModel for Custom Performance Profiles
 */
class CustomProfileViewModel(application: Application) : AndroidViewModel(application) {
    
    private val profileManager = CustomProfileManager(application)
    
    private val _profiles = MutableStateFlow<List<CustomProfileManager.CustomProfile>>(emptyList())
    val profiles: StateFlow<List<CustomProfileManager.CustomProfile>> = _profiles.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    init {
        loadProfiles()
    }
    
    /**
     * Load all custom profiles
     */
    fun loadProfiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    val loadedProfiles = profileManager.getAllProfiles()
                    _profiles.value = loadedProfiles
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to load profiles", e)
                _errorMessage.value = "Failed to load profiles: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Save a custom profile (create or update)
     */
    fun saveProfile(profile: CustomProfileManager.CustomProfile) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    val success = profileManager.saveProfile(profile)
                    if (success) {
                        loadProfiles()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                getApplication(),
                                "Profile saved successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        throw Exception("Failed to save profile")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to save profile", e)
                _errorMessage.value = "Failed to save profile: ${e.message}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Failed to save profile: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Delete a custom profile
     */
    fun deleteProfile(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    val success = profileManager.deleteProfile(id)
                    if (success) {
                        loadProfiles()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                getApplication(),
                                "Profile deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        throw Exception("Failed to delete profile")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to delete profile", e)
                _errorMessage.value = "Failed to delete profile: ${e.message}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Failed to delete profile: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Duplicate a profile
     */
    fun duplicateProfile(id: String, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    val duplicated = profileManager.duplicateProfile(id, newName)
                    if (duplicated != null) {
                        loadProfiles()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                getApplication(),
                                "Profile duplicated",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        throw Exception("Failed to duplicate profile")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to duplicate profile", e)
                _errorMessage.value = "Failed to duplicate profile: ${e.message}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Failed to duplicate profile: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Export profile to JSON string
     */
    fun exportProfile(profile: CustomProfileManager.CustomProfile): String {
        return profileManager.exportProfile(profile)
    }
    
    /**
     * Export all profiles
     */
    fun exportAllProfiles(): String {
        return profileManager.exportAllProfiles()
    }
    
    /**
     * Import profile from JSON string
     */
    fun importProfile(json: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    val imported = profileManager.importProfile(json)
                    if (imported != null) {
                        loadProfiles()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                getApplication(),
                                "Profile imported successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        throw Exception("Failed to import profile")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to import profile", e)
                _errorMessage.value = "Failed to import profile: ${e.message}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Failed to import profile: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Import multiple profiles from JSON
     */
    fun importProfiles(json: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    val count = profileManager.importProfiles(json)
                    if (count > 0) {
                        loadProfiles()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                getApplication(),
                                "Imported $count profile(s)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        throw Exception("No profiles imported")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Failed to import profiles", e)
                _errorMessage.value = "Failed to import profiles: ${e.message}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Failed to import profiles: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Create profile from base profile
     */
    fun createFromBase(
        baseProfile: PerformanceProfile,
        name: String,
        description: String,
        configOverrides: Map<String, Any> = emptyMap()
    ): CustomProfileManager.CustomProfile {
        return profileManager.createFromBase(baseProfile, name, description, configOverrides)
    }
    
    /**
     * Get profile by ID
     */
    fun getProfile(id: String): CustomProfileManager.CustomProfile? {
        return profileManager.getProfile(id)
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}


