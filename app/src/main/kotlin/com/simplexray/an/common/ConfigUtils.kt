package com.simplexray.an.common

import android.util.Log
import com.simplexray.an.BuildConfig
import com.simplexray.an.prefs.Preferences
import org.json.JSONException
import org.json.JSONObject

/**
 * Utility functions for configuration file manipulation
 * TODO: Add config schema validation
 * TODO: Implement config versioning support
 * TODO: Add config sanitization to remove sensitive data
 * TODO: Consider adding config diff functionality
 */
object ConfigUtils {
    private const val TAG = "ConfigUtils"

    @Throws(JSONException::class)
    fun formatConfigContent(content: String): String {
        val maxSize = 10 * 1024 * 1024 // 10MB limit
        if (content.length > maxSize) {
            throw JSONException("Config content too large: ${content.length} bytes (max: $maxSize)")
        }
        val jsonObject = JSONObject(content)
        (jsonObject["log"] as? JSONObject)?.apply {
            val accessValue = optString("access", "")
            val errorValue = optString("error", "")
            if (has("access") && accessValue != "none") {
                remove("access")
                Log.d(TAG, "Removed log.access")
            }
            if (has("error") && errorValue != "none") {
                remove("error")
                Log.d(TAG, "Removed log.error")
            }
        }
        var formattedContent = jsonObject.toString(2)
        formattedContent = formattedContent.replace("\\/", "/")
        return formattedContent
    }

    @Throws(JSONException::class)
    fun injectStatsService(prefs: Preferences, configContent: String): String {
        val maxSize = 10 * 1024 * 1024 // 10MB limit
        if (configContent.length > maxSize) {
            throw JSONException("Config content too large: ${configContent.length} bytes (max: $maxSize)")
        }
        val jsonObject = JSONObject(configContent)

        // 1. API section - enable StatsService
        val apiObject = JSONObject()
        apiObject.put("tag", "api")
        val servicesArray = org.json.JSONArray()
        servicesArray.put("StatsService")
        apiObject.put("services", servicesArray)
        jsonObject.put("api", apiObject)
        
        // 2. Stats section - enable stats collection
        jsonObject.put("stats", JSONObject())

        // 3. Policy section - enable stats tracking
        val policyObject = if (jsonObject.has("policy")) {
            jsonObject.getJSONObject("policy")
        } else {
            JSONObject()
        }

        // System-level stats
        val systemObject = if (policyObject.has("system")) {
            policyObject.getJSONObject("system")
        } else {
            JSONObject()
        }
        systemObject.put("statsOutboundUplink", true)
        systemObject.put("statsOutboundDownlink", true)
        systemObject.put("statsInboundUplink", true)
        systemObject.put("statsInboundDownlink", true)
        policyObject.put("system", systemObject)

        // User-level stats (level 0)
        val levelsObject = if (policyObject.has("levels")) {
            policyObject.getJSONObject("levels")
        } else {
            JSONObject()
        }
        val level0Object = if (levelsObject.has("0")) {
            levelsObject.getJSONObject("0")
        } else {
            JSONObject()
        }
        level0Object.put("statsUserUplink", true)
        level0Object.put("statsUserDownlink", true)
        levelsObject.put("0", level0Object)
        policyObject.put("levels", levelsObject)

        jsonObject.put("policy", policyObject)

        // 4. Inbounds - add dokodemo-door API listener
        val inboundsArray = if (jsonObject.has("inbounds")) {
            jsonObject.getJSONArray("inbounds")
        } else {
            org.json.JSONArray().also { jsonObject.put("inbounds", it) }
        }
        
        // Remove any existing api-in inbounds first to prevent duplicates
        val inboundsToRemove = mutableListOf<Int>()
        for (i in 0 until inboundsArray.length()) {
            val inbound = inboundsArray.getJSONObject(i)
            val tag = inbound.optString("tag", "")
            if (tag == "api-in") {
                inboundsToRemove.add(i)
            }
        }
        // Remove in reverse order to maintain indices
        for (i in inboundsToRemove.reversed()) {
            inboundsArray.remove(i)
            Log.d(TAG, "Removed existing api-in inbound to prevent duplicate")
        }
        
        // Check if API inbound already exists with correct settings
        var hasApiInbound = false
        for (i in 0 until inboundsArray.length()) {
            val inbound = inboundsArray.getJSONObject(i)
            val protocol = inbound.optString("protocol", "")
            val port = inbound.optInt("port", -1)
            val tag = inbound.optString("tag", "")
            if (protocol == "dokodemo-door" && port == prefs.apiPort && tag == "api-in") {
                hasApiInbound = true
                break
            }
        }
        
        if (!hasApiInbound) {
            val apiInbound = JSONObject().apply {
                put("listen", "127.0.0.1")
                put("port", prefs.apiPort)
                put("protocol", "dokodemo-door")
                put("tag", "api-in")
                put("settings", JSONObject().apply {
                    put("address", "127.0.0.1")
                })
            }
            inboundsArray.put(apiInbound)
            Log.d(TAG, "Added API inbound listener on port ${prefs.apiPort}")
        }

        // 5. Routing - route API inbound to API outbound
        val routingObject = if (jsonObject.has("routing")) {
            jsonObject.getJSONObject("routing")
        } else {
            JSONObject().also { jsonObject.put("routing", it) }
        }
        
        val rulesArray = if (routingObject.has("rules")) {
            routingObject.getJSONArray("rules")
        } else {
            org.json.JSONArray().also { routingObject.put("rules", it) }
        }
        
        // Check if API routing rule already exists
        var hasApiRule = false
        for (i in 0 until rulesArray.length()) {
            val rule = rulesArray.getJSONObject(i)
            val outboundTag = rule.optString("outboundTag", "")
            if (outboundTag == "api") {
                hasApiRule = true
                break
            }
        }
        
        if (!hasApiRule) {
            val apiRule = JSONObject().apply {
                put("type", "field")
                put("inboundTag", org.json.JSONArray().apply {
                    put("api-in")
                })
                put("outboundTag", "api")
            }
            rulesArray.put(apiRule)
            Log.d(TAG, "Added API routing rule")
        }

        // 6. Outbounds - ensure API outbound exists (optional, for routing)
        val outboundsArray = if (jsonObject.has("outbounds")) {
            jsonObject.getJSONArray("outbounds")
        } else {
            org.json.JSONArray().also { jsonObject.put("outbounds", it) }
        }
        
        var hasApiOutbound = false
        for (i in 0 until outboundsArray.length()) {
            val outbound = outboundsArray.getJSONObject(i)
            val tag = outbound.optString("tag", "")
            if (tag == "api") {
                hasApiOutbound = true
                break
            }
        }
        
        if (!hasApiOutbound) {
            // Add a simple API outbound (required for routing)
            val apiOutbound = JSONObject().apply {
                put("protocol", "freedom")
                put("tag", "api")
            }
            outboundsArray.put(apiOutbound)
            Log.d(TAG, "Added API outbound")
        }

        Log.d(TAG, "Stats service injected successfully with API port: ${prefs.apiPort}")
        return jsonObject.toString(2)
    }

