package com.simplexray.an.common

import android.util.Log
import com.simplexray.an.BuildConfig
import com.simplexray.an.prefs.Preferences
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONArray

/**
 * Utility functions for configuration file manipulation
 * Includes schema validation, versioning, sanitization, and diff functionality
 */
object ConfigUtils {
    private const val TAG = "ConfigUtils"
    private const val CURRENT_CONFIG_VERSION = 1
    private const val CONFIG_VERSION_KEY = "_config_version"
    
    // Sensitive fields to sanitize
    private val SENSITIVE_FIELDS = setOf(
        "password", "passwd", "psk", "pskKey", "privateKey", "privateKeyPath",
        "certificate", "certificatePath", "key", "secret", "token", "apiKey",
        "auth", "authorization", "credentials", "user", "username", "id"
    )

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
    
    /**
     * Validate config schema
     */
    data class ValidationResult(val isValid: Boolean, val errors: List<String> = emptyList())
    
    fun validateConfigSchema(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        
        try {
            val maxSize = 10 * 1024 * 1024
            if (content.length > maxSize) {
                errors.add("Config content too large: ${content.length} bytes (max: $maxSize)")
                return ValidationResult(false, errors)
            }
            
            val jsonObject = JSONObject(content)
            
            // Check required top-level fields
            if (!jsonObject.has("inbounds") && !jsonObject.has("outbounds")) {
                errors.add("Config must contain at least 'inbounds' or 'outbounds'")
            }
            
            // Validate inbounds if present
            if (jsonObject.has("inbounds")) {
                val inbounds = jsonObject.getJSONArray("inbounds")
                for (i in 0 until inbounds.length()) {
                    val inbound = inbounds.getJSONObject(i)
                    if (!inbound.has("protocol")) {
                        errors.add("Inbound at index $i missing required field: protocol")
                    }
                    if (!inbound.has("port")) {
                        errors.add("Inbound at index $i missing required field: port")
                    } else {
                        val port = inbound.getInt("port")
                        if (port < 1 || port > 65535) {
                            errors.add("Inbound at index $i has invalid port: $port")
                        }
                    }
                }
            }
            
            // Validate outbounds if present
            if (jsonObject.has("outbounds")) {
                val outbounds = jsonObject.getJSONArray("outbounds")
                for (i in 0 until outbounds.length()) {
                    val outbound = outbounds.getJSONObject(i)
                    if (!outbound.has("protocol")) {
                        errors.add("Outbound at index $i missing required field: protocol")
                    }
                }
            }
            
            // Validate routing if present
            if (jsonObject.has("routing")) {
                val routing = jsonObject.getJSONObject("routing")
                if (routing.has("rules")) {
                    val rules = routing.getJSONArray("rules")
                    for (i in 0 until rules.length()) {
                        val rule = rules.getJSONObject(i)
                        if (!rule.has("type")) {
                            errors.add("Routing rule at index $i missing required field: type")
                        }
                    }
                }
            }
            
        } catch (e: JSONException) {
            errors.add("Invalid JSON format: ${e.message}")
        } catch (e: Exception) {
            errors.add("Validation error: ${e.message}")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Add version to config
     */
    fun addConfigVersion(content: String): String {
        return try {
            val jsonObject = JSONObject(content)
            jsonObject.put(CONFIG_VERSION_KEY, CURRENT_CONFIG_VERSION)
            jsonObject.toString(2)
        } catch (e: JSONException) {
            Log.e(TAG, "Error adding config version", e)
            content
        }
    }
    
    /**
     * Get config version
     */
    fun getConfigVersion(content: String): Int {
        return try {
            val jsonObject = JSONObject(content)
            jsonObject.optInt(CONFIG_VERSION_KEY, 0)
        } catch (e: JSONException) {
            0
        }
    }
    
    /**
     * Migrate config to current version
     */
    fun migrateConfig(content: String): String {
        val version = getConfigVersion(content)
        if (version >= CURRENT_CONFIG_VERSION) {
            return content
        }
        
        var migrated = content
        try {
            val jsonObject = JSONObject(content)
            
            // Version 0 -> 1: Add version field
            if (version < 1) {
                jsonObject.put(CONFIG_VERSION_KEY, CURRENT_CONFIG_VERSION)
                migrated = jsonObject.toString(2)
                Log.d(TAG, "Migrated config from version 0 to 1")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating config", e)
        }
        
        return migrated
    }
    
    /**
     * Sanitize config by removing sensitive data
     */
    fun sanitizeConfig(content: String): String {
        return try {
            val maxSize = 10 * 1024 * 1024
            if (content.length > maxSize) {
                throw JSONException("Config content too large")
            }
            
            val jsonObject = JSONObject(content)
            sanitizeObject(jsonObject, 0, MAX_RECURSION_DEPTH)
            jsonObject.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error sanitizing config", e)
            content
        }
    }
    
    private fun sanitizeObject(obj: JSONObject, currentDepth: Int, maxDepth: Int) {
        if (currentDepth >= maxDepth) {
            return
        }
        
        val keysToRemove = mutableListOf<String>()
        val keys = obj.keys()
        
        while (keys.hasNext()) {
            val key = keys.next()
            val lowerKey = key.lowercase()
            
            // Check if key contains sensitive field name
            if (SENSITIVE_FIELDS.any { lowerKey.contains(it.lowercase()) }) {
                keysToRemove.add(key)
            } else {
                val value = obj.get(key)
                when (value) {
                    is JSONObject -> sanitizeObject(value, currentDepth + 1, maxDepth)
                    is JSONArray -> {
                        for (i in 0 until value.length()) {
                            val item = value.get(i)
                            if (item is JSONObject) {
                                sanitizeObject(item, currentDepth + 1, maxDepth)
                            }
                        }
                    }
                }
            }
        }
        
        keysToRemove.forEach { obj.remove(it) }
    }
    
    /**
     * Calculate diff between two configs
     */
    data class ConfigDiff(
        val addedFields: List<String> = emptyList(),
        val removedFields: List<String> = emptyList(),
        val modifiedFields: List<String> = emptyList()
    )
    
    fun diffConfigs(oldConfig: String, newConfig: String): ConfigDiff {
        val addedFields = mutableListOf<String>()
        val removedFields = mutableListOf<String>()
        val modifiedFields = mutableListOf<String>()
        
        try {
            val oldJson = JSONObject(oldConfig)
            val newJson = JSONObject(newConfig)
            
            compareObjects(oldJson, newJson, "", addedFields, removedFields, modifiedFields, 0, MAX_RECURSION_DEPTH)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating config diff", e)
        }
        
        return ConfigDiff(addedFields, removedFields, modifiedFields)
    }
    
    private fun compareObjects(
        oldObj: JSONObject,
        newObj: JSONObject,
        path: String,
        addedFields: MutableList<String>,
        removedFields: MutableList<String>,
        modifiedFields: MutableList<String>,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (currentDepth >= maxDepth) {
            return
        }
        
        val oldKeys = oldObj.keys().asSequence().toSet()
        val newKeys = newObj.keys().asSequence().toSet()
        
        // Find added fields
        (newKeys - oldKeys).forEach { key ->
            addedFields.add("$path.$key")
        }
        
        // Find removed fields
        (oldKeys - newKeys).forEach { key ->
            removedFields.add("$path.$key")
        }
        
        // Compare common fields
        (oldKeys intersect newKeys).forEach { key ->
            val currentPath = if (path.isEmpty()) key else "$path.$key"
            val oldValue = oldObj.get(key)
            val newValue = newObj.get(key)
            
            when {
                oldValue is JSONObject && newValue is JSONObject -> {
                    compareObjects(oldValue, newValue, currentPath, addedFields, removedFields, modifiedFields, currentDepth + 1, maxDepth)
                }
                oldValue is JSONArray && newValue is JSONArray -> {
                    if (oldValue.length() != newValue.length()) {
                        modifiedFields.add("$currentPath (array length changed)")
                    }
                }
                oldValue.toString() != newValue.toString() -> {
                    modifiedFields.add(currentPath)
                }
            }
        }
    }
    
    companion object {
        private const val MAX_RECURSION_DEPTH = 50 // Limit recursion to prevent stack overflow
    }
}

