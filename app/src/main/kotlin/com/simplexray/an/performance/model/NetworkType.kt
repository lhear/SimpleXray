package com.simplexray.an.performance.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

/**
 * Network type classification for adaptive performance tuning
 */
sealed class NetworkType(
    val name: String,
    val bandwidthEstimate: Long, // bytes per second
    val latencyEstimate: Int, // milliseconds
    val isMetered: Boolean,
    val configAdjustment: NetworkConfigAdjustment
) {
    data object WiFi : NetworkType(
        name = "Wi-Fi",
        bandwidthEstimate = 10_000_000, // 10 MB/s
        latencyEstimate = 20,
        isMetered = false,
        configAdjustment = NetworkConfigAdjustment(
            bufferMultiplier = 1.5f,
            timeoutMultiplier = 1.0f,
            aggressiveOptimization = true
        )
    )

    data object Ethernet : NetworkType(
        name = "Ethernet",
        bandwidthEstimate = 50_000_000, // 50 MB/s
        latencyEstimate = 10,
        isMetered = false,
        configAdjustment = NetworkConfigAdjustment(
            bufferMultiplier = 2.0f,
            timeoutMultiplier = 0.8f,
            aggressiveOptimization = true
        )
    )

    data object Mobile5G : NetworkType(
        name = "5G",
        bandwidthEstimate = 20_000_000, // 20 MB/s
        latencyEstimate = 30,
        isMetered = true,
        configAdjustment = NetworkConfigAdjustment(
            bufferMultiplier = 1.2f,
            timeoutMultiplier = 1.0f,
            aggressiveOptimization = true
        )
    )

    data object Mobile4G : NetworkType(
        name = "4G/LTE",
        bandwidthEstimate = 5_000_000, // 5 MB/s
        latencyEstimate = 50,
        isMetered = true,
        configAdjustment = NetworkConfigAdjustment(
            bufferMultiplier = 1.0f,
            timeoutMultiplier = 1.2f,
            aggressiveOptimization = false
        )
    )

    data object Mobile3G : NetworkType(
        name = "3G",
        bandwidthEstimate = 1_000_000, // 1 MB/s
        latencyEstimate = 100,
        isMetered = true,
        configAdjustment = NetworkConfigAdjustment(
            bufferMultiplier = 0.7f,
            timeoutMultiplier = 1.5f,
            aggressiveOptimization = false
        )
    )

    data object Mobile2G : NetworkType(
        name = "2G",
        bandwidthEstimate = 100_000, // 100 KB/s
        latencyEstimate = 300,
        isMetered = true,
        configAdjustment = NetworkConfigAdjustment(
            bufferMultiplier = 0.5f,
            timeoutMultiplier = 2.0f,
            aggressiveOptimization = false
        )
    )

    data object Unknown : NetworkType(
        name = "Unknown",
        bandwidthEstimate = 1_000_000, // 1 MB/s (conservative)
        latencyEstimate = 100,
        isMetered = true,
        configAdjustment = NetworkConfigAdjustment(
            bufferMultiplier = 0.8f,
            timeoutMultiplier = 1.5f,
            aggressiveOptimization = false
        )
    )

    companion object {
        /**
         * Detect current network type
         */
        fun detect(context: Context): NetworkType {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return Unknown

            val network = connectivityManager.activeNetwork ?: return Unknown
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return Unknown

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WiFi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Ethernet
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    detectCellularType(context)
                }
                else -> Unknown
            }
        }

        private fun detectCellularType(context: Context): NetworkType {
            return try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    ?: return Mobile4G

                // Use deprecated API safely with fallback
                @Suppress("DEPRECATION")
                val networkType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    try {
                        telephonyManager.dataNetworkType
                    } catch (e: Exception) {
                        android.util.Log.w("NetworkType", "Failed to get data network type", e)
                        return Mobile4G
                    }
                } else {
                    telephonyManager.networkType
                }

                when (networkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> Mobile5G
                    TelephonyManager.NETWORK_TYPE_LTE -> Mobile4G
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_UMTS,
                    TelephonyManager.NETWORK_TYPE_EVDO_0,
                    TelephonyManager.NETWORK_TYPE_EVDO_A,
                    TelephonyManager.NETWORK_TYPE_EVDO_B -> Mobile3G
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyManager.NETWORK_TYPE_1xRTT,
                    @Suppress("DEPRECATION")
                    TelephonyManager.NETWORK_TYPE_IDEN -> Mobile2G
                    else -> Mobile4G
                }
            } catch (e: Exception) {
                android.util.Log.e("NetworkType", "Error detecting cellular type", e)
                Mobile4G // Safe default
            }
        }

        /**
         * Check if network is metered (limited data)
         */
        fun isMetered(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true

            val network = connectivityManager.activeNetwork ?: return true
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return true

            return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }

        /**
         * Check if roaming
         */
        fun isRoaming(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        }
    }
}

/**
 * Network-specific configuration adjustments
 */
data class NetworkConfigAdjustment(
    val bufferMultiplier: Float,
    val timeoutMultiplier: Float,
    val aggressiveOptimization: Boolean
)
