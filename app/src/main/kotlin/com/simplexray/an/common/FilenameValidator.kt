package com.simplexray.an.common

import android.content.Context
import com.simplexray.an.R
import java.util.regex.Pattern

object FilenameValidator {
    private const val INVALID_CHARS_PATTERN = "[\\\\/:*?\"<>|]"

    fun validateFilename(context: Context, name: String): String? {
        val trimmedName = name.trim()
        return when {
            trimmedName.isEmpty() -> context.getString(R.string.filename_empty)
            !isValidFilenameChars(trimmedName) -> context.getString(R.string.filename_invalid)
            else -> null
        }
    }

    private fun isValidFilenameChars(filename: String): Boolean {
        return !Pattern.compile(INVALID_CHARS_PATTERN).matcher(filename).find()
    }
}