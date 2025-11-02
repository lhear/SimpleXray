# Traffic Monitor - Quick Integration Guide

## 🚀 Quick Start (5 minutes)

All the code has been implemented. You just need to make a few manual updates to integrate it.

## Step 1: Update AndroidManifest.xml

Add the traffic widget receiver **before** `</application>`:

```xml
<!-- Add this after the VpnWidget receiver -->
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

**Location:** `app/src/main/AndroidManifest.xml` (line ~110, before `</application>`)

## Step 2: Add String Resource

Add to `res/values/strings.xml`:

```xml
<!-- Add in the Widget strings section (~line 131) -->
<string name="widget_traffic_description">Shows real-time network traffic statistics</string>
```

**Location:** `app/src/main/res/values/strings.xml` (in the `<!-- Widget strings -->` section)

## Step 3: Initialize Background Logging

In `MainActivity.kt`, add this in `onCreate()`:

```kotlin
import com.simplexray.an.worker.TrafficWorkScheduler

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize traffic monitoring background worker
    TrafficWorkScheduler.schedule(this)

    // ... rest of your existing code
}
```

**Location:** `app/src/main/kotlin/com/simplexray/an/activity/MainActivity.kt`

## Step 4: Add Navigation Route

### Option A: Add to Existing Dashboard

In `DashboardScreen.kt`, add a new feature row:

```kotlin
FeatureRow(
    title = "Traffic Monitor",
    description = "Real-time speed & bandwidth monitoring",
    onClick = { appNavController.navigate("traffic_monitor") }
)
```

**Location:** `app/src/main/kotlin/com/simplexray/an/ui/screens/DashboardScreen.kt` (~line 185)

### Option B: Add to Navigation Graph

Find where your app defines the NavHost and add:

```kotlin
composable("traffic_monitor") {
    TrafficMonitorScreen()
}
```

Don't forget the import:

```kotlin
import com.simplexray.an.ui.screens.TrafficMonitorScreen
```

## Step 5: Sync and Build

```bash
./gradlew clean build
```

## Step 6: Test

1. Run the app
2. Navigate to "Traffic Monitor" from Dashboard
3. Verify real-time updates appear
4. Long-press home screen → Add "SimpleXray Traffic" widget

## Files Created

All these files have been created and are ready to use:

### Core Components
- ✅ `app/src/main/kotlin/com/simplexray/an/domain/model/TrafficSnapshot.kt`
- ✅ `app/src/main/kotlin/com/simplexray/an/network/TrafficObserver.kt`

### Database
- ✅ `app/src/main/kotlin/com/simplexray/an/data/db/TrafficDatabase.kt`
- ✅ `app/src/main/kotlin/com/simplexray/an/data/db/TrafficEntity.kt`
- ✅ `app/src/main/kotlin/com/simplexray/an/data/db/TrafficDao.kt`
- ✅ `app/src/main/kotlin/com/simplexray/an/data/repository/TrafficRepository.kt`

### UI & ViewModel
- ✅ `app/src/main/kotlin/com/simplexray/an/ui/viewmodel/TrafficViewModel.kt`
- ✅ `app/src/main/kotlin/com/simplexray/an/ui/screens/TrafficMonitorScreen.kt`

### Background Work
- ✅ `app/src/main/kotlin/com/simplexray/an/worker/TrafficWorker.kt`
- ✅ `app/src/main/kotlin/com/simplexray/an/worker/TrafficWorkScheduler.kt`

### Widget
- ✅ `app/src/main/kotlin/com/simplexray/an/widget/TrafficWidget.kt`
- ✅ `app/src/main/res/xml/traffic_widget_info.xml`
- ✅ `app/src/main/res/layout/widget_traffic_layout.xml`

### Dependencies
- ✅ `app/build.gradle` (updated with Room, WorkManager, Vico, Glance)

## Verification Checklist

After integration, verify:

- [ ] App builds successfully
- [ ] Traffic Monitor screen is accessible
- [ ] Real-time speeds update every ~500ms
- [ ] Charts display and animate
- [ ] Latency probe works (shows ms value)
- [ ] Widget can be added to home screen
- [ ] Widget shows current traffic stats
- [ ] Background logging runs (check WorkManager)

## Common Issues

### Build Error: "Unresolved reference: TrafficWorkScheduler"

**Fix:** Sync Gradle files (`File > Sync Project with Gradle Files`)

### KSP Errors with Room

**Fix:** Ensure KSP plugin is added to `app/build.gradle`:

```gradle
plugins {
    id 'com.google.devtools.ksp' version '2.0.21-1.0.27'
}
```

### Widget Not Showing

**Fix:** Check that `traffic_widget_info.xml` exists in `res/xml/` and is referenced in AndroidManifest

### No Traffic Data

**Fix:** Ensure VPN is connected and app has network permissions

## Advanced Configuration

### Change Sample Rate

Edit `TrafficObserver.kt` line ~28:

```kotlin
private const val SAMPLE_INTERVAL_MS = 500L  // Change to 1000L for 1 second
```

### Change Retention Period

Edit `TrafficWorker.kt` line ~52:

```kotlin
val deleted = repository.deleteLogsOlderThanDays(30)  // Change to 7 for 1 week
```

### Change Background Logging Interval

Edit `TrafficWorkScheduler.kt` line ~19:

```kotlin
val workRequest = PeriodicWorkRequestBuilder<TrafficWorker>(
    repeatInterval = 15, // Change to 30 for 30 minutes
    repeatIntervalTimeUnit = TimeUnit.MINUTES
)
```

## Need Help?

See `TRAFFIC_MONITOR_README.md` for comprehensive documentation.

---

**Total Integration Time:** ~5 minutes
**Difficulty:** Easy
**Code Changes Required:** 3 files (Manifest, strings.xml, MainActivity.kt)
