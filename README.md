# 原点报价助手

> 原点软件 出品 · 面向五金贸易、机械设备销售、工业配件批发企业的移动报价管理工具

一款原生 Android 应用（Kotlin + Jetpack Compose + Material 3），完全离线运行：
在手机上管理商品和客户，生成报价单，导出 A4 中文 PDF，再通过微信等应用发送给客户。

---

## 一、现成的安装包

仓库里已经放好了构建出来的安装包：**`release/app-debug.apk`**（约 9 MB）。

不想自己编译的话，直接把这个文件传到手机上安装就行（安装方法见第五节）。

---

## 二、第一版功能（全部已实现）

| 模块 | 功能 |
| --- | --- |
| 工作台 | 本月报价总额、客户数、商品数、报价单数、最近 5 条报价、快捷入口 |
| 客户管理 | 添加 / 修改 / 删除 / 搜索（公司名称、联系人、电话、备注） |
| 商品管理 | 添加 / 修改 / 删除 / 搜索（名称、型号、规格、单位、单价），常用单位一键选择 |
| 创建报价单 | 选择客户 → 从商品库多选商品 → 自由修改数量和报价单价 → 自动计算小计与合计 |
| | 自动生成报价编号（`YD20261031-001`：日期 + 当日流水号） |
| | 报价日期、有效期（日期选择器，或 7/15/30/60 天快捷选择）、备注 |
| | 保存后可查看、编辑、复制为新报价、删除；有未保存修改时离开会提示 |
| PDF 报价单 | A4 版式：公司抬头、客户信息、明细表格、合计（含人民币大写）、备注、报价条款、签章区 |
| | 应用内实时预览；保存到手机「下载/原点报价」；通过系统分享发送到微信、QQ、邮件等 |
| 设置 | 本公司名称、联系人、电话、地址、报价条款（显示在 PDF 上） |
| 数据 | 本地 SQLite 数据库，关闭 App 后数据仍在；首次安装自动写入虚构演示数据 |

**技术要点**

- 金额统一以「分」为单位用整数存储，数量用精确小数计算，避免 0.1 + 0.2 这类浮点误差。
- 报价单里的客户和商品信息保存为「快照」：之后修改或删除商品/客户，历史报价不受影响。
- PDF 用 Android 自带的 `PdfDocument` 生成，中文用系统字体并嵌入 PDF；表格单元格自动换行，不会溢出；明细超过一页时自动分页并重复表头，页脚有「第 x 页 / 共 y 页」。
- **不申请任何权限**：保存 PDF 用 MediaStore（Android 10 及以上不需要存储权限），分享用 FileProvider。
- 支持 Android 10（API 29）及以上，目标版本 Android 15（API 35）。

**暂未实现（计划后续版本）**

- 数据备份 / 恢复、导出 Excel
- 公司 Logo、电子印章图片
- 报价单状态（已发送 / 已成交）和统计报表
- 商品分类、批量导入商品
- 深色模式

**演示数据说明**：内置的 8 个客户、15 个商品、3 张报价单都是虚构的，公司名、人名、电话都是编造的，如有雷同纯属巧合。这些数据可以在 App 里直接修改或删除。

---

## 三、项目结构

```
├── build.gradle.kts              顶层构建配置（插件版本）
├── settings.gradle.kts           项目设置与仓库地址
├── gradle.properties             Gradle 参数
├── gradlew / gradlew.bat         Gradle 启动脚本（Windows 用 gradlew.bat）
├── gradle/wrapper/               Gradle 8.11.1 包装器
├── release/app-debug.apk         已构建好的安装包
└── app/
    ├── build.gradle.kts          应用模块配置与依赖
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/              图标、主题、FileProvider 配置
        │   └── java/com/yuandian/quote/
        │       ├── YuandianApp.kt        Application 与依赖容器
        │       ├── MainActivity.kt       入口 Activity
        │       ├── data/                 数据层
        │       │   ├── AppDatabase.kt        SQLite 建表
        │       │   ├── DemoData.kt           虚构演示数据
        │       │   ├── QuoteRepository.kt    增删改查、搜索、报价编号
        │       │   ├── CompanyProfileStore.kt 本公司信息
        │       │   └── Models.kt             数据模型
        │       ├── pdf/
        │       │   ├── QuotePdfGenerator.kt  A4 报价单排版
        │       │   └── PdfExporter.kt        保存 / 分享 / 预览
        │       ├── util/                 金额（含人民币大写）、日期工具
        │       └── ui/
        │           ├── AppRoot.kt            导航与底部标签栏
        │           ├── theme/                Material 3 主题（白底 + 绿色强调）
        │           ├── components/           通用组件
        │           └── screens/              各页面
        └── test/                 单元测试（金额计算、人民币大写）
```

