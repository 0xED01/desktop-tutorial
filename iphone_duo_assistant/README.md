# iPhone Duo 抢购助手（Windows 11 · 中文界面）

一个**不自动付款**的新品购买辅助工具：

- **本地模拟商店模式**：完整的全自动流程（开售等待 → 按库存/预算/偏好选配置 → 加入购物袋 → 结账 → 提交订单 → **停在“等待付款”** → Bark 高优先级推送）。
- **官网辅助模式**：针对 apple.com.cn，只做网站规则允许的事情（定时提醒、到点打开官网、按你勾选的有货配置即时推荐、你亲手下单后登记并推送），**对官网不做任何自动访问、读取或点击**。

> “iPhone Duo”的颜色、容量和价格截至编写时（2026-10）无法确认。程序中的默认值和模拟商店价格都是**虚构**的，开售后请在界面中按官网信息修改。

---

## 一、真实 Apple Store 的规则限制（请先读）

Apple 中国官网《网站使用条款》（<https://www.apple.com.cn/legal/internet-services/terms/site.html>）“你对本网站的使用”一节：

> “你不得使用任何‘深层链接’、‘网页搜罗’、‘网页机器人’、‘网页采集工具’或其他自动设备、程序、算法或方法，或者任何类似或同等的手动流程来访问、获取、拷贝或监控本网站的任何组成部分或任何内容”

《销售政策》（<https://www.apple.com.cn/shop/open/salespolicies>）还写明：Apple “可能会限制单个订单或相关联订单购买同类型产品的数量”；“您不可以为转售目的在 Apple Store 购买产品”；Apple 可在接受订单前要求额外的资格信息。

| 环节 | 在官网上能否由程序自动完成 | 本软件的做法 |
|---|---|---|
| 自动打开并**读取**商品页的库存、价格 | ❌ 属于用程序“获取/监控”网站内容 | 不做。你自己看，然后在界面上勾选“有货” |
| 自动刷新页面、监控是否开售 | ❌ 属于“监控” | 不做。用本机计时 + Bark 提醒你 |
| 自动点选颜色/容量、加入购物袋 | ❌ 属于用程序“访问” | 不做 |
| 自动结账、提交订单 | ❌ 同上 | 不做 |
| 验证码、登录、排队、限购 | ❌ 无论如何都不规避 | 模拟商店中检测到即暂停并推送，由你处理；排队则被动等待 |
| 自动付款 | ❌ 你明确不要 | 两种模式都不做。代码里有保护：任何文字含“付款/支付/pay”的按钮，程序拒绝点击 |
| 定时提醒、到点用**你的默认浏览器**打开官网 | ✅ 等同于你自己点开链接 | 已实现 |
| 根据你勾选的有货配置 + 预算/偏好给出推荐 | ✅ 不接触网站 | 已实现 |
| 你下单后登记、防重复、Bark 推送汇总 | ✅ 不接触网站 | 已实现 |

---

## 二、项目结构

```
iphone_duo_assistant/
├── run_app.py                     # 启动入口（也是 PyInstaller 入口）
├── requirements.txt               # 运行依赖
├── requirements-dev.txt           # 开发/测试/打包依赖
├── pytest.ini
├── config/config.example.json     # 配置示例（真实配置由界面保存在 %APPDATA%）
├── scripts/
│   ├── setup_windows.bat          # 一键创建虚拟环境并安装依赖
│   ├── run_gui.bat                # 启动图形界面
│   ├── run_mock_store.bat         # 单独运行模拟商店
│   ├── run_tests.bat              # 运行全部测试 + 有界面自检
│   └── build_exe.bat              # 打包 EXE
├── duo_assistant/
│   ├── app.py                     # 命令行参数：GUI / --self-test / --mock-store
│   ├── config.py                  # 配置数据结构、读写、校验
│   ├── secrets_store.py           # Bark Key 本地保存（Windows DPAPI 加密）
│   ├── selector.py                # 选配置算法（颜色/容量优先级、预算、库存）
│   ├── notifier.py                # Bark 推送
│   ├── order_guard.py             # 防重复下单（跨进程锁 + 订单账本）
│   ├── controller.py              # 暂停 / 继续 / 停止
│   ├── logging_setup.py           # 日志（滚动文件）
│   ├── catalog.py                 # 默认颜色、容量、模拟价格
│   ├── automation/
│   │   ├── mock_flow.py           # Playwright 自动流程（仅模拟商店）
│   │   ├── safety.py              # 验证码/登录/异常访问/排队识别
│   │   └── real_store.py          # 官网辅助：提醒、打开浏览器、推荐
│   ├── mock_store/server.py       # 本地模拟 Apple Store + 假 Bark 服务
│   └── gui/                       # PySide6 界面
└── tests/                         # 49 个测试（单元、HTTP、端到端浏览器、GUI）
```

