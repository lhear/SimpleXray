package com.simplexray.an.testing

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * HTML and text report generator for test results
 */
class TestReportGenerator(private val context: Context) {
    private val TAG = "TestReportGenerator"
    
    /**
     * Generate HTML report from test report
     */
    fun generateHTMLReport(report: TestReport, outputFile: File) {
        try {
            FileWriter(outputFile).use { writer ->
                PrintWriter(writer).use { pw ->
                    pw.println("<!DOCTYPE html>")
                    pw.println("<html lang=\"en\">")
                    pw.println("<head>")
                    pw.println("<meta charset=\"UTF-8\">")
                    pw.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                    pw.println("<title>SimpleXray Test Report</title>")
                    pw.println("<style>")
                    pw.println(htmlStyles())
                    pw.println("</style>")
                    pw.println("</head>")
                    pw.println("<body>")
                    
                    // Header
                    pw.println("<div class=\"header\">")
                    pw.println("<h1>SimpleXray Comprehensive Test Report</h1>")
                    pw.println("<p class=\"timestamp\">Generated: ${report.timestamp}</p>")
                    pw.println("</div>")
                    
                    // Summary
                    pw.println("<div class=\"summary\">")
                    pw.println("<h2>Summary</h2>")
                    pw.println("<div class=\"stats-grid\">")
                    pw.println("<div class=\"stat-card total\">")
                    pw.println("<div class=\"stat-value\">${report.totalTests}</div>")
                    pw.println("<div class=\"stat-label\">Total Tests</div>")
                    pw.println("</div>")
                    pw.println("<div class=\"stat-card passed\">")
                    pw.println("<div class=\"stat-value\">${report.passed}</div>")
                    pw.println("<div class=\"stat-label\">Passed</div>")
                    pw.println("</div>")
                    pw.println("<div class=\"stat-card failed\">")
                    pw.println("<div class=\"stat-value\">${report.failed}</div>")
                    pw.println("<div class=\"stat-label\">Failed</div>")
                    pw.println("</div>")
                    pw.println("<div class=\"stat-card skipped\">")
                    pw.println("<div class=\"stat-value\">${report.skipped}</div>")
                    pw.println("<div class=\"stat-label\">Skipped</div>")
                    pw.println("</div>")
                    pw.println("<div class=\"stat-card duration\">")
                    pw.println("<div class=\"stat-value\">${report.totalDuration / 1000.0}s</div>")
                    pw.println("<div class=\"stat-label\">Duration</div>")
                    pw.println("</div>")
                    pw.println("<div class=\"stat-card success-rate\">")
                    pw.println("<div class=\"stat-value\">${String.format("%.1f", report.successRate)}%</div>")
                    pw.println("<div class=\"stat-label\">Success Rate</div>")
                    pw.println("</div>")
                    pw.println("</div>")
                    pw.println("</div>")
                    
                    // Test Results by Suite
                    val groupedBySuite = report.results.groupBy { it.testSuite }
                    groupedBySuite.forEach { (suite, results) ->
                        pw.println("<div class=\"test-suite\">")
                        pw.println("<h2>${suite}</h2>")
                        
                        val suitePassed = results.count { it.status == TestStatus.PASSED }
                        val suiteFailed = results.count { it.status == TestStatus.FAILED }
                        val suiteSkipped = results.count { it.status == TestStatus.SKIPPED }
                        
                        pw.println("<div class=\"suite-stats\">")
                        pw.println("Passed: $suitePassed | Failed: $suiteFailed | Skipped: $suiteSkipped")
                        pw.println("</div>")
                        
                        pw.println("<table class=\"test-table\">")
                        pw.println("<thead>")
                        pw.println("<tr>")
                        pw.println("<th>Test Name</th>")
                        pw.println("<th>Status</th>")
                        pw.println("<th>Duration</th>")
                        pw.println("<th>Timestamp</th>")
                        pw.println("</tr>")
                        pw.println("</thead>")
                        pw.println("<tbody>")
                        
                        results.forEach { result ->
                            val statusClass = when (result.status) {
                                TestStatus.PASSED -> "status-passed"
                                TestStatus.FAILED -> "status-failed"
                                TestStatus.SKIPPED -> "status-skipped"
                                TestStatus.ERROR -> "status-error"
                            }
                            val statusIcon = when (result.status) {
                                TestStatus.PASSED -> "✅"
                                TestStatus.FAILED -> "❌"
                                TestStatus.SKIPPED -> "⏭"
                                TestStatus.ERROR -> "⚠️"
                            }
                            
                            pw.println("<tr class=\"$statusClass\">")
                            pw.println("<td>${result.testName}</td>")
                            pw.println("<td>$statusIcon ${result.status.name}</td>")
                            pw.println("<td>${result.duration}ms</td>")
                            pw.println("<td>${result.timestamp}</td>")
                            pw.println("</tr>")
                            
                            if (result.errorMessage != null || result.details != null) {
                                pw.println("<tr class=\"details-row\">")
                                pw.println("<td colspan=\"4\">")
                                pw.println("<div class=\"test-details\">")
                                if (result.errorMessage != null) {
                                    pw.println("<div class=\"error-message\"><strong>Error:</strong> ${result.errorMessage}</div>")
                                }
                                if (result.stackTrace != null) {
                                    pw.println("<details class=\"stack-trace\">")
                                    pw.println("<summary>Stack Trace</summary>")
                                    pw.println("<pre>${result.stackTrace}</pre>")
                                    pw.println("</details>")
                                }
                                if (result.details != null && result.details.isNotEmpty()) {
                                    pw.println("<div class=\"test-details-map\">")
                                    pw.println("<strong>Details:</strong>")
                                    pw.println("<ul>")
                                    result.details.forEach { (key, value) ->
                                        pw.println("<li><strong>$key:</strong> $value</li>")
                                    }
                                    pw.println("</ul>")
                                    pw.println("</div>")
                                }
                                pw.println("</div>")
                                pw.println("</td>")
                                pw.println("</tr>")
                            }
                        }
                        
                        pw.println("</tbody>")
                        pw.println("</table>")
                        pw.println("</div>")
                    }
                    
                    pw.println("</body>")
                    pw.println("</html>")
                }
            }
            Log.i(TAG, "HTML report generated: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating HTML report", e)
        }
    }
    
