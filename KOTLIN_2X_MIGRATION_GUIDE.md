# SimpleXray Kotlin 2.x Migration Guide

## [ISSUE ROOT CAUSE]

### Technical Analysis

The build failure:
```
':app:kspReleaseKotlin'
'org.gradle.api.provider.Property org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions.getJvmDefault()'
```

occurs due to a **version matrix conflict** between Gradle plugin resolution strategies.

### Conflict Source

1. **settings.gradle:9-44** implements auto-detection logic that fetches the latest KSP release (2.1.20-1.0.28), deriving Kotlin version 2.1.20
2. **build.gradle:11** explicitly declares `kotlin_version = '2.1.21'`
3. **app/build.gradle:6** uses KSP without version (delegated to settings.gradle)
4. Gradle's plugin resolution strategy applies Kotlin 2.1.20 globally (from settings.gradle auto-detection), but buildscript classpath loads Kotlin 2.1.21

### API Incompatibility

- In Kotlin 2.0+, the Gradle Kotlin DSL underwent breaking changes: `KotlinJvmCompilerOptions.getJvmDefault()` was **removed**
- The old API used `kotlinOptions.jvmDefault = "enable"` (deprecated in 1.6, removed in 2.0)
- The KSP compiler plugin or **Room KSP processor** (`androidx.room:room-compiler:2.6.1`) attempts reflective access to this property
- When KSP 2.1.x runs against a project expecting mixed Kotlin versions, the method lookup fails

### Reflection Caller

Most likely **Room KSP processor** accessing deprecated compiler options through the Gradle API, as Room generates code that previously required `jvmDefault` configuration for interface default methods.

---

## [VERSION MATRIX RESOLUTION]

### Target Plugin Versions

| Component | Current | Target | Status |
|-----------|---------|--------|--------|
| **Kotlin** | 2.1.21 (conflicting with auto-detect) | **2.1.10** | Fixed by script |
| **KSP** | 2.1.20-1.0.28 (auto-detected) | **2.1.10-1.0.29** | Fixed by script |
| **AGP** | 8.6.1 | **8.6.1** | ✓ Already compatible |
| **Gradle** | 8.8 | **8.8** | ✓ Already compatible |
| **Compose Compiler** | (auto) | **2.1.10** | Fixed by script |

### Compatibility Matrix Verification

```
Kotlin 2.1.10 ✓
├── KSP 2.1.10-1.0.29 ✓
├── AGP 8.6.1 (requires Kotlin 1.9.0+) ✓
├── Gradle 8.8 (requires 8.5+) ✓
├── Compose 1.7+ (plugin automatically matched) ✓
└── Room 2.6.1 (supports KSP 2.x) ✓
```

---

## [GRADLE FILE PATCHES]

The migration scripts apply the following changes:

### Patch 1: `build.gradle`

```diff
- kotlin_version = '2.1.21'
+ kotlin_version = '2.1.10'
```

### Patch 2: `app/build.gradle`

```diff
- kotlinOptions {
-     jvmTarget = '11'
- }

+ kotlin {
+     jvmToolchain(11)
+ }
```

**Location:** After `sourceSets` block, before closing brace

### Patch 3: `settings.gradle`

Removes 40 lines of auto-detection logic and replaces with explicit versioning:

```groovy
resolutionStrategy {
    eachPlugin {
        if (requested.id.id == 'com.google.devtools.ksp') {
            useVersion('2.1.10-1.0.29')
        }
        if (requested.id.id.startsWith('org.jetbrains.kotlin')) {
            useVersion('2.1.10')
        }
        if (requested.id.id == 'org.jetbrains.kotlin.plugin.compose') {
            useVersion('2.1.10')
        }
    }
}
```

### Patch 4: `gradle.properties`

```diff
- org.gradle.jvmargs=-Xmx4g
+ org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
+
+ # Kotlin 2.x optimizations
+ kotlin.daemon.jvmargs=-Xmx2g
+ kotlin.incremental.useClasspathSnapshot=true
```

---

## [APPLICATION INSTRUCTIONS]

### Option 1: Bash Script (Recommended for Git Bash/WSL)

```bash
cd c:/Users/halil/claude-code/SimpleXray
./apply-fix.sh
```

### Option 2: PowerShell Script (Recommended for Windows PowerShell)

```powershell
cd c:\Users\halil\claude-code\SimpleXray
.\apply-fix.ps1
```

### Option 3: Git Patch

```bash
cd c:/Users/halil/claude-code/SimpleXray
# Note: The patch file has placeholders (xxx) in line numbers
# Manual editing recommended instead
```

### Option 4: Manual Editing

Open each file and apply the changes shown in the [GRADLE FILE PATCHES] section above.

---

## [BUILD VERIFICATION]

### Step 1: Clean Build

```bash
cd c:/Users/halil/claude-code/SimpleXray
./gradlew clean
```

### Step 2: Build Release APK

```bash
./gradlew :app:assembleRelease --stacktrace
```

### Expected Success Output

```
> Task :app:kspReleaseKotlin
Processing with KSP 2.1.10-1.0.29
Generating Room database implementation...
BUILD SUCCESSFUL in 2m 15s
```

### Step 3: Verify APK Outputs

```bash
ls -lh app/build/outputs/apk/release/
```

Expected files:
- `simplexray-arm64-v8a.apk`
- `simplexray-x86_64.apk`
- `simplexray-universal.apk`

---

## [CRITICAL SUCCESS CRITERIA]

✅ **`:app:kspReleaseKotlin` must complete without reflection errors**  
✅ **Room KSP processor generates DAO implementations**  
✅ **ProGuard/R8 shrinking succeeds with Kotlin 2.x metadata**  
✅ **Native libraries (arm64-v8a, x86_64) built successfully**  
✅ **Compose compiler plugin uses Kotlin 2.1.10 (auto-matched)**

---

## [TROUBLESHOOTING]

### Issue: "kotlinOptions is deprecated"

**Solution:** Already fixed by migration script. Verify `app/build.gradle` uses:
```groovy
kotlin {
    jvmToolchain(11)
}
```

### Issue: KSP version mismatch

**Solution:** Check `settings.gradle` explicitly sets `useVersion('2.1.10-1.0.29')` for KSP plugin.

### Issue: Build still fails with getJvmDefault()

**Solution:** 
1. Run `./gradlew --stop` to kill Gradle daemon
2. Delete `.gradle` directory
3. Run `./gradlew clean build` again

---

## [NEXT STEPS]

After successful build, consider implementing the traffic monitoring features detailed in the full analysis:

1. **TrafficStatsCollector** - Real-time speed telemetry via Xray gRPC API
2. **LatencyMonitor** - Per-server jitter and latency tracking
3. **TrafficSpeedChart** - Vico-based UI charts
4. **SocialPackageOptimizer** - Domain fronting for Turk Telekom unlimited social package

See the full project analysis for implementation details.

---

**Migration Guide Version:** 1.0  
**Date:** 2025-11-02  
**Kotlin Target:** 2.1.10  
**KSP Target:** 2.1.10-1.0.29
