# SimpleXray Traffic Monitor - Implementation Guide

## Overview

This document describes the comprehensive real-time traffic speed monitoring system implemented for SimpleXray Android client. The system provides real-time upload/download throughput measurement, visualization, historical data storage, and anomaly detection.

## Features

### Core Features
- ✅ **Real-time Speed Measurement** - Measures upload/download speeds every 500ms
- ✅ **Live Charts** - Real-time line charts using Vico library
- ✅ **Traffic History** - Room database persistence with 24-hour history
- ✅ **Background Logging** - WorkManager periodic logging (every 15 minutes)
- ✅ **Latency Probing** - Automatic health checks via HTTP 204 probes
- ✅ **Burst Detection** - Anomaly detection for traffic spikes
- ✅ **Home Screen Widget** - Glance-based widget showing live stats
- ✅ **Battery Optimized** - Reduced sampling when screen is off

### UI Components
- **Current Speed Gauges** - Download/Upload Mbps with color-coded latency
- **Real-time Charts** - Streaming line graphs (1-minute window)
- **Today's Statistics** - Total usage, peak speeds, average latency
- **Session Statistics** - Current session data transferred
- **Burst Warnings** - Visual alerts for traffic anomalies
- **Connection Status** - Real-time connection indicator

## Architecture

```
app/
├── data/
│   ├── db/
│   │   ├── TrafficDatabase.kt      # Room database
│   │   ├── TrafficEntity.kt        # Entity & converters
│   │   └── TrafficDao.kt           # Data access object
│   └── repository/
│       └── TrafficRepository.kt    # Repository pattern
├── domain/
│   └── model/
│       └── TrafficSnapshot.kt      # Domain models
├── network/
│   └── TrafficObserver.kt          # Core measurement logic
├── ui/
│   ├── screens/
│   │   └── TrafficMonitorScreen.kt # Main UI screen
│   └── viewmodel/
│       └── TrafficViewModel.kt     # State management
├── widget/
│   └── TrafficWidget.kt            # Glance home screen widget
└── worker/
    ├── TrafficWorker.kt            # Background logger
    └── TrafficWorkScheduler.kt     # Work scheduling
```

## Implementation Details

### 1. Traffic Measurement

**TrafficObserver** uses Android's `TrafficStats` API to measure network throughput:

```kotlin
// Priority order:
// 1. TrafficStats.getUidRxBytes(myUid) - UID-specific stats
// 2. Fallback to NetworkStatsManager (API 23+) if needed
// 3. Optional Xray stats API integration (GET http://127.0.0.1:PORT/debug/vars)

// Mbps calculation:
// (deltaBytes / deltaTimeSec) * 8 / 1_000_000
```

**Key Features:**
- Sample interval: 500ms
- Memory-efficient: Keeps last 120 samples (60 seconds)
- Leak-free: Proper coroutine lifecycle management
- Burst detection: 3x moving average threshold

### 2. Database Schema

**TrafficEntity** stores periodic snapshots:

```kotlin
@Entity(tableName = "traffic_logs")
data class TrafficEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val timestamp: Long,
    val rxBytes: Long,          // Cumulative bytes received
    val txBytes: Long,          // Cumulative bytes transmitted
    val rxRateMbps: Float,      // Download rate (Mbps)
    val txRateMbps: Float,      // Upload rate (Mbps)
    val latencyMs: Long,        // Latency (-1 if not measured)
    val isConnected: Boolean    // Connection status
)
```

**Data Retention:**
- Automatic cleanup: Logs older than 30 days are deleted
- Storage-efficient: ~240 bytes per log entry
- Indexes: Timestamp index for fast queries

### 3. Background Logging

**TrafficWorker** runs periodically via WorkManager:

```kotlin
// Schedule:
PeriodicWorkRequest every 15 minutes

// Constraints:
- NetworkType.CONNECTED (only when connected)
- Battery-friendly scheduling

// Tasks:
1. Collect current traffic snapshot
2. Persist to Room database
3. Clean up old logs (>30 days)
```

**To Start Background Logging:**

```kotlin
// In your Application class or MainActivity onCreate:
TrafficWorkScheduler.schedule(context)
```

