package com.simplexray.an.common

import android.util.Base64
import org.json.JSONObject
import android.util.Log

private const val TAG = "V2rayUtils"

object V2rayUtils {

    fun importV2rayUri(uri: String): String? {
        if (!uri.startsWith("vmess://")) {
            return null
        }

        return try {
            val decoded = String(Base64.decode(uri.substring(8), Base64.DEFAULT))
            Log.d(TAG, "Decoded vmess: $decoded")
            val json = JSONObject(decoded)
            Log.d(TAG, "Parsed vmess: $json")

            val config = JSONObject()
            val outbounds = JSONObject()
            val settings = JSONObject()
            val vnext = JSONObject()
            val users = JSONObject()

            val outboundsArray = org.json.JSONArray()
            outboundsArray.put(outbounds)
            config.put("outbounds", outboundsArray)

            outbounds.put("tag", "proxy")
            val mux = JSONObject()
            outbounds.put("mux", mux)
            outbounds.put("protocol", "vmess")
            outbounds.put("settings", settings)

            val vnextArray = org.json.JSONArray()
            vnextArray.put(vnext)
            settings.put("vnext", vnextArray)

            vnext.put("address", json.getString("add"))
            vnext.put("port", json.getInt("port"))

            val usersArray = org.json.JSONArray()
            usersArray.put(users)
            vnext.put("users", usersArray)

            users.put("id", json.getString("id"))
            users.put("alterId", json.optInt("aid", 0))
            users.put("security", "auto")

            outbounds.put("remark", json.optString("ps", ""))

            val streamSettings = JSONObject()
            streamSettings.put("network", json.getString("net"))
            streamSettings.put("security", json.optString("tls", "none"))

            when (json.getString("net")) {
                "tcp" -> {
                    val tcpSettings = JSONObject()
                    val header = JSONObject()
                    header.put("type", json.optString("type", "none"))
                    if (json.optString("type") == "http") {
                        val request = JSONObject()
                        request.put("method", "GET")
                        request.put("path", org.json.JSONArray().put("/"))
                        val requestHeaders = JSONObject()
                        requestHeaders.put("Host", org.json.JSONArray().put(json.optString("host", json.getString("add"))))
                        request.put("headers", requestHeaders)
                        header.put("request", request)
                    }
                    tcpSettings.put("header", header)
                    streamSettings.put("tcpSettings", tcpSettings)
                }
                "kcp" -> {
                    val kcpSettings = JSONObject()
                    kcpSettings.put("header", JSONObject().put("type", json.optString("type", "none")))
                    streamSettings.put("kcpSettings", kcpSettings)
                }
                "ws" -> {
                    val wsSettings = JSONObject()
                    wsSettings.put("path", json.optString("path", "/"))
                    wsSettings.put("headers", JSONObject().put("Host", json.optString("host", json.getString("add"))))
                    streamSettings.put("wsSettings", wsSettings)
                }
                "h2" -> {
                    val httpSettings = JSONObject()
                    httpSettings.put("path", json.optString("path", "/"))
                    httpSettings.put("host", listOf(json.optString("host", "")))
                    streamSettings.put("httpSettings", httpSettings)
                }
            }

            if (json.optString("tls", "none") == "tls") {
                val tlsSettings = JSONObject()
                tlsSettings.put("serverName", json.optString("host", json.getString("add")))
                streamSettings.put("tlsSettings", tlsSettings)
            }

            outbounds.put("streamSettings", streamSettings)

            outbounds.toString(4)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import vmess uri", e)
            e.printStackTrace()
            null
        }
    }

    fun importShadowsocksUri(uri: String): String? {
        if (!uri.startsWith("ss://")) {
            return null
        }

        return try {
            val outbounds = JSONObject()
            val settings = JSONObject()
            val servers = JSONObject()

            val outboundsArray = org.json.JSONArray()
            outboundsArray.put(outbounds)

            outbounds.put("tag", "proxy")
            outbounds.put("protocol", "shadowsocks")
            outbounds.put("settings", settings)

            val serversArray = org.json.JSONArray()
            serversArray.put(servers)
            settings.put("servers", serversArray)

            val decoded = String(Base64.decode(uri.substring(5, uri.indexOf("@")), Base64.DEFAULT))
            val parts = decoded.split(":")
            servers.put("method", parts[0])
            servers.put("password", parts[1])

            val serverInfo = uri.substring(uri.indexOf("@") + 1)
            val serverParts = serverInfo.split(":")
            servers.put("address", serverParts[0])
            val portString = serverParts[1]
            val port: Int
            var remark = ""
            val questionMarkIndex = portString.indexOf("?")
            val hashIndex = portString.indexOf("#")

            if (questionMarkIndex != -1) {
                port = portString.substring(0, questionMarkIndex).toInt()
                if (hashIndex != -1) {
                    remark = portString.substring(hashIndex + 1)
                }
            } else if (hashIndex != -1) {
                port = portString.substring(0, hashIndex).toInt()
                remark = portString.substring(hashIndex + 1)
            } else {
                port = portString.toInt()
            }
            servers.put("port", port)
            outbounds.put("remark", remark)

            outbounds.toString(4)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import shadowsocks uri", e)
            e.printStackTrace()
            null
        }
    }

