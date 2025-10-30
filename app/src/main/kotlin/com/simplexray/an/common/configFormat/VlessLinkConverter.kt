package com.simplexray.an.common.configFormat

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.simplexray.an.prefs.Preferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL

class VlessLinkConverter: ConfigFormatConverter {
    override fun detect(content: String): Boolean {
        return content.startsWith("vless://")
    }

    override fun convert(context: Context, content: String): Result<DetectedConfig> {
        return try {
            val url = content.toUri()
            require(url.scheme == "vless") { "Invalid scheme" }

            val name = url.fragment?.takeIf { it.isNotBlank() } ?: ("imported_vless_" + System.currentTimeMillis())
            val address = url.host ?: return Result.failure(MalformedURLException("Missing host"))
            val port = url.port.takeIf { it != -1 } ?: 443
            val id = url.userInfo ?: return Result.failure(MalformedURLException("Missing user info"))

            val type = url.getQueryParameter("type")?.let { if (it == "h2") "http" else it } ?: "tcp"
            val security = url.getQueryParameter("security") ?: "reality"
            val sni = url.getQueryParameter("sni")?.takeIf { it.isNotBlank() } ?: url.getQueryParameter("peer")
            val fingerprint = url.getQueryParameter("fp") ?: "chrome"
            val flow = url.getQueryParameter("flow") ?: "xtls-rprx-vision"

            val realityPbk = url.getQueryParameter("pbk") ?: ""
            val realityShortId = url.getQueryParameter("sid") ?: ""
            val spiderX = url.getQueryParameter("spx") ?: "/"

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
                if (security.equals("tls", ignoreCase = true)) {
                    put("tlsSettings", JSONObject().apply {
                        put("serverName", sni ?: address)
                        put("allowInsecure", false)
                    })
                }
            }

            val config = JSONObject().apply {
                put("log", JSONObject(mapOf("loglevel" to "warning")))
                put("inbounds", JSONArray().apply {
                    put(JSONObject().apply {
                        put("port", socksPort)
                        put("listen", "127.0.0.1")
                        put("protocol", "socks")
                        put("settings", JSONObject(mapOf("udp" to true)))
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
                        put("mux", JSONObject().apply {
                            put("enabled", true)
                            put("concurrency", 8)
                        })
                    })
                })
            }

            Result.success(DetectedConfig(name, config.toString(2)))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}