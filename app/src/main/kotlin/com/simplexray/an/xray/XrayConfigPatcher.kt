package com.simplexray.an.xray

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File

object XrayConfigPatcher {
    private const val TAG = "XrayConfigPatcher"
    private val gson = Gson()

    /**
     * Enhanced config patcher that merges inbound/outbound/transport while preserving user config
     */
    fun patchConfig(
        context: Context,
        filename: String = "xray.json",
        mergeInbounds: Boolean = true,
        mergeOutbounds: Boolean = true,
        mergeTransport: Boolean = true
    ): File {
        val file = File(context.filesDir, filename)
        val root = if (file.exists()) {
            try {
                val text = file.readText()
                gson.fromJson(text, JsonObject::class.java) ?: JsonObject()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse existing config, creating new", e)
                JsonObject()
            }
        } else {
            JsonObject()
        }

        // Always ensure API/Stats/Policy
        ensureApiStatsPolicy(root, context)

        // Merge inbound/outbound/transport if requested
        if (mergeInbounds) {
            mergeInboundSection(root, context)
        }
        if (mergeOutbounds) {
            mergeOutboundSection(root, context)
        }
        if (mergeTransport) {
            mergeTransportSection(root, context)
        }

        // Write back
        file.writeText(gson.toJson(root))
        Log.d(TAG, "Config patched and saved")
        return file
    }

    fun ensureApiStatsPolicy(context: Context, filename: String = "xray.json"): File {
        val file = File(context.filesDir, filename)
        if (!file.exists()) {
            val cfg = XrayConfigBuilder.defaultConfig("127.0.0.1", 10085)
            XrayConfigBuilder.writeConfig(context, cfg, filename)
            return file
        }
        val text = try { file.readText() } catch (_: Throwable) { "" }
        val root = try { gson.fromJson(text, JsonObject::class.java) } catch (_: Throwable) { JsonObject() }

        // api
        val api = (root.get("api") as? JsonObject) ?: JsonObject().also { root.add("api", it) }
        if (!api.has("tag")) api.addProperty("tag", "api")
        val services = when (val s = api.get("services")) {
            is JsonArray -> s
            else -> JsonArray().also { api.add("services", it) }
        }
        if (!servicesContains(services, "StatsService")) services.add("StatsService")

        // stats
        if (root.get("stats") !is JsonObject) root.add("stats", JsonObject())

        // policy
        val policy = (root.get("policy") as? JsonObject) ?: JsonObject().also { root.add("policy", it) }
        val system = (policy.get("system") as? JsonObject) ?: JsonObject().also { policy.add("system", it) }
        system.addProperty("statsInboundUplink", true)
        system.addProperty("statsInboundDownlink", true)
        system.addProperty("statsOutboundUplink", true)
        system.addProperty("statsOutboundDownlink", true)
        val levels = (policy.get("levels") as? JsonObject) ?: JsonObject().also { policy.add("levels", it) }
        val level0 = (levels.get("0") as? JsonObject) ?: JsonObject().also { levels.add("0", it) }
        level0.addProperty("statsUserUplink", true)
        level0.addProperty("statsUserDownlink", true)

        // inbounds: ensure dokodemo-door API listener
        val host = com.simplexray.an.config.ApiConfig.getHost(context)
        val port = com.simplexray.an.config.ApiConfig.getPort(context)
        val inbounds = (root.get("inbounds") as? JsonArray) ?: JsonArray().also { root.add("inbounds", it) }
        if (!hasApiInbound(inbounds, host, port)) {
            val ib = JsonObject().apply {
                addProperty("listen", host)
                addProperty("port", port)
                addProperty("protocol", "dokodemo-door")
                addProperty("tag", "api-in")
                add("settings", JsonObject().apply { addProperty("address", host) })
            }
            inbounds.add(ib)
        }

        // routing: route api inbound to api outbound (special tag)
        val routing = (root.get("routing") as? JsonObject) ?: JsonObject().also { root.add("routing", it) }
        val rules = (routing.get("rules") as? JsonArray) ?: JsonArray().also { routing.add("rules", it) }
        if (!hasApiRule(rules)) {
            val rule = JsonObject().apply {
                addProperty("type", "field")
                add("inboundTag", JsonArray().apply { add("api-in") })
                addProperty("outboundTag", "api")
            }
            rules.add(rule)
        }

        file.writeText(gson.toJson(root))
        return file
    }

    private fun servicesContains(arr: JsonArray, name: String): Boolean {
        for (e: JsonElement in arr) {
            if (e.isJsonPrimitive && e.asJsonPrimitive.isString && e.asString == name) return true
        }
        return false
    }

    private fun hasApiInbound(inbounds: JsonArray, host: String, port: Int): Boolean {
        for (e in inbounds) {
            if (e is JsonObject) {
                val proto = e.get("protocol")?.asString ?: ""
                val p = e.get("port")?.asInt ?: -1
                val t = e.get("tag")?.asString ?: ""
                if (proto == "dokodemo-door" && p == port && t == "api-in") return true
            }
        }
        return false
    }

