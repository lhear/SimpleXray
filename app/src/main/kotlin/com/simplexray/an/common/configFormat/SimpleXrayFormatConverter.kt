package com.simplexray.an.common.configFormat

import android.content.Context
import android.util.Log
import com.simplexray.an.common.FilenameValidator
import com.simplexray.an.data.source.FileManager.Companion.TAG
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.InflaterInputStream

class SimpleXrayFormatConverter : ConfigFormatConverter {
    override fun detect(content: String): Boolean {
        return content.startsWith("simplexray://config/")
    }

    override fun convert(context: Context, content: String): Result<DetectedConfig> {
        return try {
            val payload = content.substring("simplexray://config/".length)
            val parts = payload.split("/", limit = 2)
            if (parts.size != 2) {
                Log.e(TAG, "Invalid simplexray URI format")
                return Result.failure(RuntimeException("Invalid simplexray URI format"))
            }

            val decodedName = URLDecoder.decode(parts[0], "UTF-8")

            val filenameError = FilenameValidator.validateFilename(context, decodedName)
            if (filenameError != null) {
                Log.e(TAG, "Invalid filename in simplexray URI: $filenameError")
                return Result.failure(RuntimeException("Invalid filename in simplexray URI: $filenameError"))
            }

            val decodedContent = Base64.getUrlDecoder().decode(parts[1])
            val decompressed = InflaterInputStream(ByteArrayInputStream(decodedContent)).use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
            Result.success(decodedName to decompressed)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to decode simplexray config", e)
            Result.failure(RuntimeException("Failed to decode simplexray config: ${e.message}", e))
        }
    }
}