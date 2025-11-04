package com.simplexray.an.performance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Battery Impact Monitor
 * Tracks battery consumption when performance mode is active
 */
class BatteryImpactMonitor(private val context: Context, private val scope: CoroutineScope) {
    
    data class BatteryImpactData(
        val currentBatteryLevel: Int = 100,
        val batteryTemperature: Float = 0f,
        val estimatedDrainPerHour: Float = 0f,
        val isCharging: Boolean = false,
        val isLowBattery: Boolean = false,
        val performanceModeActive: Boolean = false,
        val monitoringDuration: Long = 0L, // milliseconds
        val baselineDrainRate: Float = 0f, // baseline drain rate without performance mode
        val performanceModeOverhead: Float = 0f // additional drain from performance mode
    )
    
    private val _batteryData = MutableStateFlow(BatteryImpactData())
    val batteryData: StateFlow<BatteryImpactData> = _batteryData.asStateFlow()
    
    private var startBatteryLevel: Int = 100
    private var startTime: Long = 0L
    private var lastBatteryLevel: Int = 100
    private var lastUpdateTime: Long = 0L
    private var totalDrainPoints = 0
    private var totalDrainSum = 0.0
    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                -1
            }
            
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            
            if (batteryPct >= 0) {
                updateBatteryData(batteryPct, temperature, isCharging)
            }
        }
    }
    
    private var isMonitoring = false
    
    /**
     * Start monitoring battery impact
     */
    fun startMonitoring(performanceModeActive: Boolean) {
        if (isMonitoring) return
        
        isMonitoring = true
        startTime = System.currentTimeMillis()
        lastUpdateTime = startTime
        totalDrainPoints = 0
        totalDrainSum = 0.0
        
        // Get initial battery level
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            -1
        }
        
        if (batteryPct >= 0) {
            startBatteryLevel = batteryPct
            lastBatteryLevel = batteryPct
        }
        
        // Register battery receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
        
        // Start periodic monitoring
        scope.launch {
            monitorBatteryImpact(performanceModeActive)
        }
        
        AppLogger.d("$TAG: Battery monitoring started")
    }
    
    /**
     * Stop monitoring battery impact
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            AppLogger.w("$TAG: Failed to unregister battery receiver", e)
        }
        
        AppLogger.d("$TAG: Battery monitoring stopped")
    }
    
    /**
     * Periodic monitoring loop
     */
    private suspend fun monitorBatteryImpact(performanceModeActive: Boolean) {
        while (isMonitoring) {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val duration = now - startTime
                
                // Calculate drain rate
                val currentData = _batteryData.value
                val drainRate = calculateDrainRate()
                
                // Calculate estimated drain per hour
                val estimatedDrainPerHour = if (duration > 0 && totalDrainPoints > 0) {
                    val avgDrain = totalDrainSum / totalDrainPoints
                    (avgDrain * (3600000.0 / (now - lastUpdateTime).coerceAtLeast(1000))).toFloat()
                } else {
                    0f
                }
                
                // Check if low battery
                val isLowBattery = currentData.currentBatteryLevel < 20
                
                // Update state
                _batteryData.value = currentData.copy(
                    estimatedDrainPerHour = estimatedDrainPerHour,
                    isLowBattery = isLowBattery,
                    performanceModeActive = performanceModeActive,
                    monitoringDuration = duration
                )
                
                // Log warning if low battery
                if (isLowBattery && performanceModeActive) {
                    AppLogger.w("$TAG: Low battery (${currentData.currentBatteryLevel}%), consider disabling performance mode")
                }
                
                kotlinx.coroutines.delay(30000) // Update every 30 seconds
            }
        }
    }
    
    /**
     * Update battery data from broadcast receiver
     */
    private fun updateBatteryData(level: Int, temperature: Float, isCharging: Boolean) {
        val now = System.currentTimeMillis()
        val currentData = _batteryData.value
        
        // Calculate drain rate if not charging
        if (!isCharging && lastUpdateTime > 0) {
            val timeDiff = (now - lastUpdateTime) / 1000.0 // seconds
            val levelDiff = lastBatteryLevel - level
            
            if (timeDiff > 0 && levelDiff > 0) {
                val drainRate = (levelDiff / timeDiff) * 3600f // percent per hour
                totalDrainSum += drainRate
                totalDrainPoints++
            }
        }
        
        lastBatteryLevel = level
        lastUpdateTime = now
        
        // Update state
        _batteryData.value = currentData.copy(
            currentBatteryLevel = level,
            batteryTemperature = temperature,
            isCharging = isCharging
        )
    }
    
    /**
     * Calculate current drain rate (percent per hour)
     */
    private fun calculateDrainRate(): Float {
        if (totalDrainPoints == 0) return 0f
        return (totalDrainSum / totalDrainPoints).toFloat()
    }
    
    /**
     * Get battery impact warning level
     */
    fun getWarningLevel(): WarningLevel {
        val data = _batteryData.value
        
        if (!data.performanceModeActive) {
            return WarningLevel.None
        }
        
        return when {
            data.currentBatteryLevel < 10 -> WarningLevel.Critical
            data.currentBatteryLevel < 20 -> WarningLevel.High
            data.estimatedDrainPerHour > 15f -> WarningLevel.Medium
            data.batteryTemperature > 40f -> WarningLevel.Medium
            else -> WarningLevel.None
        }
    }
    
    /**
     * Get recommendation based on battery status
     */
    fun getRecommendation(): String {
        val data = _batteryData.value
        val warningLevel = getWarningLevel()
        
        return when (warningLevel) {
            WarningLevel.Critical -> "Battery critically low. Consider disabling performance mode."
            WarningLevel.High -> "Battery is low. Performance mode may drain battery faster."
            WarningLevel.Medium -> if (data.batteryTemperature > 40f) {
                "Battery temperature is high. Consider reducing performance optimizations."
            } else {
                "High battery drain detected. Monitor battery usage."
            }
            WarningLevel.None -> "Battery usage is normal."
        }
    }
    
    enum class WarningLevel {
        None,
        Medium,
        High,
        Critical
    }
    
    companion object {
        private const val TAG = "BatteryImpactMonitor"
    }
}

