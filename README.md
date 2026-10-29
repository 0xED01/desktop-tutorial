# 科目查询（初级会计）

一个简洁的安卓 App，用于查询初级会计考试（《初级会计实务》）涉及的常用会计科目。

**直接下载安装：** [`apk/科目查询-v1.0.apk`](apk/科目查询-v1.0.apk)（约 40 KB，Android 8.0 及以上）

## 功能

- **分类浏览**：资产类、负债类、共同类、所有者权益类、成本类、损益类，分类标签上显示数量，不同类别用不同颜色区分
- **快速检索**：输入即搜，支持
  - 科目编号：`1122`、`16`（前缀）
  - 科目名称：`应收`、`折旧`
  - 拼音首字母：`yszk` → 应收账款，`ljzj` → 累计折旧
  - 拼音全拼：`yinhang`
  - 内容关键词：`备抵`、`现金折扣`、`盘亏`
- **科目详情**：核算内容、借方/贷方登记内容对照、期末余额、学习要点（易错点）、典型会计分录
- **收藏**：详情页右上角 ☆ 收藏，在“★ 收藏”分类中查看
- 离线使用，无需任何权限，无广告

共收录 91 个常用科目（含新收入准则下的合同资产、合同负债、合同履约成本、合同取得成本）。

## 项目结构

```
app/src/main/java/com/acct/subjects/   Java 源码（原生 Android，无第三方依赖）
app/src/main/assets/subjects.json      科目数据（由脚本生成）
tools/subjects.py                      科目数据源，修改内容请改这里
tools/gen_data.py                      生成 subjects.json（自动加入拼音检索字段）
```

## 修改科目数据

```bash
pip install pypinyin
python3 tools/gen_data.py
```

## 构建

需要 JDK 17+ 和 Android SDK（compileSdk 34）：

```bash
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/app-release.apk
```

> 说明：release 包使用调试签名，方便直接安装。如需上架应用商店，请在 `app/build.gradle` 中配置自己的签名。
