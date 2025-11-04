package com.simplexray.an.common.configFormat

import android.content.Context
import android.util.Log
import com.simplexray.an.common.FilenameValidator
import com.simplexray.an.data.source.FileManager.Companion.TAG
import java.net.URI
import java.net.URLDecoder

/**
 * VLESS Link Converter
 * Converts VLESS URI format to Xray JSON config
 * 
 * Format: vless://UUID@host:port?type=tcp&security=reality&sni=example.com&...
 */
class VlessLinkConverter : ConfigFormatConverter {
    
    override fun detect(content: String): Boolean {
        return content.trim().startsWith("vless://", ignoreCase = true)
    }
    
    override fun convert(context: Context, content: String): Result<DetectedConfig> {
        return try {
            val trimmed = content.trim()
            val uri = URI(trimmed)
            
            if (uri.scheme?.lowercase() != "vless") {
                return Result.failure(RuntimeException("Invalid VLESS URI scheme"))
            }
            
            // Extract user info (UUID)
            val userInfo = uri.userInfo
            if (userInfo.isNullOrBlank()) {
                return Result.failure(RuntimeException("Missing UUID in VLESS URI"))
            }
            
            // Extract host and port
            val host = uri.host ?: return Result.failure(RuntimeException("Missing host in VLESS URI"))
            val port = if (uri.port > 0) uri.port else 443 // Default to 443 if not specified
            
            // Parse query parameters
            val queryParams = parseQuery(uri.query)
            
            // Extract network type
            val network = queryParams["type"] ?: "tcp"
            val security = queryParams["security"] ?: "none"
            val sni = queryParams["sni"]
            val flow = queryParams["flow"]
            val encryption = queryParams["encryption"] ?: "none"
            
            // Build Xray JSON config
            val config = buildXrayConfig(
                uuid = userInfo,
                host = host,
                port = port,
                network = network,
                security = security,
                sni = sni,
                flow = flow,
                encryption = encryption,
                queryParams = queryParams
            )
            
            // Generate a name from host
            val name = "vless_${host}_${port}"
            val filenameError = FilenameValidator.validateFilename(context, name)
            if (filenameError != null) {
                Log.e(TAG, "Invalid filename for VLESS config: $filenameError")
                return Result.failure(RuntimeException("Invalid filename: $filenameError"))
            }
            
            Result.success(name to config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert VLESS link", e)
            Result.failure(RuntimeException("Failed to convert VLESS link: ${e.message}", e))
        }
    }
    
    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        
        return query.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], "UTF-8")
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            key to value
        }
    }
    
    private fun buildXrayConfig(
        uuid: String,
        host: String,
        port: Int,
        network: String,
        security: String,
        sni: String?,
        flow: String?,
        encryption: String,
        queryParams: Map<String, String>
    ): String {
        val outbound = buildString {
            appendLine("{")
            appendLine("  \"outbounds\": [")
            appendLine("    {")
            appendLine("      \"protocol\": \"vless\",")
            appendLine("      \"settings\": {")
            appendLine("        \"vnext\": [")
            appendLine("          {")
            appendLine("            \"address\": \"$host\",")
            appendLine("            \"port\": $port,")
            appendLine("            \"users\": [")
            appendLine("              {")
            appendLine("                \"id\": \"$uuid\",")
            appendLine("                \"encryption\": \"$encryption\"")
            if (!flow.isNullOrBlank()) {
                appendLine(",")
                appendLine("                \"flow\": \"$flow\"")
            }
            appendLine("              }")
            appendLine("            ]")
            appendLine("          }")
            appendLine("        ]")
            appendLine("      },")
            
            // Stream settings
            appendLine("      \"streamSettings\": {")
            appendLine("        \"network\": \"$network\",")
            if (security != "none") {
                appendLine("        \"security\": \"$security\",")
            }
            
            // Network-specific settings
            when (network.lowercase()) {
                "ws", "websocket" -> {
                    appendLine("        \"wsSettings\": {")
                    val path = queryParams["path"] ?: "/"
                    appendLine("          \"path\": \"$path\"")
                    val hostHeader = queryParams["host"]
                    if (!hostHeader.isNullOrBlank()) {
                        appendLine(",")
                        appendLine("          \"headers\": {")
                        appendLine("            \"Host\": \"$hostHeader\"")
                        appendLine("          }")
                    }
                    appendLine("        }")
                }
                "grpc" -> {
                    appendLine("        \"grpcSettings\": {")
                    val serviceName = queryParams["serviceName"] ?: queryParams["sni"] ?: ""
                    appendLine("          \"serviceName\": \"$serviceName\"")
                    appendLine("        }")
                }
                "http", "h2" -> {
                    appendLine("        \"httpSettings\": {")
                    val path = queryParams["path"] ?: "/"
                    appendLine("          \"path\": \"$path\"")
                    val hostHeader = queryParams["host"]
                    if (!hostHeader.isNullOrBlank()) {
                        appendLine(",")
                        appendLine("          \"host\": [\"$hostHeader\"]")
                    }
                    appendLine("        }")
                }
                else -> {
                    // TCP or other
                    appendLine("        \"tcpSettings\": {")
                    appendLine("          \"header\": {")
                    appendLine("            \"type\": \"none\"")
                    appendLine("          }")
                    appendLine("        }")
                }
            }
            
            // Security settings (TLS/Reality)
            if (security == "tls" || security == "reality") {
                appendLine(",")
                appendLine("        \"securitySettings\": {")
                if (security == "reality") {
                    val pbk = queryParams["pbk"]
                    val sid = queryParams["sid"]
                    val fp = queryParams["fp"] ?: "chrome"
                    val spx = queryParams["spx"]
                    
                    appendLine("          \"reality\": {")
                    appendLine("            \"show\": false,")
                    if (!pbk.isNullOrBlank()) {
                        appendLine("            \"publicKey\": \"$pbk\",")
                    }
                    if (!sid.isNullOrBlank()) {
                        appendLine("            \"shortId\": \"$sid\",")
                    }
                    appendLine("            \"fingerprint\": \"$fp\"")
                    if (!spx.isNullOrBlank()) {
                        appendLine(",")
                        appendLine("            \"serverName\": \"$spx\"")
                    }
                    appendLine("          }")
                } else {
                    // TLS
                    appendLine("          \"tls\": {")
                    if (!sni.isNullOrBlank()) {
                        appendLine("            \"serverName\": \"$sni\",")
                    }
                    val fp = queryParams["fp"] ?: "chrome"
                    appendLine("            \"fingerprint\": \"$fp\"")
                    appendLine("          }")
                }
                appendLine("        }")
            }
            
            appendLine("      }")
            appendLine("    }")
            appendLine("  ]")
            appendLine("}")
        }
        
        return outbound.toString()
    }
}