    fun extractPortsFromJson(jsonContent: String): Set<Int> {
        val maxSize = 10 * 1024 * 1024 // 10MB limit
        if (jsonContent.length > maxSize) {
            Log.e(TAG, "JSON content too large for port extraction: ${jsonContent.length} bytes")
            return emptySet()
        }
        val ports = HashSet<Int>(64) // Pre-allocate capacity
        try {
            val jsonObject = JSONObject(jsonContent)
            extractPortsRecursive(jsonObject, ports)
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing JSON for port extraction", e)
        }
        if (BuildConfig.DEBUG && ports.isNotEmpty()) {
            Log.d(TAG, "Extracted ${ports.size} ports")
        }
        return ports
    }

    private fun extractPortsRecursive(jsonObject: JSONObject, ports: MutableSet<Int>) {
        extractPortsRecursive(jsonObject, ports, 0, MAX_RECURSION_DEPTH)
    }
    
    private fun extractPortsRecursive(jsonObject: JSONObject, ports: MutableSet<Int>, currentDepth: Int, maxDepth: Int) {
        if (currentDepth >= maxDepth) {
            Log.w(TAG, "Maximum recursion depth ($maxDepth) reached, stopping port extraction")
            return
        }
        
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = jsonObject.get(key)) {
                is Int -> {
                    if (value >= 1 && value <= 65535) {
                        ports.add(value)
                    }
                }

                is JSONObject -> {
                    extractPortsRecursive(value, ports, currentDepth + 1, maxDepth)
                }

                is org.json.JSONArray -> {
                    val arrayLength = value.length()
                    for (i in 0 until arrayLength) {
                        val item = value.get(i)
                        if (item is JSONObject) {
                            extractPortsRecursive(item, ports, currentDepth + 1, maxDepth)
                        }
                    }
                }
            }
        }
    }
    
    companion object {
        private const val MAX_RECURSION_DEPTH = 50 // Limit recursion to prevent stack overflow
    }
}

