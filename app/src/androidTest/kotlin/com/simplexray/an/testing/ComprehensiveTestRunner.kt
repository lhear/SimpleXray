package com.simplexray.an.testing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Comprehensive test runner that orchestrates all test suites
 */
class ComprehensiveTestRunner(
    private val context: Context,
    private val testLogger: TestLogger
) {
    private val TAG = "ComprehensiveTestRunner"
    
    private val testSuites = mutableListOf<TestSuite>()
    
    /**
     * Register a test suite
     */
    fun registerSuite(suite: TestSuite) {
        testSuites.add(suite)
        Log.d(TAG, "Registered test suite: ${suite.name}")
    }
    
    /**
     * Run all test suites sequentially
     */
    suspend fun runAllSequential(): TestReport = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "Starting Comprehensive Test Suite")
        Log.i(TAG, "Total suites: ${testSuites.size}")
        Log.i(TAG, "=".repeat(80))
        
        testSuites.forEach { suite ->
            runTestSuite(suite)
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "All test suites completed in ${duration}ms")
        Log.i(TAG, "=".repeat(80))
        
        testLogger.generateReport()
    }
    
    /**
     * Run all test suites in parallel (where possible)
     */
    suspend fun runAllParallel(): TestReport = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "Starting Comprehensive Test Suite (Parallel)")
        Log.i(TAG, "Total suites: ${testSuites.size}")
        Log.i(TAG, "=".repeat(80))
        
        coroutineScope {
            testSuites.map { suite ->
                async {
                    runTestSuite(suite)
                }
            }.awaitAll()
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "All test suites completed in ${duration}ms")
        Log.i(TAG, "=".repeat(80))
        
        testLogger.generateReport()
    }
    
    /**
     * Run a specific test suite
     */
    suspend fun runSuite(suiteName: String): TestReport = withContext(Dispatchers.Default) {
        val suite = testSuites.find { it.name == suiteName }
        if (suite != null) {
            runTestSuite(suite)
            testLogger.generateReport()
        } else {
            Log.w(TAG, "Test suite not found: $suiteName")
            testLogger.generateReport()
        }
    }
    
    /**
     * Run a single test suite
     */
    private suspend fun runTestSuite(suite: TestSuite) {
        val suiteStartTime = System.currentTimeMillis()
        testLogger.logSuiteStart(suite.name)
        
        try {
            suite.setup()
            suite.runTests()
            suite.teardown()
            
            val suiteDuration = System.currentTimeMillis() - suiteStartTime
            val results = testLogger.getResults().filter { it.testSuite == suite.name }
            val total = results.size
            val passed = results.count { it.status == TestStatus.PASSED }
            val failed = results.count { it.status == TestStatus.FAILED }
            val skipped = results.count { it.status == TestStatus.SKIPPED }
            
            testLogger.logSuiteEnd(suite.name, total, passed, failed, skipped, suiteDuration)
        } catch (e: Exception) {
            Log.e(TAG, "Error running test suite: ${suite.name}", e)
            val errorResult = TestResult(
                testName = "Suite Setup/Teardown",
                testSuite = suite.name,
                status = TestStatus.ERROR,
                duration = System.currentTimeMillis() - suiteStartTime,
                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()),
                errorMessage = e.message,
                stackTrace = e.stackTraceToString()
            )
            testLogger.logResult(errorResult)
        }
    }
    
    /**
     * Get registered test suites
     */
    fun getRegisteredSuites(): List<String> = testSuites.map { it.name }
}

/**
 * Base class for test suites
 */
abstract class TestSuite(
    val name: String,
    protected val context: Context,
    protected val testLogger: TestLogger
) {
    /**
     * Setup before running tests
     */
    open suspend fun setup() {
        // Override in subclasses
    }
    
    /**
     * Run all tests in this suite
     */
    abstract suspend fun runTests()
    
    /**
     * Teardown after running tests
     */
    open suspend fun teardown() {
        // Override in subclasses
    }
    
    /**
     * Helper to log a test result
     */
    protected fun logTest(
        testName: String,
        status: TestStatus,
        duration: Long,
        errorMessage: String? = null,
        stackTrace: String? = null,
        details: Map<String, Any?>? = null
    ) {
        val result = TestResult(
            testName = testName,
            testSuite = name,
            status = status,
            duration = duration,
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date()),
            errorMessage = errorMessage,
            stackTrace = stackTrace,
            details = details
        )
        testLogger.logResult(result)
    }
    
    /**
     * Helper to run a test with timing
     */
    protected suspend fun <T> runTest(
        testName: String,
        testBlock: suspend () -> T
    ): T? {
        val startTime = System.currentTimeMillis()
        return try {
            val result = testBlock()
            val duration = System.currentTimeMillis() - startTime
            logTest(testName, TestStatus.PASSED, duration)
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logTest(
                testName,
                TestStatus.FAILED,
                duration,
                errorMessage = e.message,
                stackTrace = e.stackTraceToString()
            )
            null
        }
    }
    
    /**
     * Helper to skip a test
     */
    protected fun skipTest(testName: String, reason: String = "") {
        logTest(
            testName,
            TestStatus.SKIPPED,
            0,
            errorMessage = if (reason.isNotEmpty()) "Skipped: $reason" else null
        )
    }
}
