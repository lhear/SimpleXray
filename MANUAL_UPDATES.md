# Manual Updates Required

The traffic monitoring system is 95% complete! You only need to make a few small manual edits to complete the integration.

## ⚙️ Required Manual Updates (3 files)

### 1. Update `AppNavGraph.kt`

**File:** `app/src/main/kotlin/com/simplexray/an/ui/navigation/AppNavGraph.kt`

#### Step 1: Add import at line 26 (after `ROUTE_NETWORK_VISUALIZATION` import):
```kotlin
import com.simplexray.an.common.ROUTE_TRAFFIC_MONITOR
import com.simplexray.an.ui.screens.TrafficMonitorScreen
```

#### Step 2: Add composable route at line 150 (after the NetworkVisualizationScreen composable, before the closing `}`):
```kotlin
        composable(
            route = ROUTE_TRAFFIC_MONITOR,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            TrafficMonitorScreen()
        }
```

### 2. Update `AndroidManifest.xml`

**File:** `app/src/main/AndroidManifest.xml`

Add at line 111 (after the VpnWidget receiver, before `</application>`):

```xml
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

### 3. Update `strings.xml`

**File:** `app/src/main/res/values/strings.xml`

Add at line 131 (in the Widget strings section):

```xml
    <string name="widget_traffic_description">Shows real-time network traffic statistics</string>
```

### 4. Update `DashboardScreen.kt` (Optional - for easy access)

**File:** `app/src/main/kotlin/com/simplexray/an/ui/screens/DashboardScreen.kt`

Add at line 185 (after the NetworkVisualization FeatureRow):

```kotlin
                    Spacer(modifier = Modifier.height(8.dp))

                    FeatureRow(
                        title = "Traffic Monitor",
                        description = "Real-time speed & bandwidth monitoring",
                        onClick = { appNavController.navigate(ROUTE_TRAFFIC_MONITOR) }
                    )
```

Don't forget to add the import at the top:
```kotlin
import com.simplexray.an.common.ROUTE_TRAFFIC_MONITOR
```

### 5. Initialize Background Logging in `MainActivity.kt`

**File:** `app/src/main/kotlin/com/simplexray/an/activity/MainActivity.kt`

Add in `onCreate()` method (after `super.onCreate(savedInstanceState)`):

```kotlin
import com.simplexray.an.worker.TrafficWorkScheduler

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize traffic monitoring background worker
    TrafficWorkScheduler.schedule(this)

    // ... rest of your existing code
}
```

## 🔍 Optional: Update ProGuard Rules

**File:** `app/proguard-rules.pro`

Add these rules to protect traffic monitoring classes from obfuscation:

```proguard
# Traffic Monitoring
-keep class com.simplexray.an.domain.model.** { *; }
-keep class com.simplexray.an.data.db.** { *; }
-keep class com.simplexray.an.network.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Vico Charts
-keep class com.patrykandpatrick.vico.** { *; }

# Glance Widgets
-keep class androidx.glance.** { *; }
-keep class com.simplexray.an.widget.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class com.simplexray.an.worker.** { *; }
```

## ✅ Verification Steps

After making the updates:

1. **Sync Gradle**
   ```bash
   ./gradlew sync
   ```

2. **Clean Build**
   ```bash
   ./gradlew clean build
   ```

3. **Run the app**
   - Verify no compilation errors
   - Navigate to Dashboard → Traffic Monitor
   - Verify real-time updates work

4. **Test Widget**
   - Long-press home screen
   - Add "SimpleXray Traffic" widget
   - Verify it shows current stats

## 📊 Summary of Changes

| File | Lines to Add | Difficulty | Required |
|------|--------------|------------|----------|
| AppNavGraph.kt | ~15 lines | Easy | ✅ Yes |
| AndroidManifest.xml | ~11 lines | Easy | ✅ Yes |
| strings.xml | 1 line | Easy | ✅ Yes |
| DashboardScreen.kt | ~7 lines | Easy | ⚠️ Optional |
| MainActivity.kt | ~3 lines | Easy | ✅ Yes |
| proguard-rules.pro | ~20 lines | Easy | ⚠️ Optional |

**Total:** ~57 lines across 6 files

**Estimated Time:** 5-10 minutes

## 🐛 Troubleshooting

### Build Error: "Unresolved reference: TrafficMonitorScreen"

Make sure you added both imports:
```kotlin
import com.simplexray.an.common.ROUTE_TRAFFIC_MONITOR
import com.simplexray.an.ui.screens.TrafficMonitorScreen
```

### Widget Not Showing in Widget List

Verify that:
1. `traffic_widget_info.xml` exists in `res/xml/`
2. Widget receiver is added to `AndroidManifest.xml`
3. You've rebuilt the app

### KSP/Room Compiler Errors

Ensure `build.gradle` has:
```gradle
plugins {
    id 'com.google.devtools.ksp' version '2.0.21-1.0.27'
}
```

### No Traffic Data Showing

1. Ensure VPN is connected
2. Check that `TrafficWorkScheduler.schedule(this)` is called
3. Verify app has network permissions

## 📝 Next Steps

After completing manual updates:

1. Test all features thoroughly
2. Check battery usage (should be < 1% per hour)
3. Verify widget updates work
4. Test background logging
5. Review memory usage

## 🎉 That's It!

Once you've made these manual updates, the traffic monitoring system will be fully integrated and ready to use!

For detailed documentation, see:
- `TRAFFIC_MONITOR_README.md` - Complete technical documentation
- `INTEGRATION_GUIDE.md` - Quick start guide
