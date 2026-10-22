#!/usr/bin/env bash
# 不依赖 Gradle，直接用 Android SDK 构建工具打包 APK
# 用法：ANDROID_HOME=/path/to/sdk ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/opt/android-sdk}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-34/android.jar
OUT=build
rm -rf $OUT && mkdir -p $OUT/gen $OUT/classes $OUT/dex

# 1. 编译资源
$BT/aapt2 compile --dir app/res -o $OUT/res.zip
$BT/aapt2 link -o $OUT/base.apk -I $JAR --manifest app/AndroidManifest.xml \
  -A app/assets --java $OUT/gen --auto-add-overlay $OUT/res.zip

# 2. 编译 Java 并转 dex
javac -source 8 -target 8 -nowarn -encoding UTF-8 -classpath $JAR -d $OUT/classes \
  $(find app/src $OUT/gen -name '*.java') 2>&1 | grep -v "bootstrap classpath\|obsolete\|To suppress" || true
$BT/d8 --release --min-api 24 --lib $JAR --output $OUT/dex $(find $OUT/classes -name '*.class')

# 3. 打包、对齐、签名
cp $OUT/base.apk $OUT/unsigned.apk
(cd $OUT/dex && zip -q -j ../unsigned.apk classes.dex)
$BT/zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
if [ ! -f release.keystore ]; then
  keytool -genkeypair -keystore release.keystore -storepass kuaiji123 -keypass kuaiji123 \
    -alias kuaiji -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=KuaijiShicao, O=Personal, C=CN"
fi
$BT/apksigner sign --ks release.keystore --ks-pass pass:kuaiji123 --ks-key-alias kuaiji \
  --v4-signing-enabled false --out ../KuaijiShicao-v1.0.apk $OUT/aligned.apk
$BT/apksigner verify ../KuaijiShicao-v1.0.apk && echo "APK 已生成：$(cd .. && pwd)/KuaijiShicao-v1.0.apk"
