package com.simplexray.an.common

import kotlin.math.ln
import kotlin.math.pow

fun formatBytes(bytes: Long): String {
    if (bytes == 0L) return "0B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
    return "%.2f%s".format(bytes / 1024.0.pow(exp), units[exp])
}

fun formatNumber(count: Long): String {
    if (count < 1000) return count.toString()
    val suffix = listOf('K', 'M', 'G', 'T', 'P', 'E')
    val exp = (ln(count.toDouble()) / ln(1000.0)).toInt().coerceAtMost(suffix.size)
    return "%.1f%c".format(count / 1000.0.pow(exp), suffix[exp - 1])
}

fun formatUptime(seconds: Int): String = when {
    seconds < 0 -> "N/A"
    else -> {
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        "%02d:%02d:%02d".format(hours, minutes, secs)
    }
}
