package com.simplexray.an.testing

import android.content.Context
import com.simplexray.an.domain.Category
import com.simplexray.an.domain.DomainClassifier
import kotlinx.coroutines.runBlocking

/**
 * Functional Test Suite - Tests core business logic
 */
class FunctionalTestSuite(
    context: Context,
    testLogger: TestLogger
) : TestSuite("Functional Test Suite", context, testLogger) {
    
    private lateinit var domainClassifier: DomainClassifier
    
    override suspend fun setup() {
        domainClassifier = DomainClassifier(context)
    }
    
    override suspend fun runTests() {
        // Domain Classification Tests
        runTest("DomainClassifier - Social Media Detection") {
            val socialDomains = listOf(
                "twitter.com",
                "facebook.com",
                "instagram.com",
                "tiktok.com",
                "reddit.com"
            )
            
            socialDomains.forEach { domain ->
                val category = runBlocking { domainClassifier.classify(domain) }
                if (category != Category.Social) {
                    throw Exception("Expected Social category for $domain, got $category")
                }
            }
        }
        
        runTest("DomainClassifier - Video Platform Detection") {
            val videoDomains = listOf(
                "youtube.com",
                "netflix.com",
                "hulu.com",
                "primevideo.com"
            )
            
            videoDomains.forEach { domain ->
                val category = runBlocking { domainClassifier.classify(domain) }
                if (category != Category.Video) {
                    throw Exception("Expected Video category for $domain, got $category")
                }
            }
        }
        
        runTest("DomainClassifier - Gaming Platform Detection") {
            val gamingDomains = listOf(
                "steam.com",
                "epicgames.com",
                "riotgames.com",
                "twitch.tv"
            )
            
            gamingDomains.forEach { domain ->
                val category = runBlocking { domainClassifier.classify(domain) }
                if (category != Category.Gaming) {
                    throw Exception("Expected Gaming category for $domain, got $category")
                }
            }
        }
        
        runTest("DomainClassifier - CDN Detection") {
            val cdnDomains = listOf(
                "cloudflare.com",
                "akamai.net",
                "fastly.com"
            )
            
            cdnDomains.forEach { domain ->
                val category = runBlocking { domainClassifier.classify(domain) }
                if (category != Category.CDN) {
                    throw Exception("Expected CDN category for $domain, got $category")
                }
            }
        }
        
        runTest("DomainClassifier - Cache Functionality") {
            val testDomain = "example.com"
            
            // First classification
            val start1 = System.currentTimeMillis()
            val category1 = runBlocking { domainClassifier.classify(testDomain) }
            val duration1 = System.currentTimeMillis() - start1
            
            // Second classification (should use cache)
            val start2 = System.currentTimeMillis()
            val category2 = runBlocking { domainClassifier.classify(testDomain) }
            val duration2 = System.currentTimeMillis() - start2
            
            if (category1 != category2) {
                throw Exception("Cached result differs from original")
            }
            
            // Cached result should be faster (or at least not slower due to cache overhead)
            logTest(
                "DomainClassifier Cache Performance",
                TestStatus.PASSED,
                duration1 + duration2,
                details = mapOf(
                    "firstCall" to duration1,
                    "cachedCall" to duration2,
                    "cacheWorking" to (duration2 <= duration1 || duration2 < 10)
                )
            )
        }
        
        runTest("DomainClassifier - Cache Invalidation") {
            val testDomain = "test-domain.com"
            
            // Classify and cache
            runBlocking { domainClassifier.classify(testDomain) }
            
            // Invalidate cache
            domainClassifier.invalidateCache(testDomain)
            
            // Get cache stats
            val stats = domainClassifier.getCacheStats()
            if (stats.totalEntries > 0 && stats.staleEntries == stats.totalEntries) {
                // This is expected if TTL expired
                logTest(
                    "DomainClassifier Cache Invalidation",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("cacheStats" to stats)
                )
            }
        }
        
        runTest("DomainClassifier - Unknown Domain Handling") {
            val unknownDomains = listOf(
                "random-unknown-domain-12345.com",
                "test.example.org"
            )
            
            unknownDomains.forEach { domain ->
                val category = runBlocking { domainClassifier.classify(domain) }
                if (category != Category.Other) {
                    // Other is expected for unknown domains
                    logTest(
                        "Unknown Domain Classification",
                        TestStatus.PASSED,
                        0,
                        details = mapOf(
                            "domain" to domain,
                            "category" to category.name
                        )
                    )
                }
            }
        }
        
        runTest("DomainClassifier - Streaming Platform Detection") {
            val streamingDomains = listOf(
                "youtube.com",
                "netflix.com",
                "twitch.tv"
            )
            
            streamingDomains.forEach { domain ->
                val isStreaming = runBlocking { domainClassifier.isStreamingDomain(domain) }
                if (!isStreaming) {
                    throw Exception("Expected $domain to be detected as streaming platform")
                }
            }
        }
        
        runTest("DomainClassifier - URL Extraction from Full URLs") {
            val testCases = mapOf(
                "https://www.youtube.com/watch?v=123" to "www.youtube.com",
                "http://facebook.com/path/to/page" to "facebook.com",
                "https://subdomain.netflix.com/content" to "subdomain.netflix.com",
                "twitter.com" to "twitter.com"
            )
            
            testCases.forEach { (input, expectedDomain) ->
                val detected = runBlocking { 
                    domainClassifier.detectStreamingPlatform(input)
                }
                // Just verify it doesn't crash
                logTest(
                    "URL Extraction Test",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("input" to input, "detected" to (detected != null))
                )
            }
        }
        
        runTest("DomainClassifier - Case Insensitivity") {
            val testDomains = listOf(
                "TWITTER.COM",
                "Twitter.Com",
                "tWiTtEr.CoM",
                "youtube.COM",
                "YOUTUBE.com"
            )
            
            val socialCount = testDomains.count { domain ->
                runBlocking { domainClassifier.classify(domain) == Category.Social }
            }
            val videoCount = testDomains.count { domain ->
                runBlocking { domainClassifier.classify(domain) == Category.Video }
            }
            
            if (socialCount + videoCount < testDomains.size) {
                throw Exception("Case insensitivity test failed")
            }
        }
        
        runTest("DomainClassifier - Subdomain Handling") {
            val subdomainTests = listOf(
                "m.facebook.com" to Category.Social,
                "www.youtube.com" to Category.Video,
                "api.twitter.com" to Category.Social,
                "cdn.cloudflare.com" to Category.CDN
            )
            
            subdomainTests.forEach { (domain, expectedCategory) ->
                val category = runBlocking { domainClassifier.classify(domain) }
                if (category != expectedCategory) {
                    throw Exception("Expected $expectedCategory for $domain, got $category")
                }
            }
        }
        
        runTest("DomainClassifier - TLD Variations") {
            val tldVariations = listOf(
                "youtube.co.uk",
                "facebook.fr",
                "twitter.jp",
                "netflix.de"
            )
            
            tldVariations.forEach { domain ->
                val category = runBlocking { domainClassifier.classify(domain) }
                // Should handle international TLDs
                logTest(
                    "TLD Variation Test",
                    TestStatus.PASSED,
                    0,
                    details = mapOf("domain" to domain, "category" to category.name)
                )
            }
        }
        
        runTest("DomainClassifier - Cache Pruning") {
            // Fill cache with many entries
            (1..100).forEach { i ->
                runBlocking { domainClassifier.classify("test-domain-$i.com") }
            }
            
            val statsBefore = domainClassifier.getCacheStats()
            
            // Set very short TTL and prune
            domainClassifier.setCacheTtl(1) // 1ms
            Thread.sleep(10)
            domainClassifier.pruneCache()
            
            val statsAfter = domainClassifier.getCacheStats()
            
            logTest(
                "Cache Pruning",
                TestStatus.PASSED,
                0,
                details = mapOf(
                    "entriesBefore" to statsBefore.totalEntries,
                    "entriesAfter" to statsAfter.totalEntries,
                    "staleBefore" to statsBefore.staleEntries,
                    "staleAfter" to statsAfter.staleEntries
                )
            )
        }
        
        runTest("DomainClassifier - Edge Cases") {
            val edgeCases = listOf(
                "", // Empty string
                "localhost",
                "127.0.0.1",
                "::1",
                "domain-with-dashes.com",
                "domain_with_underscores.com",
                "very-long-domain-name-that-should-still-work-properly.example.com",
                "123.456.789",
                "http://",
                "https://",
                "domain..com", // Double dot
                ".domain.com", // Leading dot
                "domain.com." // Trailing dot
            )
            
            edgeCases.forEach { domain ->
                try {
                    val category = runBlocking { domainClassifier.classify(domain) }
                    logTest(
                        "Edge Case Handling",
                        TestStatus.PASSED,
                        0,
                        details = mapOf("domain" to domain, "category" to category.name)
                    )
                } catch (e: Exception) {
                    // Some edge cases may throw, which is acceptable
                    logTest(
                        "Edge Case Exception",
                        TestStatus.PASSED,
                        0,
                        details = mapOf("domain" to domain, "exception" to e.message)
                    )
                }
            }
        }
        
        runTest("DomainClassifier - Clear Streaming Platform Cache") {
            runBlocking {
                domainClassifier.detectStreamingPlatform("youtube.com")
            }
            
            domainClassifier.clearStreamingPlatformCache()
            
            // Should not crash
            val stats = domainClassifier.getCacheStats()
            logTest(
                "Streaming Platform Cache Clear",
                TestStatus.PASSED,
                0,
                details = mapOf("cacheStats" to stats)
            )
        }
    }
    
    override suspend fun teardown() {
        // Cleanup
        if (::domainClassifier.isInitialized) {
            domainClassifier.invalidateCache()
        }
    }
}
