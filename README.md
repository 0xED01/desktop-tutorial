# Welcome to GitHub Desktop!

This is your README. READMEs are where you can communicate what your project is and how to use it.

Write your name on line 6, save it, and then head back to GitHub Desktop.

## 客户信息记录 Android App

- 安装包：[`客户信息记录-v1.1.apk`](客户信息记录-v1.1.apk)（Android 7.0 及以上）
- 源码：`CustomerRecorder/`，构建命令：`cd CustomerRecorder && ./gradlew assembleRelease`

使用：复制银行审批提示 → 打开 App 点“一键粘贴保存”（或长按输入框粘贴，会自动识别保存）。
自动识别姓名、产品、额度、利率、首次审批通过日期、借记卡/手机银行开通情况；支持搜索、筛选未开通客户、编辑备注、导出 Excel(CSV)。

### v1.1 更新
- 顶部“当前活动”栏：设置一次扫码活动、礼品、成本，之后新记录自动带上
- 打开 App 自动读取剪贴板里的审批信息并保存（菜单里可关闭）
- 新增字段：扫码活动、礼品、礼品成本、礼品已送；礼品成本自动记忆
- 统计礼品总成本、礼品未送人数；筛选“礼品未送”
