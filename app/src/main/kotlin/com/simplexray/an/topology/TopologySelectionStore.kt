package com.simplexray.an.topology

import android.content.Context
import com.google.gson.Gson

/**
 * Store for persistent node selection
 */
object TopologySelectionStore {
    private const val PREF = "topology_selection"
    private const val KEY_SELECTED_NODE_ID = "selected_node_id"
    private val gson = Gson()

    fun loadSelectedNodeId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_NODE_ID, null)
    }

    fun saveSelectedNodeId(context: Context, nodeId: String?) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_NODE_ID, nodeId).apply()
    }

    fun clearSelection(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SELECTED_NODE_ID).apply()
    }
}
