package com.simplexray.an.xray

import android.content.Context
import android.os.Build
import com.simplexray.an.common.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

object XrayCoreLauncher {
    private val procRef = AtomicReference<Process?>(null)
    private val pidRef = AtomicReference<Long>(-1L) // Store PID separately for fallback kill
    private val retryCount = AtomicInteger(0)
    private var logMonitorJob: Job? = null
    private var retryJob: Job? = null
    private val monitoringScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var logCallback: ((String) -> Unit)? = null

    fun isRunning(): Boolean = procRef.get()?.isAlive == true

    /**
     * Start Xray with auto-retry and log monitoring
     */
    @Synchronized
    fun start(
        context: Context,
        configFile: File? = null,
        maxRetries: Int = 3,
        retryDelayMs: Long = 5000,
        onLogLine: ((String) -> Unit)? = null
    ): Boolean {
        if (isRunning()) return true
        
        // Validate ABI before starting
        val validation = XrayAbiValidator.validateCurrentAbi(context)
        if (!validation.isValid) {
            AppLogger.e("ABI validation failed: ${validation.message}")
            // Continue anyway, but log the issue
        }
        
        AssetsInstaller.ensureAssets(context)
        val bin = copyExecutable(context) ?: run {
            AppLogger.e("xray binary not found in native libs")
            return false
        }
        val cfg = configFile ?: File(context.filesDir, "xray.json")
        if (!cfg.exists()) {
            AppLogger.w("config not found: ${cfg.absolutePath}; writing default")
            val def = XrayConfigBuilder.defaultConfig("127.0.0.1", 10085)
            XrayConfigBuilder.writeConfig(context, def)
        }
        
        // Patch config with inbound/outbound/transport merge
        try {
            XrayConfigPatcher.patchConfig(context, cfg.name)
        } catch (e: Exception) {
            AppLogger.w("Config patching failed, continuing with existing config", e)
        }
        
        logCallback = onLogLine
        return startProcess(context, bin, cfg, maxRetries, retryDelayMs)
    }

    private fun startProcess(
        context: Context,
        bin: File,
        cfg: File,
        maxRetries: Int,
        retryDelayMs: Long
    ): Boolean {
        return try {
            val pb = ProcessBuilder(bin.absolutePath, "-config", cfg.absolutePath)
            val filesDir = context.filesDir
            val cacheDir = context.cacheDir
            val environment = pb.environment()
            
            // Restrict filesystem access to prevent SELinux denials
            // Set HOME and TMPDIR to app-accessible directories
            environment["HOME"] = filesDir.path
            environment["TMPDIR"] = cacheDir.path
            environment["TMP"] = cacheDir.path
            
            pb.directory(filesDir)
            pb.redirectErrorStream(true)
            val logFile = File(filesDir, "xray.log")
            pb.redirectOutput(logFile)
            val p = pb.start()
            procRef.set(p)
            retryCount.set(0)
            
            val pid = try {
                p.javaClass.getMethod("pid").invoke(p) as? Long ?: -1L
            } catch (e: Exception) {
                -1L
            }
            pidRef.set(pid) // Store PID for fallback kill
            AppLogger.i("xray process started pid=$pid bin=${bin.absolutePath}")
            
            // Wait a short time to check if process stays alive (prevents immediate crashes)
            // This helps catch configuration errors or permission issues immediately
            Thread.sleep(500) // Wait 500ms
            
            // Check if process is still alive after initial wait
            if (!p.isAlive) {
                val exitCode = try {
                    p.exitValue()
                } catch (e: IllegalThreadStateException) {
                    -1
                }
                AppLogger.e("xray process died immediately after start (exit code: $exitCode)")
                
                // Try to read log file for error information
                try {
                    if (logFile.exists() && logFile.length() > 0) {
                        val errorLog = logFile.readText().take(500)
                        AppLogger.e("xray error log: $errorLog")
                    }
                } catch (e: Exception) {
                    AppLogger.w("Could not read error log", e)
                }
                
                procRef.set(null)
                pidRef.set(-1L)
                attemptRetry(context, bin, cfg, maxRetries, retryDelayMs)
                return false
            }
            
            // Start log monitoring
            startLogMonitoring(p, logFile)
            
            // Start process health monitoring with auto-retry
            startProcessMonitoring(context, bin, cfg, maxRetries, retryDelayMs)
            
            AppLogger.i("xray successfully started and running pid=$pid")
            true
        } catch (t: Throwable) {
            AppLogger.e("failed to start xray", t)
            procRef.set(null)
            pidRef.set(-1L)
            attemptRetry(context, bin, cfg, maxRetries, retryDelayMs)
            false
        }
    }

