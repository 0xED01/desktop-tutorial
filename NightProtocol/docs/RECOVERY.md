# 安全恢复流程

恢复通道按“从轻到重”排列。**任何一条都不需要 Root，也不依赖隐藏 API。**
本应用从不设置 `DISALLOW_DEBUGGING_FEATURES`、`DISALLOW_SAFE_BOOT`、`DISALLOW_FACTORY_RESET` 等用户限制，
因此 ADB、安全模式与出厂重置始终可用。

## 0. 始终可用、无需恢复的功能

| 需求 | 方式 |
|---|---|
| 接听来电 | 正常接听（拨号器 / InCallUI 在 Lock Task 白名单中） |
| 拨打电话 | 锁定界面「电话」 |
| 紧急呼叫 | 锁定界面「紧急呼叫」；锁屏上的“紧急呼叫”；长按电源键菜单中的紧急/SOS |
| 闹钟 | 锁定界面「闹钟」；闹钟到点正常响铃（时钟应用在白名单中） |
| 重启 / 关机 | 长按电源键（`LOCK_TASK_FEATURE_GLOBAL_ACTIONS`） |

## 1. 恢复码（不需要电脑）

- 在 App 主界面“故障恢复 → 生成恢复码”。20 位随机码，只显示一次，App 只保存 PBKDF2 哈希。
- **抄在纸上，交给他人或放到卧室以外**。不要截图、不要存在本机（显示期间已禁止截屏）。
- 锁定界面 →「故障恢复」→ 输入恢复码与原因（必填）→ 结束**本次**锁定。严格模式保持开启，下一晚照常。
- 连续 5 次输错后指数退避（15 分钟起，最长 4 小时）。每次使用（成功或失败）都写入执行日志。
- 睡眠时间内不能更换恢复码。

## 2. ADB 恢复（需要电脑 + USB 调试）

```bash
# 结束本次锁定，保留严格模式
scripts/adb_recover.sh "故障说明"
# 结束锁定并关闭严格模式
scripts/adb_recover.sh "故障说明" --disable-strict
```

等价命令：

```bash
adb shell am broadcast -a com.yuandian.nightprotocol.action.ADB_RECOVER \
  -n com.yuandian.nightprotocol/.recovery.AdbRecoveryReceiver --es reason "故障说明" [--ez disable_strict true]
```

该接收器受 `android.permission.DUMP` 保护：此权限只授予 shell 与系统，普通应用无法获得，
因此只有连接 ADB 的电脑可以触发。脚本随后检查 `mLockTaskModeState`，若仍锁定则执行 `adb shell am task lock stop`，
并列出仍被暂停的第三方应用。

> 前提：USB 调试保持开启，且这台电脑已被授权。**建议在启用严格模式前，在你将来要用的电脑上完成一次授权并实际测试本流程**
> （检查项 “测试：ADB 恢复命令可解除锁定”）。

## 3. 撤销测试管理身份（仅 Debug / testOnly 构建）

```bash
scripts/reset_debug_device.sh
```

依次执行：App 自行解除所有限制并关闭严格模式 → `dpm remove-active-admin` → 卸载。
先让 App 自行解除，是因为应用暂停、Lock Task 白名单、HOME 首选项都是 App 施加的，由 App 撤销最可靠；
`remove-active-admin` 之后系统是否会自动清除全部残留策略，**未在设备上验证**，脚本会打印残留状态供检查。

若 App 已无法运行（例如被强制停止后仍有残留）：

```bash
adb shell am task lock stop                                  # 结束 Lock Task
adb shell dpm remove-active-admin com.yuandian.nightprotocol/.admin.SleepAdminReceiver
adb shell dumpsys package <包名> | grep suspended            # 检查残留暂停
```

## 4. 安全模式

长按电源键 → 长按“关机/重启” → 进入安全模式。安全模式下第三方应用（包括本应用）不运行。
注意：由 Device Owner 施加的应用暂停是系统持久状态，安全模式不会自动解除；它主要用于排查其他应用造成的问题。

## 5. 出厂重置（最后手段）

Recovery 模式或系统设置中的出厂重置始终可用。会清除所有数据与 Device Owner。
（若设备启用了 FRP，需要原 Google/厂商账号凭证。）

## 原则

- 紧急恢复只结束本次锁定；不是娱乐解锁入口；每次都记录原因。
- 睡眠开始后，普通设置按钮不能修改计划、关闭严格模式或更换恢复码。
- 正式启用前，以上 1、2 两条通道都必须在测试封锁中实际跑通（App 会自动记录结果）。
