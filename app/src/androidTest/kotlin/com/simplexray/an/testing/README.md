# Comprehensive Test Module

This module is designed to comprehensively test the SimpleXray application on Android devices and log the results.

## Features

- ✅ **Test Logger**: Logs all test results in JSON and text formats
- ✅ **Test Runner**: Runs tests sequentially or in parallel
- ✅ **UI Test Suite**: Tests all UI screens
- ✅ **Functional Test Suite**: Business logic tests (DomainClassifier, etc.)
- ✅ **Integration Test Suite**: Inter-component integration tests
- ✅ **Performance Test Suite**: Performance and scalability tests
- ✅ **HTML Report Generator**: Generates detailed HTML reports

## Usage

### Running via Test Activity

1. Build the application:

```bash
./gradlew assembleDebug
```

2. Launch the Test Activity:

```bash
adb shell am start -n com.simplexray.an/com.simplexray.an.testing.ComprehensiveTestActivity
```

Or, after opening the app, to launch the test activity:

```kotlin
startActivity(ComprehensiveTestActivity.createIntent(context))
```

3. The activity provides the following buttons:
   - **Run All Tests (Parallel)**: Runs all tests in parallel
   - **Run All Tests (Sequential)**: Runs all tests sequentially
   - **Generate HTML Report**: Creates an HTML report from test results

### Programmatic Usage

```kotlin
val testLogger = TestLogger(context)
val testRunner = ComprehensiveTestRunner(context, testLogger)

// Register test suites
testRunner.registerSuite(UITestSuite(context, testLogger))
testRunner.registerSuite(FunctionalTestSuite(context, testLogger))
testRunner.registerSuite(IntegrationTestSuite(context, testLogger))
testRunner.registerSuite(PerformanceTestSuite(context, testLogger))

// Run tests
lifecycleScope.launch {
    val report = testRunner.runAllSequential()
    // or
    val report = testRunner.runAllParallel()

    // Generate report
    val htmlReport = TestReportGenerator(context)
    val htmlFile = File(testLogger.logDirectory, "report.html")
    htmlReport.generateHTMLReport(report, htmlFile)
}
```

## Test Results

Test results are saved to the following locations:

- **JSON Report**: `/sdcard/Android/data/com.simplexray.an/files/test_logs/test_results_[timestamp].json`
- **Text Summary**: `/sdcard/Android/data/com.simplexray.an/files/test_logs/test_summary_[timestamp].txt`
- **HTML Report**: `/sdcard/Android/data/com.simplexray.an/files/test_logs/test_report_[timestamp].html`

## Test Suites

### UI Test Suite

- Dashboard screen tests
- UI rendering performance tests
- Empty state handling tests

### Functional Test Suite

- DomainClassifier tests
  - Social media detection
  - Video platform detection
  - Gaming platform detection
  - CDN detection
  - Cache functionality
  - Streaming platform detection

### Integration Test Suite

- LogFileManager tests
- Network connectivity tests
- File operations tests
- System resource tests

### Performance Test Suite

- DomainClassifier performance tests
- Bulk operations tests
- Concurrent operations tests
- Memory usage tests
- Cache hit rate tests

## Test Report Format

### JSON Format

```json
{
  "timestamp": "2024-01-01 12:00:00.000",
  "totalTests": 50,
  "passed": 45,
  "failed": 3,
  "skipped": 2,
  "errors": 0,
  "totalDuration": 15000,
  "successRate": 90.0,
  "results": [...]
}
```

### TestResult Format

```json
{
  "testName": "DomainClassifier - Social Media Detection",
  "testSuite": "Functional Test Suite",
  "status": "PASSED",
  "duration": 150,
  "timestamp": "2024-01-01 12:00:00.000",
  "errorMessage": null,
  "stackTrace": null,
  "details": {
    "domainsTested": 5
  }
}
```

## Customization

To add new test suites, extend the `TestSuite` class:

```kotlin
class MyCustomTestSuite(
    context: Context,
    testLogger: TestLogger
) : TestSuite("My Custom Suite", context, testLogger) {

    override suspend fun setup() {
        // Setup code
    }

    override suspend fun runTests() {
        runTest("My Test") {
            // Test code
        }
    }

    override suspend fun teardown() {
        // Cleanup code
    }
}
```

## Notes

- Tests must be run on an Android device (emulator or physical device)
- Some tests require internet connection
- Test results are written to external storage; necessary permissions must be granted
- HTML reports can be viewed in a browser

## Troubleshooting

### Test Activity won't open

- Ensure AndroidManifest.xml is configured correctly
- Ensure the app is a debug build

### Tests are failing

- Check log files
- Ensure the device has internet connection
- Ensure necessary permissions are granted

### HTML report won't open

- Check the file path manually
- Check FileProvider configuration
