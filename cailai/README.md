# 财来

收手机记账 App（Android）。记录型号 / 内存 / 颜色 / 数量 / 买入价 / 卖出价，自动算利润和库存。

- 概览：今天 / 本周 / 本月 / 全部的利润、收货、卖出、库存
- 账本：按日期分组，可搜索型号、颜色、IMEI、卖家、备注
- 库存：按型号+内存+颜色汇总，可批量卖出（先进先出，支持部分卖出）
- 设置：自定义常用型号/内存/颜色，导出 CSV 表格，备份 / 恢复

数据保存在手机本地（`files/data.json`，并保留上一版本 `data.bak`）。

## 构建

需要 JDK 和 Android SDK（platforms;android-34、build-tools;35.0.0）：

```
ANDROID_HOME=/path/to/sdk ./build.sh   # 产物：../财来.apk
```

`cailai.keystore` 是签名密钥，后续升级安装必须用同一个密钥签名，否则需先卸载（会丢数据）。
