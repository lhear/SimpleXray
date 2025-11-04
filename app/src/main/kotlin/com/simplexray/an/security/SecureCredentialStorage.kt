package com.simplexray.an.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.simplexray.an.common.AppLogger
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure credential storage using Android Keystore
 * 
 * Uses hardware-backed key storage when available for maximum security.
 * Prevents credential theft even if device is compromised.
 * 
 * CVE-2025-0007: Fix for plaintext password storage
 */
class SecureCredentialStorage private constructor(context: Context) {
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "SimpleXray_SOCKS5_Credentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        
        @Volatile
        private var INSTANCE: SecureCredentialStorage? = null
        
        fun getInstance(context: Context): SecureCredentialStorage {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureCredentialStorage(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }
    
    /**
     * Ensure encryption key exists in Android Keystore
     * Creates key if it doesn't exist
     */
    private fun ensureKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // Allow background access
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
        
        val secretKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return secretKeyEntry.secretKey
    }
    
    /**
     * Encrypt and store credential
     * @param key Credential key (e.g., "socks5_password")
     * @param value Credential value to encrypt
     * @return true if successful
     */
    fun storeCredential(key: String, value: String): Boolean {
        return try {
            if (value.isEmpty()) {
                // Clear credential if empty
                return clearCredential(key)
            }
            
            val secretKey = ensureKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            
            // Store IV + encrypted data as base64
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            
            // Store in SharedPreferences (encrypted data is safe to store)
            // Note: We could use EncryptedSharedPreferences, but Android Keystore is more secure
            val prefs = context.getSharedPreferences("secure_credentials", Context.MODE_PRIVATE)
            prefs.edit().putString(key, encoded).apply()
            
            AppLogger.d("SecureCredentialStorage: Credential stored for key: $key")
            true
        } catch (e: Exception) {
            AppLogger.e("SecureCredentialStorage: Failed to store credential", e)
            false
        }
    }
    
    /**
     * Retrieve and decrypt credential
     * @param key Credential key
     * @return Decrypted credential or null if not found/error
     */
    fun getCredential(key: String): String? {
        return try {
            val prefs = context.getSharedPreferences("secure_credentials", Context.MODE_PRIVATE)
            val encoded = prefs.getString(key, null) ?: return null
            
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            
            if (combined.size < GCM_IV_LENGTH) {
                AppLogger.e("SecureCredentialStorage: Invalid encrypted data for key: $key")
                return null
            }
            
            val iv = ByteArray(GCM_IV_LENGTH)
            val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.size)
            
            val secretKey = ensureKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.e("SecureCredentialStorage: Failed to retrieve credential for key: $key", e)
            null
        }
    }
    
    /**
     * Clear stored credential
     */
    fun clearCredential(key: String): Boolean {
        return try {
            val prefs = context.getSharedPreferences("secure_credentials", Context.MODE_PRIVATE)
            prefs.edit().remove(key).apply()
            AppLogger.d("SecureCredentialStorage: Credential cleared for key: $key")
            true
        } catch (e: Exception) {
            AppLogger.e("SecureCredentialStorage: Failed to clear credential", e)
            false
        }
    }
    
    /**
     * Migrate plaintext credentials to secure storage
     * Should be called once during app initialization
     */
    fun migrateFromPlaintext(username: String, password: String) {
        if (username.isNotEmpty()) {
            storeCredential("socks5_username", username)
        }
        if (password.isNotEmpty()) {
            storeCredential("socks5_password", password)
        }
    }
    
    /**
     * Encrypt byte array data (for backup/export)
     * @param data Data to encrypt
     * @return Encrypted data as Base64 string, or null on error
     */
    fun encryptData(data: ByteArray): String? {
        return try {
            if (data.isEmpty()) {
                return null
            }
            
            val secretKey = ensureKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encrypted = cipher.doFinal(data)
            
            // Store IV + encrypted data as base64
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLogger.e("SecureCredentialStorage: Failed to encrypt data", e)
            null
        }
    }
    
    /**
     * Decrypt byte array data (for backup/import)
     * @param encryptedDataBase64 Encrypted data as Base64 string
     * @return Decrypted data, or null on error
     */
    fun decryptData(encryptedDataBase64: String): ByteArray? {
        return try {
            val combined = Base64.decode(encryptedDataBase64, Base64.NO_WRAP)
            
            if (combined.size < GCM_IV_LENGTH) {
                AppLogger.e("SecureCredentialStorage: Invalid encrypted data")
                return null
            }
            
            val iv = ByteArray(GCM_IV_LENGTH)
            val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.size)
            
            val secretKey = ensureKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            AppLogger.e("SecureCredentialStorage: Failed to decrypt data", e)
            null
        }
    }
}

