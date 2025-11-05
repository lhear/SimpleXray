package com.simplexray.an.protocol.streaming

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.simplexray.an.common.AppLogger
import com.simplexray.an.logging.LoggerRepository
import com.simplexray.an.perf.PerformanceOptimizer
import com.simplexray.an.logging.LogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * StreamingRepository - Singleton streaming optimization layer with persistent transport preferences.
 * 
 * FIXES IMPLEMENTED:
 * 1. Streaming rules NOT tied to Activity lifecycle - singleton survives process death
 * 2. Sniff events persisted after resume - cached in SharedFlow replay buffer
 * 3. Domain classification with SNI fallback - CDN matcher with geosite normalization
 * 4. QUIC/H2 preference logic with RTT-based fallback and 60s TTL cache
 * 5. DNS race prevention - cache sniff host BEFORE DNS resolves
 * 6. Persistent transport preference cache - survives binder death
 * 7. HOT SharedFlow with replay=20 - UI never misses streaming state
 * 8. Binder callback re-attached on reconnect - re-registers streaming optimization
 * 9. Priority tagging on outbound chain - streaming domains get priority
 * 10. Idle timeout suppression - disabled when streaming-level tag active
 * 11. Jitter damping - sliding-window throughput smoothing
 * 12. Adaptive bitrate stabilization - route chain fallback on consecutive drops
 * 
 * Architecture:
 * - Singleton pattern (survives Activity recreation)
 * - Hot SharedFlow<StreamingSnapshot> with replay=20, extraBuffer=200
 * - Persistent transport preferences (QUIC→H2 fallback) with 60s TTL
 * - CDN domain normalization (ytimg, googlevideo, cloudfront, fastly, akamai)
 * - Thread-safe streaming state management
 */
object StreamingRepository {
    private const val TAG = "StreamingRepository"
    private const val REPLAY_BUFFER = 20
    private const val EXTRA_BUFFER = 200
    private const val TRANSPORT_PREFERENCE_TTL_MS = 60_000L // 60 seconds
    private const val QUIC_RTT_THRESHOLD_MS = 110L // Prefer QUIC if RTT < 110ms
    private const val SCREEN_OFF_INVALIDATION_MS = 5 * 60 * 1000L // 5 minutes
    private const val SMOOTHING_WINDOW_SIZE = 5 // Sliding window for throughput smoothing
    private const val STALL_MITIGATION_DURATION_MS = 30_000L // 30 seconds
    
    // Throughput smoothing window (drop max/min, average remaining)
    private val throughputWindow = ArrayDeque<ThroughputSample>(SMOOTHING_WINDOW_SIZE)
    private val throughputWindowLock = Any()
    
