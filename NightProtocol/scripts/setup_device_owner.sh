#!/usr/bin/env bash
# 安装 Debug(testOnly) APK 并设为 Device Owner。
# 前提：设备上没有任何账户（设置→账户 全部移除），没有其他用户/访客/应用分身/隐私空间。
set -euo pipefail
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
ADMIN="com.yuandian.nightprotocol/.admin.SleepAdminReceiver"
adb install -r -t "$APK"
adb shell dpm set-device-owner "$ADMIN"
adb shell dumpsys device_policy | grep -iE "Device Owner|admin=|com.yuandian" | head -20
