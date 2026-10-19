#!/usr/bin/env bash
# Builds a signed APK with the plain Android SDK tools (no Gradle needed).
# Requires: JDK 17+, ANDROID_HOME with platforms;android-34 and build-tools;34.0.0
set -euo pipefail

cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"
OUT=build
KEYSTORE="${KEYSTORE:-piggybank.keystore}"

rm -rf "$OUT" && mkdir -p "$OUT"/{res,gen,classes,dex}

"$BT/aapt2" compile --dir res -o "$OUT/res/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o "$OUT/unsigned.apk" \
    --java "$OUT/gen" "$OUT/res/res.zip"

javac --release 11 -classpath "$JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java')
"$BT/d8" --release --min-api 21 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -alias piggy -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass piggybank -keypass piggybank -dname "CN=PiggyBank"
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:piggybank --key-pass pass:piggybank \
    --out "$OUT/piggybank.apk" "$OUT/aligned.apk"
echo "APK: $(pwd)/$OUT/piggybank.apk"
