# 灵动岛宠物 IslandPet

一只住在 iPhone 灵动岛里的电子宠物，适配 iPhone 17 Pro Max（iOS 17 及以上可运行，包括 iOS 26）。

## 玩法

| 系统 | 内容 |
| --- | --- |
| 🥚 领养与孵化 | 5 种宠物：猫咪、小狗、兔兔、小龙、史莱姆，各有不同的饮食喜好和属性消耗速度。从蛋开始养，敲蛋壳能帮它孵化 |
| 📈 成长进化 | 宝宝期 → 幼年期 → 少年期 → 成年期；成年形态（天使 / 元气 / 调皮）取决于平时的照顾评分 |
| 💗 五项属性 | 饱腹、心情、精力、清洁、健康，随真实时间变化，关掉 App 也会继续 |
| 😴 作息 | 哄睡后精力恢复、消耗变慢；精力耗尽会自己睡着，睡饱自动醒来 |
| 💩 便便与疾病 | 会定时拉便便；太脏、太饿或便便堆积会生病，需要吃药 |
| 💭 心愿 | 宠物会随机许愿想吃某样东西，3 小时内满足可得额外金币和经验 |
| 🎲 随机事件 | 捡到金币、叼回礼物、追蝴蝶、下雨天心情低落、做美梦…… |
| 🎮 4 个小游戏 | 接零食（拖动接住掉落零食，躲开炸弹）、翻翻乐（记忆配对）、反应力挑战、猜拳大战（每种宠物有偏爱的手势） |
| 🛍️ 商店 | 11 种食物、4 种玩具、4 种护理用品、8 件装扮（部分需要等级解锁）、6 套配色主题 |
| 🏆 成就 | 17 个成就，解锁送金币 |
| 📅 签到 / 日记 | 每日签到连续奖励；日记自动记录宠物的成长和发生的事 |
| 🔔 通知 | 快饿了、睡饱了、心愿快过期时提醒 |

### 灵动岛上能做什么

- **紧凑模式**：左边是宠物（表情随心情变化），右边是一个**自动倒计时的饱腹圆环**，加上当前最需要处理的状态（饿 / 便便 / 心愿 / 生病 / 睡觉）。
- **长按展开**：宠物、等级、金币、对话气泡、四项属性条，以及**可以直接点的按钮**：喂食、玩耍、摸摸、清洁 / 睡觉（生病时变成吃药，睡觉时变成叫醒），不用打开 App。
- **锁屏**：完整的状态卡片和同样的按钮，还有「距离饿肚子」倒计时。
- 宠物突然变饿、生病、变脏时，灵动岛会自动弹开提醒你。

## 怎么装到手机上

iOS App 必须用 **Mac + Xcode** 编译和签名，下面两种方式任选。

### 方式一：有 Mac（推荐）

1. 在 Mac 上安装 Xcode 26（App Store 搜 Xcode）和 [Homebrew](https://brew.sh)。
2. 生成工程：
   ```bash
   brew install xcodegen
   cd IslandPet
   xcodegen generate
   open IslandPet.xcodeproj
   ```
3. 在 Xcode 左侧选中项目 → 分别选中 `IslandPet` 和 `IslandPetWidget` 两个 Target → **Signing & Capabilities** → Team 选择你的 Apple ID（没有就点 Add Account 登录）。
4. 如果提示 Bundle ID 被占用，把两个 Target 的 Bundle Identifier 改成 `com.你的名字.islandpet` 和 `com.你的名字.islandpet.widget`（第二个必须以第一个开头）。
5. 用数据线连接 iPhone，手机上开启 **设置 › 隐私与安全性 › 开发者模式**，在 Xcode 顶部选择你的手机，点 ▶︎ 运行。
6. 第一次运行后，到手机 **设置 › 通用 › VPN 与设备管理** 里信任你的开发者证书。

> 用免费 Apple ID 签名的 App 7 天后会失效，需要重新用 Xcode 运行一次；加入付费开发者计划（每年 99 美元 / 688 元）后有效期为 1 年。

### 方式二：没有 Mac

仓库里配置了 GitHub Actions，每次推送都会在云端 Mac 上编译，并生成一个**未签名的 IPA**：

1. 打开 GitHub 仓库的 **Actions** 页面 → 点最新一次 `Build IslandPet` → 在页面底部下载 `IslandPet-unsigned-ipa`。
2. 在 Windows / Mac 电脑上用 [Sideloadly](https://sideloadly.io) 或 AltStore，登录你的 Apple ID，把 IPA 装到手机上（工具会用你的 Apple ID 重新签名）。
3. 同样需要开启开发者模式、信任证书，免费 Apple ID 每 7 天要重签一次。

## 使用小贴士

- 领养后在「家」页打开 **在灵动岛显示**。如果开不了，检查 **设置 › 灵动岛宠物 › 实时活动** 是否打开。
- 系统规定一个实时活动最多显示 8 小时，之后会自动结束。**打开一次 App 就会自动续上。**
- 关掉 App 时，灵动岛上的倒计时圆环会自己走；属性条和表情在你点按钮、或者系统给后台刷新机会时更新（大约 15 分钟到几小时一次，由 iOS 决定）。

## 代码结构

```
IslandPet/
├── project.yml                 XcodeGen 工程配置
├── Shared/                     App 和灵动岛共用的代码
│   ├── PetModels.swift         宠物、物种、心情、物品、存档的数据结构
│   ├── PetEngine.swift         全部玩法规则：属性衰减、互动、升级、随机事件、签到……
│   ├── Achievements.swift      成就
│   ├── PetStore.swift          存档读写
│   ├── PetActivityAttributes   灵动岛上显示的数据
│   ├── LiveActivityManager     开启 / 刷新 / 关闭灵动岛
│   ├── PetIntents.swift        灵动岛按钮（App Intents）
│   ├── PetAvatarView.swift     用 SwiftUI 图形画的宠物（不需要图片素材）
│   └── PetTheme.swift          配色主题
├── Widget/PetLiveActivity.swift  灵动岛和锁屏界面
└── App/                        App 界面：主页、游乐场（4 个小游戏）、商店、背包、档案
```

想调整难度，可以改 `PetEngine.swift` 里 `decay` 函数中的数字（每小时消耗多少）。
