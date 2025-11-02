package com.simplexray.an.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["timestampMs"])])
data class TrafficSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val uplinkBps: Long,
    val downlinkBps: Long
)

