package com.simplexray.an.topology

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TopologyLayoutStore {
    private const val PREF = "topology_layout"
    private const val KEY_POS = "positions"
    private val gson = Gson()

    fun load(context: Context): MutableMap<String, Pair<Float, Float>> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_POS, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<Map<String, List<Float>>>() {}.type
            val map: Map<String, List<Float>> = gson.fromJson(json, type)
            map.mapNotNull { (id, list) ->
                if (list.size >= 2) id to (list[0].coerceIn(0f, 1f) to list[1].coerceIn(0f, 1f)) else null
            }.toMap().toMutableMap()
        } catch (_: Throwable) {
            mutableMapOf()
        }
    }

    fun save(context: Context, positions: Map<String, Pair<Float, Float>>) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val serializable = positions.mapValues { listOf(it.value.first, it.value.second) }
        prefs.edit().putString(KEY_POS, gson.toJson(serializable)).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_POS).apply()
    }
}

