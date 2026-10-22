# Welcome to GitHub Desktop!

This is your README. READMEs are where you can communicate what your project is and how to use it.

Write your name on line 6, save it, and then head back to GitHub Desktop.

## 客户信息记录 Android App

- 安装包：[`客户信息记录-v2.1.apk`](客户信息记录-v2.1.apk)（Android 7.0 及以上）
- 源码：`CustomerRecorder/`，构建命令：`cd CustomerRecorder && ./gradlew assembleRelease`

使用：复制银行审批提示 → 打开 App 点“一键粘贴保存”（或长按输入框粘贴，会自动识别保存）。
自动识别姓名、产品、额度、利率、首次审批通过日期、借记卡/手机银行开通情况；支持搜索、筛选未开通客户、编辑备注、导出 Excel(CSV)。

### v1.1 更新
- 顶部“当前活动”栏：设置一次扫码活动、礼品、成本，之后新记录自动带上
- 打开 App 自动读取剪贴板里的审批信息并保存（菜单里可关闭）
- 新增字段：扫码活动、礼品、礼品成本、礼品已送；礼品成本自动记忆
- 统计礼品总成本、礼品未送人数；筛选“礼品未送”

### v2.0 更新
- 全新卡片式界面，客户按记录日期分组；日期筛选：今天 / 昨天 / 本周 / 本月
- 粘贴后先显示识别结果，补充手机号、信用卡、扫码活动后点“保存记录”（不再自动读取剪贴板）
- 活动管理：随处“＋ 新增活动”，活动自带礼品和成本，可查看每个活动的人数和成本
- 新增手机号（可一键拨打）、信用卡开通状态；新增“未开信用卡”筛选

### v2.1 更新
- 录入时借记卡、手机银行也可直接点选（按识别结果自动预选）
