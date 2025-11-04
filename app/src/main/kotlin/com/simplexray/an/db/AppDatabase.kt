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

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "simplexray.db"
            )
                .fallbackToDestructiveMigration()
                // TODO: Align schemas with TrafficDatabase and add proper migrations to protect persisted samples.
                .build()
                .also { INSTANCE = it }
        }
    }
}

