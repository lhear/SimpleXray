package com.simplexray.an.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrafficSample::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trafficDao(): TrafficDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            // Double-check locking pattern for thread safety
            val instance = INSTANCE
            if (instance != null) {
                return instance
            }
            
            return synchronized(this) {
                val instance2 = INSTANCE
                if (instance2 != null) {
                    instance2
                } else {
                    try {
                        val appContext = context.applicationContext
                            ?: throw IllegalStateException("Application context is null")
                        
                        // Validate database directory exists and is writable
                        val dbDir = appContext.filesDir
                        if (!dbDir.exists() || !dbDir.canWrite()) {
                            throw IllegalStateException("Database directory not writable: ${dbDir.absolutePath}")
                        }
                        
                        // Check available disk space (minimum 1MB required)
                        val freeSpace = dbDir.freeSpace
                        if (freeSpace < 1024 * 1024) {
                            throw IllegalStateException("Insufficient disk space: ${freeSpace} bytes available")
                        }
                        
                        val db = Room.databaseBuilder(
                            appContext,
                            AppDatabase::class.java,
                            "simplexray.db"
                        )
                            // Note: fallbackToDestructiveMigration is used for development.
                            // For production, implement proper Room migrations when schema changes.
                            // See: https://developer.android.com/training/data-storage/room/migrating-db-versions
                            .fallbackToDestructiveMigration()
                            .build()
                        
                        INSTANCE = db
                        db
                    } catch (e: Exception) {
                        android.util.Log.e("AppDatabase", "Failed to initialize database", e)
                        throw e
                    }
                }
            }
        }
    }
}

