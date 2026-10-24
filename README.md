# 见缝插针（AA Pin）

一款安卓"见缝插针"小游戏：点击屏幕把针射向旋转的圆盘，不能碰到盘上已有的针。

**直接安装：** [`release/AAPin.apk`](release/AAPin.apk)（约 50 KB，Android 5.0+）

## 玩法

| 模式 | 说明 |
| --- | --- |
| 闯关模式 | 6 章共 60 关，机关包括匀速、反转、变速、急停、逆转（每插一针反向）、幽灵（针忽隐忽现）、摇摆、加速。一次通过得 3 星。 |
| 无尽模式 | 一盘接一盘，速度越来越快，撞针即结束；快速连续插针触发连击加分。 |
| 限时 60 秒 | 60 秒内尽量多插针，清盘 +5 秒，撞针 -5 秒。 |
| 本机双人 | 两人分坐手机两端，点击自己半场发射。撞针判负，先插完自己的针获胜，先赢 3 局者胜。 |
| 局域网联机 | 两台手机连同一 WiFi（或一台开热点），一方创建房间，另一方在列表中加入或手动输入 IP。规则同本机双人。 |

其他：7 套皮肤（用星星解锁）、合成音效、振动反馈、粒子特效、统计数据、最佳成绩存档。

## 联机实现

- 房间发现：主机每秒向 UDP 37020 端口广播，客户端监听并列出房间。
- 对战连接：TCP 37021 端口，主机运行游戏逻辑（权威端），以 30 Hz 下发状态；客户端只上报"发射"操作，并在两帧之间外推旋转角度保证流畅。
- 客户端画面旋转 180°，双方都看到自己在屏幕下方。

## 构建

需要 JDK 17+ 和 Android SDK（platform 34）：

```bash
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/app-release.apk
```

推送到 GitHub 后，Actions 也会自动构建并上传 APK。

## 代码结构

```
app/src/main/java/com/aapin/game/
├── core/        纯 Java 游戏逻辑（不依赖 Android，可单独测试）
│   ├── Board.java       圆盘与针、碰撞判定
│   ├── RotSpec.java     各种旋转机关
│   ├── Level.java       60 个关卡 + 无尽模式关卡生成
│   ├── SoloGame.java    单人模式逻辑
│   └── VersusGame.java  双人对战逻辑 + 网络状态编解码
├── net/
│   ├── Discovery.java   UDP 广播发现房间
│   └── NetLink.java     TCP 收发线程、心跳
├── SoloView / VersusView   游戏画面（Canvas 自绘）
├── Painter / Fx            绘制工具、粒子特效
├── Sfx                     运行时合成音效 + 振动
└── *Activity               菜单、选关、设置、联机大厅
```