    private fun hasApiRule(rules: JsonArray): Boolean {
        for (e in rules) {
            if (e is JsonObject) {
                val outbound = e.get("outboundTag")?.asString ?: ""
                if (outbound == "api") return true
            }
        }
        return false
    }

    /**
     * Ensure API, Stats, and Policy sections exist
     */
    private fun ensureApiStatsPolicy(root: JsonObject, context: Context) {
        // api
        val api = (root.get("api") as? JsonObject) ?: JsonObject().also { root.add("api", it) }
        if (!api.has("tag")) api.addProperty("tag", "api")
        val services = when (val s = api.get("services")) {
            is JsonArray -> s
            else -> JsonArray().also { api.add("services", it) }
        }
        if (!servicesContains(services, "StatsService")) services.add("StatsService")

        // stats
        if (root.get("stats") !is JsonObject) root.add("stats", JsonObject())

        // policy
        val policy = (root.get("policy") as? JsonObject) ?: JsonObject().also { root.add("policy", it) }
        val system = (policy.get("system") as? JsonObject) ?: JsonObject().also { policy.add("system", it) }
        system.addProperty("statsInboundUplink", true)
        system.addProperty("statsInboundDownlink", true)
        system.addProperty("statsOutboundUplink", true)
        system.addProperty("statsOutboundDownlink", true)
        val levels = (policy.get("levels") as? JsonObject) ?: JsonObject().also { policy.add("levels", it) }
        val level0 = (levels.get("0") as? JsonObject) ?: JsonObject().also { levels.add("0", it) }
        level0.addProperty("statsUserUplink", true)
        level0.addProperty("statsUserDownlink", true)

        // inbounds: ensure dokodemo-door API listener
        val host = com.simplexray.an.config.ApiConfig.getHost(context)
        val port = com.simplexray.an.config.ApiConfig.getPort(context)
        val inbounds = (root.get("inbounds") as? JsonArray) ?: JsonArray().also { root.add("inbounds", it) }
        if (!hasApiInbound(inbounds, host, port)) {
            val ib = JsonObject().apply {
                addProperty("listen", host)
                addProperty("port", port)
                addProperty("protocol", "dokodemo-door")
                addProperty("tag", "api-in")
                add("settings", JsonObject().apply { addProperty("address", host) })
            }
            inbounds.add(ib)
        }

        // routing: route api inbound to api outbound
        val routing = (root.get("routing") as? JsonObject) ?: JsonObject().also { root.add("routing", it) }
        val rules = (routing.get("rules") as? JsonArray) ?: JsonArray().also { routing.add("rules", it) }
        if (!hasApiRule(rules)) {
            val rule = JsonObject().apply {
                addProperty("type", "field")
                add("inboundTag", JsonArray().apply { add("api-in") })
                addProperty("outboundTag", "api")
            }
            rules.add(rule)
        }
    }

    /**
     * Merge inbounds while preserving user-defined inbounds
     */
    private fun mergeInboundSection(root: JsonObject, context: Context) {
        val inbounds = (root.get("inbounds") as? JsonArray) ?: JsonArray().also { root.add("inbounds", it) }
        val host = com.simplexray.an.config.ApiConfig.getHost(context)
        val port = com.simplexray.an.config.ApiConfig.getPort(context)
        
        // Ensure API inbound exists
        if (!hasApiInbound(inbounds, host, port)) {
            val ib = JsonObject().apply {
                addProperty("listen", host)
                addProperty("port", port)
                addProperty("protocol", "dokodemo-door")
                addProperty("tag", "api-in")
                add("settings", JsonObject().apply { addProperty("address", host) })
            }
            inbounds.add(ib)
            Log.d(TAG, "Added API inbound listener")
        }
    }

    /**
     * Merge outbounds while preserving user-defined outbounds
     */
    private fun mergeOutboundSection(root: JsonObject, context: Context) {
        val outbounds = (root.get("outbounds") as? JsonArray) ?: JsonArray().also { root.add("outbounds", it) }
        
        // Check if API outbound exists
        var hasApiOutbound = false
        for (e in outbounds) {
            if (e is JsonObject) {
                val tag = e.get("tag")?.asString ?: ""
                if (tag == "api") {
                    hasApiOutbound = true
                    break
                }
            }
        }
        
        if (!hasApiOutbound) {
            // Add a simple API outbound (required for routing)
            val ob = JsonObject().apply {
                addProperty("tag", "api")
                addProperty("protocol", "freedom")
                add("settings", JsonObject())
            }
            outbounds.add(ob)
            Log.d(TAG, "Added API outbound")
        }
    }

    /**
     * Merge transport configuration (e.g., gRPC, WebSocket) while preserving user config
     */
    private fun mergeTransportSection(root: JsonObject, context: Context) {
        // Only merge if transport section doesn't exist
        if (!root.has("transport")) {
            // Example: Add gRPC transport for API if needed
            // User can customize this in their config
            Log.d(TAG, "Transport section not present, user can add manually")
        } else {
            val transport = root.get("transport") as? JsonObject
            if (transport != null) {
                // Preserve existing transport settings
                Log.d(TAG, "Transport section exists, preserving user config")
            }
        }
    }
}
