package com.simplexray.an.security

import com.simplexray.an.common.AppLogger
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

/**
 * Certificate pinning for secure update downloads
 * 
 * Prevents MITM attacks on update server by pinning GitHub's SSL certificates.
 * Uses multiple pins for redundancy (backup pins in case of certificate rotation).
 * 
 * CVE-2025-XXXX: Fix for missing certificate pinning
 */
object CertificatePinning {
    
    // GitHub's certificate pin hashes (SHA-256)
    // These are the public key pins for github.com and *.github.com
    // Format: pin-sha256="base64-encoded-sha256-hash"
    // 
    // NOTE: These pins need to be updated with actual GitHub certificate pins
    // To get current pins: openssl s_client -connect github.com:443 -servername github.com < /dev/null 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
    // Or use: https://github.com/OWASP/owasp-mstg/blob/master/Document/0x04g-Testing-Network-Communication.md
    private val GITHUB_PINS = listOf(
        // DigiCert Global Root G2 (GitHub's current CA)
        "sha256/WoiWRyIOVNa9ihaBciRSC7XHjliYS9PwDCLsNxq4SJ8=",
        // DigiCert SHA2 Extended Validation Server CA
        "sha256/8Rw90Ej3Ttt8RRkrg+WYDS9n7IS03bk5bjP/UXPtaY8=",
        // Let's Encrypt Authority X3 (backup)
        "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=",
        // ISRG Root X1 (Let's Encrypt)
        "sha256/Ko8tivDrEjiY90yGasP6ZpBU4jwXvHqVvQI0GS3GNdA="
    )
    
    /**
     * Create OkHttpClient with certificate pinning for GitHub
     * 
     * @param baseBuilder Existing OkHttpClient.Builder to extend (optional)
     * @return Configured OkHttpClient with certificate pinning
     */
    fun createPinnedClient(baseBuilder: OkHttpClient.Builder? = null): OkHttpClient {
        val builder = baseBuilder ?: OkHttpClient.Builder()
        
        val certificatePinner = CertificatePinner.Builder()
            .apply {
                // Pin for github.com
                GITHUB_PINS.forEach { pin ->
                    add("github.com", pin)
                    add("*.github.com", pin)
                    add("api.github.com", pin)
                    add("*.githubusercontent.com", pin)
                }
            }
            .build()
        
        builder.certificatePinner(certificatePinner)
        
        AppLogger.d("CertificatePinning: Created pinned OkHttpClient for GitHub")
        
        return builder.build()
    }
    
    /**
     * Get GitHub certificate pins as string (for documentation/logging)
     */
    fun getPinsAsString(): String {
        return GITHUB_PINS.joinToString(", ")
    }
}

