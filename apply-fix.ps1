# PowerShell script to apply Kotlin 2.x migration fixes
Write-Host "Applying Kotlin 2.x migration fixes..." -ForegroundColor Green

# Fix 1: Update Kotlin version in build.gradle
Write-Host "`n[1/4] Updating build.gradle..."
(Get-Content "build.gradle") -replace "kotlin_version = '2.1.21'", "kotlin_version = '2.1.10'" | Set-Content "build.gradle"

# Fix 2: Remove deprecated kotlinOptions from app/build.gradle
Write-Host "[2/4] Removing deprecated kotlinOptions from app/build.gradle..."
$content = Get-Content "app/build.gradle" -Raw
$content = $content -replace '(?ms)    ndkVersion versionProps\.NDK_VERSION\r?\n    kotlinOptions \{\r?\n        jvmTarget = ''11''\r?\n    \}\r?\n', "    ndkVersion versionProps.NDK_VERSION`n`n"
$content = $content -replace '(?ms)(            \}\r?\n        \}\r?\n    \}\r?\n)\}', "`$1`n    kotlin {`n        jvmToolchain(11)`n    }`n}"
Set-Content "app/build.gradle" -Value $content -NoNewline

# Fix 3: Simplify settings.gradle plugin resolution
Write-Host "[3/4] Updating settings.gradle..."
$settingsContent = @'
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
'@
Set-Content "settings.gradle" -Value $settingsContent

# Fix 4: Add Kotlin 2.x optimizations to gradle.properties
Write-Host "[4/4] Updating gradle.properties..."
$gradleProps = @'
android.enableJetifier=true
android.useAndroidX=true
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC

# Kotlin 2.x optimizations
kotlin.daemon.jvmargs=-Xmx2g
kotlin.incremental.useClasspathSnapshot=true
'@
Set-Content "gradle.properties" -Value $gradleProps

Write-Host "`nAll fixes applied successfully!" -ForegroundColor Green
Write-Host "Run './gradlew clean && ./gradlew :app:assembleRelease' to test the build" -ForegroundColor Yellow