**开发环境版本**：Android Gradle Plugin 8.7.3 · Kotlin 2.0.21 · Gradle 8.11.1 · Compose BOM 2024.12.01 · compileSdk / targetSdk 35 · minSdk 29 · JDK 17

---

## 四、在 Windows 上编译（一步一步来）

### 第 1 步：安装 Android Studio

1. 打开 <https://developer.android.google.cn/studio>（国内镜像）或 <https://developer.android.com/studio>，下载 Windows 版 Android Studio。
2. 双击安装，一路点「Next」，用默认选项即可。
3. 第一次启动时会出现 **Setup Wizard**，选 **Standard（标准）**，它会自动下载 Android SDK，大约需要 10–30 分钟。
   - 遇到许可协议，选择 **Accept** 后点 **Finish**。

> Android Studio 自带 JDK，**不需要另外装 Java**。

### 第 2 步：下载项目代码

任选一种方式：

- **方式 A（最简单）**：在 GitHub 仓库页面点绿色的 **Code** 按钮 → **Download ZIP**，解压到一个**不含中文和空格**的路径，例如 `D:\Projects\YuandianQuote`。
- **方式 B**：用 GitHub Desktop 克隆仓库，然后切换到 `claude/yuandian-quote-android-app-ykms9e` 分支。

> ⚠️ 项目路径里最好不要有中文、空格或特殊符号，否则 Gradle 有时会报奇怪的错误。

### 第 3 步：用 Android Studio 打开项目

1. 启动 Android Studio，在欢迎页点 **Open**（如果已经打开了别的项目，就点菜单 **File → Open**）。
2. 选中解压后的项目文件夹（里面有 `settings.gradle.kts` 的那一层），点 **OK**。
3. 如果弹出「Trust Project?」，选 **Trust Project**。
4. 右下角会显示 **Gradle Sync** 进度。第一次会下载依赖，需要 5–20 分钟，等它跑完。
   - 成功的标志：底部 **Build** 窗口显示 `BUILD SUCCESSFUL` 或 `Sync finished`。

### 第 4 步：生成 APK

1. 点菜单 **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
   （新版 Android Studio 里叫 **Build → Generate App Bundles or APKs → Generate APKs**）。
2. 等 1–3 分钟，右下角弹出 **APK(s) generated successfully**，点里面的 **locate** 就能找到文件。
3. APK 位置：

   ```
   项目文件夹\app\build\outputs\apk\debug\app-debug.apk
   ```

**也可以用命令行**（在项目文件夹的地址栏输入 `cmd` 回车打开命令行窗口）：

```bat
gradlew.bat assembleDebug
```

### 第 5 步：常见错误排查

