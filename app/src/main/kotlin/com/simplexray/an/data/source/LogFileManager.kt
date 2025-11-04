package com.simplexray.an.data.source

import android.content.Context
import com.simplexray.an.common.AppLogger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

/**
 * Manages log file operations
 * TODO: Add log rotation based on file size or date
 * TODO: Implement log compression for old logs
 * TODO: Add log level filtering
 * TODO: Consider using structured logging format (JSON)
 */
class LogFileManager(context: Context) {
    val logFile: File
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var bufferedWriter: BufferedWriter? = null
    private var appendCount = 0
    private val truncateCheckInterval = 100 // Check truncation every 100 appends

    init {
        val filesDir = context.filesDir
        this.logFile = File(filesDir, LOG_FILE_NAME)
        // Ensure log file directory exists
        logFile.parentFile?.mkdirs()
        AppLogger.d("Log file path: " + logFile.absolutePath)
    }

    @Synchronized
    fun appendLog(logEntry: String?) {
        try {
            if (logEntry != null) {
                if (bufferedWriter == null) {
                    bufferedWriter = FileWriter(logFile, true).buffered()
                }
                bufferedWriter?.let { writer ->
                    writer.append(logEntry)
                    writer.newLine()
                    writer.flush() // Ensure data is written
                }
            }
        } catch (e: IOException) {
            AppLogger.e("Error appending log to file", e)
            // Reset writer on error
            try {
                bufferedWriter?.close()
            } catch (e2: IOException) {
                // Ignore
            }
            bufferedWriter = null
        }
        appendCount++
        if (appendCount >= truncateCheckInterval) {
            appendCount = 0
            scope.launch {
                checkAndTruncateLogFileAsync()
            }
        }
    }
    
    @Synchronized
    private fun checkAndTruncateLogFileAsync() {
        checkAndTruncateLogFile()
    }

    fun readLogs(): String? {
        val maxSize = 5 * 1024 * 1024 // 5MB limit
        if (!logFile.exists()) {
            AppLogger.d("Log file does not exist.")
            return ""
        }
        
        val fileSize = logFile.length()
        if (fileSize > maxSize) {
            AppLogger.w("Log file too large: $fileSize bytes, limiting to last $maxSize bytes")
        }
        
        return try {
            val logContent = StringBuilder()
            FileReader(logFile).use { fileReader ->
                BufferedReader(fileReader).use { bufferedReader ->
                    var line: String?
                    var lineCount = 0
                    val maxLines = 10000 // Limit lines to prevent OOM
                    while (bufferedReader.readLine().also { line = it } != null && lineCount < maxLines) {
                        if (logContent.length > maxSize) {
                            break
                        }
                        logContent.append(line).append("\n")
                        lineCount++
                    }
                }
            }
            logContent.toString()
        } catch (e: IOException) {
            AppLogger.e("Error reading log file", e)
            null
        }
    }

    @Synchronized
    fun clearLogs() {
        if (logFile.exists()) {
            try {
                FileWriter(logFile, false).use { fileWriter ->
                    fileWriter.write("")
                    AppLogger.d("Log file content cleared successfully.")
                }
            } catch (e: IOException) {
                AppLogger.e("Failed to clear log file content.", e)
            }
        } else {
            AppLogger.d("Log file does not exist, no content to clear.")
        }
    }

    private fun checkAndTruncateLogFile() {
        if (!logFile.exists()) {
            AppLogger.d("Log file does not exist for truncation check.")
            return
        }
        val currentSize = logFile.length()
        if (currentSize <= MAX_LOG_SIZE_BYTES) {
            return
        }
        AppLogger.d("Log file size ($currentSize bytes) exceeds limit ($MAX_LOG_SIZE_BYTES bytes). Truncating oldest $TRUNCATE_SIZE_BYTES bytes.")
        try {
            val startByteToKeep = currentSize - TRUNCATE_SIZE_BYTES
            RandomAccessFile(logFile, "rw").use { raf ->
                raf.seek(startByteToKeep)
                val firstLineToKeepStartPos: Long
                val firstPartialOrFullLine = raf.readLine()
                if (firstPartialOrFullLine != null) {
                    firstLineToKeepStartPos = raf.filePointer
                } else {
                    AppLogger.w(
                        "Could not read line from calculated start position for truncation. Clearing file as a fallback."
                    )
                    clearLogs()
                    return
                }
                raf.channel.use { sourceChannel ->
                    val tempLogFile = File(logFile.parentFile, "$LOG_FILE_NAME.tmp")
                    FileOutputStream(tempLogFile).use { fos ->
                        fos.channel.use { destChannel ->
                            val bytesToTransfer = sourceChannel.size() - firstLineToKeepStartPos
                            // Use chunked transfer for large files
                            var remaining = bytesToTransfer
                            var position = firstLineToKeepStartPos
                            val chunkSize = 1024 * 1024L // 1MB chunks
                            while (remaining > 0) {
                                val transferred = sourceChannel.transferTo(
                                    position,
                                    minOf(remaining, chunkSize),
                                    destChannel
                                )
                                position += transferred
                                remaining -= transferred
                            }
                        }
                    }
                    // Use atomic move operation
                    try {
                        java.nio.file.Files.move(
                            tempLogFile.toPath(),
                            logFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE
                        )
                        AppLogger.d("Log file truncated successfully. New size: ${logFile.length()} bytes.")
                    } catch (e: java.nio.file.FileSystemException) {
                        AppLogger.e("Failed to atomically move temp log file", e)
                        tempLogFile.delete()
                    }
                }
            }
        } catch (e: IOException) {
            AppLogger.e("Error during log file truncation", e)
            clearLogs()
        } catch (e: SecurityException) {
            AppLogger.e("Security exception during log file truncation", e)
            clearLogs()
        }
    }

    fun close() {
        synchronized(this) {
            try {
                bufferedWriter?.close()
            } catch (e: IOException) {
                AppLogger.e("Error closing buffered writer", e)
            } finally {
                bufferedWriter = null
            }
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "LogFileManager"
        private const val LOG_FILE_NAME = "app_log.txt"
        private const val MAX_LOG_SIZE_BYTES = (10 * 1024 * 1024).toLong()
        private const val TRUNCATE_SIZE_BYTES = (5 * 1024 * 1024).toLong()
    }
}