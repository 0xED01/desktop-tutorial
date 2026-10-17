# Welcome to GitHub Desktop!

This is your README. READMEs are where you can communicate what your project is and how to use it.

Write your name on line 6, save it, and then head back to GitHub Desktop.

## K线制作器

打开 `kline/index.html`（直接用浏览器双击打开，无需安装、无需联网）。

- 左侧填日期、时间（精确到分钟）和价格，按回车即添加，时间自动 +1 分钟，可连续录入
- 也可勾选“按开/高/低/收录入”，或在“批量粘贴”里一次粘贴多行（`09:30 100.5` 或 `09:30 开 高 低 收`）
- 右侧自动生成蜡烛图，可切换 1/5/15/30/60 分钟、日K、周K；支持 MA5/10/20、滚轮缩放、拖动平移、悬停查看数值
- 数据自动保存在浏览器里；可导出/导入 CSV，可导出 PNG 图片

### 安卓 App

- 直接安装：`dist/kline-maker.apk`（下载到手机后点开安装，需允许“安装未知来源应用”）
- 导出的 CSV / 图片保存在手机 `下载/K线制作器/` 目录
- 数据保存在 App 内部，**卸载 App 会清空数据**，建议定期导出 CSV 备份；覆盖安装新版本不会丢数据
- 自己编译：进入 `android/` 目录运行 `./gradlew assembleRelease`（需 Android SDK），App 直接打包 `kline/index.html`，网页版和安卓版共用一份代码
