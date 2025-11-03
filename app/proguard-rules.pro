# Keep existing rules
-keep class com.simplexray.an.service.TProxyService {
    @kotlin.jvm.JvmStatic *;
}

# Performance Module Rules
-keep class com.simplexray.an.performance.PerformanceManager { *; }
-keep class com.simplexray.an.performance.PerformanceIntegration { *; }
-keep class com.simplexray.an.performance.MemoryPool { *; }
-keep class com.simplexray.an.performance.ThreadPoolManager { *; }
-keep class com.simplexray.an.performance.BurstTrafficManager { *; }
-keep class com.simplexray.an.performance.PerformanceMonitor { *; }
-keep enum com.simplexray.an.performance.PerformanceManager$PoolType { *; }
-keep enum com.simplexray.an.performance.PerformanceManager$NetworkType { *; }
-keepclassmembers class com.simplexray.an.performance.PerformanceManager {
    private native <methods>;
}
-keepnames class com.simplexray.an.performance.PerformanceManager {
    native *;
}

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkFieldIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Jetpack Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.animation.** { *; }

# Keep Composable functions
-keep @androidx.compose.runtime.Composable public class * {
    public <init>(...);
}
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Lifecycle
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class androidx.lifecycle.Lifecycle$State { *; }
-keepclassmembers class androidx.lifecycle.Lifecycle$Event { *; }

# Navigation
-keep class androidx.navigation.** { *; }
-keepnames class androidx.navigation.fragment.NavHostFragment
-keepnames class * extends androidx.navigation.Navigator

# gRPC and Protocol Buffers
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class io.grpc.** { *; }
-keepclassmembers class io.grpc.** { *; }
-dontwarn io.grpc.**
-keepclassmembers class * extends io.grpc.stub.AbstractStub {
    <init>(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep data classes
-keep @kotlin.Metadata class com.simplexray.an.** { *; }
-keepclassmembers class com.simplexray.an.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep error handling classes
-keep class com.simplexray.an.common.error.** { *; }
-keepclassmembers class com.simplexray.an.common.error.** { *; }

# Keep config format converters
-keep interface com.simplexray.an.common.configFormat.ConfigFormatConverter { *; }
-keep class * implements com.simplexray.an.common.configFormat.ConfigFormatConverter { *; }

# Preferences and ContentProvider
-keep class com.simplexray.an.prefs.** { *; }
-keepclassmembers class com.simplexray.an.prefs.** { *; }

# Keep service classes
-keep class com.simplexray.an.service.** { *; }
-keepclassmembers class com.simplexray.an.service.** { *; }

# Keep native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelables
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# General Android
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Keep BuildConfig
-keep class com.simplexray.an.BuildConfig { *; }

# R8 optimizations
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-repackageclasses ''
