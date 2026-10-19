#!/usr/bin/env bash
# 不依赖 Gradle，直接用 Android SDK 工具打包 APK。
# 需要：JDK、ANDROID_HOME 下装好 platforms;android-35 和 build-tools;35.0.0
# 用法：ANDROID_HOME=/path/to/sdk ./build.sh   →  生成 ../QianLaiLedger.apk
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?请设置 ANDROID_HOME}"
BT="$ANDROID_HOME/build-tools/35.0.0"
JAR="$ANDROID_HOME/platforms/android-35/android.jar"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT"/{assets,gen,classes,dex}

cp ../index.html "$OUT/assets/"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest AndroidManifest.xml \
  -A "$OUT/assets" --java "$OUT/gen" "$OUT/res.zip" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR" \
  -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q ../base.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

# 签名：一直用同一个 keystore，才能覆盖安装升级（换签名必须卸载重装，数据会丢）
KS=release.jks
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias ledger -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass zhuanmai -keypass zhuanmai -dname "CN=ZhuanMai Ledger"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:zhuanmai --key-pass pass:zhuanmai \
  --out ../QianLaiLedger.apk "$OUT/aligned.apk"
"$BT/apksigner" verify ../QianLaiLedger.apk
rm -f ../QianLaiLedger.apk.idsig
echo "OK -> $(cd .. && pwd)/QianLaiLedger.apk"
