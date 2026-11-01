#!/usr/bin/env bash
# 仅适用于 Debug(testOnly) 构建：彻底撤销测试管理身份并卸载。
set -uo pipefail
PKG=com.yuandian.nightprotocol
ADMIN="$PKG/.admin.SleepAdminReceiver"
# 1) 先让 App 自己解除全部限制并关闭严格模式（策略由 App 施加，最好由 App 撤销）
adb shell am broadcast -a $PKG.action.ADB_RECOVER -n $PKG/.recovery.AdbRecoveryReceiver --es reason "\"reset_debug_device\"" --ez disable_strict true
sleep 3
adb shell am task lock stop 2>/dev/null
# 2) 撤销 Device Owner（testOnly 才允许）
adb shell dpm remove-active-admin "$ADMIN"
# 3) 卸载
adb uninstall $PKG
adb shell dumpsys device_policy | grep -i "device owner" || echo "Device Owner 已清除"
