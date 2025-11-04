package com.simplexray.an.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// TODO: Add database version migration strategy when schema changes
// TODO: Consider adding database backup/restore functionality
@Database(
    entities = [TrafficSample::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trafficDao(): TrafficDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // TODO: Add database initialization error handling
        // TODO: Consider using dependency injection for database instance
        // BUG: Database initialization lacks proper error handling - Room.databaseBuilder may throw exceptions
        // BUG: No validation for database file permissions or disk space availability
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "simplexray.db"
            )
                .fallbackToDestructiveMigration()
                // Note: AppDatabase and TrafficDatabase currently serve different purposes.
                // When schema changes are needed, add proper Room migrations to preserve data.
                // Consider consolidating with TrafficDatabase if both serve similar traffic logging needs.
                // TODO: Implement proper migration strategy instead of fallbackToDestructiveMigration
                // BUG: fallbackToDestructiveMigration will delete all data on schema changes - this may cause data loss
                .build()
                .also { INSTANCE = it }
        }
    }
}

