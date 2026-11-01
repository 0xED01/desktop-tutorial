# 构建记录（2026-11-01，实际执行）

## 环境检查结果

| 项目 | 结果 |
|---|---|
| JDK | OpenJDK 21.0.12（已安装） |
| Gradle | 本机 8.14.3；工程使用 Gradle Wrapper 8.14.3 |
| Android SDK | **原本不存在**；已用 cmdline-tools 安装 platform-tools、platforms;android-36、build-tools;36.0.0（AGP 自动补装 build-tools 35） |
| 模拟器 | **不可用**：容器无 `/dev/kvm`，Android Emulator 在 Linux 上运行 x86_64 镜像需要硬件加速 |
| 真机 | 无 |

工具链：AGP 8.13.2、Kotlin 2.2.21、KSP 2.3.4、Compose BOM 2025.12.01、Room 2.8.4、DataStore 1.2.0。

## 命令与结果

```
./gradlew clean assembleDebug assembleRelease assembleDebugAndroidTest testDebugUnitTest lintDebug
BUILD SUCCESSFUL in 1m 18s
139 actionable tasks: 138 executed, 1 up-to-date
```

| 产物 | 大小 | SHA-256 |
|---|---|---|
| app-debug.apk（testOnly=true，= dist/night-protocol-debug.apk） | 33,061,249 B | `bd81c748d535d14932b177fe5444092b6b6ba847da7fe1b3d68c013ceb28bfcd` |
| app-release-unsigned.apk | 24,956,607 B | `74c2d671feeebc3c60094830e8e26bf1e471b92a339af80b8d05ca00309e09dc` |
| app-debug-androidTest.apk | 819,443 B | `b07ad50b8a44fb5c16003494aa182a678240e88b46be29da4b6fdf7621b0f286` |

### 单元测试（JVM）

| 测试类 | 用例 | 失败 |
|---|---|---|
| SleepWindowCalculatorTest | 11 | 0 |
| DecisionEngineTest | 10 | 0 |
| RecoveryCodeTest | 5 | 0 |
| PolicyPlanningTest | 4 | 0 |
| ReadinessTest | 4 | 0 |
| **合计** | **34** | **0** |

### Lint

0 个 Error。Warning 主要为依赖有新版本、`UseKtx` 风格建议等。
构建中修复过的 Lint 错误：`TelecomManager.getSystemDialerPackage()` 需要 API 29（已加版本判断）。

### APK Manifest 核对（aapt2 dump）

- Debug：`android:testOnly=true` ✅；Release：无 testOnly ✅
- `SleepKioskActivity` `lockTaskMode=3`（if_whitelisted）✅
- `AdbRecoveryReceiver` / `DebugCommandReceiver` 受 `android.permission.DUMP` 保护 ✅
- `DebugCommandReceiver` 只存在于 Debug APK ✅
- 所有接收器 `directBootAware=true` ✅

## 构建过程中遇到的问题

- repo.maven.apache.org 返回 HTTP 429（限流）。在 `settings.gradle.kts` 中加入 Google 托管的 Maven Central 镜像
  `https://maven-central.storage-download.googleapis.com/maven2/` 后解决。