    /**
     * Monitor process health and restart on failure
     */
    private fun startProcessMonitoring(
        context: Context,
        bin: File,
        cfg: File,
        maxRetries: Int,
        retryDelayMs: Long
    ) {
        retryJob?.cancel()
        retryJob = monitoringScope.launch {
            while (isActive) {
                delay(10000) // Check every 10 seconds
                val proc = procRef.get()
                if (proc == null || !proc.isAlive) {
                    val current = procRef.get()
                    if (current != null && !current.isAlive) {
                        val exitCode = try {
                            current.exitValue()
                        } catch (e: IllegalThreadStateException) {
                            -1
                        }
                        val pid = pidRef.get() // Use stored PID instead of trying to get from dead process
                        AppLogger.w("Process died unexpectedly (PID: $pid, exit code: $exitCode), attempting restart")
                        
                        // Clear process and PID references
                        procRef.set(null)
                        pidRef.set(-1L)
                        
                        // Try to read log file for error information
                        val logFile = File(context.filesDir, "xray.log")
                        try {
                            if (logFile.exists() && logFile.length() > 0) {
                                val errorLog = logFile.readText().takeLast(500) // Last 500 chars
                                AppLogger.w("Recent xray log: $errorLog")
                            }
                        } catch (e: Exception) {
                            AppLogger.w("Could not read error log", e)
                        }
                        
                        val retries = retryCount.incrementAndGet()
                        if (retries <= maxRetries) {
                            delay(retryDelayMs)
                            if (startProcess(context, bin, cfg, maxRetries, retryDelayMs)) {
                                AppLogger.i("Successfully restarted after failure")
                                return@launch
                            }
                        } else {
                            AppLogger.e("Max retries ($maxRetries) reached, stopping")
                            return@launch
                        }
                    }
                }
            }
        }
    }

    /**
     * Monitor log file and stream to callback
     * Note: Process output is redirected to logFile, so we read from the file instead of inputStream
     */
    private fun startLogMonitoring(process: Process, logFile: File) {
        logMonitorJob?.cancel()
        logMonitorJob = monitoringScope.launch {
            try {
                var lastPosition = 0L
                // Wait a bit for log file to be created
                delay(1000)
                
                while (isActive && process.isAlive) {
                    try {
                        if (logFile.exists() && logFile.length() > lastPosition) {
                            logFile.inputStream().use { stream ->
                                stream.skip(lastPosition)
                                BufferedReader(InputStreamReader(stream)).use { reader ->
                                    var line: String?
                                    while (reader.readLine().also { line = it } != null && isActive) {
                                        line?.let {
                                            logCallback?.invoke(it)
                                        }
                                    }
                                }
                            }
                            lastPosition = logFile.length()
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Error reading log file", e)
                    }
                    delay(1000) // Check every second
                }
            } catch (e: Exception) {
                AppLogger.d("Log monitoring stopped: ${e.message}")
            }
        }
    }

    /**
     * Attempt retry with exponential backoff
     */
    private fun attemptRetry(
        context: Context,
        bin: File,
        cfg: File,
        maxRetries: Int,
        retryDelayMs: Long
    ) {
        val retries = retryCount.incrementAndGet()
        if (retries <= maxRetries) {
            retryJob = monitoringScope.launch {
                val backoff = retryDelayMs * retries // Exponential backoff
                AppLogger.i("Retrying start in ${backoff}ms (attempt $retries/$maxRetries)")
                delay(backoff)
                startProcess(context, bin, cfg, maxRetries, retryDelayMs)
            }
        } else {
            AppLogger.e("Max retries reached, giving up")
        }
    }

    @Synchronized
    fun stop(): Boolean {
        logMonitorJob?.cancel()
        retryJob?.cancel()
        logCallback = null
        val p = procRef.getAndSet(null)
        val pid = pidRef.getAndSet(-1L)
        
        // Try to kill using Process reference first
        if (p != null) {
            return try {
                if (p.isAlive) {
                    p.destroy()
                    try {
                        val exited = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                        if (!exited) {
                            AppLogger.w("Process (PID: $pid) did not exit gracefully, forcing termination")
                            p.destroyForcibly()
                            p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)
                        }
                    } catch (e: InterruptedException) {
                        AppLogger.d("Process wait interrupted during stop")
                        p.destroyForcibly()
                    } catch (e: Exception) {
                        AppLogger.w("Error waiting for process termination", e)
                        p.destroyForcibly()
                    }
                }
                true
            } catch (t: Throwable) {
                AppLogger.e("failed to stop xray via Process reference (PID: $pid)", t)
                // Fall through to PID-based kill
                killProcessByPid(pid)
            }
        }
        
        // Fallback: kill by PID if Process reference is unavailable
        if (pid != -1L) {
            return killProcessByPid(pid)
        }
        
        return true
    }
    
