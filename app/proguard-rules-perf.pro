# Performance Module ProGuard Rules

# Keep native methods
-keepclassmembers class com.simplexray.an.performance.PerformanceManager {
    private native <methods>;
}

# Keep performance classes
-keep class com.simplexray.an.performance.PerformanceManager { *; }
-keep class com.simplexray.an.performance.PerformanceIntegration { *; }
-keep class com.simplexray.an.performance.MemoryPool { *; }
-keep class com.simplexray.an.performance.ThreadPoolManager { *; }
-keep class com.simplexray.an.performance.BurstTrafficManager { *; }
-keep class com.simplexray.an.performance.PerformanceMonitor { *; }

# Keep enums
-keep enum com.simplexray.an.performance.PerformanceManager$PoolType { *; }
-keep enum com.simplexray.an.performance.PerformanceManager$NetworkType { *; }

# Keep data classes
-keep class com.simplexray.an.performance.** { 
    <fields>;
    <methods>;
}

# Don't obfuscate native method names
-keepnames class com.simplexray.an.performance.PerformanceManager {
    native *;
}



