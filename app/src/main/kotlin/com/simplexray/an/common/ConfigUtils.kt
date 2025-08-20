package com.simplexray.an.common

import android.util.Log
import com.simplexray.an.prefs.Preferences
import org.json.JSONException
import org.json.JSONObject

object ConfigUtils {
    private const val TAG = "ConfigUtils"

    fun getRemarkFromConfig(config: String): String {
        try {
            val json = org.json.JSONObject(config)
            val outbounds = json.optJSONArray("outbounds")
            if (outbounds != null && outbounds.length() > 0) {
                val outbound = outbounds.getJSONObject(0)
                val remark = outbound.optString("remark", outbound.optString("ps", ""))
                if (remark.isNotEmpty()) {
                    return remark
                }
            }
        } catch (e: JSONException) {
            // Not a valid json, ignore
        }
        return ""
    }

    @Throws(JSONException::class)
    fun mergeWithTemplate(application: android.app.Application, outboundJson: String): String {
        val templateJson = application.assets.open("template").bufferedReader().use { it.readText() }
        val template = org.json.JSONObject(templateJson)
        val outbound = org.json.JSONObject(outboundJson)
        template.getJSONArray("outbounds").put(outbound)
        return template.toString(4)
    }

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

    @Throws(JSONException::class)
    fun injectStatsService(prefs: Preferences, configContent: String): String {
        val jsonObject = JSONObject(configContent)

        val apiObject = JSONObject()
        apiObject.put("tag", "api")
        apiObject.put("listen", "127.0.0.1:${prefs.apiPort}")
        val servicesArray = org.json.JSONArray()
        servicesArray.put("StatsService")
        apiObject.put("services", servicesArray)

        jsonObject.put("api", apiObject)
        jsonObject.put("stats", JSONObject())

        val policyObject = JSONObject()
        val systemObject = JSONObject()
        systemObject.put("statsOutboundUplink", true)
        systemObject.put("statsOutboundDownlink", true)
        policyObject.put("system", systemObject)

        jsonObject.put("policy", policyObject)

        return jsonObject.toString(2)
    }

    fun extractPortsFromJson(jsonContent: String): Set<Int> {
        val ports = mutableSetOf<Int>()
        try {
            val jsonObject = JSONObject(jsonContent)
            extractPortsRecursive(jsonObject, ports)
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing JSON for port extraction", e)
        }
        Log.d(TAG, "Extracted ports: $ports")
        return ports
    }

    private fun extractPortsRecursive(jsonObject: JSONObject, ports: MutableSet<Int>) {
        for (key in jsonObject.keys()) {
            when (val value = jsonObject.get(key)) {
                is Int -> {
                    if (value in 1..65535) {
                        ports.add(value)
                    }
                }

                is JSONObject -> {
                    extractPortsRecursive(value, ports)
                }

                is org.json.JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.get(i)
                        if (item is JSONObject) {
                            extractPortsRecursive(item, ports)
                        }
                    }
                }
            }
        }
    }

    fun saveConfig(content: String, filename: String, configDir: java.io.File): java.io.File {
        val file = java.io.File(configDir, filename)
        file.writeText(content)
        return file
    }
}

