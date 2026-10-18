# Star Drive 码表（奔驰风格 Android 数字仪表）

黑色系、奔驰风格的 GPS 码表 App。安装包：[`release/StarDrive-v1.0.apk`](release/StarDrive-v1.0.apk)（Android 7.0+）

## 启动动画
光线汇聚 → 镀铬表圈绘制 → 刻度逐格点亮 → 指针满量程自检扫表 → 标题流光浮现（点击可跳过）

![splash](docs/splash.png)

## 三种表盘
经典指针 / 运动光环（SPORT+）/ HUD 数字

![gauges](docs/gauges.png)

## 主界面与全屏码表

![dashboard](docs/dashboard.png)

## 功能
- **GPS 实时车速**，指针平滑动画，竖屏 / 横屏自适应布局
- **全屏码表**：双击码表或点「全屏」，隐藏状态栏；四角显示时间、GPS、里程、均速 / 方向
- **HUD 抬头显示**：长按码表或点「HUD」，画面镜像 + 亮度拉满，手机放挡风玻璃下即可反射看车速
- **0-100 km/h 加速测试**（英制为 0-60 mph），自动计时并记录最佳成绩
- **超速提醒**：40–140 可选，超速时表盘闪红 + 提示音 + 震动
- **8 种氛围灯配色**：冰川蓝、AMG 赤红、极光紫、琥珀金、湖光青、极速绿、玫瑰粉、冰晶白
- 行程数据：本次里程、行驶时间、平均 / 最高速度、海拔、方向（指南针）、G 值 / 峰值、总里程
- km/h / mph 切换，表盘量程可选，屏幕方向锁定，常亮屏幕
- **演示模式**：没有 GPS 信号时模拟驾驶，体验效果

## 构建
```bash
./gradlew assembleRelease      # 输出 app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest  # Robolectric 渲染截图到 app/build/screens/
```
需要 Android SDK（compileSdk 34）。纯 Java + Canvas 自绘，无第三方运行时依赖。

> 驾驶时请勿操作手机。GPS 车速仅供参考，以车辆仪表为准。本项目为风格致敬，与梅赛德斯-奔驰官方无关。
