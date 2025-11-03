package com.simplexray.an.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.simplexray.an.R
import com.simplexray.an.activity.MainActivity
import com.simplexray.an.service.TProxyService

class VpnWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Widget is added for the first time
    }

    override fun onDisabled(context: Context) {
        // Last widget is removed
    }

    companion object {
        private const val ACTION_TOGGLE_VPN = "com.simplexray.an.TOGGLE_VPN"
        private const val ACTION_OPEN_APP = "com.simplexray.an.OPEN_APP"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_vpn)
            
            val isServiceRunning = isVpnServiceRunning(context)
            
            // Update button state
            if (isServiceRunning) {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_connected))
                views.setInt(R.id.widget_toggle_button, "setBackgroundResource", R.drawable.widget_bg_connected)
            } else {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_disconnected))
                views.setInt(R.id.widget_toggle_button, "setBackgroundResource", R.drawable.widget_bg_disconnected)
            }

            // Toggle button click
            val toggleIntent = Intent(context, VpnWidget::class.java).apply {
                action = ACTION_TOGGLE_VPN
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent)

            // Open app click
            val openIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun isVpnServiceRunning(context: Context): Boolean {
            // Use modern ServiceStateChecker utility instead of deprecated APIs
            return com.simplexray.an.common.ServiceStateChecker.isVpnServiceRunning(context)
        }

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, VpnWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, VpnWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_TOGGLE_VPN) {
            // Toggle VPN state
            val isRunning = isVpnServiceRunning(context)
            if (isRunning) {
                // Stop VPN
                context.stopService(Intent(context, TProxyService::class.java))
            } else {
                // Start VPN - this requires MainActivity to handle
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("widget_toggle", true)
                }
                context.startActivity(mainIntent)
            }
            
            // Update widget UI
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