    private fun htmlStyles(): String {
        return """
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                margin: 0;
                padding: 20px;
                background-color: #f5f5f5;
                color: #333;
            }
            .header {
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                padding: 30px;
                border-radius: 10px;
                margin-bottom: 30px;
                box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            }
            .header h1 {
                margin: 0 0 10px 0;
                font-size: 2em;
            }
            .timestamp {
                margin: 0;
                opacity: 0.9;
            }
            .summary {
                background: white;
                padding: 25px;
                border-radius: 10px;
                margin-bottom: 30px;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            .summary h2 {
                margin-top: 0;
            }
            .stats-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                gap: 15px;
                margin-top: 20px;
            }
            .stat-card {
                padding: 20px;
                border-radius: 8px;
                text-align: center;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            .stat-card.total { background: #e3f2fd; }
            .stat-card.passed { background: #e8f5e9; }
            .stat-card.failed { background: #ffebee; }
            .stat-card.skipped { background: #fff3e0; }
            .stat-card.duration { background: #f3e5f5; }
            .stat-card.success-rate { background: #e0f2f1; }
            .stat-value {
                font-size: 2em;
                font-weight: bold;
                color: #333;
            }
            .stat-label {
                margin-top: 5px;
                color: #666;
                font-size: 0.9em;
            }
            .test-suite {
                background: white;
                padding: 25px;
                border-radius: 10px;
                margin-bottom: 30px;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            .test-suite h2 {
                margin-top: 0;
                color: #667eea;
            }
            .suite-stats {
                margin-bottom: 20px;
                color: #666;
            }
            .test-table {
                width: 100%;
                border-collapse: collapse;
                margin-top: 15px;
            }
            .test-table th {
                background: #f5f5f5;
                padding: 12px;
                text-align: left;
                font-weight: 600;
                border-bottom: 2px solid #ddd;
            }
            .test-table td {
                padding: 12px;
                border-bottom: 1px solid #eee;
            }
            .test-table tr:hover {
                background: #f9f9f9;
            }
            .status-passed {
                background: #f1f8f4;
            }
            .status-failed {
                background: #fff5f5;
            }
            .status-skipped {
                background: #fffbf0;
            }
            .status-error {
                background: #fff0f0;
            }
            .details-row {
                background: #fafafa;
            }
            .test-details {
                padding: 10px;
                font-size: 0.9em;
            }
            .error-message {
                color: #d32f2f;
                margin-bottom: 10px;
            }
            .stack-trace {
                margin-top: 10px;
            }
            .stack-trace summary {
                cursor: pointer;
                color: #666;
                font-weight: 500;
            }
            .stack-trace pre {
                background: #f5f5f5;
                padding: 10px;
                border-radius: 4px;
                overflow-x: auto;
                font-size: 0.85em;
            }
            .test-details-map ul {
                margin: 5px 0;
                padding-left: 20px;
            }
            .test-details-map li {
                margin: 3px 0;
            }
            @media (max-width: 768px) {
                .stats-grid {
                    grid-template-columns: repeat(2, 1fr);
                }
                .test-table {
                    font-size: 0.9em;
                }
            }
        """.trimIndent()
    }
}
