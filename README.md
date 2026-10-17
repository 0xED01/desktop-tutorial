# 水印相机 WatermarkCamera

一个 iOS 相机 App：拍完照后在照片底部加一条 **iOS 26 液态玻璃（Liquid Glass）质感**的水印——
左边是 ` iPhone 型号`，右边是**年月日**（可选时间），下面还能带上焦距 / 光圈 / 快门 / ISO。

```
╭──────────────────────────────────────────────────────╮
│   iPhone 17 Pro                 │  2026年10月17日   │
│  24mm  ƒ/1.78  1/120s  ISO 64     │           14:32   │
╰──────────────────────────────────────────────────────╯
```

## 功能

- 实时取景，0.5× / 1× / 2× / 长焦档位切换，点按对焦，闪光灯，前后摄切换
- 两种水印样式
  - **悬浮玻璃**：玻璃胶囊浮在照片底部
  - **底部玻璃栏**：照片下方延伸出一条磨砂底栏，玻璃胶囊放在里面
- 玻璃效果：背后画面高斯模糊 + 增强饱和度、白色薄雾、顶部高光、底部内发光、上亮下暗的高光描边和柔和投影；
  会根据背景亮度自动切换深色 / 白色文字
- 型号自动识别（优先读照片 EXIF，其次本机型号），也可以在设置里自定义
- 日期格式：`2026年10月17日` / `2026.10.17` / `2026-10-17`
- 可以从相册选照片加水印
- 保存到相册时保留原图 EXIF；也可以直接分享
- 界面按钮在 iOS 26 上使用系统 `glassEffect`，旧系统自动退回毛玻璃

## 运行

需要 **Xcode 26**（用到 iOS 26 的 Liquid Glass API），最低支持 iOS 17。

1. 用 Xcode 打开 `WatermarkCamera.xcodeproj`
2. 选中 WatermarkCamera target → Signing & Capabilities，选择你的 Team（个人 Apple ID 也可以），
   如有冲突把 Bundle Identifier 改成你自己的
3. 连接 iPhone 运行（模拟器没有摄像头，但可以用左下角按钮从相册导入照片测试水印）

## 代码结构

| 文件 | 作用 |
| --- | --- |
| `Camera/CameraModel.swift` | AVCaptureSession：权限、拍照、变焦、对焦、方向 |
| `Camera/CapturedPhoto.swift` | 照片 + EXIF 拍摄参数解析 |
| `Watermark/WatermarkRenderer.swift` | 核心：用 Core Image + Core Graphics 绘制玻璃水印 |
| `Watermark/DeviceModel.swift` | 机型代号 → 型号名（iPhone 11 ~ iPhone 17 / Air） |
| `Watermark/WatermarkSettings.swift` | 水印样式、日期格式等设置 |
| `Watermark/PhotoSaver.swift` | 带 EXIF 保存到相册 |
| `Views/` | SwiftUI 界面：取景、预览保存、设置 |
