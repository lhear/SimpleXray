package com.simplexray.an.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class QuickTileService : TileService() {

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                TProxyService.ACTION_START -> updateTileState(true)
                TProxyService.ACTION_STOP -> updateTileState(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "QuickTileService created.")
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "QuickTileService started listening.")

        IntentFilter().apply {
            addAction(TProxyService.ACTION_START)
            addAction(TProxyService.ACTION_STOP)
        }.also { filter ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(
                    broadcastReceiver,
                    filter
                )
            }
        }

        updateTileState(isVpnServiceRunning(this, TProxyService::class.java))
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "QuickTileService stopped listening.")
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver not registered", e)
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "QuickTileService clicked.")

        qsTile.run {
            if (state == Tile.STATE_INACTIVE) {
                // Check if VPN permission is granted
                if (VpnService.prepare(this@QuickTileService) != null) {
                    // Permission not granted, launch the main activity to prompt for permission
                    Intent(
                        this@QuickTileService,
                        com.simplexray.an.activity.MainActivity::class.java
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }.also { intent ->
                        startActivity(intent)
                    }
                    return
                }
                startTProxyService(TProxyService.ACTION_START)
            } else {
                startTProxyService(TProxyService.ACTION_DISCONNECT)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "QuickTileService destroyed.")
    }

    private fun updateTileState(isActive: Boolean) {
        qsTile.apply {
            state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    private fun isVpnServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.getRunningServices(Int.MAX_VALUE).any { service ->
            serviceClass.name == service.service.className
        }
    }

    private fun startTProxyService(action: String) {
        Intent(this, TProxyService::class.java).apply {
            this.action = action
        }.also { intent ->
            startService(intent)
        }
    }

    companion object {
        private const val TAG = "QuickTileService"
    }
}