    /**
     * Kill process by PID as fallback when Process reference is invalid.
     * This is critical when app goes to background and Process reference becomes stale.
     */
    private fun killProcessByPid(pid: Long): Boolean {
        if (pid == -1L) {
            return true
        }
        
        return try {
            AppLogger.d("Killing xray process by PID: $pid (Process reference unavailable)")
            
            // Check if process is still alive
            val isAlive = isProcessAlive(pid.toInt())
            if (!isAlive) {
                AppLogger.d("Process (PID: $pid) already dead")
                return true
            }
            
            // Use Android Process.killProcess for same-UID processes
            android.os.Process.killProcess(pid.toInt())
            AppLogger.d("Sent kill signal to process PID: $pid")
            
            // Wait a bit to see if it exits
            Thread.sleep(500)
            
            // Verify process is dead
            val stillAlive = isProcessAlive(pid.toInt())
            if (stillAlive) {
                AppLogger.w("Process (PID: $pid) still alive after killProcess, trying force kill")
                // Last resort: try kill -9 via Runtime.exec
                try {
                    Runtime.getRuntime().exec("kill -9 $pid").waitFor()
                    AppLogger.d("Force killed process PID: $pid")
                } catch (e: Exception) {
                    AppLogger.e("Failed to force kill process PID: $pid", e)
                    return false
                }
            } else {
                AppLogger.d("Process (PID: $pid) successfully killed")
            }
            
            true
        } catch (e: SecurityException) {
            AppLogger.e("Permission denied killing process PID: $pid", e)
            false
        } catch (e: Exception) {
            AppLogger.e("Error killing process by PID: $pid", e)
            false
        }
    }
    
    /**
     * Check if a process is alive by PID.
     * Uses API-appropriate method based on Android version.
     */
    private fun isProcessAlive(pid: Int): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // API 26+: Use getProcessState
                try {
                    android.os.Process.getProcessState(pid) != -1
                } catch (e: Exception) {
                    // Fallback: check /proc/PID directory exists
                    java.io.File("/proc/$pid").exists()
                }
            } else {
                // API < 26: Check /proc/PID directory exists
                java.io.File("/proc/$pid").exists()
            }
        } catch (e: Exception) {
            // If we can't check, assume it might be alive and try to kill
            true
        }
    }

    private fun copyExecutable(context: Context): File? {
        val libDir = context.applicationInfo.nativeLibraryDir ?: return null
        val src = File(libDir, "libxray.so")
        if (!src.exists()) {
            AppLogger.e("libxray.so not found at ${src.absolutePath}")
            // Try ABI validation for better error message
            val validation = XrayAbiValidator.validateCurrentAbi(context)
            AppLogger.e("Validation result: ${validation.message}")
            return null
        }
        val dst = File(context.filesDir, "xray_core")
        try {
            src.inputStream().use { ins -> dst.outputStream().use { outs -> ins.copyTo(outs) } }
            dst.setExecutable(true)
            if (!dst.canExecute()) {
                AppLogger.e("Failed to set executable permission on ${dst.absolutePath}")
                return null
            }
            return dst
        } catch (t: Throwable) {
            AppLogger.e("copyExecutable failed", t)
            return null
        }
    }
}

