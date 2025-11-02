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
            pb.directory(context.filesDir)
            pb.redirectErrorStream(true)
            val logFile = File(context.filesDir, "xray.log")
            pb.redirectOutput(logFile)
            val p = pb.start()
            procRef.set(p)
            retryCount.set(0)
            
            // Start log monitoring
            startLogMonitoring(p, logFile)
            
            // Start process health monitoring with auto-retry
            startProcessMonitoring(context, bin, cfg, maxRetries, retryDelayMs)
            
            val pid = try {
                p.javaClass.getMethod("pid").invoke(p) as? Long ?: -1L
            } catch (e: Exception) {
                -1L
            }
            Log.i(TAG, "xray started pid=$pid bin=${bin.absolutePath}")
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
                        Log.w(TAG, "Process died unexpectedly, attempting restart")
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
     */
    private fun startLogMonitoring(process: Process, logFile: File) {
        logMonitorJob?.cancel()
        logMonitorJob = monitoringScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null && isActive) {
                    line?.let {
                        logCallback?.invoke(it)
                        // Also append to log file
                        try {
                            logFile.appendText("$it\n")
                        } catch (e: Exception) {
                            // Ignore log file write errors
                        }
                    }
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

