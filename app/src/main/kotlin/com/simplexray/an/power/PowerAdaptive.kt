package com.simplexray.an.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.simplexray.an.config.ApiConfig
import java.util.concurrent.atomic.AtomicBoolean

object PowerAdaptive {
    private val screenOn = AtomicBoolean(true)
    private val powerSave = AtomicBoolean(false)
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerSave.set(pm.isPowerSaveMode)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        appContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> screenOn.set(true)
                    Intent.ACTION_SCREEN_OFF -> screenOn.set(false)
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        val pmLocal = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                        powerSave.set(pmLocal.isPowerSaveMode)
                    }
                }
            }
        }, filter)
    }

    fun intervalMs(context: Context): Long {
        val adaptive = ApiConfig.isAdaptive(context)
        val base = ApiConfig.getBaseIntervalMs(context)
        if (!adaptive) return base
        val off = ApiConfig.getScreenOffIntervalMs(context)
        val idle = ApiConfig.getIdleIntervalMs(context)
        return when {
            !screenOn.get() -> off
            powerSave.get() -> idle
            else -> base
        }
    }
}

