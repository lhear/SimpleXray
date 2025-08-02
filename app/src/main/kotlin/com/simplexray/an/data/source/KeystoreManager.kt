package com.simplexray.an.data.source

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import com.simplexray.an.prefs.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreManager(
    private val appContext: Context,
    private val prefs: Preferences
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private var masterKeyGenerated = false
    private var cachedDataKey: SecretKey? = null

    @Synchronized
    private fun createMasterKeyIfNotFound() {
        if (masterKeyGenerated) return
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            masterKeyGenerated = true
            return
        }
        masterKeyGenerated = createMasterKey(hasStrongBoxFeature())
    }

    private fun createMasterKey(strongBoxAvailable: Boolean): Boolean {
        return try {
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUnlockedDeviceRequired(true)
                .setIsStrongBoxBacked(strongBoxAvailable)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()

            Log.d(TAG, "Master key generated: $MASTER_KEY_ALIAS")
            true
        } catch (e: StrongBoxUnavailableException) {
            Log.w(TAG, "StrongBox is unavailable", e)
            createMasterKey(false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create master key", e)
            cleanupMasterKey()
            false
        }
    }

    private fun cleanupMasterKey() {
        try {
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        } catch (ignore: Exception) {
        }
    }

    private fun getMasterSecretKey(): SecretKey? {
        return try {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Error getting master key from Keystore", e)
            null
        }
    }

    @Synchronized
    private fun generateOrRestoreDataKey(): SecretKey? {
        cachedDataKey?.let { return it }
        createMasterKeyIfNotFound()
        val masterKey = getMasterSecretKey() ?: return null
        val encryptedKeyStr = prefs.encryptedDataKey
        if (encryptedKeyStr.isNotEmpty()) {
            try {
                val encryptedKey = Base64.getDecoder().decode(encryptedKeyStr)
                cachedDataKey = unwrapKey(encryptedKey, masterKey)
                return cachedDataKey
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode or unwrap data key", e)
                return null
            }
        }

        return try {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val newDataKey = keyGenerator.generateKey()
            val wrapped = wrapKey(newDataKey, masterKey)
            prefs.encryptedDataKey = Base64.getEncoder().encodeToString(wrapped)
            cachedDataKey = newDataKey
            Log.d(TAG, "New data encryption key generated and wrapped.")
            newDataKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate or wrap data key", e)
            null
        }
    }

    private fun wrapKey(dataKey: SecretKey, masterKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.WRAP_MODE, masterKey)
        val wrapped = cipher.wrap(dataKey)
        return cipher.iv + wrapped
    }

    private fun unwrapKey(wrappedBytes: ByteArray, masterKey: SecretKey): SecretKey? {
        val iv = wrappedBytes.copyOfRange(0, IV_SIZE)
        val wrapped = wrappedBytes.copyOfRange(IV_SIZE, wrappedBytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.UNWRAP_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.unwrap(wrapped, "AES", Cipher.SECRET_KEY) as? SecretKey
    }

    fun encrypt(plainText: String): ByteArray? {
        val dataKey = generateOrRestoreDataKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, dataKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            combined
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting data with data key", e)
            null
        }
    }

    fun decrypt(encrypted: ByteArray): String? {
        val dataKey = generateOrRestoreDataKey() ?: return null
        return try {
            if (encrypted.size < IV_SIZE) {
                Log.e(TAG, "Decryption failed: Invalid IV size")
                return null
            }
            val iv = ByteArray(IV_SIZE)
            System.arraycopy(encrypted, 0, iv, 0, IV_SIZE)
            val encryptedBytes = ByteArray(encrypted.size - IV_SIZE)
            System.arraycopy(encrypted, IV_SIZE, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, dataKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting data with data key", e)
            null
        }
    }

    @Synchronized
    fun clearCachedKey() {
        cachedDataKey = null
    }

    fun loadCachedKeyAsync() {
        scope.launch {
            generateOrRestoreDataKey()
        }
    }

    fun hasStrongBoxFeature(): Boolean {
        return appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    companion object {
        private const val TAG = "KeystoreManager"
        private const val MASTER_KEY_ALIAS = "profile_protection_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val GCM_TAG_LENGTH = 128
    }
}