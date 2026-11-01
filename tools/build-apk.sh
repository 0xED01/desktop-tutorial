#!/usr/bin/env bash
# Builds the installable debug APK: web bundle -> android assets -> gradle assembleDebug.
# Requires: Node 18+, JDK 17+, Android SDK (platforms;android-34, build-tools;34.0.0), Gradle 8.9+ (or the wrapper).
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
node tools/build-web.mjs
rm -rf android/app/src/main/assets/www
mkdir -p android/app/src/main/assets
cp -r web/dist android/app/src/main/assets/www
echo "sdk.dir=$ANDROID_HOME" > android/local.properties
cd android
if [ -x ./gradlew ]; then GRADLE=./gradlew; else GRADLE=gradle; fi
$GRADLE --no-daemon -q assembleDebug "$@"
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p ../dist
cp "$APK" ../dist/TrustNothing-v1.0.0-debug.apk
ls -l ../dist/TrustNothing-v1.0.0-debug.apk
