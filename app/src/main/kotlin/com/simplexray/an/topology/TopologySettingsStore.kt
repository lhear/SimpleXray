package com.simplexray.an.topology

import android.content.Context

object TopologySettingsStore {
    private const val PREF = "topology_settings"
    private const val KEY_SHOW_LABELS = "show_labels"

    fun loadShowLabels(context: Context, default: Boolean = true): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_LABELS, default)
    }

    fun saveShowLabels(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_LABELS, value).apply()
    }
}

