# ProGuard Rules for Traffic Monitoring System
# Add these rules to your main proguard-rules.pro file

# Traffic Monitoring Domain Models
-keep class com.simplexray.an.domain.model.** { *; }
-keepclassmembers class com.simplexray.an.domain.model.** { *; }

# Traffic Database (Room)
-keep class com.simplexray.an.data.db.** { *; }
-keepclassmembers class com.simplexray.an.data.db.** { *; }

# Traffic Repository
-keep class com.simplexray.an.data.repository.TrafficRepository { *; }
-keepclassmembers class com.simplexray.an.data.repository.TrafficRepository { *; }
-keep class com.simplexray.an.data.repository.TrafficRepositoryFactory { *; }

# Traffic Observer
-keep class com.simplexray.an.network.TrafficObserver { *; }
-keepclassmembers class com.simplexray.an.network.TrafficObserver { *; }

# Traffic ViewModel
-keep class com.simplexray.an.ui.viewmodel.TrafficViewModel { *; }
-keepclassmembers class com.simplexray.an.ui.viewmodel.TrafficViewModel { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-dontwarn androidx.room.paging.**
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** getInstance(***);
}

# Vico Charts Library
-keep class com.patrykandpatrick.vico.** { *; }
-keepclassmembers class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**

# Glance Widgets
-keep class androidx.glance.** { *; }
-keepclassmembers class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# Traffic Widget
-keep class com.simplexray.an.widget.TrafficWidget { *; }
-keep class com.simplexray.an.widget.TrafficWidgetReceiver { *; }
-keep class com.simplexray.an.widget.TrafficData { *; }
-keepclassmembers class com.simplexray.an.widget.** { *; }

# WorkManager Background Tasks
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class androidx.work.** { *; }
-keepclassmembers class androidx.work.** { *; }

# Traffic Worker
-keep class com.simplexray.an.worker.TrafficWorker { *; }
-keep class com.simplexray.an.worker.TrafficWorkScheduler { *; }
-keepclassmembers class com.simplexray.an.worker.** { *; }

# Keep Composable functions for Traffic UI
-keep @androidx.compose.runtime.Composable class com.simplexray.an.ui.screens.TrafficMonitorScreen { *; }
-keepclassmembers class com.simplexray.an.ui.screens.TrafficMonitorScreen {
    @androidx.compose.runtime.Composable <methods>;
}

# Prevent obfuscation of data classes used in Room queries
-keepclassmembers class com.simplexray.an.data.db.TotalBytes { *; }
-keepclassmembers class com.simplexray.an.data.db.SpeedStats { *; }
