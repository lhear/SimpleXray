package com.simplexray.an.testing

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comprehensive test logger that writes test results to files and logcat
 */
class TestLogger(private val context: Context) {
    private val TAG = "TestLogger"
    private val testResults = ConcurrentLinkedQueue<TestResult>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    val logDirectory: File
    val resultsFile: File
    val summaryFile: File
    
    init {
        logDirectory = File(context.getExternalFilesDir(null), "test_logs")
        if (!logDirectory.exists()) {
            logDirectory.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        resultsFile = File(logDirectory, "test_results_$timestamp.json")
        summaryFile = File(logDirectory, "test_summary_$timestamp.txt")
        
        Log.d(TAG, "TestLogger initialized. Log directory: ${logDirectory.absolutePath}")
    }
    
    /**
     * Log a test result
     */
    fun logResult(result: TestResult) {
        testResults.offer(result)
        
        val status = when (result.status) {
            TestStatus.PASSED -> "✅ PASS"
            TestStatus.FAILED -> "❌ FAIL"
            TestStatus.SKIPPED -> "⏭ SKIP"
            TestStatus.ERROR -> "⚠️ ERROR"
        }
        
        val message = "[$status] ${result.testName} (${result.duration}ms)"
        if (result.status == TestStatus.FAILED || result.status == TestStatus.ERROR) {
            Log.e(TAG, message)
            result.errorMessage?.let { Log.e(TAG, "Error: $it") }
            result.stackTrace?.let { Log.e(TAG, "Stack trace:\n$it") }
        } else {
            Log.d(TAG, message)
        }
        
        result.details?.let { details ->
            Log.v(TAG, "Details: $details")
        }
        
        // Write to file asynchronously
        writeToFile(result)
    }
    
    /**
     * Log test suite start
     */
    fun logSuiteStart(suiteName: String) {
        val message = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "Starting Test Suite: $suiteName\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        Log.i(TAG, message)
        appendToSummary(message)
    }
    
    /**
     * Log test suite end
     */
    fun logSuiteEnd(suiteName: String, total: Int, passed: Int, failed: Int, skipped: Int, duration: Long) {
        val message = "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "Test Suite: $suiteName COMPLETED\n" +
                "Total: $total | Passed: $passed | Failed: $failed | Skipped: $skipped\n" +
                "Duration: ${duration}ms\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        Log.i(TAG, message)
        appendToSummary(message)
    }
    
    /**
     * Generate comprehensive test report
     */
    suspend fun generateReport(): TestReport = withContext(Dispatchers.IO) {
        val results = testResults.toList()
        val total = results.size
        val passed = results.count { it.status == TestStatus.PASSED }
        val failed = results.count { it.status == TestStatus.FAILED }
        val skipped = results.count { it.status == TestStatus.SKIPPED }
        val error = results.count { it.status == TestStatus.ERROR }
        
        val duration = results.sumOf { it.duration }
        
        val report = TestReport(
            timestamp = dateFormat.format(Date()),
            totalTests = total,
            passed = passed,
            failed = failed,
            skipped = skipped,
            errors = error,
            totalDuration = duration,
            results = results,
            successRate = if (total > 0) (passed.toFloat() / total * 100) else 0f
        )
        
        // Write JSON report
        try {
            FileWriter(resultsFile).use { writer ->
                gson.toJson(report, writer)
            }
            Log.i(TAG, "JSON report written to: ${resultsFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing JSON report", e)
        }
        
        // Write summary text report
        writeSummaryReport(report)
        
        report
    }
    
    private fun writeToFile(result: TestResult) {
        try {
            FileWriter(resultsFile, true).use { writer ->
                PrintWriter(writer).use { pw ->
                    pw.println(gson.toJson(result))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing test result to file", e)
        }
    }
    
    private fun appendToSummary(message: String) {
        try {
            FileWriter(summaryFile, true).use { writer ->
                PrintWriter(writer).use { pw ->
                    pw.println(message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to summary file", e)
        }
    }
    
    private fun writeSummaryReport(report: TestReport) {
        try {
            FileWriter(summaryFile, false).use { writer ->
                PrintWriter(writer).use { pw ->
                    pw.println("=".repeat(80))
                    pw.println("COMPREHENSIVE TEST REPORT")
                    pw.println("=".repeat(80))
                    pw.println("Timestamp: ${report.timestamp}")
                    pw.println("Total Tests: ${report.totalTests}")
                    pw.println("Passed: ${report.passed} (${String.format("%.2f", report.successRate)}%)")
                    pw.println("Failed: ${report.failed}")
                    pw.println("Skipped: ${report.skipped}")
                    pw.println("Errors: ${report.errors}")
                    pw.println("Total Duration: ${report.totalDuration}ms (${report.totalDuration / 1000.0}s)")
                    pw.println("=".repeat(80))
                    pw.println()
                    
                    // Group by test suite
                    val grouped = report.results.groupBy { it.testSuite }
                    grouped.forEach { (suite, results) ->
                        pw.println("Test Suite: $suite")
                        pw.println("-".repeat(80))
                        results.forEach { result ->
                            val status = when (result.status) {
                                TestStatus.PASSED -> "✅ PASS"
                                TestStatus.FAILED -> "❌ FAIL"
                                TestStatus.SKIPPED -> "⏭ SKIP"
                                TestStatus.ERROR -> "⚠️ ERROR"
                            }
                            pw.println("  [$status] ${result.testName} (${result.duration}ms)")
                            result.errorMessage?.let {
                                pw.println("    Error: $it")
                            }
                        }
                        pw.println()
                    }
                    
                    // Failed tests summary
                    val failedTests = report.results.filter { 
                        it.status == TestStatus.FAILED || it.status == TestStatus.ERROR 
                    }
                    if (failedTests.isNotEmpty()) {
                        pw.println("=".repeat(80))
                        pw.println("FAILED TESTS SUMMARY")
                        pw.println("=".repeat(80))
                        failedTests.forEach { result ->
                            pw.println("Test: ${result.testName}")
                            pw.println("Suite: ${result.testSuite}")
                            pw.println("Error: ${result.errorMessage ?: "Unknown error"}")
                            result.stackTrace?.let {
                                pw.println("Stack trace:")
                                pw.println(it)
                            }
                            pw.println("-".repeat(80))
                        }
                    }
                }
            }
            Log.i(TAG, "Summary report written to: ${summaryFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing summary report", e)
        }
    }
    
    /**
     * Clear all test results
     */
    fun clear() {
        testResults.clear()
        Log.d(TAG, "Test results cleared")
    }
    
    /**
     * Get all test results
     */
    fun getResults(): List<TestResult> = testResults.toList()
}

/**
 * Test result data class
 */
data class TestResult(
    val testName: String,
    val testSuite: String,
    val status: TestStatus,
    val duration: Long,
    val timestamp: String,
    val errorMessage: String? = null,
    val stackTrace: String? = null,
    val details: Map<String, Any?>? = null
)

/**
 * Test status enum
 */
enum class TestStatus {
    PASSED,
    FAILED,
    SKIPPED,
    ERROR
}

/**
 * Test report data class
 */
data class TestReport(
    val timestamp: String,
    val totalTests: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val errors: Int,
    val totalDuration: Long,
    val successRate: Float,
    val results: List<TestResult>
)
