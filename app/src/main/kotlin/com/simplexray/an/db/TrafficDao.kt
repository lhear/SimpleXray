package com.simplexray.an.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrafficDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: TrafficSample)

    @Query("DELETE FROM TrafficSample WHERE timestampMs < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)

    @Query("SELECT * FROM TrafficSample WHERE timestampMs >= :from ORDER BY timestampMs ASC")
    suspend fun getSince(from: Long): List<TrafficSample>
}

