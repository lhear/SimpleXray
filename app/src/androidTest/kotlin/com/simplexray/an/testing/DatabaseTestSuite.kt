package com.simplexray.an.testing

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.simplexray.an.data.db.TrafficDatabase
import com.simplexray.an.data.db.TrafficEntity
import com.simplexray.an.domain.model.TrafficSnapshot
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Database Test Suite - Tests Room database operations
 */
class DatabaseTestSuite(
    context: Context,
    testLogger: TestLogger
) : TestSuite("Database Test Suite", context, testLogger) {
    
    private lateinit var database: TrafficDatabase
    
    override suspend fun setup() {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            TrafficDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }
    
    override suspend fun runTests() {
        val dao = database.trafficDao()
        
        runTest("Database - Insert Single Entity") {
            val entity = TrafficEntity(
                timestamp = System.currentTimeMillis(),
                rxBytes = 1024 * 1024,
                txBytes = 512 * 1024,
                rxRateMbps = 10.5f,
                txRateMbps = 5.2f,
                latencyMs = 50,
                isConnected = true
            )
            
            runBlocking {
                dao.insert(entity)
            }
            
            val count = runBlocking { dao.getCount() }
            if (count < 1) {
                throw Exception("Entity was not inserted")
            }
        }
        
        runTest("Database - Insert Multiple Entities") {
            val entities = (1..100).map { i ->
                TrafficEntity(
                    timestamp = System.currentTimeMillis() + i * 1000,
                    rxBytes = i * 1024L,
                    txBytes = i * 512L,
                    rxRateMbps = i.toFloat(),
                    txRateMbps = i.toFloat() / 2,
                    latencyMs = i.toLong(),
                    isConnected = i % 2 == 0
                )
            }
            
            runBlocking {
                entities.forEach { dao.insert(it) }
            }
            
            val count = runBlocking { dao.getAllLogs().first() }.size
            if (count < entities.size) {
                throw Exception("Expected ${entities.size} entities, got $count")
            }
        }
        
        runTest("Database - Query All Entities") {
            val allEntities = runBlocking { dao.getAllLogs().first() }
            
            logTest(
                "Query All Entities",
                TestStatus.PASSED,
                0,
                details = mapOf("entityCount" to allEntities.size)
            )
        }
        
        runTest("Database - Query by Time Range") {
            val now = System.currentTimeMillis()
            val startTime = now - 3600000 // 1 hour ago
            val endTime = now
            
            val entities = runBlocking { 
                dao.getLogsInRange(startTime, endTime).first() 
            }
            
            entities.forEach { entity ->
                if (entity.timestamp < startTime || entity.timestamp > endTime) {
                    throw Exception("Entity timestamp out of range")
                }
            }
        }
        
        runTest("Database - Delete All Entities") {
            runBlocking { dao.deleteAll() }
            val remaining = runBlocking { dao.getAllLogs().first() }
            
            if (remaining.isNotEmpty()) {
                throw Exception("Expected empty database, got ${remaining.size} entities")
            }
        }
        
        runTest("Database - Delete Logs Older Than") {
            val cutoffTime = System.currentTimeMillis()
            
            // Insert some old and new entities
            runBlocking {
                dao.insert(TrafficEntity(
                    timestamp = cutoffTime - 2000,
                    rxBytes = 1000,
                    txBytes = 500,
                    rxRateMbps = 1.0f,
                    txRateMbps = 0.5f,
                    latencyMs = 10,
                    isConnected = true
                ))
                dao.insert(TrafficEntity(
                    timestamp = cutoffTime + 2000,
                    rxBytes = 2000,
                    txBytes = 1000,
                    rxRateMbps = 2.0f,
                    txRateMbps = 1.0f,
                    latencyMs = 20,
                    isConnected = true
                ))
            }
            
            val deleted = runBlocking { dao.deleteLogsOlderThan(cutoffTime) }
            
            val remaining = runBlocking { dao.getAllLogs().first() }
            if (remaining.any { it.timestamp < cutoffTime }) {
                throw Exception("Old entities were not deleted")
            }
        }
        
        runTest("Database - Concurrent Writes") {
            val threadCount = 10
            val entitiesPerThread = 10
            val latch = CountDownLatch(threadCount)
            val errorCount = java.util.concurrent.atomic.AtomicInteger(0)
            
            repeat(threadCount) { threadId ->
                Thread {
                    try {
                        runBlocking {
                            repeat(entitiesPerThread) { i ->
                                val entity = TrafficEntity(
                                    timestamp = System.currentTimeMillis() + threadId * 1000 + i,
                                    rxBytes = (threadId * entitiesPerThread + i).toLong(),
                                    txBytes = (threadId * entitiesPerThread + i).toLong() / 2,
                                    rxRateMbps = (threadId * entitiesPerThread + i).toFloat(),
                                    txRateMbps = (threadId * entitiesPerThread + i).toFloat() / 2,
                                    latencyMs = (threadId * entitiesPerThread + i).toLong(),
                                    isConnected = true
                                )
                                dao.insert(entity)
                            }
                        }
                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }.start()
            }
            
            latch.await(5, TimeUnit.SECONDS)
            
            if (errorCount.get() > threadCount / 2) {
                throw Exception("Too many concurrent write errors: ${errorCount.get()}")
            }
        }
        
        runTest("Database - Large Data Insertion") {
            val largeEntities = (1..1000).map { i ->
                TrafficEntity(
                    timestamp = System.currentTimeMillis() + i,
                    rxBytes = i * 1024L * 1024L,
                    txBytes = i * 512L * 1024L,
                    rxRateMbps = i.toFloat(),
                    txRateMbps = i.toFloat() / 2,
                    latencyMs = i.toLong(),
                    isConnected = i % 2 == 0
                )
            }
            
            val startTime = System.currentTimeMillis()
            runBlocking {
                largeEntities.forEach { dao.insert(it) }
            }
            val duration = System.currentTimeMillis() - startTime
            
            val count = runBlocking { dao.getAllLogs().first() }.size
            
            logTest(
                "Large Data Insertion",
                if (count >= largeEntities.size) TestStatus.PASSED else TestStatus.FAILED,
                duration,
                details = mapOf(
                    "inserted" to largeEntities.size,
                    "actualCount" to count,
                    "durationMs" to duration,
                    "throughput" to (largeEntities.size * 1000.0 / duration)
                )
            )
            
            if (count < largeEntities.size) {
                throw Exception("Expected ${largeEntities.size} entities, got $count")
            }
        }
        
        runTest("Database - Flow Observation") {
            val initialCount = runBlocking { dao.getAllLogs().first() }.size
            
            val testEntity = TrafficEntity(
                timestamp = System.currentTimeMillis(),
                rxBytes = 999999,
                txBytes = 888888,
                rxRateMbps = 99.9f,
                txRateMbps = 88.8f,
                latencyMs = 999,
                isConnected = true
            )
            
            runBlocking { dao.insert(testEntity) }
            
            // Wait a bit for Flow to emit
            Thread.sleep(100)
            
            val updatedCount = runBlocking { dao.getAllLogs().first() }.size
            
            if (updatedCount <= initialCount) {
                throw Exception("Flow did not emit updated data")
            }
        }
        
        runTest("Database - Query Statistics") {
            val startOfDay = System.currentTimeMillis() - 86400000
            
            val totalBytes = runBlocking { dao.getTotalBytesToday(startOfDay) }
            val count = runBlocking { dao.getCountToday(startOfDay) }
            
            logTest(
                "Database Statistics",
                TestStatus.PASSED,
                0,
                details = mapOf(
                    "totalBytes" to (totalBytes?.total ?: 0),
                    "countToday" to count
                )
            )
        }
        
        runTest("Database - Domain Model Conversion") {
            val entity = TrafficEntity(
                timestamp = System.currentTimeMillis(),
                rxBytes = 1024 * 1024,
                txBytes = 512 * 1024,
                rxRateMbps = 10.5f,
                txRateMbps = 5.2f,
                latencyMs = 50,
                isConnected = true
            )
            
            val snapshot = entity.toSnapshot()
            
            if (snapshot.timestamp != entity.timestamp ||
                snapshot.rxBytes != entity.rxBytes ||
                snapshot.txBytes != entity.txBytes) {
                throw Exception("Domain model conversion failed")
            }
            
            val backToEntity = snapshot.toEntity()
            
            if (backToEntity.rxBytes != entity.rxBytes ||
                backToEntity.txBytes != entity.txBytes) {
                throw Exception("Round-trip conversion failed")
            }
        }
    }
    
    override suspend fun teardown() {
        database.close()
    }
    
    // Helper to get first value from Flow
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.first(): T {
        var value: T? = null
        collect { value = it; return@collect }
        return value ?: throw IllegalStateException("Flow is empty")
    }
}

