package com.simplexray.an.stats

import android.content.Context
import com.simplexray.an.db.AppDatabase
import com.xray.app.stats.command.StatsServiceGrpcKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class StatsRepository(
    private val context: Context,
    private val stub: StatsServiceGrpcKt.StatsServiceCoroutineStub,
    private val externalScope: CoroutineScope? = null
) {
    private val db by lazy { AppDatabase.get(context) }
    private val dao by lazy { db.trafficDao() }
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _live = MutableSharedFlow<BitratePoint>(replay = 1)
    val live: Flow<BitratePoint> = _live.asSharedFlow()

    fun start(mock: Boolean = false) {
        scope.launch {
            val flow = if (mock) MockTrafficObserver().live() else TrafficObserver(
                stub,
                intervalProvider = { com.simplexray.an.power.PowerAdaptive.intervalMs(context) },
                deadlineProvider = { com.simplexray.an.config.ApiConfig.getGrpcDeadlineMs(context) }
            ).live()
            flow.collect { point ->
                dao.insertSample(
                    com.simplexray.an.db.TrafficSample(
                        timestampMs = point.timestampMs,
                        uplinkBps = point.uplinkBps,
                        downlinkBps = point.downlinkBps
                    )
                )
                _live.emit(point)
                // Broadcast to global bus for detectors/consumers outside the VM
                BitrateBus.emit(point)
            }
        }
    }

    fun flow(): Flow<BitratePoint> = live
}
