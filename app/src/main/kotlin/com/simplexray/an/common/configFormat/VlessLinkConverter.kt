package com.simplexray.an.common.configFormat

import android.content.Context
import com.simplexray.an.prefs.Preferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class VlessLinkConverter : ConfigFormatConverter {
    override fun detect(content: String): Boolean {
        return content.startsWith("vless://")
    }

    override fun convert(context: Context, content: String): Result<DetectedConfig> {
        return runCatching {
            val uri = URI(content)
            require(uri.scheme.equals("vless", ignoreCase = true)) { "Invalid scheme" }

            val name = uri.fragment?.takeIf { it.isNotBlank() } ?: ("imported_vless_" + System.currentTimeMillis())
            val address = uri.host ?: throw MalformedURLException("Missing host")
            val port = if (uri.port == -1) 443 else uri.port
            val id = uri.userInfo ?: throw MalformedURLException("Missing user info")

            val queryParams = parseQuery(uri.rawQuery)

            val type = queryParams["type"]?.lowercase()?.let { if (it == "h2") "http" else it } ?: "tcp"
            val security = queryParams["security"] ?: "reality"
            val sni = queryParams["sni"]?.takeIf { it.isNotBlank() } ?: queryParams["peer"]
            val fingerprint = queryParams["fp"] ?: "chrome"
            val flow = queryParams["flow"] ?: "xtls-rprx-vision"

            val realityPbk = queryParams["pbk"] ?: ""
            val realityShortId = queryParams["sid"] ?: ""
            val spiderX = queryParams["spx"]?.takeIf { it.isNotBlank() } ?: "/"

            val socksPort = Preferences(context).socksPort

            val streamSettings = JSONObject().apply {
                put("network", type)
                put("security", security)
                put("realitySettings", JSONObject().apply {
                    put("show", false)
                    put("fingerprint", fingerprint)
                    put("serverName", sni ?: address)
                    put("publicKey", realityPbk)
                    put("shortId", realityShortId)
                    put("spiderX", spiderX)
                })
            }

            val config = JSONObject().apply {
                put("log", JSONObject().apply { put("loglevel", "warning") })
                put("inbounds", JSONArray().apply {
                    put(JSONObject().apply {
                        put("port", socksPort)
                        put("listen", "127.0.0.1")
                        put("protocol", "socks")
                        put("settings", JSONObject().apply { put("udp", true) })
                    })
                })
                put("outbounds", JSONArray().apply {
                    put(JSONObject().apply {
                        put("protocol", "vless")
                        put("settings", JSONObject().apply {
                            put("vnext", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("address", address)
                                    put("port", port)
                                    put("users", JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("id", id)
                                            put("encryption", "none")
                                            put("flow", flow)
                                        })
                                    })
                                })
                            })
                        })
                        put("streamSettings", streamSettings)
                    })
                })
            }

            DetectedConfig(name, config.toString(2))
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val separatorIndex = part.indexOf('=')
                val key = if (separatorIndex >= 0) part.substring(0, separatorIndex) else part
                if (key.isBlank()) return@mapNotNull null
                val value = if (separatorIndex >= 0) part.substring(separatorIndex + 1) else ""
                URLDecoder.decode(key, StandardCharsets.UTF_8)
                    .lowercase() to URLDecoder.decode(value, StandardCharsets.UTF_8)
            }
            .toMap()
    }
}