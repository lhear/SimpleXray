#!/bin/bash
# Bash script to apply Kotlin 2.x migration fixes

echo -e "\033[32mApplying Kotlin 2.x migration fixes...\033[0m"

cd "$(dirname "$0")"

# Fix 1: Update Kotlin version in build.gradle
echo -e "\n[1/4] Updating build.gradle..."
sed -i "s/kotlin_version = '2.1.21'/kotlin_version = '2.1.10'/" build.gradle

# Fix 2: Remove deprecated kotlinOptions from app/build.gradle
echo "[2/4] Removing deprecated kotlinOptions from app/build.gradle..."
sed -i '/kotlinOptions {/,/}/d' app/build.gradle
sed -i '/sourceSets {/i\    kotlin {\n        jvmToolchain(11)\n    }\n' app/build.gradle

# Fix 3: Simplify settings.gradle plugin resolution
echo "[3/4] Updating settings.gradle..."
cat > settings.gradle << 'SETTINGS_EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    // Explicit plugin versions for Kotlin 2.1.10 ecosystem
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
}

include ':app'
SETTINGS_EOF

# Fix 4: Add Kotlin 2.x optimizations to gradle.properties
echo "[4/4] Updating gradle.properties..."
cat > gradle.properties << 'PROPS_EOF'
android.enableJetifier=true
android.useAndroidX=true
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC

# Kotlin 2.x optimizations
kotlin.daemon.jvmargs=-Xmx2g
kotlin.incremental.useClasspathSnapshot=true
PROPS_EOF

echo -e "\n\033[32mAll fixes applied successfully!\033[0m"
echo -e "\033[33mRun './gradlew clean && ./gradlew :app:assembleRelease' to test the build\033[0m"
