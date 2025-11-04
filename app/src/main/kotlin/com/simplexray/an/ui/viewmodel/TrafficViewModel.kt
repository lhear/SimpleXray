package com.simplexray.an.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.data.db.SpeedStats
import com.simplexray.an.data.db.TotalBytes
import com.simplexray.an.data.db.TrafficDatabase
import com.simplexray.an.data.repository.TrafficRepository as DbTrafficRepository
import com.simplexray.an.data.repository.TrafficRepositoryFactory
import com.simplexray.an.domain.model.TrafficHistory
import com.simplexray.an.domain.model.TrafficSnapshot
import com.simplexray.an.domain.ThrottleDetector
import com.simplexray.an.traffic.TrafficRepository
import com.simplexray.an.traffic.toTrafficSnapshot
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * ViewModel for traffic monitoring UI.
 * Manages real-time traffic observation and historical data.
 */
class TrafficViewModel(application: Application) : AndroidViewModel(application) {

    private val throttleDetector = ThrottleDetector()
    private val dbRepository: DbTrafficRepository
    
    // Use Application-level TrafficRepository singleton
    private val trafficRepository: TrafficRepository? = TrafficRepository.getInstanceOrNull()

    private val _uiState = MutableStateFlow(TrafficUiState())
    val uiState: StateFlow<TrafficUiState> = _uiState.asStateFlow()

    private var burstHistory = mutableListOf<TrafficSnapshot>()
    private val burstThreshold = 3.0f
    
    // History buffer for throttling detection
    private val historyBuffer = mutableListOf<TrafficSnapshot>()

    init {
        // Initialize database repository for historical data
        val database = TrafficDatabase.getInstance(application)
        dbRepository = TrafficRepositoryFactory.create(database.trafficDao())

        // Observe real-time traffic from Application-level TrafficRepository
        observeRealTimeTraffic()

        // Load today's statistics
        loadTodayStats()
    }

    /**
     * Observe real-time traffic updates from TrafficRepository (Application-level singleton).
     * This uses hot SharedFlow with replay buffer, so UI always gets values even after resume.
     */
    private fun observeRealTimeTraffic() {
        val repository = trafficRepository ?: run {
            AppLogger.w("TrafficViewModel: TrafficRepository not initialized")
            return
        }
        
        viewModelScope.launch {
            // Collect from hot SharedFlow - this will replay last 50 samples on subscription
            repository.trafficFlow.map { sample -> sample.toTrafficSnapshot() }
                .collect { snapshot ->
                    // Update UI state with current snapshot
                    _uiState.update { state ->
                        state.copy(
                            currentSnapshot = snapshot,
                            isConnected = snapshot.isConnected
                        )
                    }

                    // Update history buffer for throttling detection
                    historyBuffer.add(snapshot)
                    if (historyBuffer.size > 120) {
                        historyBuffer.removeAt(0)
                    }
                    
                    // Update history for charting
                    val trafficHistory = TrafficHistory.from(historyBuffer.takeLast(120))
                    val throttle = throttleDetector.evaluate(historyBuffer.takeLast(120))
                    _uiState.update { state ->
                        state.copy(
                            history = trafficHistory,
                            isThrottled = throttle.isThrottled,
                            throttleMessage = throttle.message
                        )
                    }

                    // Detect bursts
                    detectAndUpdateBurst(snapshot)
                }
        }
    }

