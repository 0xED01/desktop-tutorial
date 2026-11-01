# 晚安协议 (Night Protocol)

包名 `com.yuandian.nightprotocol` · Kotlin · Jetpack Compose · MVVM · DataStore · Room · minSdk 28 / targetSdk 36

每天在设定的睡眠时间（默认 23:30 → 07:30）用 Android 官方 Device Owner 能力限制整台手机，
只保留电话、紧急呼叫、闹钟与必要的系统恢复功能；到点自动恢复。没有临时娱乐解锁。

> ⚠️ **当前状态**：工程可编译，34 个 JVM 单元测试通过；**尚未在任何模拟器或真机上运行**
> （构建环境没有 KVM，无法启动模拟器）。在完成 [docs/TESTING.md](docs/TESTING.md) 中的设备验证之前，
> App 内的“正式启用前检查”会阻止严格模式启用。详见 [验证状态](#验证状态)。

## 文档

| 文档 | 内容 |
|---|---|
| [docs/RECOVERY.md](docs/RECOVERY.md) | 安全恢复流程（恢复码、ADB、撤销 Device Owner、出厂重置） |
| [docs/TESTING.md](docs/TESTING.md) | 自动化测试与手动测试方案，逐项状态 |
| [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) | Android 14/15/16 兼容性说明；iQOO 15 / OriginOS 待验证清单 |
| [docs/BUILD_LOG.md](docs/BUILD_LOG.md) | 实际构建环境与 Gradle 构建结果 |

## 构建

```bash
# 需要 JDK 17+、Android SDK (platform 36, build-tools 36)
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk  (testOnly=true)
./gradlew testDebugUnitTest        # JVM 单元测试
./gradlew assembleDebugAndroidTest # 仪器测试 APK（需设备 + Device Owner 才能运行）
./gradlew assembleRelease          # 未签名 release（无 testOnly）
```

预构建的 Debug APK：`dist/night-protocol-debug.apk`（SHA-256 见 docs/BUILD_LOG.md）。

## 安装与 Device Owner 配置（Debug 测试构建）

Device Owner 只能在**没有任何账户**的设备上通过 ADB 设置（或出厂设置流程中配置）。

1. 设置 → 账户：移除所有账户（Google、厂商账号等）。关闭访客、多用户、应用分身、隐私空间等会产生额外用户的功能。
2. 开启开发者选项 → USB 调试。
3. 执行：

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk   # -t：允许安装 testOnly APK
adb shell dpm set-device-owner com.yuandian.nightprotocol/.admin.SleepAdminReceiver
# 或：scripts/setup_device_owner.sh
```

成功输出：`Success: Device owner set to package ComponentInfo{com.yuandian.nightprotocol/...}`。
之后可以重新登录账户。

4. 打开 App → 生成恢复码（抄在纸上离线保管）→ 授予精确闹钟/通知/全屏通知/忽略电池优化 → 运行“测试封锁”
   并逐项完成检查 → 全部“已验证”后才能打开“严格模式”。

## 卸载

Device Owner 应用不能直接卸载。

- **Debug(testOnly) 构建**：`scripts/reset_debug_device.sh`，等价于：
  ```bash
  adb shell am broadcast -a com.yuandian.nightprotocol.action.ADB_RECOVER \
      -n com.yuandian.nightprotocol/.recovery.AdbRecoveryReceiver --es reason "卸载" --ez disable_strict true
  adb shell dpm remove-active-admin com.yuandian.nightprotocol/.admin.SleepAdminReceiver
  adb uninstall com.yuandian.nightprotocol
  ```
- **非 testOnly 构建**：在非睡眠时间关闭严格模式 → “移除 Device Owner（卸载前）” → 系统设置中卸载。
  （`dpm remove-active-admin` 对非 testOnly 的 Device Owner 无效；最终兜底为出厂重置。）

## 架构

```
触发源 ──────────────────────────────┐
  AlarmManager 单次闹钟 (开始/结束)     │
  BOOT_COMPLETED / LOCKED_BOOT_COMPLETED│
  TIME_SET / TIMEZONE_CHANGED          ├─► Enforcer.reconcile(trigger)   （Mutex 串行、幂等）
  MY_PACKAGE_REPLACED (APK 更新)       │     1. 读 DataStore 计划
  进程启动 / USER_PRESENT(动态)         │     2. DecisionEngine.decide(state, now, zone, isDO)  ← 纯函数
  Lock Task 意外退出回调                │     3a. 锁定：应用暂停 → Lock Task 白名单/功能位 → Kiosk 设为 HOME → 通知 → 拉起 Kiosk
  紧急恢复 (恢复码 / ADB)              ─┘     3b. 正常：解除暂停(记录集∪实测) → 清空白名单 → 清除 HOME 首选项 → 通知 Kiosk 退出
                                             4. 只调度“下一次切换”的一个闹钟；结果写入 Room 日志
```

| 包 | 职责 |
|---|---|
| `admin` | `SleepAdminReceiver`（DeviceAdminReceiver）、`DeviceOwnerState` 实时检测 |
| `policy` | `DpmPolicyController`（DPM 封装）、`EssentialPackageResolver`（动态解析拨号器/InCallService/紧急拨号/闹钟/紧急警报）、`SuspensionPlanner`、`LockTaskFeaturePolicy` |
| `schedule` | `SleepWindowCalculator`、`DecisionEngine`、`TransitionScheduler`、系统事件接收器 |
| `core` | `Enforcer`、`LockNotifier`、`NightApp`、`Graph`(DI) |
| `kiosk` | `SleepKioskActivity`（`lockTaskMode="if_whitelisted"`）+ `KioskHomeAlias`（仅睡眠期间启用的 HOME 别名） |
| `recovery` | `RecoveryCode`（PBKDF2）、`RecoveryManager`、`AdbRecoveryReceiver`（DUMP 权限保护） |
| `data` | DataStore（设备加密存储区，直接启动可读）、Room 执行日志、检查项与启用门槛 |
| `ui` | Compose 主界面 + ViewModel |

### 关键设计决定

- **Lock Task ≠ 屏幕固定**：Kiosk 只在 `DevicePolicyManager.isLockTaskPermitted()` 为 true 时调用 `startLockTask()`；
  否则系统会退化为用户可随时退出的屏幕固定（`LOCK_TASK_MODE_PINNED`），App 将其记为**失败**而不是锁定。
- **应用暂停与 Kiosk 分离**：`setPackagesSuspended()` 先执行且不依赖任何界面。即使后台 Activity 启动被拦截，
  非必要应用仍按时被停用；Kiosk 还有三层兜底：直接启动（Device Owner 属于后台启动豁免）、全屏通知、Kiosk 作为 HOME。
- **HOME 处理**：睡眠期间启用 `KioskHomeAlias` 并 `addPersistentPreferredActivity()`；早晨 `clearPackagePersistentPreferredActivities()`
  并禁用别名，用户原默认桌面随即恢复。Kiosk 接管 HOME 后才开启 `LOCK_TASK_FEATURE_HOME|NOTIFICATIONS`
  （来电 heads-up 浮窗属于 NOTIFICATIONS 功能，而 NOTIFICATIONS 必须与 HOME 同时开启）。
- **始终保留**：`GLOBAL_ACTIONS`（电源菜单：重启/紧急 SOS）、`KEYGUARD`（安全锁屏 + 锁屏紧急呼叫）、ADB 调试、
  安全模式、出厂重置。App 不设置任何 `DISALLOW_*` 用户限制。
- **幂等**：期望状态只由（当前时间, 时区, 持久化计划）决定；解除时取“记录集 ∪ 实测仍被暂停集合”，崩溃后也能修复。
- **不轮询**：只设置一个精确闹钟指向下一次切换。

### 无法保证的自动化行为（如实说明）

1. **厂商电池管理**可能在 Android 规则之外杀死进程或推迟闹钟（OriginOS 未验证）。已暂停的应用状态由系统持久保存，
   不受进程被杀影响；但 Kiosk 拉起和早晨恢复依赖闹钟按时触发。若闹钟被推迟，恢复会在下一次触发源（解锁屏幕、
   打开 App、重启）时完成。
2. **没有精确闹钟权限**时会退化为非精确闹钟，可能延迟数分钟或更久（App 会显示并记录）。
3. **后台 Activity 启动**：Device Owner 在 AOSP 中属于豁免，但厂商 ROM 可能另加限制；此时依赖全屏通知/HOME 兜底，
   锁定界面可能在用户下次点亮并解锁屏幕时才出现（应用暂停不受影响）。
4. **ACTION_USER_PRESENT** 不能在 Manifest 静态注册，只在进程存活时监听。
5. **直接启动阶段**（重启后尚未解锁）不会重写白名单，沿用重启前已持久化的系统策略，解锁后再完整核对。
6. 无法确认来电、紧急拨号等**通话行为**——这些只能由用户在真机上手动验证。

## 验证状态

| 阶段 | 内容 | 编译 | 单元测试 | 设备验证 |
|---|---|---|---|---|
| 1 | 工程、DeviceAdminReceiver、Device Owner 检测 | ✅ | — | ⬜ 未验证 |
| 2 | 系统级暂停应用 | ✅ | ✅ 规划逻辑 | ⬜ 未验证 |
| 3 | Lock Task 进入/退出 | ✅ | ✅ 功能位 | ⬜ 未验证 |
| 4 | 白名单、来电/拨号/紧急呼叫 | ✅ | — | ⬜ 未验证 |
| 5 | 23:30/07:30 调度 | ✅ | ✅ 窗口/决策 | ⬜ 未验证 |
| 6 | 重启、时区、异常恢复 | ✅ | ✅ 时区/夏令时/幂等 | ⬜ 未验证 |
| 7 | UI、日志、兼容性 | ✅ | ✅ 启用门槛 | ⬜ 未验证 |

✅ = 已在本环境实际执行并通过；⬜ = 未执行。没有任何一项被标为“失败”，因为没有进行过设备测试。
