package com.simplexray.an.xray

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File

object XrayConfigBuilder {
    private val gson = Gson()

    fun defaultConfig(apiHost: String, apiPort: Int): JsonObject {
        val root = JsonObject()
        // Enable api service with StatsService
        val api = JsonObject().apply {
            addProperty("tag", "api")
            add("services", gson.toJsonTree(listOf("StatsService")))
        }
        root.add("api", api)
        // Stats enabled
        root.add("stats", JsonObject())
        // Policy to collect stats
        val system = JsonObject().apply {
            addProperty("statsInboundUplink", true)
            addProperty("statsInboundDownlink", true)
            addProperty("statsOutboundUplink", true)
            addProperty("statsOutboundDownlink", true)
        }
        val levels = JsonObject().apply {
            add("0", JsonObject().apply {
                addProperty("statsUserUplink", true)
                addProperty("statsUserDownlink", true)
            })
        }
        val policy = JsonObject().apply {
            add("system", system)
            add("levels", levels)
        }
        root.add("policy", policy)

        // Add inbounds/outbounds placeholders (user should merge their own)
        root.add("inbounds", gson.toJsonTree(emptyList<Any>()))
        root.add("outbounds", gson.toJsonTree(emptyList<Any>()))

        // Optional: transport/grpc/ws etc. left to user config
        return root
    }

    fun writeConfig(context: Context, config: JsonObject, filename: String = "xray.json"): File {
        val file = File(context.filesDir, filename)
        file.writeText(gson.toJson(config))
        return file
    }
}

