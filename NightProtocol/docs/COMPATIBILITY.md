# 兼容性说明

以下为基于 Android 官方行为变更与 API 文档的**设计分析**，均**未在真机上验证**。

## 通用（API 28+）

| 能力 | API | 说明 |
|---|---|---|
| `setPackagesSuspended` | 24 | 系统会拒绝暂停：调用者自身、活动管理员、默认桌面、默认拨号器、必需的安装器/卸载器/验证器、权限控制器等，拒绝列表作为返回值写入日志 |
| `setLockTaskPackages` / `isLockTaskPermitted` | 21 / 23 | 从白名单移除本包时系统会自动结束本包的 Lock Task，用于早晨恢复 |
| `setLockTaskFeatures` | 28 | 决定 minSdk = 28；`NOTIFICATIONS`/`OVERVIEW` 需与 `HOME` 同时设置 |
| `ActivityOptions.setLockTaskEnabled` | 28 | 从后台直接以 Lock Task 启动 Kiosk |
| `addPersistentPreferredActivity` | 21 | Kiosk 睡眠期间接管 HOME |
| `lockTaskMode="if_whitelisted"` | 23 | 在白名单时启动即进入 Lock Task（重启后从 HOME 进入同样生效） |

## Android 14（API 34）

- **精确闹钟**：targetSdk ≥ 33 的新安装应用默认不再获得 `SCHEDULE_EXACT_ALARM`。本应用声明 `USE_EXACT_ALARM`（安装时授予）；
  `SCHEDULE_EXACT_ALARM` 仅对 ≤ 32 声明。若通过 Google Play 分发，`USE_EXACT_ALARM` 受政策限制（本应用为侧载 Device Owner 应用）。
- **全屏通知**：`USE_FULL_SCREEN_INTENT` 仅限通话/闹钟类应用默认授予，本应用通过 `canUseFullScreenIntent()` 检查并提供设置入口；
  它只是 Kiosk 的兜底层。
- **后台 Activity 启动**：对 `PendingIntent` 发送方新增显式授权要求。本应用不依赖通过 PendingIntent 启动 Activity，
  而是在自身进程中直接 `startActivity`，依赖“Device Owner 豁免”。
- **动态广播接收器**须声明导出属性：使用 `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`。
- 内部 Intent 均为显式（指定组件或 `setPackage`）。

## Android 15（API 35）

- targetSdk 35 强制 **edge-to-edge**：界面使用 `systemBarsPadding()`。
- 后台 Activity 启动进一步收紧（PendingIntent 创建方也需授权）；同上，不依赖该路径。
- `BOOT_COMPLETED` 中启动部分前台服务类型受限：本应用不使用前台服务，仅在 `goAsync()` 期限内完成核对。
- 私密空间（Private Space）会创建额外用户，**可能导致无法设置 Device Owner**，配置前需删除。

## Android 16（API 36）

- 大屏设备忽略 `screenOrientation`/不可调整大小声明：Kiosk 未锁定方向，布局可滚动。
- targetSdk 36 默认启用**预测性返回**：`onBackPressed()` 不再被调用；本应用使用 `OnBackPressedDispatcher` 回调拦截返回。
- 新的“高级保护”等安全功能可能限制侧载/USB 调试，若启用会影响 ADB 恢复通道——需在真机确认。

## iQOO 15 / OriginOS 真机待验证清单

所有项目状态均为 **⬜ 未验证**。厂商包名为推测，以设备上 App“查看受保护应用”页面与 `adb shell pm list packages` 的实际结果为准。

### 配置阶段
1. ⬜ `dpm set-device-owner` 能否成功：需移除 vivo 账号、Google 账号；关闭应用分身、原子隐私/隐私空间、儿童模式、访客等额外用户。记录报错原文。
2. ⬜ 开发者选项中是否需额外开启 “USB 安装”“USB 调试（安全设置）”之类选项；安装 testOnly APK 时是否被安全检查拦截。
3. ⬜ OTA 系统更新后 Device Owner 是否保持。

### 应用暂停
4. ⬜ vivo 系统应用（应用商店、浏览器、游戏中心、i 管家、主题、视频等）`setPackagesSuspended` 的实际接受/拒绝列表（看执行日志 SUSPEND 条目）。
5. ⬜ 被暂停应用在 OriginOS 桌面上的表现（灰色图标/弹窗），以及是否仍可通过负一屏、全局搜索、超级卡片、快捷方式、小窗打开。

### Lock Task
6. ⬜ 实际进入 `LOCKED` 而非 `PINNED`。
7. ⬜ 手势导航（上滑回桌面、上滑停顿进最近任务、侧滑返回）、控制中心、负一屏、侧边栏、悬浮球、小窗/分屏、原子通知是否全部被阻止。
8. ⬜ Kiosk 接管 HOME 时 OriginOS 是否弹出“选择桌面”或强制回到 vivo 桌面；早晨是否正确恢复原桌面。

### 通信与紧急能力
9. ⬜ 解析到的默认拨号器 / InCallUI 包名（预期类似 `com.android.dialer`、`com.android.incallui`，需确认），是否全部在白名单中。
10. ⬜ 亮屏来电浮窗与熄屏全屏来电界面（B5/B6）。
11. ⬜ 锁屏紧急呼叫、电源键菜单中的紧急 SOS、OriginOS 的“连按电源键紧急呼叫”。
12. ⬜ 双卡场景下的拨号与来电。

### 闹钟与调度
13. ⬜ vivo 时钟（推测 `com.android.BBKClock`）是否被解析为闹钟应用并加入白名单；响铃界面在 Lock Task 中能否显示与关闭。
14. ⬜ 后台管理：“自启动”“后台高耗电”“锁屏后清理”对 23:30/07:30 闹钟准时性的影响；建议将本应用设为允许自启动与后台高耗电。
15. ⬜ 重启后 `LOCKED_BOOT_COMPLETED` / `BOOT_COMPLETED` 是否送达；锁定期间重启后 Kiosk 是否通过 HOME 自动进入 Lock Task。

### 恢复
16. ⬜ 恢复码、ADB 恢复脚本、`am task lock stop`、`dpm remove-active-admin`（testOnly）在该机上的实际效果。
17. ⬜ 安全模式进入方式与出厂重置可用性（确认未被任何策略阻止）。
