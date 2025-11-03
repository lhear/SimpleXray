package com.simplexray.an.xray

import android.content.Context
import android.os.Build
import android.util.Log
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
    private const val TAG = "XrayCore"
    private val procRef = AtomicReference<Process?>(null)
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
            Log.e(TAG, "ABI validation failed: ${validation.message}")
            // Continue anyway, but log the issue
        }
        
        AssetsInstaller.ensureAssets(context)
        val bin = copyExecutable(context) ?: run {
            Log.e(TAG, "xray binary not found in native libs")
            return false
        }
        val cfg = configFile ?: File(context.filesDir, "xray.json")
        if (!cfg.exists()) {
            Log.w(TAG, "config not found: ${cfg.absolutePath}; writing default")
            val def = XrayConfigBuilder.defaultConfig("127.0.0.1", 10085)
            XrayConfigBuilder.writeConfig(context, def)
        }
        
        // Patch config with inbound/outbound/transport merge
        try {
            XrayConfigPatcher.patchConfig(context, cfg.name)
        } catch (e: Exception) {
            Log.w(TAG, "Config patching failed, continuing with existing config", e)
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
            Log.i(TAG, "xray process started pid=$pid bin=${bin.absolutePath}")
            
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
                Log.e(TAG, "xray process died immediately after start (exit code: $exitCode)")
                
                // Try to read log file for error information
                try {
                    if (logFile.exists() && logFile.length() > 0) {
                        val errorLog = logFile.readText().take(500)
                        Log.e(TAG, "xray error log: $errorLog")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read error log", e)
                }
                
                procRef.set(null)
                attemptRetry(context, bin, cfg, maxRetries, retryDelayMs)
                return false
            }
            
            // Start log monitoring
            startLogMonitoring(p, logFile)
            
            // Start process health monitoring with auto-retry
            startProcessMonitoring(context, bin, cfg, maxRetries, retryDelayMs)
            
            Log.i(TAG, "xray successfully started and running pid=$pid")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "failed to start xray", t)
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
                        val pid = try {
                            current.javaClass.getMethod("pid").invoke(current) as? Long ?: -1L
                        } catch (e: Exception) {
                            -1L
                        }
                        Log.w(TAG, "Process died unexpectedly (PID: $pid, exit code: $exitCode), attempting restart")
                        
                        // Try to read log file for error information
                        val logFile = File(context.filesDir, "xray.log")
                        try {
                            if (logFile.exists() && logFile.length() > 0) {
                                val errorLog = logFile.readText().takeLast(500) // Last 500 chars
                                Log.w(TAG, "Recent xray log: $errorLog")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not read error log", e)
                        }
                        
                        val retries = retryCount.incrementAndGet()
                        if (retries <= maxRetries) {
                            delay(retryDelayMs)
                            if (startProcess(context, bin, cfg, maxRetries, retryDelayMs)) {
                                Log.i(TAG, "Successfully restarted after failure")
                                return@launch
                            }
                        } else {
                            Log.e(TAG, "Max retries ($maxRetries) reached, stopping")
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
                        Log.d(TAG, "Error reading log file", e)
                    }
                    delay(1000) // Check every second
                }
            } catch (e: Exception) {
                Log.d(TAG, "Log monitoring stopped", e)
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
                Log.i(TAG, "Retrying start in ${backoff}ms (attempt $retries/$maxRetries)")
                delay(backoff)
                startProcess(context, bin, cfg, maxRetries, retryDelayMs)
            }
        } else {
            Log.e(TAG, "Max retries reached, giving up")
        }
    }

    @Synchronized
    fun stop(): Boolean {
        logMonitorJob?.cancel()
        retryJob?.cancel()
        logCallback = null
        val p = procRef.getAndSet(null) ?: return true
        return try {
            p.destroy()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "failed to stop xray", t)
            false
        }
    }

    private fun copyExecutable(context: Context): File? {
        val libDir = context.applicationInfo.nativeLibraryDir ?: return null
        val src = File(libDir, "libxray.so")
        if (!src.exists()) {
            Log.e(TAG, "libxray.so not found at ${src.absolutePath}")
            // Try ABI validation for better error message
            val validation = XrayAbiValidator.validateCurrentAbi(context)
            Log.e(TAG, "Validation result: ${validation.message}")
            return null
        }
        val dst = File(context.filesDir, "xray_core")
        try {
            src.inputStream().use { ins -> dst.outputStream().use { outs -> ins.copyTo(outs) } }
            dst.setExecutable(true)
            if (!dst.canExecute()) {
                Log.e(TAG, "Failed to set executable permission on ${dst.absolutePath}")
                return null
            }
            return dst
        } catch (t: Throwable) {
            Log.e(TAG, "copyExecutable failed", t)
            return null
        }
    }
}