| 报错信息（关键字） | 原因与解决办法 |
| --- | --- |
| `Connect timed out` / `Could not resolve` / `Could not GET 'https://dl.google.com/...'` | 网络无法访问 Google 或 Maven 仓库。见下面的「国内网络加速」。 |
| `SDK location not found` | Android Studio 还没装好 SDK。打开 **File → Settings → Languages & Frameworks → Android SDK**，确认已安装 **Android 15.0 (API 35)**；或者直接重新打开项目让它自动生成 `local.properties`。 |
| `Failed to find target with hash string 'android-35'` | 在上面同一个 SDK 设置页面里勾选 **Android 15.0 (API 35)**，点 **Apply** 安装。 |
| `Unsupported class file major version` / `requires Java 17` | JDK 版本不对。打开 **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**，把 **Gradle JDK** 改成 **jbr-17** 或 **jbr-21**（Android Studio 自带）。 |
| `Gradle sync failed` 但没有具体原因 | 点菜单 **File → Invalidate Caches… → Invalidate and Restart**，重启后再同步一次。 |
| `The project is using an incompatible version of the Android Gradle plugin` | 你的 Android Studio 太旧。请升级到 2024.2（Ladybug）或更新的版本。 |
| 编译特别慢或内存不足 | 关掉其他大型软件；`gradle.properties` 中的 `-Xmx2048m` 可以改成 `-Xmx4096m`。 |

**国内网络加速**（如果下载依赖一直失败）：打开项目根目录的 `settings.gradle.kts`，在 **两处** `google()` 的上面各加一行阿里云镜像：

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/google") }
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
```

保存后点右上角的 **Sync Now**。

如果 Gradle 本身（`gradle-8.11.1-bin.zip`）下载失败，可以把 `gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 改成腾讯镜像：

```
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.11.1-bin.zip
```

---

## 五、安装到安卓手机

**方式 A：把 APK 文件发到手机（推荐）**

1. 把 `app-debug.apk` 通过微信「文件传输助手」、QQ 或 USB 数据线传到手机。
   - 微信可能把 `.apk` 自动改名为 `.apk.1`，下载后用文件管理器把名字改回 `.apk` 即可。
2. 在手机上点开这个 APK。
3. 系统提示「禁止安装未知来源应用」时，点 **设置**，打开「允许安装此来源的应用」，然后返回继续安装。
4. 华为、小米、OPPO、vivo 等手机可能会提示「纯净模式」或「风险应用」，选择 **继续安装** 即可（debug 包没有上架应用商店，这是正常提示）。

**方式 B：用数据线直接运行**

1. 手机进入「设置 → 关于手机」，连续点 7 次「版本号」打开开发者模式。
2. 在「开发者选项」里打开 **USB 调试**。
3. 用数据线连接电脑，手机上弹窗选择 **允许 USB 调试**。
4. Android Studio 顶部设备下拉框会出现你的手机，点绿色的 ▶ **Run** 按钮，就会自动安装并打开。

---

## 六、验收操作流程

1. **打开 App**：工作台会显示演示数据的统计。
2. **添加客户**：底部「客户」→ 右下角「添加客户」→ 填写公司名称等 → 保存。
3. **添加商品**：底部「商品」→「添加商品」→ 填写名称、型号、规格、单价，选择单位 → 保存。
4. **创建报价单**：工作台「新建报价单」→ 点「请选择客户」→ 选中刚才的客户 →「添加商品」多选商品 → 修改数量和报价单价，**小计和合计会实时自动计算**。
5. **保存报价**：点底部「保存报价」，自动进入详情页并显示 PDF 预览。
6. **导出 PDF**：点「保存到手机」，文件会存到 **文件管理 → 下载 → 原点报价** 文件夹。
7. **分享**：点「分享 PDF」→ 选择微信 → 选择联系人发送。
8. **验证数据保存**：从后台彻底关掉 App 再打开，所有数据都还在。

> 建议先进入工作台右上角 ⚙ **设置**，把本公司名称、电话、地址改成你自己的，PDF 抬头会同步更新。

---

## 七、本次构建与验证情况

- 在 Linux 云端环境里用 Android SDK 35 + Gradle 8.11.1 **真实编译**出了 `app-debug.apk`（`BUILD SUCCESSFUL`）。
- 单元测试全部通过：金额解析、四舍五入、千分位格式化、输入过滤、人民币大写。
- Android Lint 检查没有错误。
- **没有在真机或模拟器上运行过**（构建环境不支持硬件虚拟化，开不了模拟器）。界面效果、PDF 排版和分享到微信的流程需要你在手机上实际验证，有问题可以截图反馈。

---

© 原点软件
