#!/usr/bin/env bash
# Builds 财来.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Needs ANDROID_HOME with platforms;android-34 and build-tools;35.0.0 installed.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest AndroidManifest.xml \
  -A assets --java "$OUT/gen" "$OUT/res.zip"
javac --release 8 -nowarn -cp "$JAR" -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS=cailai.keystore
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias cailai -keyalg RSA -keysize 2048 \
  -validity 36500 -storepass cailai123 -keypass cailai123 -dname "CN=CaiLai" 2>/dev/null
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:cailai123 --key-pass pass:cailai123 \
  --out ../财来.apk "$OUT/aligned.apk"
"$BT/apksigner" verify ../财来.apk && echo "OK -> $(cd .. && pwd)/财来.apk"
