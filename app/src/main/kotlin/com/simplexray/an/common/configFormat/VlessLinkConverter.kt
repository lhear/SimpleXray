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
            assert(url.scheme == "vless")

            val name = url.fragment ?: ("imported_vless_" + System.currentTimeMillis())
            val address = url.host ?: return Result.failure(MalformedURLException("Missing host"))
            val port = url.port.takeIf { it != -1 } ?: 443
            val id = url.userInfo ?: return Result.failure(MalformedURLException("Missing user info"))

            val type = url.getQueryParameter("type")?.let { if (it == "h2") "http" else it } ?: "tcp"
            val security = url.getQueryParameter("security") ?: "reality"
            val sni = url.getQueryParameter("sni") ?: url.getQueryParameter("peer")
            val fingerprint = url.getQueryParameter("fp") ?: "chrome"
            val flow = url.getQueryParameter("flow") ?: "xtls-rprx-vision"

            val realityPbk = url.getQueryParameter("pbk") ?: ""
            val realityShortId = url.getQueryParameter("sid") ?: ""
            val spiderX = url.getQueryParameter("spx") ?: "/"

            val socksPort = Preferences(context).socksPort

            // Build config JSON
            val config = JSONObject(
                mapOf(
                    "log" to mapOf("loglevel" to "warning"),
                    "inbounds" to listOf(
                        mapOf(
                            "port" to socksPort,
                            "listen" to "127.0.0.1",
                            "protocol" to "socks",
                            "settings" to mapOf("udp" to true)
                        )
                    ),
                    "outbounds" to listOf(
                        mapOf(
                            "protocol" to "vless",
                            "settings" to mapOf(
                                "vnext" to listOf(
                                    mapOf(
                                        "address" to address,
                                        "port" to port,
                                        "users" to listOf(
                                            mapOf(
                                                "id" to id,
                                                "encryption" to "none",
                                                "flow" to flow
                                            )
                                        )
                                    )
                                )
                            ),
                            "streamSettings" to mapOf(
                                "network" to type,
                                "security" to security,
                                "realitySettings" to mapOf(
                                    "show" to false,
                                    "fingerprint" to fingerprint,
                                    "serverName" to (sni ?: address),
                                    "publicKey" to realityPbk,
                                    "shortId" to realityShortId,
                                    "spiderX" to spiderX
                                )
                            )
                        )
                    )
                )
            )

            Result.success(DetectedConfig(name, config.toString(2)))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}