数据位置（只在本机）：`%APPDATA%\iPhoneDuoAssistant\`
`config.json`（配置）、`secrets.json`（DPAPI 加密的 Bark Key）、`order_ledger.json`（防重复记录）、`logs\`、`screenshots\`、`browser_profile\`。

---

## 三、在 Windows 11 上运行

### 方式 A：直接用打包好的 EXE（推荐）
每次推送代码后，GitHub Actions 会在 Windows 上运行测试并打包。在仓库的 **Actions → “iPhone Duo 助手 - Windows 测试与打包” → 最新一次运行 → Artifacts** 下载 `iPhoneDuoAssistant-windows`，解压后双击 `iPhoneDuoAssistant.exe`。

默认使用 Windows 11 自带的 Microsoft Edge，不需要另装浏览器。

### 方式 B：从源码运行
1. 安装 [Python 3.12](https://www.python.org/downloads/)（勾选 *Add python.exe to PATH*）。
2. 双击 `scripts\setup_windows.bat`。
3. 双击 `scripts\run_gui.bat`。

等价命令：
```bat
py -3.12 -m venv .venv
.venv\Scripts\activate
pip install -r requirements-dev.txt
python -m playwright install chromium
python run_app.py
```

### 打包 EXE
```bat
.venv\Scripts\activate
pyinstaller --noconfirm --clean --onedir --windowed --name iPhoneDuoAssistant --collect-submodules duo_assistant run_app.py
```
输出：`dist\iPhoneDuoAssistant\iPhoneDuoAssistant.exe`（整个 `dist\iPhoneDuoAssistant` 文件夹需一起拷贝）。

EXE 的命令行参数：
```bat
iPhoneDuoAssistant.exe                                  :: 图形界面
iPhoneDuoAssistant.exe --self-test --headed             :: 显示浏览器跑一遍完整自检
iPhoneDuoAssistant.exe --self-test --self-test-out r.json
iPhoneDuoAssistant.exe --mock-store --port 8765 --preset captcha
```

---

## 四、使用说明

### 1. 购买配置页
- **可接受颜色 / 容量**：勾选=可以接受；用“上移/下移”调优先级（越靠上越优先）。可以添加官网公布的新名称。
- **预算上限**：总价（单价 × 数量）不超过该值才会购买。
- **数量**：1~2。如商店限购更少，程序会停止并通知，**不会拆单规避**。
- **选择策略**：容量优先 / 颜色优先 / 总价最低。
- **开售时间**：勾选后，程序在开售前“提前打开页面”秒数才开始访问（模拟商店）。
- **刷新间隔**：最少 2 秒，避免给服务器造成压力。

### 2. Bark 通知页
在 iPhone 安装 Bark App，复制设备 Key 粘贴进来，点“保存并发送测试通知到手机”。默认级别 `critical`（静音模式也会响）并重复响铃。自建 Bark 服务器可修改服务器地址。

### 3. 运行页（本地模拟商店）
1. 选一个场景，点“开始自动抢购”（会自动启动模拟商店）。
2. 浏览器窗口会弹出，你能看到每一步操作。
3. 停在“订单已提交，等待付款”页时，手机会收到推送：配置、单价、总价、订单号、付款期限。
4. 付款由你本人完成（模拟商店中可以手动点“立即付款”体验）。完成后点“停止”关闭浏览器。

勾选“通知发到模拟商店内置的假 Bark”后，推送不会发到手机，可在“打开管理页”中查看。

| 场景 | 测试内容 |
|---|---|
| normal | 正常完整流程 |
| presale | 20 秒后开售，程序按间隔刷新等待 |
| login | 结账前要求登录 → 暂停 + 推送 → 你手动登录（任意账号，密码 `test`）→ 自动继续 |
| captcha | 结账时出现验证码 → 暂停 + 推送 → 你手动填写 → 自动继续 |
| queue | 开售后排队 15 秒 → 被动等待（不绕过）|
| partial_soldout | 256GB 售罄 → 自动选择下一个可接受配置 |
| race | 加购瞬间售罄 → 自动尝试下一个候选 |
| all_soldout | 全部无货 → 推送一次后持续等待补货 |
| price_change | 结账价比商品页高 3000 → 超预算，**不提交订单** |
| ratelimit | 访问过快 → “异常访问”页面 → 暂停 + 推送 |

**暂停 / 继续 / 停止**：随时可用。遇到安全检查暂停后，你在浏览器里处理完会自动继续；也可以点“继续”（程序会重新加载一次页面再判断）。

### 4. 防重复下单
- 同一时间只能运行一个任务（跨进程文件锁）。
- 点击“提交订单”**之前**先写入账本；之后状态变为“待付款”。如果提交后结果不明，记为“unknown”并停止，**绝不自动重试提交**。
- 账本里有未清除的记录时，再次开始会被拒绝。确认之前的订单已处理后，点“清除当前产品的记录”。
- 模拟商店服务端也有幂等令牌：同一个结账页最多生成一个订单。

### 5. 官网辅助页
1. 填写开售时间（购买配置页）、官网单价（按容量）。
2. 点“启用开售提醒”：开售前 10 分钟、1 分钟推送提醒；开售前 N 秒推送并用默认浏览器打开官网。
3. 你在官网看到哪些配置有货，就在网格里勾“有货”，界面立刻显示“推荐购买：xxx，总价 ¥xxx”。
4. 你亲手下单后点“我已在官网提交订单（待付款）”，程序登记记录并推送汇总到手机。

建议提前在浏览器登录 Apple 账户，并确认收货地址和付款方式。

---

## 五、验证情况（如实说明）

开发环境是 Linux 云容器（Python 3.12.x、Playwright 1.56.0、PySide6 6.8.1、Chromium 141），**没有 Windows 机器**。

**已实际运行并通过：**
- 48 个测试通过，1 个跳过（DPAPI，仅限 Windows）：选择算法、配置/密钥存储、防重复账本、跨进程锁、Bark 请求格式与失败重试、模拟商店规则（限购、幂等、限流），以及**用真实浏览器跑的端到端流程**：正常下单、重复运行被拒、售罄换配置、加购瞬间售罄、结账涨价不提交、开售前等待、排队等待、无货等待、登录暂停后继续、验证码暂停（并确认程序从未通过验证码）、限流暂停、用户暂停/停止。
- GUI 在无窗口模式下完成整套操作：点“开始”→ 后台线程跑完流程 → 状态、记录和推送都正确。
- Linux 上用 PyInstaller 打包后，`--self-test` 自检通过。

**在 Windows 上实际运行之前不能视为已验证的部分：**
- Windows 上的界面显示效果（字体、DPI 缩放）。
- 使用 Microsoft Edge（`msedge`）启动浏览器。
- DPAPI 加密 Bark Key（`test_dpapi_encrypts_on_windows`）。
- Windows 上的文件锁（`msvcrt`）。
- Windows EXE 打包和 EXE 自检。
- 向真实 `api.day.app` 推送（容器无法联系你的手机；请求格式已用本地假 Bark 服务验证，与 Bark 官方 `POST /push` JSON 接口一致）。

上面前五项都已写进 GitHub Actions 的 `windows-latest` 工作流（`.github/workflows/iphone-duo-windows.yml`）。工作流跑通后，这五项就在真实 Windows 环境中验证过了；界面的实际观感仍需你本人在电脑上确认。

---

## 六、开发者命令

```bash
python -m pytest -v                                  # 全部测试
DUO_TEST_CHANNEL=msedge python -m pytest tests/test_e2e_mock_flow.py   # 用 Edge 跑端到端
DUO_TEST_HEADED=1 python -m pytest tests/test_e2e_mock_flow.py         # 显示浏览器
python run_app.py --mock-store --preset login        # 单独运行模拟商店，浏览器打开 http://127.0.0.1:8765
```
