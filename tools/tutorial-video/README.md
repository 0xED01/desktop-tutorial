# 教程视频生成工具

`docs/tutorial-video/` 里的教程视频按下面的流程生成：

1. **`TutorialShots.kt`**：Robolectric + Roborazzi 截图测试。它在电脑上运行真实的 App 代码，
   像用户一样依次点击、输入：添加客户 → 添加商品 → 新建报价 → 修改数量单价 → 保存 → PDF。
   每一步截一张图，并记录点击位置和字幕，写入 `frames.json`。
   PDF 页面用 `QuotePdfGenerator.renderToBitmaps()` 渲染，与 App 生成 PDF 用的是同一套排版代码。
2. **`make_video.py`**：用 Pillow 和 ffmpeg 把截图合成 1080×1920 竖屏视频，
   包含片头、手机外框、步骤字幕、点击动画、PDF 展示和片尾。同时输出封面图和配音稿。
   字幕字体为 Noto Sans SC（SIL OFL 开源许可）。
3. **配音**：`tts_batch.py` 通过 sherpa-onnx 离线运行 Kokoro v1.1 中文模型
   （[hexgrad/Kokoro-82M-v1.1-zh](https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh)，Apache-2.0 许可，可商用；
   ONNX 版本来自 Hugging Face `csukuangfj/kokoro-multi-lang-v1_1`），音色 zf_001（女声）。
   中文先用 jieba 分词；旁白按标点切分逐段合成，再插入固定停顿（顿号 0.12 秒、逗号 0.22 秒、句号 0.4 秒），
   保证断句准确。每个画面的停留时间按旁白长度自动延长。
   背景音乐由 `make_video.py` 用代码合成（C-Am-F-G 琶音），没有版权问题；说话时音乐自动压低。

   用法：`python3 make_video.py <截图目录> <字体目录> <输出目录> <装有 sherpa-onnx 的 python> <kokoro 模型目录>`

截图测试依赖 Robolectric 4.14.1、Roborazzi 1.39.0 和 compose ui-test。
为了不增加主项目的同步时间，这些依赖没有加入主工程；需要重新生成视频时，
再临时加入 `app/build.gradle.kts` 的 testImplementation 即可。
