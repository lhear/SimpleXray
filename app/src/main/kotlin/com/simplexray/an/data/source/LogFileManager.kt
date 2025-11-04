package com.simplexray.an.data.source

import android.content.Context
import com.simplexray.an.common.AppLogger
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.RandomAccessFile

/**
 * Manages log file operations
 * TODO: Add log rotation based on file size or date
 * TODO: Implement log compression for old logs
 * TODO: Add log level filtering
 * TODO: Consider using structured logging format (JSON)
 */
class LogFileManager(context: Context) {
    val logFile: File

    init {
        val filesDir = context.filesDir
        this.logFile = File(filesDir, LOG_FILE_NAME)
        // TODO: Add log file directory creation if it doesn't exist
        AppLogger.d("Log file path: " + logFile.absolutePath)
    }

    // THREAD: @Synchronized uses monitor lock - can cause contention with readLogs() and clearLogs()
    // PERF: FileWriter with append=true opens file on every call - should use buffered writer with flush policy
    // MEMORY: checkAndTruncateLogFile() in finally can allocate large buffers - should be async or rate-limited
    @Synchronized
    fun appendLog(logEntry: String?) {
        try {
            // PERF: FileWriter allocates new object on every call - should reuse buffered writer
            FileWriter(logFile, true).use { fileWriter ->
                PrintWriter(fileWriter).use { printWriter ->
                    if (logEntry != null) {
                        // PERF: println() allocates string - consider direct buffer write
                        printWriter.println(logEntry)
                    }
                }
            }
        } catch (e: IOException) {
            AppLogger.e("Error appending log to file", e)
        } finally {
            // PERF: Truncation check on every append - should check every N calls or use size threshold
            checkAndTruncateLogFile()
        }
    }

    // THREAD: @Synchronized blocks all other operations - can cause ANR if file is large
    // PERF: readLogs() loads entire file into memory - can cause OOM with large logs
    // MEMORY: StringBuilder grows unbounded - should use streaming or limit size
    @Synchronized
    fun readLogs(): String? {
        // MEMORY: StringBuilder grows unbounded - can cause OOM with 10MB+ logs
        val logContent = StringBuilder()
        if (!logFile.exists()) {
            AppLogger.d("Log file does not exist.")
            return ""
        }
        try {
            // PERF: FileReader reads entire file - should use NIO or streaming for large files
            FileReader(logFile).use { fileReader ->
                BufferedReader(fileReader).use { bufferedReader ->
                    var line: String?
                    // PERF: Tight loop without yield() - can block thread with large files
                    while (bufferedReader.readLine().also { line = it } != null) {
                        // PERF: String concatenation in loop - StringBuilder is correct but append("\n") allocates
                        logContent.append(line).append("\n")
                    }
                }
            }
        } catch (e: IOException) {
            AppLogger.e("Error reading log file", e)
            return null
        }
        // PERF: toString() creates new string copy - consider returning CharSequence or sequence
        return logContent.toString()
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

    // THREAD: @Synchronized blocks all log operations during truncation - can cause long delays
    // PERF: File truncation is expensive - should run async or use background thread
    // MEMORY: transferTo() can allocate large buffers - consider chunked transfer
    @Synchronized
    private fun checkAndTruncateLogFile() {
        if (!logFile.exists()) {
            AppLogger.d("Log file does not exist for truncation check.")
            return
        }
        // PERF: File.length() is syscall - should cache or check less frequently
        val currentSize = logFile.length()
        if (currentSize <= MAX_LOG_SIZE_BYTES) {
            return
        }
        // PERF: String concatenation in log message - should use template or StringBuilder
        AppLogger.d(
            "Log file size ($currentSize bytes) exceeds limit ($MAX_LOG_SIZE_BYTES bytes). Truncating oldest $TRUNCATE_SIZE_BYTES bytes."
        )
        try {
            val startByteToKeep = currentSize - TRUNCATE_SIZE_BYTES
            // PERF: RandomAccessFile is slower than NIO - should use FileChannel
            RandomAccessFile(logFile, "rw").use { raf ->
                raf.seek(startByteToKeep)
                val firstLineToKeepStartPos: Long
                // PERF: readLine() reads line-by-line - can be slow for large files
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
                            // PERF: transferTo() can be slow for large transfers - should use chunked transfer
                            // MEMORY: transferTo() may allocate internal buffers - consider size limits
                            sourceChannel.transferTo(
                                firstLineToKeepStartPos,
                                bytesToTransfer,
                                destChannel
                            )
                        }
                    }
                    // BUG: File.delete() and renameTo() are not atomic - race condition possible
                    // TODO: Use atomic move operation or file locking
                    if (logFile.delete()) {
                        if (tempLogFile.renameTo(logFile)) {
                            // PERF: File.length() called again - should cache from transfer result
                            AppLogger.d(
                                "Log file truncated successfully. New size: " + logFile.length() + " bytes."
                            )
                        } else {
                            AppLogger.e("Failed to rename temp log file to original file.")
                            tempLogFile.delete()
                        }
                    } else {
                        AppLogger.e("Failed to delete original log file during truncation.")
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

    companion object {
        private const val TAG = "LogFileManager"
        private const val LOG_FILE_NAME = "app_log.txt"
        private const val MAX_LOG_SIZE_BYTES = (10 * 1024 * 1024).toLong()
        private const val TRUNCATE_SIZE_BYTES = (5 * 1024 * 1024).toLong()
    }
}