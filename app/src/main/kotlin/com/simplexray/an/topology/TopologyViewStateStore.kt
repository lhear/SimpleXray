package com.simplexray.an.topology

import android.content.Context
import com.google.gson.Gson

data class ViewState(val scale: Float, val panX: Float, val panY: Float)

object TopologyViewStateStore {
    private const val PREF = "topology_view_state"
    private const val KEY = "state"
    private val gson = Gson()

    fun load(context: Context): ViewState? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null) ?: return null
        return try { gson.fromJson(json, ViewState::class.java) } catch (_: Throwable) { null }
    }

    fun save(context: Context, scale: Float, panXNorm: Float, panYNorm: Float) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val vs = ViewState(scale, panXNorm, panYNorm)
        prefs.edit().putString(KEY, gson.toJson(vs)).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

