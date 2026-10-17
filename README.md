# 五子棋 · 局域网对战（Android）

简洁的五子棋 App，支持两台手机在同一 Wi-Fi / 热点下联机对战，也可以本机双人对弈。

**安装包：** [`apk/Gomoku-LAN-1.0.apk`](apk/Gomoku-LAN-1.0.apk)（Android 8.0 及以上）

## 玩法

1. 两台手机连接同一个 Wi-Fi，或一台开热点、另一台连上它。
2. 一方点 **创建房间**，另一方点 **加入房间**，房间会自动出现在列表中，点一下即可加入。
   如果列表里没有出现（部分路由器会屏蔽广播），在下方输入房主屏幕上显示的 IP 地址后点 **连接**。
3. 点击棋盘预选位置，再点同一位置确认落子，避免误触。
4. 支持悔棋（需对方同意）、认输、再来一局（自动交换黑白），顶部记录比分。

规则为自由五子棋：黑先，五子及以上连成一线即胜。

## 构建

```bash
./gradlew assembleRelease   # 输出 app/build/outputs/apk/release/app-release.apk
```

需要 Android SDK（compileSdk 35）。纯 Kotlin + Android 原生 View，无第三方依赖。
联机使用 TCP 37520 端口对局、UDP 37521 端口广播发现房间。
