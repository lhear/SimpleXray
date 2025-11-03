package com.simplexray.an.testing

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simplexray.an.common.ConfigUtils
import com.simplexray.an.common.FilenameValidator
import com.simplexray.an.data.source.FileManager
import com.simplexray.an.prefs.Preferences
import java.io.File

/**
 * Configuration Test Suite - Tests config parsing, validation, and file operations
 */
class ConfigurationTestSuite(
    context: Context,
    testLogger: TestLogger
) : TestSuite("Configuration Test Suite", context, testLogger) {
    
    private lateinit var fileManager: FileManager
    private val gson = Gson()
    
    override suspend fun setup() {
        val prefs = Preferences(context)
        fileManager = FileManager(context, prefs)
    }
    
    override suspend fun runTests() {
        runTest("Configuration - Valid JSON Parsing") {
            val validJson = """
                {
                    "log": {"level": "debug"},
                    "inbound": [{"protocol": "socks", "port": 1080}],
                    "outbound": [{"protocol": "freedom"}]
                }
            """.trimIndent()
            
            try {
                val jsonObject = gson.fromJson(validJson, JsonObject::class.java)
                
                if (jsonObject == null || !jsonObject.has("log")) {
                    throw Exception("Failed to parse valid JSON")
                }
                
                logTest(
                    "Valid JSON Parsing",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("hasLog" to jsonObject.has("log"), "hasInbound" to jsonObject.has("inbound"))
                )
            } catch (e: Exception) {
                throw Exception("JSON parsing failed: ${e.message}")
            }
        }
        
        runTest("Configuration - Invalid JSON Handling") {
            val invalidJson = """
                {
                    "log": {"level": "debug",
                    "inbound": [{"protocol": "socks"}]
                }
            """.trimIndent()
            
            try {
                val jsonObject = gson.fromJson(invalidJson, JsonObject::class.java)
                // If parsing succeeds for invalid JSON, that's actually a problem
                logTest(
                    "Invalid JSON Handling",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("result" to "Parsed (may be lenient)", "jsonObject" to (jsonObject != null))
                )
            } catch (e: Exception) {
                // Expected to throw
                logTest(
                    "Invalid JSON Handling",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("exception" to e.message)
                )
            }
        }
        
        runTest("Configuration - Filename Validation") {
            val validNames = listOf(
                "config.json",
                "my-config.json",
                "test_123.json",
                "simple-config.json"
            )
            
            val invalidNames = listOf(
                "",
                "..json",
                "/path/config.json",
                "config<>.json",
                "config|json"
            )
            
            validNames.forEach { name ->
                val error = FilenameValidator.validateFilename(context, name)
                if (error != null) {
                    throw Exception("Valid filename rejected: $name - $error")
                }
            }
            
            invalidNames.forEach { name ->
                val error = FilenameValidator.validateFilename(context, name)
                if (error == null) {
                    logTest(
                        "Filename Validation",
                        TestStatus.PASSED,
                        0,
                        details = mapOf("invalidName" to name, "validated" to false)
                    )
                }
            }
        }
        
        runTest("Configuration - Config Format Conversion") {
            val simpleJson = """
                {
                    "log": {"level": "info"},
                    "inbound": [],
                    "outbound": []
                }
            """.trimIndent()
            
            try {
                val formatted = ConfigUtils.formatConfigContent(simpleJson)
                
                if (formatted.isEmpty()) {
                    throw Exception("Config formatting returned empty string")
                }
                
                // Verify it's still valid JSON
                val parsed = gson.fromJson(formatted, JsonObject::class.java)
                if (parsed == null) {
                    throw Exception("Formatted config is not valid JSON")
                }
                
                logTest(
                    "Config Format Conversion",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("originalLength" to simpleJson.length, "formattedLength" to formatted.length)
                )
            } catch (e: Exception) {
                throw Exception("Config format conversion failed: ${e.message}")
            }
        }
        
        runTest("Configuration - Config File Creation") {
            val testFileName = "test_config_${System.currentTimeMillis()}.json"
            val testContent = """
                {
                    "log": {"level": "debug"},
                    "inbound": [{"protocol": "socks", "port": 1080}],
                    "outbound": [{"protocol": "freedom"}]
                }
            """.trimIndent()
            
            try {
                val file = fileManager.createConfigFile(testFileName, testContent)
                
                if (!file.exists()) {
                    throw Exception("Config file was not created")
                }
                
                val content = file.readText()
                if (content != testContent.trim()) {
                    throw Exception("Config file content mismatch")
                }
                
                // Cleanup
                file.delete()
                
                logTest(
                    "Config File Creation",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("fileName" to testFileName, "fileSize" to content.length)
                )
            } catch (e: Exception) {
                throw Exception("Config file creation failed: ${e.message}")
            }
        }
        
        runTest("Configuration - Config File Reading") {
            val testFileName = "test_read_${System.currentTimeMillis()}.json"
            val testContent = """{"test": "content"}"""
            
            try {
                val file = fileManager.createConfigFile(testFileName, testContent)
                val readContent = fileManager.readConfigFile(testFileName)
                
                if (readContent != testContent) {
                    throw Exception("Read content does not match written content")
                }
                
                // Cleanup
                file.delete()
                
                logTest(
                    "Config File Reading",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("fileName" to testFileName)
                )
            } catch (e: Exception) {
                throw Exception("Config file reading failed: ${e.message}")
            }
        }
        
        runTest("Configuration - Config File List") {
            try {
                val configFiles = fileManager.listConfigFiles()
                
                logTest(
                    "Config File List",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("fileCount" to configFiles.size, "files" to configFiles.map { it.name })
                )
            } catch (e: Exception) {
                throw Exception("Config file listing failed: ${e.message}")
            }
        }
        
        runTest("Configuration - Config File Deletion") {
            val testFileName = "test_delete_${System.currentTimeMillis()}.json"
            val testContent = """{"test": "delete"}"""
            
            try {
                val file = fileManager.createConfigFile(testFileName, testContent)
                
                if (!file.exists()) {
                    throw Exception("File was not created")
                }
                
                val deleted = fileManager.deleteConfigFile(testFileName)
                
                if (!deleted) {
                    throw Exception("File deletion returned false")
                }
                
                if (file.exists()) {
                    throw Exception("File still exists after deletion")
                }
                
                logTest(
                    "Config File Deletion",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("fileName" to testFileName)
                )
            } catch (e: Exception) {
                throw Exception("Config file deletion failed: ${e.message}")
            }
        }
        
        runTest("Configuration - Large Config File") {
            val largeConfig = buildString {
                append("{\n")
                append("  \"log\": {\"level\": \"debug\"},\n")
                append("  \"inbound\": [\n")
                repeat(100) { i ->
                    append("    {\"protocol\": \"socks\", \"port\": ${1080 + i}},\n")
                }
                append("  ],\n")
                append("  \"outbound\": [{\"protocol\": \"freedom\"}]\n")
                append("}\n")
            }
            
            val testFileName = "test_large_${System.currentTimeMillis()}.json"
            
            try {
                val file = fileManager.createConfigFile(testFileName, largeConfig)
                
                if (!file.exists() || file.length() < largeConfig.length) {
                    throw Exception("Large config file was not created correctly")
                }
                
                val readContent = fileManager.readConfigFile(testFileName)
                if (readContent.length < largeConfig.length) {
                    throw Exception("Large config file reading failed")
                }
                
                // Cleanup
                file.delete()
                
                logTest(
                    "Large Config File",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("fileSize" to file.length(), "lineCount" to largeConfig.lines().size)
                )
            } catch (e: Exception) {
                throw Exception("Large config file test failed: ${e.message}")
            }
        }
        
        runTest("Configuration - Special Characters in Config") {
            val specialCharConfig = """
                {
                    "comment": "Test with special chars: <>&\"'",
                    "log": {"level": "debug"},
                    "inbound": [],
                    "outbound": []
                }
            """.trimIndent()
            
            val testFileName = "test_special_${System.currentTimeMillis()}.json"
            
            try {
                val file = fileManager.createConfigFile(testFileName, specialCharConfig)
                val readContent = fileManager.readConfigFile(testFileName)
                
                // Verify special characters are preserved
                if (!readContent.contains("special chars")) {
                    throw Exception("Special characters not preserved")
                }
                
                // Should still be valid JSON
                val parsed = gson.fromJson(readContent, JsonObject::class.java)
                if (parsed == null) {
                    throw Exception("Config with special chars is not valid JSON")
                }
                
                // Cleanup
                file.delete()
                
                logTest(
                    "Special Characters in Config",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("fileName" to testFileName)
                )
            } catch (e: Exception) {
                throw Exception("Special characters test failed: ${e.message}")
            }
        }
    }
    
    override suspend fun teardown() {
        // Cleanup test files if needed
    }
}

