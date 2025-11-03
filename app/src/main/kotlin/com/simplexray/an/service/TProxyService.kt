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
import com.simplexray.an.service.XrayProcessManager
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
import java.util.concurrent.atomic.AtomicReference
import java.lang.Process

class TProxyService : VpnService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private val logBroadcastBuffer: MutableList<String> = mutableListOf()
    private val broadcastLogsRunnable = Runnable {
        synchronized(logBroadcastBuffer) {
            if (logBroadcastBuffer.isNotEmpty()) {
                val logUpdateIntent = Intent(ACTION_LOG_UPDATE)
                logUpdateIntent.setPackage(application.packageName)
                logUpdateIntent.putStringArrayListExtra(
                    EXTRA_LOG_DATA, ArrayList(logBroadcastBuffer)
                )
                sendBroadcast(logUpdateIntent)
                logBroadcastBuffer.clear()
                AppLogger.d("Broadcasted a batch of logs.")
            }
        }
    }

    private fun findAvailablePort(excludedPorts: Set<Int>): Int? {
        (10000..65535)
            .shuffled()
            .forEach { port ->
                if (port in excludedPorts) return@forEach
                runCatching {
                    ServerSocket(port).use { socket ->
                        socket.reuseAddress = true
                    }
                    port
                }.onFailure {
                    AppLogger.d("Port $port unavailable: ${it.message}")
                }.onSuccess {
                    return port
                }
            }
        return null
    }

    private lateinit var logFileManager: LogFileManager

    // Data class to hold both process and reloading state atomically
    private data class ProcessState(
        val process: Process?,
        val reloading: Boolean
    )

    // Use single AtomicReference for thread-safe process state management
    // This prevents race conditions when reading/updating both process and reloading flag together
    private val processState = AtomicReference(ProcessState(null, false))
    private var tunFd: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        logFileManager = LogFileManager(this)
        AppLogger.d("TProxyService created.")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
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
                        ProcessState(state.process, reloading = true)
                    }
                    currentState.process?.destroy()
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
                    ProcessState(state.process, reloading = true)
                }
                currentState.process?.destroy()
                serviceScope.launch { runXrayProcess() }
                return START_STICKY
            }

            ACTION_START -> {
                logFileManager.clearLogs()
                val prefs = Preferences(this)
                if (prefs.disableVpn) {
                    serviceScope.launch { runXrayProcess() }
                    val successIntent = Intent(ACTION_START)
                    successIntent.setPackage(application.packageName)
                    sendBroadcast(successIntent)

                    @Suppress("SameParameterValue") val channelName = "nosocks"
                    initNotificationChannel(channelName)
                    createNotification(channelName)

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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(broadcastLogsRunnable)
        broadcastLogsRunnable.run()
        serviceScope.cancel()
        AppLogger.d("TProxyService destroyed.")
        // Removed exitProcess(0) - let Android handle service lifecycle properly
        // exitProcess() forcefully kills the entire app process which prevents proper cleanup
    }

    override fun onRevoke() {
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
            val configContent = File(selectedConfigPath).readText()
            val apiPort = findAvailablePort(extractPortsFromJson(configContent)) ?: return
            prefs.apiPort = apiPort
            AppLogger.d("Found and set API port: $apiPort")

            val processBuilder = getProcessBuilder(xrayPath)
            // Update process manager ports for observers
            XrayProcessManager.updateFrom(applicationContext)
            currentProcess = processBuilder.start()
            // Atomically update process state, preserving reloading flag
            processState.updateAndGet { state ->
                ProcessState(currentProcess, state.reloading)
            }
            
            // Log process PID for debugging
            try {
                processPid = currentProcess.javaClass.getMethod("pid").invoke(currentProcess) as? Long ?: -1L
                AppLogger.i("Xray process started successfully with PID: $processPid")
            } catch (e: Exception) {
                AppLogger.w("Could not get process PID", e)
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
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            logFileManager.appendLog(it)
                            synchronized(logBroadcastBuffer) {
                                logBroadcastBuffer.add(it)
                                if (!handler.hasCallbacks(broadcastLogsRunnable)) {
                                    handler.postDelayed(broadcastLogsRunnable, BROADCAST_DELAY_MS)
                                }
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
            
            // Properly cleanup process with timeout
            currentProcess?.let { proc ->
                try {
                    // Try graceful shutdown first
                    proc.destroy()
                    
                    // Wait up to 5 seconds for graceful shutdown
                    val exited = try {
                        proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        AppLogger.d("Process waitFor interrupted")
                        false
                    }
                    
                    if (!exited) {
                        // Force kill if still running after timeout
                        AppLogger.w("Process (PID: $processPid) did not exit gracefully, forcing termination")
                        try {
                            proc.destroyForcibly()
                            proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                        } catch (e: Exception) {
                            AppLogger.w("Error force killing process", e)
                        }
                    } else {
                        val exitValue = try {
                            proc.exitValue()
                        } catch (e: IllegalThreadStateException) {
                            -1
                        }
                        AppLogger.d("Process (PID: $processPid) exited with code: $exitValue")
                    }
                } catch (e: Exception) {
                    AppLogger.e("Error cleaning up process (PID: $processPid)", e)
                }
            }
            
            // Atomically check reloading flag and clear process reference if it matches
            val wasReloading = processState.getAndUpdate { state ->
                if (state.process === currentProcess) {
                    // Clear process and reset reloading flag atomically
                    ProcessState(null, reloading = false)
                } else {
                    // Keep current state, only reset reloading flag if it was set
                    ProcessState(state.process, reloading = false)
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
        serviceScope.cancel()
        AppLogger.d("CoroutineScope cancelled.")

        // Atomically get and clear process state
        val oldState = processState.getAndSet(ProcessState(null, false))
        oldState.process?.let { proc ->
            try {
                val pid = try {
                    proc.javaClass.getMethod("pid").invoke(proc) as? Long ?: -1L
                } catch (e: Exception) {
                    -1L
                }
                AppLogger.d("Stopping xray process (PID: $pid)")
                
                // Try graceful shutdown
                proc.destroy()
                try {
                    val exited = proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                    if (!exited) {
                        AppLogger.w("Process (PID: $pid) did not exit gracefully, forcing termination")
                        proc.destroyForcibly()
                        proc.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)
                    }
                } catch (e: InterruptedException) {
                    AppLogger.d("Process wait interrupted during stop")
                    proc.destroyForcibly()
                } catch (e: Exception) {
                    AppLogger.w("Error waiting for process termination", e)
                    proc.destroyForcibly()
                }
            } catch (e: Exception) {
                AppLogger.e("Error stopping xray process", e)
            }
        }
        AppLogger.d("processState cleared.")

        AppLogger.d("Calling stopService (stopping VPN).")
        stopService()
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
            appName?.let { name ->
                try {
                    when {
                        prefs.bypassSelectedApps -> addDisallowedApplication(name)
                        else -> addAllowedApplication(name)
                    }
                } catch (ignored: PackageManager.NameNotFoundException) {
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
            .setSmallIcon(R.drawable.ic_stat_name).setContentIntent(pi).build()
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
        val channel = NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
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
            var tproxyConf = """misc:
  task-stack-size: ${prefs.taskStackSize}
tunnel:
  mtu: ${prefs.tunnelMtu}
"""
            tproxyConf += """socks5:
  port: ${prefs.socksPort}
  address: '${prefs.socksAddress}'
  udp: '${if (prefs.udpInTcp) "tcp" else "udp"}'
"""
            if (prefs.socksUsername.isNotEmpty() && prefs.socksPassword.isNotEmpty()) {
                tproxyConf += "  username: '" + prefs.socksUsername + "'\n"
                tproxyConf += "  password: '" + prefs.socksPassword + "'\n"
            }
            return tproxyConf
        }
    }
}
