package com.simplexray.an.performance

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplexray.an.common.AppLogger
import com.simplexray.an.performance.model.PerformanceConfig
import com.simplexray.an.performance.model.PerformanceProfile
import com.simplexray.an.prefs.Preferences
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Custom Performance Profile Manager
 * Manages user-defined performance profiles
 * 
 * TODO: Add profile validation to ensure configuration compatibility
 * TODO: Implement profile versioning for backward compatibility
 * TODO: Add profile sharing functionality (export/import via QR code)
 * TODO: Consider adding profile templates for common use cases
 */
class CustomProfileManager(private val context: Context) {
    
    data class CustomProfile(
        val id: String,
        val name: String,
        val description: String,
        val config: PerformanceConfig,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val category: String = "Custom"
    )
    
    private val gson = Gson()
    private val profilesDir = File(context.filesDir, "custom_profiles")
    private val profilesFile = File(profilesDir, "profiles.json")
    
    init {
        if (!profilesDir.exists()) {
            profilesDir.mkdirs()
        }
    }
    
    /**
     * Get all custom profiles
     * TODO: Add profile sorting and filtering options
     * TODO: Consider adding profile caching to reduce file I/O
     */
    fun getAllProfiles(): List<CustomProfile> {
        return try {
            if (!profilesFile.exists()) {
                return emptyList()
            }
            
            val json = profilesFile.readText()
            val type = object : TypeToken<List<CustomProfile>>() {}.type
            // TODO: Add JSON schema validation for profile data integrity
            gson.fromJson<List<CustomProfile>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            AppLogger.e("$TAG: Failed to load profiles", e)
            emptyList()
        }
    }
    
    /**
     * Get profile by ID
     */
    fun getProfile(id: String): CustomProfile? {
        return getAllProfiles().find { it.id == id }
    }
    
    /**
     * Save a custom profile
     * TODO: Add profile name uniqueness validation
     * TODO: Implement atomic file write to prevent data corruption
     */
    fun saveProfile(profile: CustomProfile): Boolean {
        return try {
            val profiles = getAllProfiles().toMutableList()
            val existingIndex = profiles.indexOfFirst { it.id == profile.id }
            
            // TODO: Add profile validation before saving
            val updatedProfile = if (existingIndex >= 0) {
                profile.copy(updatedAt = System.currentTimeMillis())
            } else {
                profile
            }
            
            if (existingIndex >= 0) {
                profiles[existingIndex] = updatedProfile
            } else {
                profiles.add(updatedProfile)
            }
            
            saveProfiles(profiles)
            AppLogger.d("$TAG: Profile saved: ${profile.name}")
            true
        } catch (e: Exception) {
            AppLogger.e("$TAG: Failed to save profile", e)
            false
        }
    }
    
    /**
     * Delete a custom profile
     */
    fun deleteProfile(id: String): Boolean {
        return try {
            val profiles = getAllProfiles().toMutableList()
            val removed = profiles.removeAll { it.id == id }
            
            if (removed) {
                saveProfiles(profiles)
                AppLogger.d("$TAG: Profile deleted: $id")
            }
            
            removed
        } catch (e: Exception) {
            AppLogger.e("$TAG: Failed to delete profile", e)
            false
        }
    }
    
    /**
     * Duplicate a profile
     */
    fun duplicateProfile(id: String, newName: String): CustomProfile? {
        val original = getProfile(id) ?: return null
        
        val duplicated = original.copy(
            id = "${id}_copy_${System.currentTimeMillis()}",
            name = newName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        return if (saveProfile(duplicated)) duplicated else null
    }
    
    /**
     * Export profile to JSON
     */
    fun exportProfile(profile: CustomProfile): String {
        return gson.toJson(profile)
    }
    
    /**
     * Import profile from JSON
     */
    fun importProfile(json: String): CustomProfile? {
        return try {
            val profile = gson.fromJson(json, CustomProfile::class.java)
            // Generate new ID to avoid conflicts
            val imported = profile.copy(
                id = "imported_${System.currentTimeMillis()}",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            if (saveProfile(imported)) imported else null
        } catch (e: Exception) {
            AppLogger.e("$TAG: Failed to import profile", e)
            null
        }
    }
    
    /**
     * Export all profiles to JSON
     */
    fun exportAllProfiles(): String {
        return gson.toJson(getAllProfiles())
    }
    
    /**
     * Import multiple profiles from JSON
     */
    fun importProfiles(json: String): Int {
        return try {
            val type = object : TypeToken<List<CustomProfile>>() {}.type
            val profiles = gson.fromJson<List<CustomProfile>>(json, type) ?: emptyList()
            
            var importedCount = 0
            profiles.forEach { profile ->
                val imported = profile.copy(
                    id = "imported_${System.currentTimeMillis()}_${importedCount}",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                if (saveProfile(imported)) {
                    importedCount++
                }
            }
            
            importedCount
        } catch (e: Exception) {
            AppLogger.e("$TAG: Failed to import profiles", e)
            0
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
    ): CustomProfile {
        val baseConfig = baseProfile.config
        val newConfig = PerformanceConfig(
            bufferSize = configOverrides["bufferSize"] as? Int ?: baseConfig.bufferSize,
            connectionTimeout = (configOverrides["connectionTimeout"] as? Number)?.toInt() ?: baseConfig.connectionTimeout,
            handshakeTimeout = (configOverrides["handshakeTimeout"] as? Number)?.toInt() ?: baseConfig.handshakeTimeout,
            idleTimeout = (configOverrides["idleTimeout"] as? Number)?.toInt() ?: baseConfig.idleTimeout,
            tcpFastOpen = configOverrides["tcpFastOpen"] as? Boolean ?: baseConfig.tcpFastOpen,
            tcpNoDelay = configOverrides["tcpNoDelay"] as? Boolean ?: baseConfig.tcpNoDelay,
            keepAlive = configOverrides["keepAlive"] as? Boolean ?: baseConfig.keepAlive,
            keepAliveInterval = (configOverrides["keepAliveInterval"] as? Number)?.toInt() ?: baseConfig.keepAliveInterval,
            dnsCacheTtl = (configOverrides["dnsCacheTtl"] as? Number)?.toInt() ?: baseConfig.dnsCacheTtl,
            dnsPrefetch = configOverrides["dnsPrefetch"] as? Boolean ?: baseConfig.dnsPrefetch,
            parallelConnections = (configOverrides["parallelConnections"] as? Number)?.toInt() ?: baseConfig.parallelConnections,
            enableCompression = configOverrides["enableCompression"] as? Boolean ?: baseConfig.enableCompression,
            enableMultiplexing = configOverrides["enableMultiplexing"] as? Boolean ?: baseConfig.enableMultiplexing,
            statsUpdateInterval = (configOverrides["statsUpdateInterval"] as? Number)?.toLong() ?: baseConfig.statsUpdateInterval,
            logLevel = configOverrides["logLevel"] as? String ?: baseConfig.logLevel
        )
        
        return CustomProfile(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            description = description,
            config = newConfig,
            category = "Custom"
        )
    }
    
    /**
     * Save profiles list to file
     */
    private fun saveProfiles(profiles: List<CustomProfile>) {
        profilesFile.writeText(gson.toJson(profiles))
    }
    
    companion object {
        private const val TAG = "CustomProfileManager"
    }
}

