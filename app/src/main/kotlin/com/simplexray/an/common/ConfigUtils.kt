package com.simplexray.an.common

import android.util.Log
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

    // TODO: Add input validation before formatting
    // PERF: formatConfigContent() parses entire JSON - should use streaming parser for large configs
    // MEMORY: JSONObject loads entire content into memory - can cause OOM with large configs
    @Throws(JSONException::class)
    fun formatConfigContent(content: String): String {
        // PERF: JSONObject constructor parses entire string - should validate size first
        val jsonObject = JSONObject(content)
        (jsonObject["log"] as? JSONObject)?.apply {
            // PERF: Multiple has() and optString() calls - should combine checks
            if (has("access") && optString("access") != "none") {
                remove("access")
                Log.d(TAG, "Removed log.access")
            }
            if (has("error") && optString("error") != "none") {
                remove("error")
                Log.d(TAG, "Removed log.error")
            }
        }
        // PERF: toString(2) creates formatted string - expensive for large configs
        var formattedContent = jsonObject.toString(2)
        // PERF: replace() creates new string - should use StringBuilder or regex replace
        formattedContent = formattedContent.replace("\\/", "/")
        return formattedContent
    }

    // TODO: Add config validation after injection
    // TODO: Consider adding rollback mechanism for failed injections
    @Throws(JSONException::class)
    fun injectStatsService(prefs: Preferences, configContent: String): String {
        // TODO: Add input validation for configContent
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

    // PERF: extractPortsFromJson() parses entire JSON - should use streaming parser
    // MEMORY: JSONObject loads entire content - can cause OOM with large configs
    fun extractPortsFromJson(jsonContent: String): Set<Int> {
        // PERF: mutableSetOf() creates new set - should use HashSet with initial capacity
        val ports = mutableSetOf<Int>()
        try {
            // PERF: JSONObject constructor parses entire string - should validate size first
            val jsonObject = JSONObject(jsonContent)
            extractPortsRecursive(jsonObject, ports)
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing JSON for port extraction", e)
        }
        // PERF: String interpolation in log - should use debug flag check
        Log.d(TAG, "Extracted ports: $ports")
        return ports
    }

    // PERF: extractPortsRecursive() is recursive - can cause stack overflow with deeply nested JSON
    // TODO: Use iterative traversal for deep structures
    private fun extractPortsRecursive(jsonObject: JSONObject, ports: MutableSet<Int>) {
        // PERF: jsonObject.keys() creates new iterator - should use direct iteration
        for (key in jsonObject.keys()) {
            when (val value = jsonObject.get(key)) {
                is Int -> {
                    // PERF: Range check (1..65535) creates Range object - should use direct comparison
                    if (value in 1..65535) {
                        ports.add(value)
                    }
                }

                is JSONObject -> {
                    // CRASH: Recursive call without depth limit - can cause stack overflow
                    extractPortsRecursive(value, ports)
                }

                is org.json.JSONArray -> {
                    // PERF: value.length() called in loop condition - should cache
                    for (i in 0 until value.length()) {
                        val item = value.get(i)
                        if (item is JSONObject) {
                            // CRASH: Recursive call without depth limit - can cause stack overflow
                            extractPortsRecursive(item, ports)
                        }
                    }
                }
            }
        }
    }
}

