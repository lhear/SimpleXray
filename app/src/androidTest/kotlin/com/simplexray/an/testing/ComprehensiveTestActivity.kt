package com.simplexray.an.testing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Activity to run comprehensive tests with UI feedback
 */
class ComprehensiveTestActivity : AppCompatActivity() {
    
    private lateinit var logTextView: TextView
    private lateinit var runAllButton: Button
    private lateinit var runSequentialButton: Button
    private lateinit var generateReportButton: Button
    
    private lateinit var testRunner: ComprehensiveTestRunner
    private lateinit var testLogger: TestLogger
    private lateinit var reportGenerator: TestReportGenerator
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create views programmatically
        val scrollView = ScrollView(this)
        logTextView = TextView(this).apply {
            text = "Test Logger Ready\n"
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setTextIsSelectable(true)
        }
        scrollView.addView(logTextView)
        
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        runAllButton = Button(this).apply {
            text = "Run All Tests (Parallel)"
            setOnClickListener { runAllTests(parallel = true) }
        }
        
        runSequentialButton = Button(this).apply {
            text = "Run All Tests (Sequential)"
            setOnClickListener { runAllTests(parallel = false) }
        }
        
        generateReportButton = Button(this).apply {
            text = "Generate HTML Report"
            setOnClickListener { generateReport() }
        }
        
        buttonLayout.addView(runAllButton)
        buttonLayout.addView(runSequentialButton)
        buttonLayout.addView(generateReportButton)
        
        val mainLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        mainLayout.addView(buttonLayout)
        mainLayout.addView(scrollView)
        
        setContentView(mainLayout)
        
        // Initialize test components
        testLogger = TestLogger(this)
        testRunner = ComprehensiveTestRunner(this, testLogger)
        reportGenerator = TestReportGenerator(this)
        
        // Register all test suites
        testRunner.registerSuite(UITestSuite(this, testLogger))
        testRunner.registerSuite(FunctionalTestSuite(this, testLogger))
        testRunner.registerSuite(IntegrationTestSuite(this, testLogger))
        testRunner.registerSuite(PerformanceTestSuite(this, testLogger))
        testRunner.registerSuite(DatabaseTestSuite(this, testLogger))
        testRunner.registerSuite(NetworkTestSuite(this, testLogger))
        testRunner.registerSuite(ConfigurationTestSuite(this, testLogger))
        testRunner.registerSuite(SecurityTestSuite(this, testLogger))
        testRunner.registerSuite(StressTestSuite(this, testLogger))
        testRunner.registerSuite(MemoryLeakTestSuite(this, testLogger))
        
        appendLog("Comprehensive Test Module Initialized")
        appendLog("Registered ${testRunner.getRegisteredSuites().size} test suites:")
        testRunner.getRegisteredSuites().forEach { suite ->
            appendLog("  - $suite")
        }
    }
    
    private fun runAllTests(parallel: Boolean) {
        runAllButton.isEnabled = false
        runSequentialButton.isEnabled = false
        
        appendLog("\n${"=".repeat(50)}")
        appendLog("Starting ${if (parallel) "parallel" else "sequential"} test execution...")
        appendLog("${"=".repeat(50)}\n")
        
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val report = withContext(Dispatchers.Default) {
                    if (parallel) {
                        testRunner.runAllParallel()
                    } else {
                        testRunner.runAllSequential()
                    }
                }
                
                appendLog("\n${"=".repeat(50)}")
                appendLog("Test Execution Completed!")
                appendLog("${"=".repeat(50)}")
                appendLog("Total Tests: ${report.totalTests}")
                appendLog("Passed: ${report.passed}")
                appendLog("Failed: ${report.failed}")
                appendLog("Skipped: ${report.skipped}")
                appendLog("Errors: ${report.errors}")
                appendLog("Success Rate: ${String.format("%.2f", report.successRate)}%")
                appendLog("Total Duration: ${report.totalDuration}ms (${report.totalDuration / 1000.0}s)")
                appendLog("\nResults saved to:")
                appendLog("  JSON: ${testLogger.resultsFile.absolutePath}")
                appendLog("  Summary: ${testLogger.summaryFile.absolutePath}")
                
                // Auto-generate HTML report
                generateReport()
                
            } catch (e: Exception) {
                appendLog("\nERROR: ${e.message}")
                appendLog(e.stackTraceToString())
            } finally {
                runAllButton.isEnabled = true
                runSequentialButton.isEnabled = true
            }
        }
    }
    
    private fun generateReport() {
        generateReportButton.isEnabled = false
        
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val report = withContext(Dispatchers.Default) {
                    testLogger.generateReport()
                }
                
                val htmlFile = File(testLogger.logDirectory, "test_report_${System.currentTimeMillis()}.html")
                withContext(Dispatchers.IO) {
                    reportGenerator.generateHTMLReport(report, htmlFile)
                }
                
                appendLog("\nHTML Report generated:")
                appendLog("  ${htmlFile.absolutePath}")
                
                // Open file browser intent
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            FileProvider.getUriForFile(
                                this@ComprehensiveTestActivity,
                                "${packageName}.fileprovider",
                                htmlFile
                            ),
                            "text/html"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    appendLog("Could not open HTML report. Please open manually:")
                    appendLog("  ${htmlFile.absolutePath}")
                }
                
            } catch (e: Exception) {
                appendLog("\nERROR generating report: ${e.message}")
            } finally {
                generateReportButton.isEnabled = true
            }
        }
    }
    
    private fun appendLog(message: String) {
        runOnUiThread {
            logTextView.append("$message\n")
            // Auto-scroll to bottom
            val scrollView = logTextView.parent as? ScrollView
            scrollView?.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }
    
    companion object {
        /**
         * Create intent to start test activity
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, ComprehensiveTestActivity::class.java)
        }
    }
}
