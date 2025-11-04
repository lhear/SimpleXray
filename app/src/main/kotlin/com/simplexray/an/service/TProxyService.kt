package com.simplexray.an.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.simplexray.an.common.AppLogger
import androidx.core.app.NotificationCompat
import com.simplexray.an.BuildConfig
import com.simplexray.an.R
import com.simplexray.an.activity.MainActivity
import com.simplexray.an.common.ConfigUtils
import com.simplexray.an.common.ConfigUtils.extractPortsFromJson
import com.simplexray.an.data.source.LogFileManager
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.security.SecureCredentialStorage
import com.simplexray.an.service.XrayProcessManager
import com.simplexray.an.performance.PerformanceIntegration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.lang.Process

class TProxyService : VpnService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private val logBroadcastBuffer: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    
    // Connection monitoring - check if VPN connection is still active
    private val connectionCheckRunnable = Runnable {
        checkVpnConnection()
    }
    private var isMonitoringConnection = false
    private val broadcastLogsRunnable = Runnable {
        val logs = mutableListOf<String>()
        while (logBroadcastBuffer.isNotEmpty()) {
            logBroadcastBuffer.poll()?.let { logs.add(it) }
        }
        if (logs.isNotEmpty()) {
            val logUpdateIntent = Intent(ACTION_LOG_UPDATE)
            logUpdateIntent.setPackage(application.packageName)
            logUpdateIntent.putStringArrayListExtra(EXTRA_LOG_DATA, ArrayList(logs))
            sendBroadcast(logUpdateIntent)
            AppLogger.d("Broadcasted a batch of ${logs.size} logs.")
        }
    }

    private val portCache = mutableSetOf<Int>()
    private val portCacheLock = Any()
    
    private fun findAvailablePort(excludedPorts: Set<Int>): Int? {
        // Use smaller port range and prioritize OS-assigned ports
        val startPort = 49152
        val endPort = 65535
        
        // Check cached ports first
        synchronized(portCacheLock) {
            portCache.removeAll(excludedPorts)
            val cachedPort = portCache.firstOrNull { port ->
                port !in excludedPorts && port in startPort..endPort && runCatching {
                    ServerSocket(port).use { socket ->
                        socket.reuseAddress = true
                    }
                    true
                }.getOrDefault(false)
            }
            if (cachedPort != null) {
                return cachedPort
            }
        }
        
        // Try sequential search with early exit
        val portRange = (startPort..endPort).shuffled().take(1000)
        for (port in portRange) {
            if (port in excludedPorts) continue
            runCatching {
                ServerSocket(port).use { socket ->
                    socket.reuseAddress = true
                }
                synchronized(portCacheLock) {
                    portCache.add(port)
                }
                return port
            }.onFailure {
                AppLogger.d("Port $port unavailable: ${it.message}")
            }
        }
        
        // Fallback: try OS-assigned port (port 0)
        return runCatching {
            ServerSocket(0).use { socket ->
                socket.reuseAddress = true
                val assignedPort = socket.localPort
                synchronized(portCacheLock) {
                    portCache.add(assignedPort)
                }
                assignedPort
            }
        }.getOrNull()
    }

    private lateinit var logFileManager: LogFileManager
    
    // Performance optimization (optional, enabled via Preferences)
    private var perfIntegration: PerformanceIntegration? = null
    private val enablePerformanceMode: Boolean
        get() = Preferences(this).enablePerformanceMode

    // Data class to hold both process and reloading state atomically
    // PID is stored separately to allow killing process even if Process reference becomes invalid
    private data class ProcessState(
        val process: Process?,
        val pid: Long = -1L,
        val reloading: Boolean
    )

    // Use single AtomicReference for thread-safe process state management
    // This prevents race conditions when reading/updating both process and reloading flag together
    private val processState = AtomicReference(ProcessState(null, -1L, false))
    private var tunFd: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning.set(true)
        logFileManager = LogFileManager(this)
        
        // Initialize performance optimizations if enabled
        if (enablePerformanceMode) {
            try {
                perfIntegration = PerformanceIntegration(this)
                perfIntegration?.initialize()
                AppLogger.d("Performance mode enabled")
            } catch (e: Exception) {
                AppLogger.w("Failed to initialize performance mode", e)
            }
        }
        
        AppLogger.d("TProxyService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle null intent (service restart after being killed by system)
        if (intent == null) {
            AppLogger.w("TProxyService: Restarted with null intent, checking VPN state")
            // If VPN is still active, keep it running
            if (tunFd != null) {
                AppLogger.d("TProxyService: VPN still active, maintaining connection")
                // Ensure notification is shown to keep service in foreground
                val prefs = Preferences(this)
                val channelName = if (prefs.disableVpn) "nosocks" else "socks5"
                initNotificationChannel(channelName)
                createNotification(channelName)
                
                // Check if xray process is still running
                val currentState = processState.get()
                val isProcessAlive = currentState.process?.isAlive == true || 
                    (currentState.pid != -1L && isProcessAlive(currentState.pid.toInt()))
                
                if (!isProcessAlive) {
                    AppLogger.w("TProxyService: Xray process not running after restart, reconnecting...")
                    // Restart xray process
                    serviceScope.launch { runXrayProcess() }
                }
                
                // Always send ACTION_START broadcast to notify UI that service is running
                // This ensures UI state is synchronized even after service restart
                val successIntent = Intent(ACTION_START)
                successIntent.setPackage(application.packageName)
                sendBroadcast(successIntent)
                AppLogger.d("TProxyService: Sent ACTION_START broadcast to notify UI after restart")
                
                // Start connection monitoring if not already running
                if (!isMonitoringConnection) {
                    startConnectionMonitoring()
                }
                
                return START_STICKY
            } else {
                AppLogger.d("TProxyService: VPN not active, service will stop")
                return START_NOT_STICKY
            }
        }
        
        val action = intent.action
        when (action) {
            ACTION_DISCONNECT -> {
                stopXray()
                return START_NOT_STICKY
            }

            ACTION_RELOAD_CONFIG -> {
                val prefs = Preferences(this)
                if (prefs.disableVpn) {
                    AppLogger.d("Received RELOAD_CONFIG action (core-only mode)")
                    // Atomically get current process, destroy it, and set reloading flag
                    val currentState = processState.getAndUpdate { state ->
                        ProcessState(state.process, state.pid, reloading = true)
                    }
                    killProcessSafely(currentState.process, currentState.pid)
                    serviceScope.launch { runXrayProcess() }
                    return START_STICKY
                }
                if (tunFd == null) {
                    AppLogger.w("Cannot reload config, VPN service is not running.")
                    return START_STICKY
                }
                AppLogger.d("Received RELOAD_CONFIG action.")
                // Atomically get current process, destroy it, and set reloading flag
                val currentState = processState.getAndUpdate { state ->
                    ProcessState(state.process, state.pid, reloading = true)
                }
                killProcessSafely(currentState.process, currentState.pid)
                serviceScope.launch { runXrayProcess() }
                return START_STICKY
            }

            ACTION_START -> {
                logFileManager.clearLogs()
                val prefs = Preferences(this)
                if (prefs.disableVpn) {
                    // Even in core-only mode, ensure foreground notification is shown
                    // This prevents the service from being killed when app goes to background
                    @Suppress("SameParameterValue") val channelName = "nosocks"
                    initNotificationChannel(channelName)
                    createNotification(channelName)
                    
                    // Start monitoring even in core-only mode (to detect service issues)
                    startConnectionMonitoring()
                    
                    serviceScope.launch { runXrayProcess() }
                    val successIntent = Intent(ACTION_START)
                    successIntent.setPackage(application.packageName)
                    sendBroadcast(successIntent)

                } else {
                    startXray()
                }
                return START_STICKY
            }

            else -> {
                logFileManager.clearLogs()
                startXray()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // When user swipes away the app, restart the service to keep VPN connection alive
        AppLogger.d("TProxyService: App task removed, ensuring service continues")
        // Don't stop the service - let it continue running in background
        // The service will keep running even when app is removed from recent apps
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning.set(false)
        
        // Stop connection monitoring
        stopConnectionMonitoring()
        
        handler.removeCallbacks(broadcastLogsRunnable)
        broadcastLogsRunnable.run()
        
        // Cleanup performance optimizations
        try {
            perfIntegration?.cleanup()
        } catch (e: Exception) {
            AppLogger.w("Error during performance cleanup", e)
        } finally {
            perfIntegration = null
        }
        
        // Ensure xray process is stopped when service is destroyed
        // This is critical when app goes to background and service is killed by system
        AppLogger.d("TProxyService destroyed, stopping xray process")
        val oldState = processState.getAndSet(ProcessState(null, -1L, false))
        killProcessSafely(oldState.process, oldState.pid)
        
        serviceScope.cancel()
        AppLogger.d("TProxyService destroyed.")
        // Removed exitProcess(0) - let Android handle service lifecycle properly
        // exitProcess() forcefully kills the entire app process which prevents proper cleanup
    }

    override fun onRevoke() {
        AppLogger.w("TProxyService: VPN connection revoked by system")
        // VPN was revoked, stop the service
        stopXray()
        super.onRevoke()
    }

    private fun startXray() {
        startService()
        serviceScope.launch { runXrayProcess() }
    }

    private fun runXrayProcess() {
        var currentProcess: Process? = null
        var processPid: Long = -1
        try {
            AppLogger.d("Attempting to start xray process.")
            val libraryDir = getNativeLibraryDir(applicationContext)
            if (libraryDir == null) {
                AppLogger.e("Failed to get native library directory")
                stopXray()
                return
            }
            val prefs = Preferences(applicationContext)
            val selectedConfigPath = prefs.selectedConfigPath
            if (selectedConfigPath == null) {
                AppLogger.e("No configuration file selected")
                stopXray()
                return
            }
            val xrayPath = "$libraryDir/libxray.so"
            val configFile = File(selectedConfigPath)
            val maxConfigSize = 10 * 1024 * 1024 // 10MB limit
            if (configFile.length() > maxConfigSize) {
                AppLogger.e("Config file too large: ${configFile.length()} bytes (max: $maxConfigSize)")
                stopXray()
                return
            }
            val configContent = configFile.readText()
            val apiPort = findAvailablePort(extractPortsFromJson(configContent)) ?: return
            prefs.apiPort = apiPort
            AppLogger.d("Found and set API port: $apiPort")

            val processBuilder = getProcessBuilder(xrayPath)
            // Update process manager ports for observers
            XrayProcessManager.updateFrom(applicationContext)
            currentProcess = processBuilder.start()
            
            // Get process PID immediately after starting
            try {
                processPid = currentProcess.javaClass.getMethod("pid").invoke(currentProcess) as? Long ?: -1L
                AppLogger.i("Xray process started successfully with PID: $processPid")
            } catch (e: Exception) {
                AppLogger.w("Could not get process PID", e)
                processPid = -1L
            }
            
            // Atomically update process state with PID, preserving reloading flag
            processState.updateAndGet { state ->
                ProcessState(currentProcess, processPid, state.reloading)
            }
            
            // Apply performance optimizations after Xray process starts (if enabled)
            if (enablePerformanceMode && perfIntegration != null) {
                try {
                    // Request CPU boost for Xray process
                    perfIntegration?.getPerformanceManager()?.requestCPUBoost(10000) // 10 seconds
                    AppLogger.d("Performance optimizations applied to Xray process")
                } catch (e: Exception) {
                    AppLogger.w("Failed to apply performance optimizations to Xray process", e)
                }
            }

            AppLogger.d("Writing config to xray stdin from: $selectedConfigPath")
            val injectedConfigContent =
                ConfigUtils.injectStatsService(prefs, configContent)
            currentProcess.outputStream.use { os ->
                os.write(injectedConfigContent.toByteArray())
                os.flush()
            }

            val inputStream = currentProcess.inputStream
            InputStreamReader(inputStream).use { isr ->
                BufferedReader(isr).use { reader ->
                    var line: String?
                    AppLogger.d("Reading xray process output.")
                    var lineCount = 0
                    while (reader.readLine().also { line = it } != null && serviceScope.isActive) {
                        line?.let {
                            logFileManager.appendLog(it)
                            logBroadcastBuffer.offer(it)
                            lineCount++
                            if (!handler.hasCallbacks(broadcastLogsRunnable)) {
                                handler.postDelayed(broadcastLogsRunnable, BROADCAST_DELAY_MS)
                            }
                            if (lineCount % 100 == 0) {
                                kotlinx.coroutines.yield()
                            }
                        }
                    }
                }
            }
            AppLogger.d("xray process output stream finished.")
        } catch (e: InterruptedIOException) {
            AppLogger.d("Xray process reading interrupted.")
        } catch (e: Exception) {
            AppLogger.e("Error executing xray", e)
        } finally {
            AppLogger.d("Xray process task finished (PID: $processPid).")
            
            // Get current process state to check if this is still the active process
            val currentState = processState.get()
            val isActiveProcess = currentState.process === currentProcess
            
            // Only cleanup if this is still the active process or if process reference is invalid
            if (isActiveProcess || currentProcess != null) {
                // Use killProcessSafely to handle both Process reference and PID fallback
                killProcessSafely(currentProcess, processPid)
            } else {
                AppLogger.d("Skipping cleanup: process instance changed (old PID: $processPid)")
            }
            
            // Atomically check reloading flag and clear process reference if it matches
            val wasReloading = processState.getAndUpdate { state ->
                if (state.process === currentProcess) {
                    // Clear process and reset reloading flag atomically
                    ProcessState(null, -1L, reloading = false)
                } else {
                    // Keep current state, only reset reloading flag if it was set
                    ProcessState(state.process, state.pid, reloading = false)
                }
            }
            
            if (wasReloading.reloading && wasReloading.process === currentProcess) {
                AppLogger.d("Xray process stopped due to configuration reload.")
            } else if (wasReloading.process === currentProcess) {
                AppLogger.d("Xray process exited unexpectedly or due to stop request. Stopping VPN.")
                stopXray()
            } else {
                AppLogger.w("Finishing task for an old xray process instance.")
            }
        }
    }

    private fun getProcessBuilder(xrayPath: String): ProcessBuilder {
        val filesDir = applicationContext.filesDir
        val cacheDir = applicationContext.cacheDir
        val command: MutableList<String> = mutableListOf(xrayPath)
        val processBuilder = ProcessBuilder(command)
        val environment = processBuilder.environment()
        
        // Set xray-specific environment variables
        environment["XRAY_LOCATION_ASSET"] = filesDir.path
        
        // Restrict filesystem access to prevent SELinux denials
        // Set HOME and TMPDIR to app-accessible directories to prevent system directory probing
        environment["HOME"] = filesDir.path
        environment["TMPDIR"] = cacheDir.path
        environment["TMP"] = cacheDir.path
        
        processBuilder.directory(filesDir)
        processBuilder.redirectErrorStream(true)
        return processBuilder
    }

    private fun stopXray() {
        AppLogger.d("stopXray called with keepExecutorAlive=" + false)
        
        // Stop connection monitoring
        stopConnectionMonitoring()
        
        serviceScope.cancel()
        AppLogger.d("CoroutineScope cancelled.")

        // Atomically get and clear process state
        val oldState = processState.getAndSet(ProcessState(null, -1L, false))
        
        // Kill process using both Process reference and PID as fallback
        killProcessSafely(oldState.process, oldState.pid)
        
        AppLogger.d("processState cleared.")

        AppLogger.d("Calling stopService (stopping VPN).")
        stopService()
    }
    
    /**
     * Safely kill process using Process reference if available, or PID as fallback.
     * This is critical when app goes to background and Process reference becomes invalid.
     */
    private fun killProcessSafely(proc: Process?, pid: Long) {
        if (proc == null && pid == -1L) {
            return // Nothing to kill
        }
        
        val effectivePid = if (pid != -1L) {
            pid
        } else {
            // Try to get PID from Process reference
            try {
                proc?.javaClass?.getMethod("pid")?.invoke(proc) as? Long ?: -1L
            } catch (e: Exception) {
                -1L
            }
        }
        
        if (effectivePid == -1L) {
            AppLogger.w("Cannot kill process: no valid PID or Process reference")
            return
        }
        
        AppLogger.d("Stopping xray process (PID: $effectivePid)")
        
        // First try graceful shutdown using Process reference if available
        if (proc != null) {
            try {
                if (proc.isAlive) {
                    proc.destroy()
                    try {
                        val exited = proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                        if (!exited) {
                            AppLogger.w("Process (PID: $effectivePid) did not exit gracefully, forcing termination")
                            proc.destroyForcibly()
                            proc.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)
                        } else {
                            AppLogger.d("Process (PID: $effectivePid) exited gracefully")
                            return
                        }
                    } catch (e: InterruptedException) {
                        AppLogger.d("Process wait interrupted during stop")
                        proc.destroyForcibly()
                    } catch (e: Exception) {
                        AppLogger.w("Error waiting for process termination via Process reference", e)
                        proc.destroyForcibly()
                    }
                } else {
                    AppLogger.d("Process (PID: $effectivePid) already dead")
                    return
                }
            } catch (e: Exception) {
                AppLogger.w("Error stopping process via Process reference (PID: $effectivePid), will try PID kill", e)
            }
        }
        
        // Fallback: kill by PID directly if Process reference is invalid or didn't work
        // This is critical when app goes to background and Process reference becomes stale
        if (effectivePid != -1L) {
            try {
                // Check if process is still alive using PID
                val isAlive = isProcessAlive(effectivePid.toInt())
                
                if (isAlive) {
                    AppLogger.d("Killing process by PID: $effectivePid (Process reference unavailable or invalid)")
                    try {
                        // Use Android Process.killProcess for same-UID processes
                        android.os.Process.killProcess(effectivePid.toInt())
                        AppLogger.d("Sent kill signal to process PID: $effectivePid")
                        
                        // Verify process is dead (immediate check)
                        val stillAlive = isProcessAlive(effectivePid.toInt())
                        
                        if (stillAlive) {
                            AppLogger.w("Process (PID: $effectivePid) still alive after killProcess, trying force kill")
                            // Last resort: try kill -9 via Runtime.exec
                            try {
                                Runtime.getRuntime().exec("kill -9 $effectivePid").waitFor()
                                AppLogger.d("Force killed process PID: $effectivePid")
                            } catch (e: Exception) {
                                AppLogger.e("Failed to force kill process PID: $effectivePid", e)
                            }
                        } else {
                            AppLogger.d("Process (PID: $effectivePid) successfully killed")
                        }
                    } catch (e: SecurityException) {
                        AppLogger.e("Permission denied killing process PID: $effectivePid", e)
                    } catch (e: Exception) {
                        AppLogger.e("Error killing process by PID: $effectivePid", e)
                    }
                } else {
                    AppLogger.d("Process (PID: $effectivePid) already dead")
                }
            } catch (e: Exception) {
                AppLogger.e("Error in PID-based process kill for PID: $effectivePid", e)
            }
        }
    }
    
    /**
     * Check if a process is alive by PID.
     * Uses /proc/PID directory existence check.
     */
    private val processAliveCache = mutableMapOf<Int, Pair<Boolean, Long>>()
    private val processCacheLock = Any()
    private val PROCESS_CACHE_TTL_MS = 1000L // Cache for 1 second
    
    private fun isProcessAlive(pid: Int): Boolean {
        val now = System.currentTimeMillis()
        synchronized(processCacheLock) {
            processAliveCache.entries.removeIf { (_, value) -> now - value.second > PROCESS_CACHE_TTL_MS }
            processAliveCache[pid]?.let { (alive, timestamp) ->
                if (now - timestamp < PROCESS_CACHE_TTL_MS) {
                    return alive
                }
            }
        }
        
        return try {
            val alive = File("/proc/$pid").exists()
            synchronized(processCacheLock) {
                processAliveCache[pid] = Pair(alive, now)
            }
            alive
        } catch (e: Exception) {
            AppLogger.w("Error checking process $pid status, assuming dead", e)
            false
        }
    }
    
    /**
     * Check if VPN connection is still active.
     * This helps detect if the VPN connection was lost when app goes to background.
     * Also checks if xray process is still alive to detect zombie processes.
     */
    private fun checkVpnConnection() {
        if (!Companion.isRunning()) {
            isMonitoringConnection = false
            return
        }
        
        // Check if xray process is still alive (detect zombie processes)
        val currentState = processState.get()
        val isProcessAlive = currentState.process?.isAlive == true || 
            (currentState.pid != -1L && isProcessAlive(currentState.pid.toInt()))
        
        if (!isProcessAlive && currentState.pid != -1L) {
            AppLogger.w("TProxyService: Xray process died (PID: ${currentState.pid}), attempting to restart")
            // Clear the dead process state
            processState.set(ProcessState(null, -1L, false))
            // Restart xray process
            if (Companion.isRunning()) {
                serviceScope.launch {
                    try {
                        val prefs = Preferences(this@TProxyService)
                        if (prefs.disableVpn) {
                            runXrayProcess()
                        } else {
                            startXray()
                        }
                    } catch (e: Exception) {
                        AppLogger.e("TProxyService: Failed to restart xray process", e)
                    }
                }
            }
            scheduleNextConnectionCheck()
            return
        }
        
        val prefs = Preferences(this)
        if (prefs.disableVpn) {
            // In core-only mode, no VPN to check, but we already checked process above
            scheduleNextConnectionCheck()
            return
        }
        
        // Check if tunFd is still valid
        val fd = tunFd
        if (fd == null) {
            AppLogger.w("TProxyService: VPN connection lost (tunFd is null)")
            // VPN connection was lost, try to restart
            if (Companion.isRunning()) {
                AppLogger.d("TProxyService: Attempting to restore VPN connection")
                serviceScope.launch {
                    try {
                        startXray()
                    } catch (e: Exception) {
                        AppLogger.e("TProxyService: Failed to restore VPN connection", e)
                    }
                }
            }
            isMonitoringConnection = false
            return
        }
        
        // Check if file descriptor is still valid
        try {
            val isValid = fd.fileDescriptor.valid()
            if (!isValid) {
                AppLogger.w("TProxyService: VPN file descriptor is invalid")
                tunFd = null
                if (Companion.isRunning()) {
                    AppLogger.d("TProxyService: Attempting to restore VPN connection")
                    serviceScope.launch {
                        try {
                            startXray()
                        } catch (e: Exception) {
                            AppLogger.e("TProxyService: Failed to restore VPN connection", e)
                        }
                    }
                }
                isMonitoringConnection = false
                return
            }
        } catch (e: Exception) {
            AppLogger.w("TProxyService: Error checking VPN file descriptor", e)
            // Assume connection is still valid if we can't check
        }
        
        // Schedule next check
        scheduleNextConnectionCheck()
    }
    
    /**
     * Schedule the next VPN connection check.
     * Checks every 30 seconds to detect connection loss.
     */
    private fun scheduleNextConnectionCheck() {
        if (!Companion.isRunning() || !isMonitoringConnection) {
            return
        }
        handler.removeCallbacks(connectionCheckRunnable)
        val checkInterval = 30000L // 30 seconds default, configurable via Preferences if needed
        handler.postDelayed(connectionCheckRunnable, checkInterval)
    }
    
    /**
     * Start monitoring VPN connection status.
     */
    private fun startConnectionMonitoring() {
        if (isMonitoringConnection) {
            return
        }
        isMonitoringConnection = true
        AppLogger.d("TProxyService: Started VPN connection monitoring")
        scheduleNextConnectionCheck()
    }
    
    /**
     * Stop monitoring VPN connection status.
     */
    private fun stopConnectionMonitoring() {
        if (!isMonitoringConnection) {
            return
        }
        isMonitoringConnection = false
        handler.removeCallbacks(connectionCheckRunnable)
        AppLogger.d("TProxyService: Stopped VPN connection monitoring")
    }

    private fun startService() {
        if (tunFd != null) return
        val prefs = Preferences(this)
        val builder = getVpnBuilder(prefs)
        tunFd = builder.establish()
        if (tunFd == null) {
            stopXray()
            return
        }
        val tproxyFile = File(cacheDir, "tproxy.conf")
        try {
            tproxyFile.createNewFile()
            FileOutputStream(tproxyFile, false).use { fos ->
                val tproxyConf = getTproxyConf(prefs)
                fos.write(tproxyConf.toByteArray())
            }
        } catch (e: IOException) {
            AppLogger.e(e.toString())
            stopXray()
            return
        }
        tunFd?.fd?.let { fd ->
            TProxyStartService(tproxyFile.absolutePath, fd)
            
            // Apply performance optimizations if enabled
            if (enablePerformanceMode && perfIntegration != null) {
                try {
                    perfIntegration?.applyNetworkOptimizations(fd)
                } catch (e: Exception) {
                    AppLogger.w("Failed to apply network optimizations", e)
                }
            }
        } ?: run {
            AppLogger.e("tunFd is null after establish()")
            stopXray()
            return
        }

        val successIntent = Intent(ACTION_START)
        successIntent.setPackage(application.packageName)
        sendBroadcast(successIntent)
        @Suppress("SameParameterValue") val channelName = "socks5"
        initNotificationChannel(channelName)
        createNotification(channelName)
        
        // Start monitoring VPN connection to detect if it's lost when app goes to background
        startConnectionMonitoring()
    }

    private fun getVpnBuilder(prefs: Preferences): Builder = Builder().apply {
        setBlocking(false)
        setMtu(prefs.tunnelMtu)

        if (prefs.bypassLan) {
            addRoute("10.0.0.0", 8)
            addRoute("172.16.0.0", 12)
            addRoute("192.168.0.0", 16)
        }
        if (prefs.httpProxyEnabled) {
            setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", prefs.socksPort))
        }
        if (prefs.ipv4) {
            addAddress(prefs.tunnelIpv4Address, prefs.tunnelIpv4Prefix)
            addRoute("0.0.0.0", 0)
            prefs.dnsIpv4.takeIf { it.isNotEmpty() }?.also { addDnsServer(it) }
        }
        if (prefs.ipv6) {
            addAddress(prefs.tunnelIpv6Address, prefs.tunnelIpv6Prefix)
            addRoute("::", 0)
            prefs.dnsIpv6.takeIf { it.isNotEmpty() }?.also { addDnsServer(it) }
        }

        prefs.apps?.forEach { appName ->
            if (appName != null) {
                try {
                    if (prefs.bypassSelectedApps) {
                        addDisallowedApplication(appName)
                    } else {
                        addAllowedApplication(appName)
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    if (BuildConfig.DEBUG) {
                        AppLogger.d("Package not found: $appName", e)
                    }
                }
            }
        }
        if (prefs.bypassSelectedApps || prefs.apps.isNullOrEmpty())
            addDisallowedApplication(BuildConfig.APPLICATION_ID)
    }

    private fun stopService() {
        tunFd?.let {
            try {
                it.close()
            } catch (ignored: IOException) {
            } finally {
                tunFd = null
            }
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            TProxyStopService()
        }
        exit()
    }

    @Suppress("SameParameterValue")
    private fun createNotification(channelName: String) {
        val i = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, channelName)
        val notify = notification.setContentTitle(getString(R.string.app_name))
            .setContentText("VPN aktif")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pi)
            .setOngoing(true) // Make notification persistent so service isn't killed
            .setPriority(NotificationCompat.PRIORITY_LOW) // Keep it visible but not intrusive
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false) // Don't show timestamp to reduce notification updates
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notify)
        } else {
            startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    private fun exit() {
        val stopIntent = Intent(ACTION_STOP)
        stopIntent.setPackage(application.packageName)
        sendBroadcast(stopIntent)
        stopSelf()
    }

    @Suppress("SameParameterValue")
    private fun initNotificationChannel(channelName: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val name: CharSequence = getString(R.string.app_name)
        val channel = NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_LOW).apply {
            // Set to LOW importance to reduce notification intrusiveness while keeping service alive
            // The service will still run in foreground, but notification won't be as prominent
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private val isServiceRunning = AtomicBoolean(false)
        
        fun isRunning(): Boolean = isServiceRunning.get()
        
        const val ACTION_CONNECT: String = "com.simplexray.an.CONNECT"
        const val ACTION_DISCONNECT: String = "com.simplexray.an.DISCONNECT"
        const val ACTION_START: String = "com.simplexray.an.START"
        const val ACTION_STOP: String = "com.simplexray.an.STOP"
        const val ACTION_LOG_UPDATE: String = "com.simplexray.an.LOG_UPDATE"
        const val ACTION_RELOAD_CONFIG: String = "com.simplexray.an.RELOAD_CONFIG"
        const val EXTRA_LOG_DATA: String = "log_data"
        private const val TAG = "VpnService"
        private const val BROADCAST_DELAY_MS: Long = 3000

        init {
            System.loadLibrary("hev-socks5-tunnel")
        }

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStartService(configPath: String, fd: Int)

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStopService()

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyGetStats(): LongArray?

        fun getNativeLibraryDir(context: Context?): String? {
            if (context == null) {
                AppLogger.e("Context is null")
                return null
            }
            try {
                val applicationInfo = context.applicationInfo
                if (applicationInfo != null) {
                    val nativeLibraryDir = applicationInfo.nativeLibraryDir
                    AppLogger.d("Native Library Directory: $nativeLibraryDir")
                    return nativeLibraryDir
                } else {
                    AppLogger.e("ApplicationInfo is null")
                    return null
                }
            } catch (e: Exception) {
                AppLogger.e("Error getting native library dir", e)
                return null
            }
        }

        private fun getTproxyConf(prefs: Preferences): String {
            val confBuilder = StringBuilder()
            confBuilder.append("""misc:
  task-stack-size: ${prefs.taskStackSize}
tunnel:
  mtu: ${prefs.tunnelMtu}
socks5:
  port: ${prefs.socksPort}
  address: '${prefs.socksAddress}'
  udp: '${if (prefs.udpInTcp) "tcp" else "udp"}'
""")
            // Use secure credential storage instead of plaintext
            // CVE-2025-0007: Fix for plaintext password storage
            val secureStorage = SecureCredentialStorage.getInstance(applicationContext)
            val username = secureStorage?.getCredential("socks5_username") ?: prefs.socksUsername
            val password = secureStorage?.getCredential("socks5_password") ?: run {
                val plaintextPassword = prefs.socksPassword
                if (plaintextPassword.isNotEmpty()) {
                    secureStorage?.migrateFromPlaintext(prefs.socksUsername, plaintextPassword)
                    plaintextPassword
                } else {
                    ""
                }
            }
            
            if (username.isNotEmpty() && password.isNotEmpty()) {
                // Password written to config file is required for SOCKS5 server functionality
                // Credentials are now encrypted in storage, reducing exposure window
                confBuilder.append("  username: '$username'\n")
                confBuilder.append("  password: '$password'\n")
            }
            return confBuilder.toString()
        }
    }
}
