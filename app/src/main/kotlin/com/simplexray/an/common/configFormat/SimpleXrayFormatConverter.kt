package com.simplexray.an.common.configFormat

import android.content.Context
import android.util.Log
import com.simplexray.an.common.FilenameValidator
import com.simplexray.an.data.source.FileManager.Companion.TAG
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.Inflater

class SimpleXrayFormatConverter: ConfigFormatConverter {
    override fun detect(content: String): Boolean {
        return content.startsWith("simplexray://config/")
    }

    override fun convert(context: Context, content: String): Result<DetectedConfig> {
        val parts = content.substring("simplexray://config/".length).split("/")
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

        val inflater = Inflater()
        inflater.setInput(decodedContent)
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        val decompressed = outputStream.toByteArray().toString(Charsets.UTF_8)

        return Result.success(Pair(decodedName, decompressed))
    }
}