### 4. Compose UI

**TrafficMonitorScreen** displays:
- Connection status banner (connected/disconnected)
- Burst warning banner (when anomaly detected)
- Speed gauges (download/upload/latency)
- Real-time chart (Vico LineChart)
- Today's statistics card
- Session statistics card

**Vico Chart Integration:**

```kotlin
val chartModelProducer = remember { CartesianChartModelProducer() }

LaunchedEffect(history) {
    chartModelProducer.runTransaction {
        lineSeries {
            series(rxValues)  // Download (blue)
            series(txValues)  // Upload (green)
        }
    }
}
```

### 5. Home Screen Widget

**Glance Widget** shows:
- Connection status indicator
- Download speed (↓ with blue color)
- Upload speed (↑ with green color)
- Latency (⏱)
- Today's total usage

**Widget Update Strategy:**
- Updates every 30 minutes (configurable)
- Tap to open main app
- Battery-efficient (uses cached data)

## Setup Instructions

### Step 1: Dependencies

The following dependencies are already added to `app/build.gradle`:

```gradle
// Room Database
def room_version = "2.6.1"
implementation "androidx.room:room-runtime:$room_version"
implementation "androidx.room:room-ktx:$room_version"
ksp "androidx.room:room-compiler:$room_version"

// WorkManager
implementation "androidx.work:work-runtime-ktx:2.9.0"

// Vico Charts
implementation "com.patrykandpatrick.vico:compose:2.0.0-alpha.28"
implementation "com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.28"
implementation "com.patrykandpatrick.vico:core:2.0.0-alpha.28"

// Glance for Widgets
implementation "androidx.glance:glance:1.1.0"
implementation "androidx.glance:glance-appwidget:1.1.0"
implementation "androidx.glance:glance-material3:1.1.0"

// ViewModel
implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7"
implementation "androidx.lifecycle:lifecycle-runtime-compose:2.8.7"
```

### Step 2: AndroidManifest Updates

Add the following to `AndroidManifest.xml`:

```xml
<!-- Add this before </application> -->
<receiver
    android:name="com.simplexray.an.widget.TrafficWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/traffic_widget_info" />
</receiver>
```

### Step 3: Add String Resources

Add to `res/values/strings.xml`:

```xml
<string name="widget_traffic_description">Shows real-time network traffic statistics</string>
```

### Step 4: Navigation Integration

Add the TrafficMonitorScreen to your navigation graph:

```kotlin
// In your NavHost composition:
composable("traffic_monitor") {
    TrafficMonitorScreen()
}

// Navigate to it:
navController.navigate("traffic_monitor")
```

### Step 5: Initialize Background Logging

In your `Application` class or `MainActivity.onCreate()`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Schedule traffic logging
        TrafficWorkScheduler.schedule(this)
    }
}
```

Or in `MainActivity`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize traffic monitoring
    TrafficWorkScheduler.schedule(this)

    // ... rest of your code
}
```

## Usage Guide

### Viewing Traffic Monitor

1. Navigate to the Traffic Monitor screen in your app
2. The screen will automatically start showing real-time traffic
3. View current speeds, latency, and traffic history

### Adding Home Screen Widget

1. Long-press on home screen
2. Tap "Widgets"
3. Find "SimpleXray Traffic"
4. Drag to home screen
5. Widget will update every 30 minutes

### Understanding Metrics

**Download Speed (↓)**
- Measured in Mbps (Megabits per second)
- Blue color coding
- Automatically formatted (Kbps → Mbps → Gbps)

**Upload Speed (↑)**
- Measured in Mbps
- Green color coding
- Automatically formatted

**Latency (⏱)**
- Measured in milliseconds (ms)
- Color-coded:
  - Green: < 50ms (Excellent)
  - Yellow: 50-100ms (Good)
  - Red: > 100ms (Poor)

**Burst Detection**
- Triggers when current rate > 3× moving average
- Shows yellow warning banner
- Helps identify traffic anomalies

## API Reference

### TrafficObserver

