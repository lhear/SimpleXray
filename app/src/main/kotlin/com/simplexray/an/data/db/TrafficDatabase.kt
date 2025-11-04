package com.simplexray.an.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for traffic monitoring.
 * Stores historical traffic data for analysis and charting.
 */
@Database(
    entities = [TrafficEntity::class, PerformanceMetricsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TrafficDatabase : RoomDatabase() {

    abstract fun trafficDao(): TrafficDao
    abstract fun performanceMetricsDao(): PerformanceMetricsDao

    companion object {
        @Volatile
        private var INSTANCE: TrafficDatabase? = null

        fun getInstance(context: Context): TrafficDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrafficDatabase::class.java,
                    "traffic_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