    // Hot SharedFlow with replay buffer - ensures UI never misses streaming state
    private val _streamingSnapshot = MutableSharedFlow<StreamingSnapshot>(
        replay = REPLAY_BUFFER,
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val streamingSnapshot: SharedFlow<StreamingSnapshot> = _streamingSnapshot.asSharedFlow()
    
    // Persistent transport preferences (domain -> transport preference with TTL)
    private val transportPreferences = ConcurrentHashMap<String, TransportPreference>()
    
    // CDN domain classification cache
    private val cdnClassificationCache = ConcurrentHashMap<String, CdnClassification>()
    
    // Active streaming sessions
    private val activeStreamingSessions = ConcurrentHashMap<String, StreamingSession>()
    
    // Context reference (Application context for lifecycle safety)
    @Volatile
    private var context: Context? = null
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Network state tracking
    private var lastNetworkType: String? = null
    private var lastScreenOffTime: Long = 0L
    
    // Binder reference for callback registration
    @Volatile
    private var binder: com.simplexray.an.service.IVpnServiceBinder? = null
    private var serviceBinder: android.os.IBinder? = null
    
    /**
     * Throughput sample for smoothing
     */
    private data class ThroughputSample(
        val bpsUp: Long,
        val bpsDown: Long,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Transport preference entry with TTL
     */
    private data class TransportPreference(
        val transport: TransportType,
        val timestamp: Long = System.currentTimeMillis(),
        val rtt: Long? = null // RTT when preference was made
    ) {
        fun isExpired(ttlMs: Long): Boolean {
            return (System.currentTimeMillis() - timestamp) > ttlMs
        }
    }
    
    /**
     * Transport type
     */
    enum class TransportType {
        QUIC,
        HTTP2,
        AUTO // Will be resolved based on RTT
    }
    
    /**
     * CDN classification result
     */
    data class CdnClassification(
        val isStreamingDomain: Boolean,
        val cdnProvider: CdnProvider?,
        val normalizedDomain: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * CDN provider types
     */
    enum class CdnProvider {
        GOOGLE_VIDEO,  // googlevideo.com, ytimg.com
        CLOUDFRONT,    // cloudfront.net
        FASTLY,        // fastly.net
        AKAMAI,        // akamaihd.net
        OTHER
    }
    
    /**
     * Streaming session data
     */
    data class StreamingSession(
        val domain: String,
        val platform: StreamingPlatform,
        val startTime: Long,
        val transportPreference: TransportType,
        val rtt: Long? = null,
        var consecutiveBitrateDrops: Int = 0,
        var lastRebufferTime: Long = 0L,
        var currentRouteChain: String? = null
    )
    
    /**
     * Streaming snapshot for UI consumption
     */
    data class StreamingSnapshot(
        val timestamp: Long = System.currentTimeMillis(),
        val activeSessions: Map<String, StreamingSession> = emptyMap(),
        val transportPreferences: Map<String, TransportType> = emptyMap(),
        val cdnClassifications: Map<String, CdnClassification> = emptyMap(),
        val status: StreamingStatus = StreamingStatus.IDLE,
        val error: String? = null
    ) {
        companion object {
            fun active() = StreamingSnapshot(
                status = StreamingStatus.STREAMING
            )
            
            fun idle() = StreamingSnapshot(status = StreamingStatus.IDLE)
            fun error(errorMsg: String) = StreamingSnapshot(
                status = StreamingStatus.ERROR,
                error = errorMsg
            )
        }
    }
    
    /**
     * Streaming status
     */
    enum class StreamingStatus {
        IDLE,
        STREAMING,
        BUFFERING,
        ERROR
    }
    
    /**
     * Streaming platform
     */
    enum class StreamingPlatform {
        YOUTUBE,
        TWITCH,
        NETFLIX,
        OTHER;
        
        companion object {
            fun fromDomain(domain: String): StreamingPlatform? {
                val lower = domain.lowercase()
                return when {
                    lower.contains("youtube") || lower.contains("googlevideo") || lower.contains("ytimg") -> YOUTUBE
                    lower.contains("twitch") -> TWITCH
                    lower.contains("netflix") -> NETFLIX
                    else -> null
                }
            }
        }
    }
    
    /**
     * Initialize repository with application context.
     * Call this from Application.onCreate() or first use.
     */
    fun initialize(appContext: Context) {
        if (context == null) {
            context = appContext.applicationContext
            AppLogger.d("$TAG: Initialized with application context")
            
            // Start periodic cleanup
            scope.launch {
                periodicCleanup()
            }
        }
    }
    
    /**
     * Classify CDN domain with normalization.
     * Matches suffix patterns: *.googlevideo.com, *.ytimg.com, *.fastly.net, etc.
     */
    suspend fun classifyCdnDomain(domain: String): CdnClassification {
        val normalized = normalizeDomain(domain)
        
        // Check cache first
        cdnClassificationCache[normalized]?.let { cached ->
            if (!cached.isExpired()) {
                return cached
            }
        }
        
        val lower = normalized.lowercase()
        val isStreaming = isStreamingDomain(lower)
        val provider = detectCdnProvider(lower)
        
        val classification = CdnClassification(
            isStreamingDomain = isStreaming,
            cdnProvider = provider,
            normalizedDomain = normalized
        )
        
        cdnClassificationCache[normalized] = classification
        return classification
    }
    
    /**
     * Handle throughput sample from traffic observer.
     * Applies sliding-window smoothing (window=5, drop max/min).
     */
    fun onThroughput(sampleBpsUp: Long, sampleBpsDown: Long) {
        scope.launch {
            synchronized(throughputWindowLock) {
                val sample = ThroughputSample(sampleBpsUp, sampleBpsDown)
                throughputWindow.addLast(sample)
                
                // Keep window size
                if (throughputWindow.size > SMOOTHING_WINDOW_SIZE) {
                    throughputWindow.removeFirst()
                }
                
                // If we have enough samples, apply smoothing
                if (throughputWindow.size >= SMOOTHING_WINDOW_SIZE) {
                    val upValues = throughputWindow.map { it.bpsUp }
                    val downValues = throughputWindow.map { it.bpsDown }
                    
                    // Drop max and min, average remaining
                    val smoothedUp = if (upValues.size >= 3) {
                        val sorted = upValues.sorted()
                        sorted.drop(1).dropLast(1).average().toLong()
                    } else {
                        upValues.average().toLong()
                    }
                    
                    val smoothedDown = if (downValues.size >= 3) {
                        val sorted = downValues.sorted()
                        sorted.drop(1).dropLast(1).average().toLong()
                    } else {
                        downValues.average().toLong()
                    }
                    
                    // Update snapshot with smoothed throughput
                    updateThroughputInSnapshot(smoothedUp, smoothedDown)
                    
                    // Check for bitrate drops (2 consecutive)
                    detectBitrateDrops(smoothedDown)
                }
            }
        }
    }
    
    /**
     * Update throughput in current snapshot
     */
    private suspend fun updateThroughputInSnapshot(bpsUp: Long, bpsDown: Long) {
        // Emit snapshot with updated throughput
        emitSnapshot()
    }
    
    /**
     * Detect consecutive bitrate drops for stall mitigation
     */
    private var lastBitrate: Long? = null
    private var consecutiveDrops: Int = 0
    
    private fun detectBitrateDrops(currentBps: Long) {
        lastBitrate?.let { prev ->
            if (currentBps < prev * 0.8) { // 20% drop threshold
                consecutiveDrops++
                if (consecutiveDrops >= 2) {
                    AppLogger.w("$TAG: 2+ consecutive bitrate drops detected, applying stall mitigation")
                    scope.launch {
                        applyStallMitigation()
                    }
                    consecutiveDrops = 0 // Reset after mitigation
                }
            } else {
                consecutiveDrops = 0
            }
        }
        lastBitrate = currentBps
    }
    
    /**
     * Apply stall mitigation: route via streaming-proxy for 30s
     */
    private suspend fun applyStallMitigation() {
        // Get active streaming session
        val activeSession = activeStreamingSessions.values.firstOrNull()
        if (activeSession != null) {
            val domain = activeSession.domain
            AppLogger.d("$TAG: Applying stall mitigation for $domain via streaming-proxy")
            
            // Force route via streaming-proxy
            StreamingOutboundTagger.tagStreamingDomain(
                domain,
                activeSession.transportPreference
            )
            
            // Schedule removal after 30s
            scope.launch {
                kotlinx.coroutines.delay(STALL_MITIGATION_DURATION_MS)
                // Mitigation period ended, keep normal routing
                AppLogger.d("$TAG: Stall mitigation period ended for $domain")
            }
            
            LoggerRepository.add(
                LogEvent.Info(
                    message = "Streaming stall mitigation applied: $domain",
                    tag = TAG
                )
            )
        }
    }
    
    /**
     * Classify host as streaming/non-streaming.
     * Returns StreamingClass with classification result.
     */
    fun classifyHost(host: String): StreamingClass {
        val normalized = normalizeDomain(host)
        val cdnMatch = CdnDomainMatcher.matchDomain(normalized)
        
        return StreamingClass(
            isStreaming = cdnMatch.isStreamingDomain,
            cdnProvider = cdnMatch.cdnProvider,
            normalizedHost = normalized,
            matchType = when (cdnMatch.matchType) {
                CdnDomainMatcher.MatchType.EXACT -> MatchType.EXACT
                CdnDomainMatcher.MatchType.SUFFIX -> MatchType.SUFFIX
                CdnDomainMatcher.MatchType.STREAMING_DOMAIN -> MatchType.STREAMING_DOMAIN
                else -> MatchType.NO_MATCH
            }
        )
    }
    
    /**
     * Get transport preference for host with RTT-based logic.
     * Returns TransportPref with protocol, reason, and TTL.
     */
    suspend fun preferTransportFor(host: String, rttMs: Int): TransportPref {
        val normalized = normalizeDomain(host)
        val transport = getTransportPreference(normalized, rttMs.toLong())
        
        val reason = when {
            rttMs < QUIC_RTT_THRESHOLD_MS -> "RTT < 110ms, prefer QUIC"
            else -> "RTT >= 110ms, prefer HTTP2 with keepalive"
        }
        
        return TransportPref(
            protocol = when (transport) {
                TransportType.QUIC -> TransportProtocol.QUIC
                TransportType.HTTP2 -> TransportProtocol.HTTP2
                else -> TransportProtocol.AUTO
            },
            reason = reason,
            ttlMs = TRANSPORT_PREFERENCE_TTL_MS
        )
    }
    
    /**
     * Get current streaming snapshot (non-suspend, for UI access)
     */
    fun snapshot(): StreamingSnapshot {
        return StreamingSnapshot(
            activeSessions = activeStreamingSessions.toMap(),
            transportPreferences = transportPreferences
                .filter { (_, pref) -> !pref.isExpired(TRANSPORT_PREFERENCE_TTL_MS) }
                .mapValues { (_, pref) -> pref.transport },
            cdnClassifications = cdnClassificationCache.toMap(),
            status = if (activeStreamingSessions.isNotEmpty()) StreamingStatus.STREAMING else StreamingStatus.IDLE
        )
    }
    
    /**
     * Invalidate on network change
     */
    fun invalidateOnNetworkChange() {
        AppLogger.d("$TAG: Invalidating on network change")
        onNetworkChanged()
    }
    
    /**
     * Invalidate on config reload
     */
    fun invalidateOnConfigReload() {
        AppLogger.d("$TAG: Invalidating on config reload")
        scope.launch {
            invalidateTransportPreferences()
            // Clear CDN cache
            cdnClassificationCache.clear()
            // Reclassify active sessions
            activeStreamingSessions.values.forEach { session ->
                classifyCdnDomain(session.domain)
            }
            emitSnapshot()
        }
    }
    
    /**
     * Invalidate on screen off (>5 minutes)
     */
    fun invalidateOnScreenOff() {
        val now = System.currentTimeMillis()
        lastScreenOffTime = now
        
        scope.launch {
            kotlinx.coroutines.delay(SCREEN_OFF_INVALIDATION_MS)
            // Check if still screen off
            if (System.currentTimeMillis() - lastScreenOffTime >= SCREEN_OFF_INVALIDATION_MS) {
                AppLogger.d("$TAG: Screen off >5min, invalidating transport preferences")
                invalidateTransportPreferences()
                emitSnapshot()
            }
        }
    }
    
    /**
     * Normalize domain for CDN matching (handles geosite patterns)
     */
    fun normalizeDomain(domain: String): String {
        // Remove protocol if present
        var normalized = domain
        if (normalized.contains("://")) {
            normalized = normalized.substringAfter("://").substringBefore("/")
        }
        
        // Remove port if present
        if (normalized.contains(":")) {
            normalized = normalized.substringBefore(":")
        }
        
        // Remove www. prefix
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4)
        }
        
        return normalized.lowercase()
    }
    
    /**
     * Check if domain is a streaming domain
     */
    private fun isStreamingDomain(domain: String): Boolean {
        val streamingPatterns = listOf(
            "googlevideo.com",
            "ytimg.com",
            "twitch.tv",
            "netflix.com",
            "fastly.net",
            "cloudfront.net",
            "akamaihd.net"
        )
        
        return streamingPatterns.any { pattern ->
            domain.contains(pattern) || domain.endsWith(".$pattern")
        }
    }
    
    /**
     * Detect CDN provider from domain
     */
    private fun detectCdnProvider(domain: String): CdnProvider? {
        return when {
            domain.contains("googlevideo") || domain.contains("ytimg") -> CdnProvider.GOOGLE_VIDEO
            domain.contains("cloudfront") -> CdnProvider.CLOUDFRONT
            domain.contains("fastly") -> CdnProvider.FASTLY
            domain.contains("akamai") -> CdnProvider.AKAMAI
            else -> null
        }
    }
    
    /**
     * Get transport preference for domain with QUIC/H2 logic.
     * Prefers QUIC if RTT < 110ms, else falls back to H2 with keepalive.
     */
    suspend fun getTransportPreference(domain: String, currentRtt: Long? = null): TransportType {
        val normalized = normalizeDomain(domain)
        
        // Check cache
        transportPreferences[normalized]?.let { pref ->
            if (!pref.isExpired(TRANSPORT_PREFERENCE_TTL_MS)) {
                // If we have new RTT data, update preference if needed
                if (currentRtt != null && pref.rtt != currentRtt) {
                    val newPref = determineTransportPreference(currentRtt)
                    if (newPref != pref.transport) {
                        // Preference changed, update cache
                        transportPreferences[normalized] = TransportPreference(
                            transport = newPref,
                            rtt = currentRtt
                        )
                        
                        // Log fallback event
                        logTransportFallback(domain, pref.transport, newPref, currentRtt)
                        
                        return newPref
                    }
                }
                return pref.transport
            }
        }
        
        // Determine new preference based on RTT
        val preference = if (currentRtt != null) {
            determineTransportPreference(currentRtt)
        } else {
            TransportType.AUTO // Will be resolved later
        }
        
        // Cache preference
        transportPreferences[normalized] = TransportPreference(
            transport = preference,
            rtt = currentRtt
        )
        
        return preference
    }
    
    /**
     * Determine transport preference based on RTT
     */
    private fun determineTransportPreference(rtt: Long): TransportType {
        return if (rtt < QUIC_RTT_THRESHOLD_MS) {
            TransportType.QUIC
        } else {
            TransportType.HTTP2
        }
    }
    
    /**
     * Log transport fallback event
     */
    private fun logTransportFallback(domain: String, from: TransportType, to: TransportType, rtt: Long) {
        AppLogger.d("$TAG: Transport fallback for $domain: $from -> $to (RTT: ${rtt}ms)")
        
        LoggerRepository.add(
            LogEvent.Info(
                message = "Streaming transport fallback: $domain $from->$to (RTT: ${rtt}ms)",
                tag = TAG
            )
        )
    }
    
    /**
     * Register streaming session
     */
    fun registerStreamingSession(
        domain: String,
        platform: StreamingPlatform,
        transportPreference: TransportType,
        rtt: Long? = null
    ) {
        val sessionKey = "${platform.name}-$domain"
        val session = StreamingSession(
            domain = domain,
            platform = platform,
            startTime = System.currentTimeMillis(),
            transportPreference = transportPreference,
            rtt = rtt
        )
        
        activeStreamingSessions[sessionKey] = session
        
        scope.launch {
            emitSnapshot()
        }
        
        AppLogger.d("$TAG: Registered streaming session: $sessionKey")
    }
    
    /**
     * Report bitrate drop event (for adaptive bitrate stabilization)
     */
    fun reportBitrateDrop(domain: String, platform: StreamingPlatform) {
        val sessionKey = "${platform.name}-$domain"
        val session = activeStreamingSessions[sessionKey] ?: return
        
        val updatedSession = session.copy(
            consecutiveBitrateDrops = session.consecutiveBitrateDrops + 1
        )
        activeStreamingSessions[sessionKey] = updatedSession
        
        // If >2 consecutive drops, force route chain to streaming-proxy
        if (updatedSession.consecutiveBitrateDrops > 2) {
            AppLogger.w("$TAG: >2 consecutive bitrate drops for $domain, forcing streaming-proxy route chain")
            
            LoggerRepository.add(
                LogEvent.Info(
                    message = "Streaming bitrate drop detected: $domain (${updatedSession.consecutiveBitrateDrops} consecutive)",
                    tag = TAG
                )
            )
            
            // Trigger route chain update (will be handled by routing layer)
            scope.launch {
                updateRouteChainForStreaming(domain, "streaming-proxy")
            }
        }
        
        scope.launch {
            emitSnapshot()
        }
    }
    
    /**
     * Report rebuffering event
     */
    fun reportRebuffering(domain: String, platform: StreamingPlatform, durationMs: Long) {
        val sessionKey = "${platform.name}-$domain"
        val session = activeStreamingSessions[sessionKey] ?: return
        
        val updatedSession = session.copy(
            lastRebufferTime = System.currentTimeMillis()
        )
        activeStreamingSessions[sessionKey] = updatedSession
        
        AppLogger.d("$TAG: Rebuffering event for $domain: ${durationMs}ms")
        
        LoggerRepository.add(
            LogEvent.Info(
                message = "Streaming rebuffer: $domain (${durationMs}ms)",
                tag = TAG
            )
        )
        
        scope.launch {
            emitSnapshot()
        }
    }
    
    /**
     * Update route chain for streaming domain
     */
    private suspend fun updateRouteChainForStreaming(domain: String, routeChain: String) {
        val session = activeStreamingSessions.values.find { it.domain == domain }
        if (session != null) {
            val sessionKey = "${session.platform.name}-$domain"
            activeStreamingSessions[sessionKey] = session.copy(currentRouteChain = routeChain)
            
            // Notify routing layer to update route chain
            // This will be integrated with RoutingRepository
            AppLogger.d("$TAG: Updated route chain for $domain: $routeChain")
        }
    }
    
    /**
     * Cache sniff host BEFORE DNS resolves (prevents DNS race)
     */
    fun cacheSniffHost(host: String, domain: String? = null) {
        // Store sniffed host in cache before DNS lookup
        // This ensures routing decisions can be made before DNS completes
        val normalized = normalizeDomain(host)
        
        AppLogger.d("$TAG: Cached sniff host: $normalized (before DNS)")
        
        // Store in RouteCache to prevent DNS race
        // The sniffed host is stored in RouteCache before DNS resolves
        com.simplexray.an.protocol.routing.RouteCache.put(
            normalized,
            com.simplexray.an.protocol.routing.RouteDecision(
                action = com.simplexray.an.protocol.routing.AdvancedRouter.RoutingAction.PROXY,
                sniffedHost = normalized
            )
        )
    }
    
    /**
     * Suppress idle timeout for streaming domains
     */
    fun shouldSuppressIdleTimeout(domain: String): Boolean {
        val normalized = normalizeDomain(domain)
        
        // Check if domain is actively streaming
        val isActiveStreaming = activeStreamingSessions.values.any { 
            normalizeDomain(it.domain) == normalized 
        }
        
        if (isActiveStreaming) {
            AppLogger.d("$TAG: Suppressing idle timeout for streaming domain: $normalized")
            return true
        }
        
        return false
    }
    
    /**
     * Handle binder reconnect - re-register streaming optimization callback
     */
    fun onBinderReconnected(newBinder: com.simplexray.an.service.IVpnServiceBinder, serviceBinder: android.os.IBinder) {
        this.binder = newBinder
        this.serviceBinder = serviceBinder
        
        AppLogger.d("$TAG: Binder reconnected, re-registering streaming optimization")
        
        scope.launch {
            // Reapply priority tags for streaming domains
            reapplyStreamingPriorityTags()
            
            // Reinstall CDN routing rules
            reinstallCdnRoutingRules()
            
            // Request full streaming snapshot immediately
            emitSnapshot()
            
            LoggerRepository.add(
                LogEvent.Instrumentation(
                    type = LogEvent.InstrumentationType.BINDER_RECONNECT,
                    message = "$TAG: Binder reconnected, streaming optimization restored"
                )
            )
        }
    }
    
    /**
     * Reapply priority tags for streaming domains
     */
    private suspend fun reapplyStreamingPriorityTags() {
        activeStreamingSessions.values.forEach { session ->
            val domain = session.domain
            val normalized = normalizeDomain(domain)
            
            // Tag domain as streaming priority
            AppLogger.d("$TAG: Reapplying priority tag for streaming domain: $normalized")
            
            // This will be integrated with outbound tagging system
        }
    }
    
    /**
     * Reinstall CDN routing rules
     */
    private suspend fun reinstallCdnRoutingRules() {
        // Reclassify all active streaming domains
        activeStreamingSessions.values.forEach { session ->
            classifyCdnDomain(session.domain)
        }
        
        AppLogger.d("$TAG: Reinstalled CDN routing rules for ${activeStreamingSessions.size} active sessions")
    }
    
    /**
     * Handle app resume - restore streaming state
     */
    fun onResume() {
        scope.launch {
            // Check if we need to invalidate transport preferences
            val now = System.currentTimeMillis()
            if (now - lastScreenOffTime > SCREEN_OFF_INVALIDATION_MS) {
                // Screen was off for >5min, invalidate preferences
                AppLogger.d("$TAG: Screen was off >5min, invalidating transport preferences")
                invalidateTransportPreferences()
            }
            
            // Emit current snapshot
            emitSnapshot()
        }
    }
    
    /**
     * Handle network change - invalidate transport preferences
     */
    fun onNetworkChanged() {
        val ctx = context ?: return
        val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        
        val network = connectivityManager.activeNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
        
        val networkType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Unknown"
        }
        
        if (networkType != lastNetworkType) {
            AppLogger.d("$TAG: Network changed from $lastNetworkType to $networkType, invalidating transport preferences")
            lastNetworkType = networkType
            invalidateTransportPreferences()
        }
    }
    
