package com.simplexray.an.alert

import android.content.Context
import android.util.Log
import com.simplexray.an.domain.DomainClassifier
import com.simplexray.an.topology.TopologyRepository
import com.simplexray.an.topology.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * Detects CDN traffic spikes
 */
class CdnSpikeDetector(
    private val context: Context,
    private val scope: CoroutineScope,
    private val topologyRepo: TopologyRepository,
    private val classifier: DomainClassifier
) {
    private const val TAG = "CdnSpikeDetector"
    private var job: Job? = null
    private val cdnHistory = ArrayDeque<Float>(60) // Last 60 samples
    private var lastSpikeTs = 0L
    private val spikeThreshold = 2.0f // 2x increase triggers alert

    fun start() {
        NotificationEngine.ensureChannel(context)
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            topologyRepo.start()
            topologyRepo.graph.collect { (nodes, edges) ->
                if (nodes.isEmpty()) return@collect
                
                // Calculate current CDN traffic percentage
                val totalWeight = edges.sumOf { it.weight.toDouble() }.toFloat()
                if (totalWeight == 0f) return@collect
                
                // Calculate CDN weight (async classification)
                val cdnWeight = nodes.filter { n ->
                    n.type == Node.Type.Domain
                }.mapNotNull { n ->
                    val isCdn = try {
                        classifier.classify(n.label) == com.simplexray.an.domain.Category.CDN
                    } catch (e: Exception) {
                        false // Default to false if classification fails
                    }
                    if (isCdn) {
                        edges.filter { it.from == n.id || it.to == n.id }
                            .sumOf { it.weight.toDouble() }.toFloat()
                    } else null
                }.sumOf { it.toDouble() }.toFloat()
                
                val cdnPercentage = cdnWeight / totalWeight
                
                // Update history
                cdnHistory.addLast(cdnPercentage)
                if (cdnHistory.size > 60) {
                    cdnHistory.removeFirst()
                }
                
                // Check for spike
                if (cdnHistory.size >= 10) {
                    val recentAvg = cdnHistory.takeLast(10).average().toFloat()
                    val baseline = cdnHistory.take(50).average().toFloat()
                    
                    if (baseline > 0f && recentAvg > baseline * spikeThreshold) {
                        val now = System.currentTimeMillis()
                        val cooldown = com.simplexray.an.config.ApiConfig.getAlertCooldownMs(context)
                        if (now - lastSpikeTs > cooldown) {
                            val increasePercent = ((recentAvg - baseline) / baseline * 100).toInt()
                            NotificationEngine.notifyCdnSpike(
                                context,
                                baseline,
                                recentAvg,
                                increasePercent
                            )
                            EventLogger.log(
                                AlertEvent.EventType.CDN_SPIKE,
                                AlertEvent.Severity.MEDIUM,
                                "CDN traffic spike detected: ${increasePercent}% increase",
                                mapOf(
                                    "baseline" to baseline,
                                    "current" to recentAvg,
                                    "increasePercent" to increasePercent
                                )
                            )
                            lastSpikeTs = now
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}

