# 教程视频生成工具

`docs/tutorial-video/` 里的教程视频按下面的流程生成：

1. **`TutorialShots.kt`**：Robolectric + Roborazzi 截图测试。它在电脑上运行真实的 App 代码，
   像用户一样依次点击、输入：添加客户 → 添加商品 → 新建报价 → 修改数量单价 → 保存 → PDF。
   每一步截一张图，并记录点击位置和字幕，写入 `frames.json`。
   PDF 页面用 `QuotePdfGenerator.renderToBitmaps()` 渲染，与 App 生成 PDF 用的是同一套排版代码。
2. **`make_video.py`**：用 Pillow 和 ffmpeg 把截图合成 1080×1920 竖屏视频，
   包含片头、手机外框、步骤字幕、点击动画、PDF 展示和片尾。同时输出封面图和配音稿。
   字幕字体为 Noto Sans SC（SIL OFL 开源许可）。

截图测试依赖 Robolectric 4.14.1、Roborazzi 1.39.0 和 compose ui-test。
为了不增加主项目的同步时间，这些依赖没有加入主工程；需要重新生成视频时，
再临时加入 `app/build.gradle.kts` 的 testImplementation 即可。
