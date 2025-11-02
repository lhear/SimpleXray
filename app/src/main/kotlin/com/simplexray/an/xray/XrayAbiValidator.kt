package com.simplexray.an.xray

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Validates libxray.so availability and executability across all supported ABIs
 */
object XrayAbiValidator {
    private const val TAG = "XrayAbiValidator"
    private val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    /**
     * Current device ABI
     */
    fun getDeviceAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI
    }

    /**
     * Check if libxray.so exists and is executable for current ABI
     */
    fun validateCurrentAbi(context: Context): ValidationResult {
        val abi = getDeviceAbi()
        val libDir = context.applicationInfo.nativeLibraryDir
            ?: return ValidationResult(false, "No native library directory", null)
        
        val libFile = File(libDir, "libxray.so")
        return validateFile(libFile, abi)
    }

    /**
     * Validate all supported ABIs in the APK
     */
    fun validateAllAbis(context: Context): Map<String, ValidationResult> {
        val results = mutableMapOf<String, ValidationResult>()
        
        // Check current device ABI
        val currentAbi = getDeviceAbi()
        results[currentAbi] = validateCurrentAbi(context)
        
        // Check other ABIs if available in split APKs
        val apkLibDir = context.applicationInfo.nativeLibraryDir ?: return results
        val apkDir = File(apkLibDir).parentFile
        
        for (abi in SUPPORTED_ABIS) {
            if (abi != currentAbi) {
                val abiLibDir = File(apkDir, "lib/$abi")
                val libFile = File(abiLibDir, "libxray.so")
                results[abi] = validateFile(libFile, abi)
            }
        }
        
        return results
    }

    /**
     * Validate a specific file
     */
    private fun validateFile(file: File, abi: String): ValidationResult {
        return when {
            !file.exists() -> {
                ValidationResult(false, "File not found: ${file.absolutePath}", abi)
            }
            !file.canRead() -> {
                ValidationResult(false, "File not readable: ${file.absolutePath}", abi)
            }
            !file.setExecutable(true, false) -> {
                ValidationResult(false, "Cannot set executable permission: ${file.absolutePath}", abi)
            }
            else -> {
                // Verify file is actually an ELF binary (basic check)
                val isValid = try {
                    val header = ByteArray(4)
                    file.inputStream().use { it.read(header) }
                    // ELF magic: 0x7F 'E' 'L' 'F'
                    header[0] == 0x7F.toByte() && 
                    header[1] == 'E'.code.toByte() && 
                    header[2] == 'L'.code.toByte() && 
                    header[3] == 'F'.code.toByte()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read file header for ${file.absolutePath}", e)
                    false
                }
                
                if (isValid) {
                    ValidationResult(true, "Valid executable binary", abi)
                } else {
                    ValidationResult(false, "Invalid ELF binary", abi)
                }
            }
        }
    }

    /**
     * Get validation summary
     */
    fun getValidationSummary(context: Context): String {
        val currentResult = validateCurrentAbi(context)
        val allResults = validateAllAbis(context)
        
        val sb = StringBuilder()
        sb.append("Device ABI: ${getDeviceAbi()}\n")
        sb.append("Current ABI: ${if (currentResult.isValid) "✓ Valid" else "✗ Invalid: ${currentResult.message}"}\n")
        sb.append("\nAll ABIs:\n")
        for ((abi, result) in allResults) {
            val status = if (result.isValid) "✓" else "✗"
            sb.append("  $status $abi: ${result.message}\n")
        }
        
        return sb.toString()
    }

    data class ValidationResult(
        val isValid: Boolean,
        val message: String,
        val abi: String?
    )
}
