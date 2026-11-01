#!/usr/bin/env bash
# 紧急恢复：结束当前锁定（保留 Device Owner 与严格模式，下一晚照常生效）。
# 用法：scripts/adb_recover.sh "原因" [--disable-strict]
set -uo pipefail
PKG=com.yuandian.nightprotocol
REASON="${1:-ADB 恢复}"
EXTRA=()
[[ "${2:-}" == "--disable-strict" ]] && EXTRA=(--ez disable_strict true)
adb shell am broadcast -a $PKG.action.ADB_RECOVER -n $PKG/.recovery.AdbRecoveryReceiver --es reason "\"$REASON\"" "${EXTRA[@]}"
sleep 3
STATE=$(adb shell dumpsys activity activities | grep -o "mLockTaskModeState=[A-Z]*" | head -1)
echo "Lock Task: ${STATE:-unknown}"
if [[ "$STATE" != *NONE* && -n "$STATE" ]]; then
  echo "Lock Task 仍处于激活状态，执行 am task lock stop"
  adb shell am task lock stop
fi
echo "仍被暂停的应用："
for p in $(adb shell pm list packages -3 | sed 's/package://' | tr -d '\r'); do
  adb shell dumpsys package "$p" | grep -q "suspended=true" && echo "  $p"
done
echo "完成。"
