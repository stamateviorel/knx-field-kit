#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$DIR/app-android/build/outputs/apk/debug/app-android-debug.apk"

if [ ! -f "$APK" ]; then
    echo "[ERROR] APK not found at $APK. Run ./gradlew :app-android:assembleDebug first."
    exit 1
fi

echo "Installing KNX Field Kit v3.11 to connected Android phone..."
adb install -r "$APK"
echo "Launching KNX Field Kit on Android..."
adb shell am start -n dev.knxfieldkit.android/.MainActivity
echo "Done!"
