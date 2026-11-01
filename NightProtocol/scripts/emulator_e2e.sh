#!/usr/bin/env bash
# 阶段 2–4 自动化端到端验证（需要已连接、已设 Device Owner 的模拟器或测试机）。
# 输出每一项 PASS / FAIL，不会把未执行的项目标为通过。
set -uo pipefail
PKG=com.yuandian.nightprotocol
pass(){ echo "PASS  $*"; }; fail(){ echo "FAIL  $*"; FAILED=1; }
FAILED=0

adb shell dumpsys device_policy | grep -q "$PKG" && pass "Device Owner 已设置" || { fail "未设置 Device Owner"; exit 1; }

# 选一个可被暂停的第三方/系统应用作为探针（模拟器上通常有 Chrome/相机/日历）
PROBE=""
for p in com.android.chrome com.android.camera2 com.google.android.calendar com.android.calculator2 com.google.android.youtube; do
  adb shell pm path "$p" >/dev/null 2>&1 && { PROBE=$p; break; }
done
echo "探针应用：${PROBE:-无}"
DIALER=$(adb shell cmd telecom get-default-dialer 2>/dev/null | tr -d '\r')
echo "默认拨号器：$DIALER"

adb shell am broadcast -a $PKG.debug.START_TEST -n $PKG/.debug.DebugCommandReceiver --ei minutes 1 >/dev/null
sleep 8
STATE=$(adb shell dumpsys activity activities | grep -o "mLockTaskModeState=[A-Z]*" | head -1)
[[ "$STATE" == *LOCKED* ]] && pass "进入 Lock Task ($STATE)" || fail "未进入 Lock Task ($STATE)；PINNED 表示屏幕固定，不算通过"
[[ -n "$PROBE" ]] && { adb shell dumpsys package "$PROBE" | grep -q "suspended=true" && pass "$PROBE 已被暂停" || fail "$PROBE 未被暂停"; }
[[ -n "$DIALER" ]] && { adb shell dumpsys package "$DIALER" | grep -q "suspended=true" && fail "拨号器被暂停" || pass "拨号器未被暂停"; }
adb shell am start -a android.intent.action.DIAL >/dev/null 2>&1; sleep 2
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -2
adb shell input keyevent KEYCODE_APP_SWITCH; sleep 1
TOP=$(adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity" )
[[ "$TOP" == *recents* || "$TOP" == *Recents* ]] && fail "最近任务可被打开" || pass "最近任务被阻止 ($TOP)"

echo "等待测试封锁到期（约 70 秒）…"
sleep 70
STATE=$(adb shell dumpsys activity activities | grep -o "mLockTaskModeState=[A-Z]*" | head -1)
[[ "$STATE" == *NONE* ]] && pass "Lock Task 已退出" || fail "Lock Task 未退出 ($STATE)"
[[ -n "$PROBE" ]] && { adb shell dumpsys package "$PROBE" | grep -q "suspended=true" && fail "$PROBE 仍被暂停" || pass "$PROBE 已恢复"; }
HOME=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')
[[ "$HOME" == $PKG* ]] && fail "HOME 仍指向 Kiosk ($HOME)" || pass "HOME 已恢复 ($HOME)"

echo "运行仪器测试…"
./gradlew -q connectedDebugAndroidTest && pass "connectedDebugAndroidTest" || fail "connectedDebugAndroidTest"
echo; echo "手动项目（来电/拨号/紧急拨号/电源键/闹钟）脚本无法代替，见 docs/TESTING.md"
exit $FAILED