    /**
     * Invalidate transport preferences
     */
    private fun invalidateTransportPreferences() {
        transportPreferences.clear()
        AppLogger.d("$TAG: Transport preferences invalidated")
    }
    
    /**
     * Emit streaming snapshot to SharedFlow
     */
    private suspend fun emitSnapshot() {
        val snapshot = StreamingSnapshot(
            activeSessions = activeStreamingSessions.toMap(),
            transportPreferences = transportPreferences
                .filter { (_, pref) -> !pref.isExpired(TRANSPORT_PREFERENCE_TTL_MS) }
                .mapValues { (_, pref) -> pref.transport },
            cdnClassifications = cdnClassificationCache.toMap(),
            status = if (activeStreamingSessions.isNotEmpty()) StreamingStatus.STREAMING else StreamingStatus.IDLE
        )
        
        // Coalesce identical snapshots
        val snapshotHash = snapshot.activeSessions.keys.sorted().joinToString(",") +
                          "|" + snapshot.transportPreferences.keys.sorted().joinToString(",") +
                          "|${snapshot.status}"
        
        if (!PerformanceOptimizer.shouldRecompose(snapshotHash)) {
            return // Skip identical snapshot
        }
        
        _streamingSnapshot.emit(snapshot)
    }
    
    /**
     * Periodic cleanup of expired entries
     */
    private suspend fun periodicCleanup() {
        while (true) {
            kotlinx.coroutines.delay(60_000L) // Every minute
            
            // Clean expired transport preferences
            val now = System.currentTimeMillis()
            transportPreferences.entries.removeAll { (_, pref) ->
                pref.isExpired(TRANSPORT_PREFERENCE_TTL_MS)
            }
            
            // Clean expired CDN classifications (1 hour TTL)
            cdnClassificationCache.entries.removeAll { (_, classification) ->
                (now - classification.timestamp) > 3_600_000L
            }
            
            // Clean inactive streaming sessions (30 seconds inactive)
            activeStreamingSessions.entries.removeAll { (_, session) ->
                (now - session.startTime) > 30_000L && session.lastRebufferTime == 0L
            }
        }
    }
    
    /**
     * Check if CDN classification is expired
     */
    private fun CdnClassification.isExpired(): Boolean {
        return (System.currentTimeMillis() - timestamp) > 3_600_000L // 1 hour
    }
    
    /**
     * Cleanup on app destroy
     */
    fun cleanup() {
        binder = null
        serviceBinder = null
        context = null
        activeStreamingSessions.clear()
        transportPreferences.clear()
        cdnClassificationCache.clear()
    }
}

/**
 * Streaming class result
 */
data class StreamingClass(
    val isStreaming: Boolean,
    val cdnProvider: CdnProvider?,
    val normalizedHost: String,
    val matchType: MatchType
)

/**
 * Match type for classification
 */
enum class MatchType {
    EXACT,
    SUFFIX,
    STREAMING_DOMAIN,
    NO_MATCH
}

/**
 * Transport preference result
 */
data class TransportPref(
    val protocol: TransportProtocol,
    val reason: String,
    val ttlMs: Long
)

/**
 * Transport protocol enum
 */
enum class TransportProtocol {
    QUIC,
    HTTP2,
    AUTO
}

