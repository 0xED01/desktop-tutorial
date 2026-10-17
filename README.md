# Welcome to GitHub Desktop!

This is your README. READMEs are where you can communicate what your project is and how to use it.

Write your name on line 6, save it, and then head back to GitHub Desktop.

## K线制作器

自己填价格（可精确到分钟），自动生成K线蜡烛图。有网页版 `kline/index.html`（浏览器直接打开）和安卓 App `dist/kline-maker.apk`，两者功能相同。

**录入**
- 填日期、时间、价格，回车即添加，时间自动 +1 分钟，可连续录入；“上一分钟 / 下一分钟 / 现在”快速调时间
- 价格旁 “− / +” 按步长微调，“同上” 一键填入上一条收盘价
- 可按开/高/低/收录入，可填成交量
- 批量粘贴：`09:30 100.5`、`09:30 100.5 1200`（价格 量）、`09:30 开 高 低 收 [量]`，可带日期
- 在“数据”页点任意一行即可修改，支持删除和撤销

**图表**
- 周期：1 / 3 / 5 / 15 / 30 / 60 分钟、日、周、月
- 主图：实心K线、空心K线、美国线、收盘价折线；均线 MA（周期可自定义）、布林线 BOLL
- 副图：成交量、MACD、KDJ、RSI
- 水平线（输入价格或在图上点选），标注可见区最高/最低价、最新价线
- 电脑：滚轮缩放、拖动平移、双击全部显示；手机：双指缩放、单指滑动、轻点显示十字光标（再拖动可移动光标）

**数据**
- 多品种，各自独立保存
- 区间统计：开盘、最新、最高、最低、涨跌幅、振幅、成交量等
- 导入/导出 CSV，备份/恢复全部数据（JSON），导出图表 PNG
- 深色/浅色主题，红涨绿跌可切换，价格小数位可设置

### 安卓 App

- 直接安装：`dist/kline-maker.apk`（下载到手机后点开安装，需允许“安装未知来源应用”）
- 导出的文件保存在手机 `下载/K线制作器/` 目录
- 数据保存在 App 内部，**卸载 App 会清空数据**，请定期“备份全部”；覆盖安装新版本不会丢数据
- 自己编译：进入 `android/` 目录运行 `./gradlew assembleRelease`（需 Android SDK），App 直接打包 `kline/index.html`