    fun importTrojanUri(uri: String): String? {
        if (!uri.startsWith("trojan://")) {
            return null
        }

        return try {
            val outbounds = JSONObject()
            val settings = JSONObject()
            val servers = JSONObject()

            val outboundsArray = org.json.JSONArray()
            outboundsArray.put(outbounds)

            outbounds.put("tag", "proxy")
            outbounds.put("protocol", "trojan")
            outbounds.put("settings", settings)

            val serversArray = org.json.JSONArray()
            serversArray.put(servers)
            settings.put("servers", serversArray)

            val password = uri.substring(9, uri.indexOf("@"))
            servers.put("password", password)

            val serverInfo = uri.substring(uri.indexOf("@") + 1)
            val serverParts = serverInfo.split(":")
            servers.put("address", serverParts[0])
            val portString = serverParts[1]
            val port: Int
            var remark = ""
            val questionMarkIndex = portString.indexOf("?")
            val hashIndex = portString.indexOf("#")

            if (questionMarkIndex != -1) {
                port = portString.substring(0, questionMarkIndex).toInt()
                if (hashIndex != -1) {
                    remark = portString.substring(hashIndex + 1)
                }
            } else if (hashIndex != -1) {
                port = portString.substring(0, hashIndex).toInt()
                remark = portString.substring(hashIndex + 1)
            } else {
                port = portString.toInt()
            }
            servers.put("port", port)
            outbounds.put("remark", remark)

            outbounds.toString(4)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import trojan uri", e)
            e.printStackTrace()
            null
        }
    }

    fun importVlessUri(uri: String): String? {
        if (!uri.startsWith("vless://")) {
            return null
        }

        return try {
            val outbounds = JSONObject()
            val settings = JSONObject()
            val vnext = JSONObject()
            val users = JSONObject()

            val outboundsArray = org.json.JSONArray()
            outboundsArray.put(outbounds)

            outbounds.put("tag", "proxy")
            outbounds.put("protocol", "vless")
            outbounds.put("settings", settings)

            val vnextArray = org.json.JSONArray()
            vnextArray.put(vnext)
            settings.put("vnext", vnextArray)

            val serverInfo = uri.substring(uri.indexOf("@") + 1)
            val serverParts = serverInfo.split(":")
            vnext.put("address", serverParts[0])
            val portString = serverParts[1]
            val port: Int
            val questionMarkIndex = portString.indexOf("?")
            if (questionMarkIndex != -1) {
                port = portString.substring(0, questionMarkIndex).toInt()
            } else {
                port = portString.toInt()
            }
            vnext.put("port", port)

            val params = uri.split("?")[1].split("&")
            val paramsMap = mutableMapOf<String, String>()
            for (param in params) {
                val pair = param.split("=")
                paramsMap[pair[0]] = pair[1]
            }

            val usersArray = org.json.JSONArray()
            usersArray.put(users)
            vnext.put("users", usersArray)

            users.put("id", uri.substring(8, uri.indexOf("@")))
            users.put("encryption", paramsMap["encryption"] ?: "none")

            outbounds.put("remark", paramsMap["remark"] ?: "")

            val streamSettings = JSONObject()
            val network = when {
                paramsMap.containsKey("ws") -> "ws"
                paramsMap.containsKey("h2") -> "h2"
                paramsMap.containsKey("kcp") -> "kcp"
                else -> "tcp"
            }
            streamSettings.put("network", network)
            streamSettings.put("security", if (paramsMap["security"] == "tls" || paramsMap["security"] == "reality") "tls" else "none")

            when (network) {
                "tcp" -> {
                    val tcpSettings = JSONObject()
                    val header = JSONObject()
                    header.put("type", paramsMap["headerType"] ?: "none")
                    if (paramsMap["headerType"] == "http") {
                        val request = JSONObject()
                        request.put("method", "GET")
                        request.put("path", org.json.JSONArray().put("/"))
                        val requestHeaders = JSONObject()
                        requestHeaders.put("Host", org.json.JSONArray().put(paramsMap["host"] ?: serverParts[0]))
                        request.put("headers", requestHeaders)
                        header.put("request", request)
                    }
                    tcpSettings.put("header", header)
                    streamSettings.put("tcpSettings", tcpSettings)
                }
                "kcp" -> {
                    val kcpSettings = JSONObject()
                    kcpSettings.put("header", JSONObject().put("type", paramsMap["headerType"] ?: "none"))
                    streamSettings.put("kcpSettings", kcpSettings)
                }
                "ws" -> {
                    val wsSettings = JSONObject()
                    wsSettings.put("path", paramsMap["path"] ?: "/")
                    wsSettings.put("headers", JSONObject().put("Host", paramsMap["host"] ?: serverParts[0]))
                    streamSettings.put("wsSettings", wsSettings)
                }
                "h2" -> {
                    val httpSettings = JSONObject()
                    httpSettings.put("path", paramsMap["path"] ?: "/")
                    httpSettings.put("host", listOf(paramsMap["host"] ?: serverParts[0]))
                    streamSettings.put("httpSettings", httpSettings)
                }
            }

            if (paramsMap["security"] == "tls" || paramsMap["security"] == "reality") {
                val tlsSettings = JSONObject()
                tlsSettings.put("serverName", paramsMap["sni"] ?: serverParts[0])
                if (paramsMap["security"] == "reality") {
                    tlsSettings.put("reality", JSONObject().put("enabled", true))
                    tlsSettings.put("publicKey", paramsMap["pbk"])
                    tlsSettings.put("shortId", paramsMap["sid"])
                }
                streamSettings.put("tlsSettings", tlsSettings)
            }

            outbounds.put("streamSettings", streamSettings)

            outbounds.toString(4)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import vless uri", e)
            e.printStackTrace()
            null
        }
    }
}