```kotlin
val observer = TrafficObserver(context, coroutineScope)

// Start monitoring
observer.start()

// Stop monitoring
observer.stop()

// Collect current snapshot
val snapshot = observer.collectNow()

// Access current data
val downloadSpeed = observer.getCurrentDownloadSpeed() // Mbps
val uploadSpeed = observer.getCurrentUploadSpeed()     // Mbps
val latency = observer.getCurrentLatency()             // ms
val isConnected = observer.isConnected()               // Boolean

// Observe real-time updates
observer.currentSnapshot.collect { snapshot ->
    // Handle snapshot
}

observer.history.collect { snapshots ->
    // Handle history for charting
}

// Reset stats
observer.reset()
```

### TrafficRepository

```kotlin
val repository = TrafficRepository(trafficDao)

// Insert snapshot
repository.insert(snapshot)

// Query data
val logsToday = repository.getLogsForToday() // Flow<List<TrafficSnapshot>>
val totalBytes = repository.getTotalBytesToday() // TotalBytes
val speedStats = repository.getSpeedStatsToday() // SpeedStats
val avgLatency = repository.getAverageLatencyToday() // Long

// Cleanup
repository.deleteLogsOlderThanDays(30)
repository.deleteAll()
```

### TrafficViewModel

```kotlin
val viewModel: TrafficViewModel = viewModel()

// Observe UI state
val uiState by viewModel.uiState.collectAsState()

// Actions
viewModel.refresh()            // Reload statistics
viewModel.resetSession()       // Reset current session
viewModel.clearHistory()       // Delete all logs
viewModel.deleteOldLogs(days = 7)  // Delete logs older than 7 days
```

## Performance Considerations

### Battery Optimization

- **Screen On:** 500ms sampling interval
- **Screen Off:** WorkManager handles periodic logging (15min)
- **Idle Detection:** Skips logging when no traffic detected

### Memory Usage

- Maximum history buffer: 120 samples (~5KB in memory)
- Database cleanup: Automatic deletion of logs >30 days
- Leak prevention: Proper coroutine scope management

### Network Impact

- Latency probes: Once every 5 seconds
- Probe size: ~200 bytes (HTTP HEAD request)
- Minimal overhead: Uses Android's native TrafficStats API

## Troubleshooting

### Issue: No traffic data showing

**Solution:**
- Ensure VPN is connected
- Check that app has network access
- Verify TrafficStats permissions

### Issue: Widget not updating

**Solution:**
- Check battery optimization settings
- Ensure widget update interval is configured
- Verify background restrictions are disabled

### Issue: Database growing too large

**Solution:**
- Reduce retention period in `TrafficWorker`
- Manually call `deleteLogsOlderThanDays()`
- Clear history via UI

### Issue: High battery drain

**Solution:**
- Increase sample interval in `TrafficObserver`
- Disable latency probing
- Use WorkManager only (disable real-time monitoring)

## Testing

### Unit Tests

```bash
./gradlew test
```

Tests include:
- TrafficSnapshot calculations
- Repository queries
- ViewModel state management

### UI Tests

```bash
./gradlew connectedAndroidTest
```

Tests include:
- TrafficMonitorScreen rendering
- Chart updates
- Widget display

### Manual Testing Checklist

- [ ] Real-time speed updates (500ms interval)
- [ ] Chart animates smoothly
- [ ] Burst detection triggers correctly
- [ ] Background logging persists data
- [ ] Widget shows current stats
- [ ] Latency probe works
- [ ] Database cleanup runs
- [ ] Screen off reduces CPU usage

## Future Enhancements

- [ ] Export traffic history to CSV
- [ ] Configurable sampling intervals
- [ ] Network type detection (WiFi/4G/5G)
- [ ] Per-app traffic tracking
- [ ] Custom alert thresholds
- [ ] Dark mode chart themes
- [ ] Weekly/monthly statistics
- [ ] Notification for high usage

## License

This implementation is part of SimpleXray and follows the same license.

## Support

For issues or questions:
- Open an issue on GitHub
- Check existing documentation
- Review code comments

---

**Implementation Status:** ✅ Complete

All core components are implemented and ready to use. Follow the setup instructions above to integrate into your app.