    /**
     * Load today's traffic statistics from database
     */
    private fun loadTodayStats() {
        viewModelScope.launch {
            try {
                val totalBytes = dbRepository.getTotalBytesToday()
                val speedStats = dbRepository.getSpeedStatsToday()
                val avgLatency = dbRepository.getAverageLatencyToday()

                _uiState.update { state ->
                    state.copy(
                        todayTotalBytes = totalBytes,
                        todaySpeedStats = speedStats,
                        todayAvgLatency = avgLatency
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(error = "Failed to load today's stats: ${e.message}")
                }
            }
        }
    }

    /**
     * Load last 24 hours statistics
     */
    fun loadLast24HoursStats() {
        viewModelScope.launch {
            try {
                val speedStats = dbRepository.getSpeedStatsLast24Hours()
                _uiState.update { state ->
                    state.copy(last24HoursSpeedStats = speedStats)
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(error = "Failed to load 24h stats: ${e.message}")
                }
            }
        }
    }

    /**
     * Detect burst anomalies
     */
    private fun detectAndUpdateBurst(snapshot: TrafficSnapshot) {
        burstHistory.add(snapshot)
        if (burstHistory.size > 10) {
            burstHistory.removeAt(0)
        }

        if (burstHistory.size >= 10) {
            val avgRx = burstHistory.map { it.rxRateMbps }.average().toFloat()
            val avgTx = burstHistory.map { it.txRateMbps }.average().toFloat()

            val isBurst = snapshot.rxRateMbps > avgRx * burstThreshold ||
                         snapshot.txRateMbps > avgTx * burstThreshold

            _uiState.update { state ->
                state.copy(
                    isBurst = isBurst,
                    burstMessage = if (isBurst) {
                        "⚡ Traffic burst detected! " +
                        "RX: ${snapshot.formatDownloadSpeed()}, " +
                        "TX: ${snapshot.formatUploadSpeed()}"
                    } else {
                        null
                    }
                )
            }
        }
    }

    /**
     * Reset session statistics
     * Note: TrafficRepository persists values, so we only reset UI state
     */
    fun resetSession() {
        burstHistory.clear()
        historyBuffer.clear()
        _uiState.update { state ->
            state.copy(
                currentSnapshot = TrafficSnapshot(),
                history = TrafficHistory(),
                isBurst = false,
                burstMessage = null
            )
        }
    }

    /**
     * Clear all historical data from database
     */
    fun clearHistory() {
        viewModelScope.launch {
            try {
                dbRepository.deleteAll()
                _uiState.update { state ->
                    state.copy(
                        todayTotalBytes = TotalBytes(0, 0),
                        todaySpeedStats = SpeedStats(0f, 0f, 0f, 0f),
                        todayAvgLatency = -1L,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                // Re-throw cancellation to properly handle coroutine cancellation
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(error = "Failed to clear history: ${e.message}")
                }
            }
        }
    }

    /**
     * Delete logs older than specified days
     */
    fun deleteOldLogs(days: Int) {
        viewModelScope.launch {
            try {
                val deleted = dbRepository.deleteLogsOlderThanDays(days)
                _uiState.update { state ->
                    state.copy(error = "Deleted $deleted old logs")
                }
            } catch (e: CancellationException) {
                // Re-throw cancellation to properly handle coroutine cancellation
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(error = "Failed to delete old logs: ${e.message}")
                }
            }
        }
    }

    /**
     * Refresh all statistics
     */
    fun refresh() {
        loadTodayStats()
        loadLast24HoursStats()
    }
    
    /**
     * Restart observers when service reconnects.
     * Note: TrafficRepository handles reconnection automatically via binder callbacks.
     * This method is kept for backward compatibility but does nothing.
     */
    fun restartObservers() {
        // TrafficRepository automatically handles binder reconnection
        // No action needed here
        AppLogger.d("TrafficViewModel: restartObservers() called (no-op, handled by TrafficRepository)")
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { state ->
            state.copy(error = null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // TrafficRepository runs in Application scope, so no cleanup needed here
        // The repository will continue running even after ViewModel is cleared
    }
}

/**
 * UI state for traffic monitoring
 */
data class TrafficUiState(
    val currentSnapshot: TrafficSnapshot = TrafficSnapshot(),
    val history: TrafficHistory = TrafficHistory(),
    val isConnected: Boolean = false,
    val todayTotalBytes: TotalBytes = TotalBytes(0, 0),
    val todaySpeedStats: SpeedStats = SpeedStats(0f, 0f, 0f, 0f),
    val todayAvgLatency: Long = -1L,
    val last24HoursSpeedStats: SpeedStats = SpeedStats(0f, 0f, 0f, 0f),
    val isBurst: Boolean = false,
    val burstMessage: String? = null,
    val isThrottled: Boolean = false,
    val throttleMessage: String? = null,
    val error: String? = null
) {
    /**
     * Format today's total data usage
     */
    fun formatTodayTotal(): String {
        val total = todayTotalBytes.total.toDouble()
        return when {
            total >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.2f GB", total / 1_073_741_824)
            total >= 1_048_576 -> String.format(java.util.Locale.US, "%.2f MB", total / 1_048_576)
            total >= 1024 -> String.format(java.util.Locale.US, "%.2f KB", total / 1024)
            else -> String.format(java.util.Locale.US, "%.0f B", total)
        }
    }

    /**
     * Format today's download usage
     */
    fun formatTodayDownload(): String {
        val rx = todayTotalBytes.rxTotal.toDouble()
        return when {
            rx >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.2f GB", rx / 1_073_741_824)
            rx >= 1_048_576 -> String.format(java.util.Locale.US, "%.2f MB", rx / 1_048_576)
            rx >= 1024 -> String.format(java.util.Locale.US, "%.2f KB", rx / 1024)
            else -> String.format(java.util.Locale.US, "%.0f B", rx)
        }
    }

    /**
     * Format today's upload usage
     */
    fun formatTodayUpload(): String {
        val tx = todayTotalBytes.txTotal.toDouble()
        return when {
            tx >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.2f GB", tx / 1_073_741_824)
            tx >= 1_048_576 -> String.format(java.util.Locale.US, "%.2f MB", tx / 1_048_576)
            tx >= 1024 -> String.format(java.util.Locale.US, "%.2f KB", tx / 1024)
            else -> String.format(java.util.Locale.US, "%.0f B", tx)
        }
    }
}
