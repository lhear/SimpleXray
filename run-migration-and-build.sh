#!/bin/bash
# Complete migration and build verification script

set -e  # Exit on error

echo -e "\033[1;34m╔═══════════════════════════════════════════════════════╗\033[0m"
echo -e "\033[1;34m║  SimpleXray Kotlin 2.x Migration & Build Automation  ║\033[0m"
echo -e "\033[1;34m╚═══════════════════════════════════════════════════════╝\033[0m"

cd "$(dirname "$0")"

echo -e "\n\033[1;33m[STEP 1/5] Applying Kotlin 2.x migration fixes...\033[0m"
./apply-fix.sh

echo -e "\n\033[1;33m[STEP 2/5] Stopping Gradle daemon...\033[0m"
./gradlew --stop || true

echo -e "\n\033[1;33m[STEP 3/5] Cleaning build artifacts...\033[0m"
./gradlew clean

echo -e "\n\033[1;33m[STEP 4/5] Building release APK...\033[0m"
echo -e "\033[90m(This may take 2-5 minutes)\033[0m"
./gradlew :app:assembleRelease --stacktrace

echo -e "\n\033[1;33m[STEP 5/5] Verifying build outputs...\033[0m"
if [ -f "app/build/outputs/apk/release/simplexray-universal.apk" ]; then
    echo -e "\033[1;32m✓ Build successful!\033[0m"
    echo -e "\nGenerated APK files:"
    ls -lh app/build/outputs/apk/release/*.apk | awk '{print "  " $9 " (" $5 ")"}'
    
    echo -e "\n\033[1;32m╔═══════════════════════════════════════════════╗\033[0m"
    echo -e "\033[1;32m║  MIGRATION SUCCESSFUL - BUILD VERIFIED ✓      ║\033[0m"
    echo -e "\033[1;32m╚═══════════════════════════════════════════════╝\033[0m"
    
    echo -e "\nTo install on device:"
    echo -e "  \033[36madb install app/build/outputs/apk/release/simplexray-universal.apk\033[0m"
else
    echo -e "\033[1;31m✗ Build failed - check logs above\033[0m"
    exit 1
fi
