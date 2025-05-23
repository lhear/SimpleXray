package com.simplexray.an;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class LogFileManager {
    private static final String TAG = "LogFileManager";
    private static final String LOG_FILE_NAME = "app_log.txt";
    private static final long MAX_LOG_SIZE_BYTES = 10 * 1024 * 1024;
    private static final long TRUNCATE_SIZE_BYTES = 5 * 1024 * 1024;
    private final File logFile;

    public LogFileManager(Context context) {
        File filesDir = context.getFilesDir();
        this.logFile = new File(filesDir, LOG_FILE_NAME);
        Log.d(TAG, "Log file path: " + logFile.getAbsolutePath());
    }

    public synchronized void appendLog(String logEntry) {
        try (FileWriter fileWriter = new FileWriter(logFile, true); PrintWriter printWriter = new PrintWriter(fileWriter)) {
            if (logEntry != null) {
                printWriter.println(logEntry);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error appending log to file", e);
        } finally {
            checkAndTruncateLogFile();
        }
    }

    public String readLogs() {
        StringBuilder logContent = new StringBuilder();
        if (!logFile.exists()) {
            Log.d(TAG, "Log file does not exist.");
            return "";
        }
        try (FileReader fileReader = new FileReader(logFile); BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                logContent.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading log file", e);
            return null;
        }
        return logContent.toString();
    }

    public synchronized void clearLogs() {
        if (logFile.exists()) {
            try (FileWriter fileWriter = new FileWriter(logFile, false)) {
                fileWriter.write("");
                Log.d(TAG, "Log file content cleared successfully.");
            } catch (IOException e) {
                Log.e(TAG, "Failed to clear log file content.", e);
            }
        } else {
            Log.d(TAG, "Log file does not exist, no content to clear.");
        }
    }

    private synchronized void checkAndTruncateLogFile() {
        if (!logFile.exists()) {
            Log.d(TAG, "Log file does not exist for truncation check.");
            return;
        }
        long currentSize = logFile.length();
        if (currentSize <= MAX_LOG_SIZE_BYTES) {
            return;
        }
        Log.d(TAG, "Log file size (" + currentSize + " bytes) exceeds limit (" + MAX_LOG_SIZE_BYTES + " bytes). Truncating oldest " + TRUNCATE_SIZE_BYTES + " bytes.");
        try {
            long startByteToKeep = currentSize - TRUNCATE_SIZE_BYTES;
            try (RandomAccessFile raf = new RandomAccessFile(logFile, "rw")) {
                raf.seek(startByteToKeep);
                long firstLineToKeepStartPos = startByteToKeep;
                String firstPartialOrFullLine = raf.readLine();
                if (firstPartialOrFullLine != null) {
                    firstLineToKeepStartPos = raf.getFilePointer();
                } else {
                    Log.w(TAG, "Could not read line from calculated start position for truncation. Clearing file as a fallback.");
                    clearLogs();
                    return;
                }
                try (FileChannel sourceChannel = raf.getChannel()) {
                    File tempLogFile = new File(logFile.getParentFile(), LOG_FILE_NAME + ".tmp");
                    try (FileChannel destChannel = new FileOutputStream(tempLogFile).getChannel()) {
                        long bytesToTransfer = sourceChannel.size() - firstLineToKeepStartPos;
                        sourceChannel.transferTo(firstLineToKeepStartPos, bytesToTransfer, destChannel);
                    }
                    if (logFile.delete()) {
                        if (tempLogFile.renameTo(logFile)) {
                            Log.d(TAG, "Log file truncated successfully. New size: " + logFile.length() + " bytes.");
                        } else {
                            Log.e(TAG, "Failed to rename temp log file to original file.");
                            tempLogFile.delete();
                        }
                    } else {
                        Log.e(TAG, "Failed to delete original log file during truncation.");
                        tempLogFile.delete();
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error during log file truncation", e);
            clearLogs();
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during log file truncation", e);
            clearLogs();
        }
    }

    public File getLogFile() {
        return logFile;
    }
}