# Firebase Crashlytics Setup Guide

This guide explains how to set up Firebase Crashlytics for crash reporting in SimpleXray.

## Overview

Firebase Crashlytics is integrated into the app to automatically collect crash reports and errors in production builds. This helps identify and fix issues that users encounter.

## Prerequisites

- A Firebase project (create one at [Firebase Console](https://console.firebase.google.com/))
- Access to Firebase project settings
- Android app registered in Firebase

## Setup Steps

### 1. Create a Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" or select an existing project
3. Follow the setup wizard to create your project

### 2. Register Your Android App

1. In Firebase Console, click "Add app" → "Android"
2. Enter your package name: `com.simplexray.an`
3. (Optional) Enter app nickname and SHA-1 certificate
4. Click "Register app"

### 3. Download google-services.json

1. Download the `google-services.json` file from Firebase Console
2. Place it in the `SimpleXray/app/` directory
3. **IMPORTANT**: This file is already in `.gitignore` to prevent committing sensitive data

**Quick Setup**:
```bash
# If you have the template file, copy and edit it
cp app/google-services.json.template app/google-services.json
# Then edit app/google-services.json with your Firebase project details
```

**Note**: The repository includes `google-services.json.template` as a reference. Replace the placeholder values with your actual Firebase configuration.

### 4. Enable Crashlytics in Firebase Console

1. In Firebase Console, go to "Crashlytics" in the left menu
2. Click "Enable Crashlytics"
3. Follow the setup wizard

### 5. Build and Test

1. Build the app:
   ```bash
   ./gradlew assembleRelease
   ```

2. Install on a test device and force a test crash:
   ```kotlin
   // In your code (for testing only)
   AppLogger.e("Test crash", RuntimeException("Test exception"))
   ```

3. Check Firebase Console → Crashlytics after a few minutes to see the report

## Configuration

### Build Types

- **Debug builds**: Logs are shown in Logcat, NOT sent to Crashlytics
- **Release builds**: Errors are sent to Firebase Crashlytics

### Crashlytics Collection

Crashlytics is automatically enabled for release builds. To disable it temporarily:

```kotlin
// In your Application class onCreate()
FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
```

### Data Collection

The app collects:
- Stack traces of crashes and errors
- Custom error messages via `AppLogger.e()`
- Non-fatal warnings via `AppLogger.w()` with throwables
- Custom keys set via `AppLogger.setCustomKey()`
- Breadcrumbs via `AppLogger.addBreadcrumb()`
- User IDs (anonymized) via `AppLogger.setUserId()`

## Usage Examples

### Basic Error Logging

```kotlin
try {
    // Some risky operation
    performNetworkRequest()
} catch (e: Exception) {
    AppLogger.e("Network request failed", e)
}
```

### Adding Context with Custom Keys

```kotlin
AppLogger.setCustomKey("connection_type", connectionType)
AppLogger.setCustomKey("server_count", serverList.size)
AppLogger.setCustomKey("vpn_enabled", isVpnActive)
```

### Setting User Identifier (Anonymized)

```kotlin
// Use a hashed or anonymized identifier
val anonymizedId = UUID.randomUUID().toString()
AppLogger.setUserId(anonymizedId)
```

### Adding Breadcrumbs

```kotlin
AppLogger.addBreadcrumb("User navigated to Dashboard")
AppLogger.addBreadcrumb("Server list loaded: ${servers.size} servers")
AppLogger.addBreadcrumb("Connection initiated")
```

### Logging Warnings

```kotlin
try {
    // Some operation that might fail
    parseConfig()
} catch (e: Exception) {
    // Non-fatal error, logged to Crashlytics but app continues
    AppLogger.w("Config parsing failed, using defaults", e)
}
```

## Privacy Considerations

- **No PII**: Do not log personally identifiable information
- **Anonymize IDs**: Use hashed or anonymized user identifiers
- **Sanitize Data**: Remove sensitive data from error messages
- **User Consent**: Consider adding opt-in/opt-out for analytics

## Troubleshooting

### No Crash Reports Appearing

1. Verify `google-services.json` is in `app/` directory
2. Check Firebase Console → Project Settings → Your App
3. Ensure app package name matches: `com.simplexray.an`
4. Wait 5-10 minutes after first crash (processing delay)
5. Verify you're testing a release build, not debug

### Build Errors

If you see build errors related to Firebase:

1. Ensure `google-services.json` exists in `app/` directory
2. Check that all Firebase dependencies are compatible
3. Sync Gradle files
4. Clean and rebuild:
   ```bash
   ./gradlew clean
   ./gradlew assembleRelease
   ```

### Missing google-services.json

If you don't have `google-services.json`, the app will still build and run normally. Crashlytics features will be disabled gracefully. This allows development without Firebase setup.

## CI/CD Integration

For GitHub Actions or other CI/CD:

1. Encode `google-services.json` as base64:
   ```bash
   base64 -i app/google-services.json -o google-services.json.base64
   ```

2. Add as GitHub Secret: `GOOGLE_SERVICES_JSON`

3. In your workflow, decode it:
   ```yaml
   - name: Decode google-services.json
     run: |
       echo "${{ secrets.GOOGLE_SERVICES_JSON }}" | base64 -d > app/google-services.json
   ```

## ProGuard Configuration

The Firebase Crashlytics plugin automatically adds necessary ProGuard rules. No manual configuration needed.

## Testing Crashlytics

To test if Crashlytics is working:

1. Add a test button in debug builds:
   ```kotlin
   Button(onClick = { 
       throw RuntimeException("Test Crash") 
   }) {
       Text("Test Crash (Debug Only)")
   }
   ```

2. Or use ADB to force a crash:
   ```bash
   adb shell am crash com.simplexray.an
   ```

3. Check Firebase Console after 5-10 minutes

## Resources

- [Firebase Crashlytics Documentation](https://firebase.google.com/docs/crashlytics)
- [Firebase Android Setup](https://firebase.google.com/docs/android/setup)
- [Crashlytics Best Practices](https://firebase.google.com/docs/crashlytics/best-practices)

## Support

If you encounter issues with Firebase setup, please check:
- [Firebase Status Dashboard](https://status.firebase.google.com/)
- [Firebase Support](https://firebase.google.com/support)

