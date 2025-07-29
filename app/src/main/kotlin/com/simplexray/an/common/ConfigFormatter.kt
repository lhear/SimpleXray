package com.simplexray.an.common

import android.util.Log
import org.json.JSONException
import org.json.JSONObject

object ConfigFormatter {
    private const val TAG = "ConfigFormatter"

    @Throws(JSONException::class)
    fun formatConfigContent(content: String): String {
        val jsonObject = JSONObject(content)
        (jsonObject["log"] as? JSONObject)?.apply {
            remove("access")?.also { Log.d(TAG, "Removed log.access") }
            remove("error")?.also { Log.d(TAG, "Removed log.error") }
        }
        var formattedContent = jsonObject.toString(2)
        formattedContent = formattedContent.replace("\\/", "/")
        return formattedContent
    }
}
