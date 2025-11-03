package com.simplexray.an.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.simplexray.an.common.AppLogger

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
        AppLogger.d("QuickTileService created.")
    }

    override fun onStartListening() {
        super.onStartListening()
        AppLogger.d("QuickTileService started listening.")

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
        AppLogger.d("QuickTileService stopped listening.")
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: IllegalArgumentException) {
            AppLogger.w("Receiver not registered", e)
        }
    }

    override fun onClick() {
        super.onClick()
        AppLogger.d("QuickTileService clicked.")

        qsTile.run {
            if (state == Tile.STATE_INACTIVE) {
                if (VpnService.prepare(this@QuickTileService) != null) {
                    AppLogger.e("QuickTileService VPN not ready.")
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
        AppLogger.d("QuickTileService destroyed.")
    }

    private fun updateTileState(isActive: Boolean) {
        qsTile.apply {
            state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    private fun isVpnServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        // Use modern ServiceStateChecker utility instead of deprecated APIs
        return com.simplexray.an.common.ServiceStateChecker.isServiceRunning(context, serviceClass)
    }

    private fun startTProxyService(action: String) {
        Intent(this, TProxyService::class.java).apply {
            this.action = action
        }.also { intent ->
            startService(intent)
        }
    }

}