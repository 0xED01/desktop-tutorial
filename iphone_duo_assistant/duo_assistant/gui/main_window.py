from __future__ import annotations

import logging
import os
import subprocess
import sys
import threading
import webbrowser
from datetime import datetime

from PySide6.QtCore import QDateTime, Qt, QTimer
from PySide6.QtGui import QFont
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QDateTimeEdit,
    QDoubleSpinBox,
    QFormLayout,
    QGridLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QPushButton,
    QSpinBox,
    QTableWidget,
    QTableWidgetItem,
    QTabWidget,
    QTextBrowser,
    QVBoxLayout,
    QWidget,
)

from .. import APP_NAME, __version__, paths
from ..automation import real_store
from ..config import BARK_LEVELS, BROWSER_CHANNELS, MAX_QUANTITY, AppConfig, load_config, save_config
from ..controller import RunController
from ..mock_store import PRESETS, MockStoreServer
from ..notifier import BarkNotifier, LogOnlyNotifier, Notifier
from ..order_guard import OrderGuard
from ..secrets_store import load_bark_key, mask, save_bark_key
from ..selector import STRATEGIES
from .widgets import PriorityList, QtLogHandler
from .workers import FlowWorker

log = logging.getLogger(__name__)

PAYMENT_METHODS = {"alipay": "支付宝", "wechat": "微信支付", "unionpay": "银联"}

REAL_STORE_RULES = """
<h3>为什么官网模式不做自动选货/下单</h3>
<p>Apple 中国官网《网站使用条款》（apple.com.cn/legal/internet-services/terms/site.html）
“你对本网站的使用”一节规定：</p>
<blockquote>“你不得使用任何‘深层链接’、‘网页搜罗’、‘网页机器人’、‘网页采集工具’或其他自动设备、
程序、算法或方法，或者任何类似或同等的手动流程来访问、获取、拷贝或监控本网站的任何组成部分或任何内容”</blockquote>
<p>《销售政策》还规定 Apple 可限制单个或关联订单的购买数量，不可为转售目的购买，
并可能在付款前要求额外资格信息。</p>
<p><b>受限环节：</b>自动打开并读取商品页库存/价格、自动刷新监控开售、自动点选颜色容量、
自动加入购物袋、自动结账与提交订单——这些都属于用程序访问或监控网站，本软件对官网一律不做。</p>
<p><b>本页提供的合规辅助：</b>开售前 Bark 定时提醒；到点用你的默认浏览器打开官网（与你手动点链接相同）；
你看到哪些配置有货就勾选，程序按预算/偏好即时告诉你选哪个；你亲手下单后一键登记并推送汇总，
同时防止重复下单。完整的自动化流程请在“运行”页使用本地模拟商店测试。</p>
"""


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(f"{APP_NAME} v{__version__}")
        self.resize(1000, 760)
        self.cfg = load_config()
        self.guard = OrderGuard()
        self.mock_server: MockStoreServer | None = None
        self.worker: FlowWorker | None = None
        self.controller: RunController | None = None
        self.reminders: list[real_store.Reminder] = []

        self.log_handler = QtLogHandler()
        logging.getLogger().addHandler(self.log_handler)

        tabs = QTabWidget()
        tabs.addTab(self._build_config_tab(), "购买配置")
        tabs.addTab(self._build_bark_tab(), "Bark 通知")
        tabs.addTab(self._build_run_tab(), "运行（本地模拟商店）")
        tabs.addTab(self._build_real_tab(), "官网辅助")
        self.setCentralWidget(tabs)
        self.log_handler.bridge.line.connect(self.log_view.appendPlainText)

        self._apply_config(self.cfg)
        self._refresh_records()
        self.timer = QTimer(self)
        self.timer.timeout.connect(self._tick)
        self.timer.start(1000)

    # ================================================================ 配置页
    def _build_config_tab(self) -> QWidget:
        w = QWidget()
        form = QFormLayout(w)
        self.product_name = QLineEdit()
        form.addRow("目标产品", self.product_name)
        self.colors = PriorityList("颜色")
        form.addRow("可接受颜色\n（勾选=可接受，\n越靠上越优先）", self.colors)
        self.capacities = PriorityList("容量")
        form.addRow("可接受容量\n（勾选=可接受，\n越靠上越优先）", self.capacities)
        self.budget = QSpinBox()
        self.budget.setRange(1, 1_000_000)
        self.budget.setSingleStep(500)
        self.budget.setSuffix(" 元（总价，含数量）")
        form.addRow("预算上限", self.budget)
        self.quantity = QSpinBox()
        self.quantity.setRange(1, MAX_QUANTITY)
        form.addRow("购买数量", self.quantity)
        self.strategy = QComboBox()
        for k, v in STRATEGIES.items():
            self.strategy.addItem(v, k)
        form.addRow("选择策略", self.strategy)
        self.payment = QComboBox()
        for k, v in PAYMENT_METHODS.items():
            self.payment.addItem(v, k)
        form.addRow("结算时选择的付款方式\n（只选择，不付款）", self.payment)

        sale_row = QHBoxLayout()
        self.use_sale_time = QCheckBox("使用开售时间")
        self.sale_time = QDateTimeEdit()
        self.sale_time.setCalendarPopup(True)
        self.sale_time.setDisplayFormat("yyyy-MM-dd HH:mm:ss")
        sale_row.addWidget(self.use_sale_time)
        sale_row.addWidget(self.sale_time, 1)
        form.addRow("开售时间", sale_row)
        self.prewarm = QSpinBox()
        self.prewarm.setRange(0, 3600)
        self.prewarm.setSuffix(" 秒")
        form.addRow("提前打开页面", self.prewarm)
        self.poll = QDoubleSpinBox()
        self.poll.setRange(2.0, 120.0)
        self.poll.setSuffix(" 秒")
        form.addRow("刷新间隔（≥2 秒）", self.poll)
        self.max_minutes = QSpinBox()
        self.max_minutes.setRange(1, 600)
        self.max_minutes.setSuffix(" 分钟")
        form.addRow("最长运行时间", self.max_minutes)
        self.channel = QComboBox()
        for c in BROWSER_CHANNELS:
            self.channel.addItem({"msedge": "Microsoft Edge（推荐，Win11 自带）", "chrome": "Google Chrome", "chromium": "Playwright Chromium"}[c], c)
        form.addRow("浏览器", self.channel)
        self.headless = QCheckBox("无界面运行（不推荐：遇到验证时你需要看到浏览器）")
        form.addRow("", self.headless)

        btn = QPushButton("保存配置")
        btn.clicked.connect(self._save)
        form.addRow("", btn)
        return w

    # ================================================================ Bark 页
    def _build_bark_tab(self) -> QWidget:
        w = QWidget()
        form = QFormLayout(w)
        self.bark_server = QLineEdit()
        form.addRow("Bark 服务器", self.bark_server)
        key_row = QHBoxLayout()
        self.bark_key = QLineEdit()
        self.bark_key.setEchoMode(QLineEdit.Password)
        self.bark_key.setPlaceholderText("在 iPhone 的 Bark App 中复制设备 Key")
        show = QCheckBox("显示")
        show.toggled.connect(
            lambda on: self.bark_key.setEchoMode(QLineEdit.Normal if on else QLineEdit.Password)
        )
        key_row.addWidget(self.bark_key, 1)
        key_row.addWidget(show)
        form.addRow("设备 Key", key_row)
        self.key_status = QLabel()
        form.addRow("", self.key_status)
        self.bark_level = QComboBox()
        for lv, text in zip(
            BARK_LEVELS,
            ["critical 重要警告（静音也响）", "timeSensitive 时效性通知", "active 普通", "passive 静默"],
        ):
            self.bark_level.addItem(text, lv)
        form.addRow("默认级别", self.bark_level)
        self.bark_volume = QSpinBox()
        self.bark_volume.setRange(0, 10)
        form.addRow("重要警告音量", self.bark_volume)
        self.bark_sound = QLineEdit()
        form.addRow("铃声", self.bark_sound)
        self.bark_group = QLineEdit()
        form.addRow("分组", self.bark_group)
        self.bark_call = QCheckBox("重要通知重复响铃（call=1）")
        form.addRow("", self.bark_call)
        btn = QPushButton("保存并发送测试通知到手机")
        btn.clicked.connect(self._test_bark)
        form.addRow("", btn)
        note = QLabel("密钥只保存在本机（Windows 下使用 DPAPI 加密），不会写入配置文件或日志。")
        note.setWordWrap(True)
        form.addRow("", note)
        return w

    # ================================================================ 运行页
    def _build_run_tab(self) -> QWidget:
        w = QWidget()
        v = QVBoxLayout(w)

        mock_box = QGroupBox("本地模拟商店（非官网，用于完整测试自动流程）")
        g = QGridLayout(mock_box)
        self.preset = QComboBox()
        for k, (desc, _) in PRESETS.items():
            self.preset.addItem(f"{k} — {desc}", k)
        self.port = QSpinBox()
        self.port.setRange(1024, 65535)
        self.mock_btn = QPushButton("启动模拟商店")
        self.mock_btn.clicked.connect(self._toggle_mock)
        admin_btn = QPushButton("打开管理页")
        admin_btn.clicked.connect(lambda: webbrowser.open(f"http://127.0.0.1:{self.port.value()}/__admin"))
        self.use_fake_bark = QCheckBox("通知发到模拟商店内置的假 Bark（不打扰手机）")
        g.addWidget(QLabel("场景"), 0, 0)
        g.addWidget(self.preset, 0, 1, 1, 3)
        g.addWidget(QLabel("端口"), 1, 0)
        g.addWidget(self.port, 1, 1)
        g.addWidget(self.mock_btn, 1, 2)
        g.addWidget(admin_btn, 1, 3)
        g.addWidget(self.use_fake_bark, 2, 0, 1, 4)
        v.addWidget(mock_box)

        ctl = QHBoxLayout()
        self.start_btn = QPushButton("开始自动抢购（模拟商店）")
        self.pause_btn = QPushButton("暂停")
        self.resume_btn = QPushButton("继续")
        self.stop_btn = QPushButton("停止")
        self.start_btn.clicked.connect(self._start)
        self.pause_btn.clicked.connect(lambda: self.controller and self.controller.pause("用户暂停"))
        self.pause_btn.clicked.connect(lambda: self._set_status("已暂停（用户）", "#b26a00"))
        self.resume_btn.clicked.connect(self._resume)
        self.stop_btn.clicked.connect(self._stop)
        for b in (self.start_btn, self.pause_btn, self.resume_btn, self.stop_btn):
            ctl.addWidget(b)
        v.addLayout(ctl)

        self.status = QLabel("就绪")
        f = QFont()
        f.setPointSize(13)
        f.setBold(True)
        self.status.setFont(f)
        self.status.setWordWrap(True)
        v.addWidget(self.status)

        self.log_view = QPlainTextEdit()
        self.log_view.setReadOnly(True)
        self.log_view.setMaximumBlockCount(5000)
        v.addWidget(self.log_view, 1)

        rec_box = QGroupBox("防重复下单记录")
        rl = QHBoxLayout(rec_box)
        self.records_label = QLabel()
        self.records_label.setWordWrap(True)
        rl.addWidget(self.records_label, 1)
        clear_btn = QPushButton("清除当前产品的记录")
        clear_btn.clicked.connect(self._clear_records)
        rl.addWidget(clear_btn)
        v.addWidget(rec_box)

        dirs = QHBoxLayout()
        for text, fn in (
            ("打开截图目录", paths.screenshots_dir),
            ("打开日志目录", paths.logs_dir),
            ("打开数据目录", paths.data_dir),
        ):
            b = QPushButton(text)
            b.clicked.connect(lambda _=False, fn=fn: self._open_dir(fn()))
            dirs.addWidget(b)
        v.addLayout(dirs)
        self._set_running(False)
        return w

    # ================================================================ 官网辅助页
    def _build_real_tab(self) -> QWidget:
        w = QWidget()
        v = QVBoxLayout(w)
        rules = QTextBrowser()
        rules.setHtml(REAL_STORE_RULES)
        rules.setOpenExternalLinks(True)
        rules.setMaximumHeight(230)
        v.addWidget(rules)

        form = QFormLayout()
        self.store_url = QLineEdit()
        form.addRow("官网购买页地址", self.store_url)
        self.open_before = QSpinBox()
        self.open_before.setRange(0, 600)
        self.open_before.setSuffix(" 秒")
        form.addRow("开售前多久打开官网", self.open_before)
        v.addLayout(form)

        row = QHBoxLayout()
        open_btn = QPushButton("立即用默认浏览器打开官网")
        open_btn.clicked.connect(lambda: real_store.open_store(self.store_url.text()))
        self.remind_btn = QPushButton("启用开售提醒")
        self.remind_btn.setCheckable(True)
        self.remind_btn.toggled.connect(self._toggle_reminders)
        row.addWidget(open_btn)
        row.addWidget(self.remind_btn)
        v.addLayout(row)
        self.countdown = QLabel()
        v.addWidget(self.countdown)

        h = QHBoxLayout()
        price_box = QGroupBox("官网单价（按容量，公布后填写）")
        pl = QVBoxLayout(price_box)
        self.price_table = QTableWidget(0, 2)
        self.price_table.setHorizontalHeaderLabels(["容量", "单价（元）"])
        self.price_table.itemChanged.connect(lambda *_: self._update_recommendation())
        pl.addWidget(self.price_table)
        h.addWidget(price_box, 1)

        avail_box = QGroupBox("官网显示有货的配置（你看到后勾选）")
        self.avail_grid = QGridLayout(avail_box)
        self.avail_checks: dict[tuple[str, str], QCheckBox] = {}
        h.addWidget(avail_box, 2)
        v.addLayout(h)

        self.recommend_label = QLabel("推荐：—")
        f = QFont()
        f.setPointSize(13)
        f.setBold(True)
        self.recommend_label.setFont(f)
        self.recommend_label.setWordWrap(True)
        v.addWidget(self.recommend_label)

        done_btn = QPushButton("我已在官网提交订单（待付款）→ 登记并推送到手机")
        done_btn.clicked.connect(self._manual_order_done)
        v.addWidget(done_btn)
        v.addStretch()

        self.colors.changed.connect(self._rebuild_real_grid)
        self.capacities.changed.connect(self._rebuild_real_grid)
        return w

    def _rebuild_real_grid(self) -> None:
        if not hasattr(self, "avail_grid"):
            return
        while self.avail_grid.count():
            item = self.avail_grid.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        self.avail_checks.clear()
        colors, caps = self.colors.checked_items(), self.capacities.checked_items()
        for c_i, cap in enumerate(caps):
            self.avail_grid.addWidget(QLabel(cap), 0, c_i + 1)
        for r_i, color in enumerate(colors):
            self.avail_grid.addWidget(QLabel(color), r_i + 1, 0)
            for c_i, cap in enumerate(caps):
                cb = QCheckBox("有货")
                cb.toggled.connect(lambda *_: self._update_recommendation())
                self.avail_grid.addWidget(cb, r_i + 1, c_i + 1)
                self.avail_checks[(color, cap)] = cb
        # 价格表跟随全部容量
        prices = self._prices()
        self.price_table.blockSignals(True)
        all_caps = self.capacities.all_items()
        self.price_table.setRowCount(len(all_caps))
        for i, cap in enumerate(all_caps):
            name = QTableWidgetItem(cap)
            name.setFlags(name.flags() & ~Qt.ItemIsEditable)
            self.price_table.setItem(i, 0, name)
            p = prices.get(cap) or self.cfg.real.prices.get(cap)
            self.price_table.setItem(i, 1, QTableWidgetItem(str(p) if p else ""))
        self.price_table.blockSignals(False)
        self._update_recommendation()

    def _prices(self) -> dict[str, int]:
        out = {}
        for i in range(self.price_table.rowCount()):
            cap, val = self.price_table.item(i, 0), self.price_table.item(i, 1)
            if cap and val and val.text().strip().isdigit():
                out[cap.text()] = int(val.text().strip())
        return out

    def _current_recommendation(self):
        available = [k for k, cb in self.avail_checks.items() if cb.isChecked()]
        cfg = self._collect()
        return real_store.recommend(available, self._prices(), cfg.preferences())

    def _update_recommendation(self) -> None:
        if not self.avail_checks:
            return
        if not any(cb.isChecked() for cb in self.avail_checks.values()):
            self.recommend_label.setText("推荐：请勾选官网上显示有货的配置")
            return
        r = self._current_recommendation()
        if r.best:
            others = "；".join(c.describe() for c in r.candidates[1:3])
            self.recommend_label.setText(
                f"推荐购买：{r.best.describe()}" + (f"\n备选：{others}" if others else "")
            )
        else:
            missing = [cap for (_, cap), cb in self.avail_checks.items() if cb.isChecked() and cap not in self._prices()]
            hint = "（请先填写这些容量的单价：" + "、".join(sorted(set(missing))) + "）" if missing else ""
            self.recommend_label.setText("推荐：已勾选的配置都不满足预算/条件" + hint)

    # ================================================================ 配置读写
    def _apply_config(self, cfg: AppConfig) -> None:
        p = cfg.purchase
        self.product_name.setText(p.product_name)
        self.colors.set_items(p.all_colors, p.acceptable_colors)
        self.capacities.set_items(p.all_capacities, p.acceptable_capacities)
        self.budget.setValue(p.budget_total)
        self.quantity.setValue(p.quantity)
        self.strategy.setCurrentIndex(max(0, self.strategy.findData(p.strategy)))
        self.payment.setCurrentIndex(max(0, self.payment.findData(p.payment_method)))
        self.use_sale_time.setChecked(p.use_sale_time)
        dt = QDateTime.currentDateTime().addSecs(3600)
        if p.sale_time:
            parsed = QDateTime.fromString(p.sale_time, Qt.ISODate)
            if parsed.isValid():
                dt = parsed
        self.sale_time.setDateTime(dt)
        self.prewarm.setValue(p.prewarm_seconds)
        self.poll.setValue(p.poll_interval_sec)
        self.max_minutes.setValue(p.max_run_minutes)
        self.channel.setCurrentIndex(max(0, self.channel.findData(cfg.browser.channel)))
        self.headless.setChecked(cfg.browser.headless)

        b = cfg.bark
        self.bark_server.setText(b.server)
        key = load_bark_key()
        self.bark_key.setText(key)
        self.key_status.setText(f"当前已保存：{mask(key)}")
        self.bark_level.setCurrentIndex(max(0, self.bark_level.findData(b.level)))
        self.bark_volume.setValue(b.volume)
        self.bark_sound.setText(b.sound)
        self.bark_group.setText(b.group)
        self.bark_call.setChecked(b.call)

        self.port.setValue(cfg.mock.port)
        self.preset.setCurrentIndex(max(0, self.preset.findData(cfg.mock.preset)))
        self.store_url.setText(cfg.real.store_url)
        self.open_before.setValue(cfg.real.open_seconds_before)
        self._rebuild_real_grid()

    def _collect(self) -> AppConfig:
        cfg = AppConfig()
        p = cfg.purchase
        p.product_name = self.product_name.text().strip()
        p.all_colors = self.colors.all_items()
        p.acceptable_colors = self.colors.checked_items()
        p.all_capacities = self.capacities.all_items()
        p.acceptable_capacities = self.capacities.checked_items()
        p.budget_total = self.budget.value()
        p.quantity = self.quantity.value()
        p.strategy = self.strategy.currentData()
        p.payment_method = self.payment.currentData()
        p.use_sale_time = self.use_sale_time.isChecked()
        p.sale_time = self.sale_time.dateTime().toString(Qt.ISODate)
        p.prewarm_seconds = self.prewarm.value()
        p.poll_interval_sec = self.poll.value()
        p.max_run_minutes = self.max_minutes.value()
        cfg.browser.channel = self.channel.currentData()
        cfg.browser.headless = self.headless.isChecked()
        b = cfg.bark
        b.server = self.bark_server.text().strip() or "https://api.day.app"
        b.level = self.bark_level.currentData()
        b.volume = self.bark_volume.value()
        b.sound = self.bark_sound.text().strip()
        b.group = self.bark_group.text().strip()
        b.call = self.bark_call.isChecked()
        cfg.mock.port = self.port.value()
        cfg.mock.preset = self.preset.currentData()
        cfg.real.store_url = self.store_url.text().strip()
        cfg.real.open_seconds_before = self.open_before.value()
        cfg.real.prices = self._prices()
        return cfg

    def _save(self, quiet: bool = False) -> AppConfig | None:
        cfg = self._collect()
        errors = cfg.validate()
        if errors:
            QMessageBox.warning(self, "配置有误", "\n".join(errors))
            return None
        save_config(cfg)
        key = self.bark_key.text().strip()
        if key != load_bark_key():
            scheme = save_bark_key(key)
            log.info("Bark 密钥已保存（%s，保护方式：%s）", mask(key), scheme)
        self.key_status.setText(f"当前已保存：{mask(key)}")
        self.cfg = cfg
        if not quiet:
            self.statusBar().showMessage(f"配置已保存到 {paths.config_path()}", 5000)
        return cfg

    # ================================================================ Bark
    def _notifier(self, cfg: AppConfig, allow_fake: bool = True) -> Notifier:
        if allow_fake and self.use_fake_bark.isChecked():
            if not self.mock_server:
                self._toggle_mock()
            if self.mock_server:
                fake = type(cfg.bark)(**{**cfg.bark.__dict__, "server": self.mock_server.bark_server})
                return BarkNotifier(fake, "FAKE-LOCAL")
        key = load_bark_key()
        return BarkNotifier(cfg.bark, key) if key else LogOnlyNotifier()

    def _send_async(self, notifier: Notifier, title: str, body: str, level: str | None = None) -> None:
        def job():
            ok = notifier.send(title, body, level=level)
            log.info("推送%s：%s", "成功" if ok else "失败", title)

        threading.Thread(target=job, daemon=True).start()

    def _test_bark(self) -> None:
        cfg = self._save()
        if not cfg:
            return
        if not load_bark_key():
            QMessageBox.warning(self, "缺少密钥", "请先填写 Bark 设备 Key")
            return
        self._send_async(
            self._notifier(cfg, allow_fake=False),
            "抢购助手测试通知",
            f"如果你看到这条消息，说明 Bark 配置正确。{datetime.now():%H:%M:%S}",
        )
        self.statusBar().showMessage("测试通知已发送，请查看手机（结果见运行页日志）", 5000)

    # ================================================================ 模拟商店
    def _toggle_mock(self) -> None:
        if self.mock_server:
            self.mock_server.stop()
            self.mock_server = None
            self.mock_btn.setText("启动模拟商店")
            log.info("模拟商店已停止")
            return
        try:
            self.mock_server = MockStoreServer(port=self.port.value(), preset=self.preset.currentData()).start()
        except OSError as exc:
            QMessageBox.critical(self, "无法启动", f"端口 {self.port.value()} 无法使用：{exc}")
            return
        self.mock_btn.setText("停止模拟商店")
        log.info("模拟商店已启动：%s（场景 %s）", self.mock_server.product_url, self.preset.currentData())

    # ================================================================ 运行控制
    def _set_running(self, running: bool) -> None:
        self.start_btn.setEnabled(not running)
        self.pause_btn.setEnabled(running)
        self.resume_btn.setEnabled(running)
        self.stop_btn.setEnabled(running)

    def _set_status(self, text: str, color: str = "#1d1d1f") -> None:
        self.status.setText(text)
        self.status.setStyleSheet(f"color:{color}")

    def _start(self) -> None:
        cfg = self._save(quiet=True)
        if not cfg:
            return
        if self.mock_server is None:
            self._toggle_mock()
            if self.mock_server is None:
                return
        elif self.mock_server.state.preset != cfg.mock.preset:
            self.mock_server.state.apply_preset(cfg.mock.preset)
            log.info("模拟商店场景已切换为 %s", cfg.mock.preset)
        notifier = self._notifier(cfg)
        if isinstance(notifier, LogOnlyNotifier):
            ans = QMessageBox.question(
                self, "未配置 Bark", "未设置 Bark 密钥，通知只会写入日志。仍要继续吗？"
            )
            if ans != QMessageBox.Yes:
                return
        self.controller = RunController()
        self.worker = FlowWorker(cfg, self.mock_server.product_url, notifier, self.controller)
        self.worker.event.connect(self._on_event)
        self.worker.done.connect(self._on_done)
        self._set_running(True)
        self._set_status("运行中…", "#0071e3")
        self.worker.start()

    def _resume(self) -> None:
        if self.controller:
            self.controller.resume()
            self._set_status("已继续…", "#0071e3")

    def _stop(self) -> None:
        if self.controller:
            self.controller.stop()
            self._set_status("正在停止…", "#86868b")

    def _on_event(self, kind: str, msg: str) -> None:
        colors = {"paused": "#b00020", "error": "#b00020", "success": "#1a7f37", "selected": "#1a7f37"}
        if kind in ("status", "paused", "resumed", "selected", "success", "error"):
            self._set_status(msg, colors.get(kind, "#0071e3"))
        if kind == "paused":
            self.raise_()
            self.activateWindow()
        if kind in ("success", "selected", "notify"):
            self._refresh_records()

    def _on_done(self, result) -> None:
        self._set_running(False)
        self._refresh_records()
        if result.success:
            self._set_status("✅ " + result.message.replace("\n", "；"), "#1a7f37")
        else:
            self._set_status("结束：" + result.message, "#b00020")
        self.worker = None
        self.controller = None

    # ================================================================ 记录
    def _refresh_records(self) -> None:
        name = self.product_name.text().strip()
        active = self.guard.active(name) if name else []
        if not active:
            self.records_label.setText(f"「{name}」暂无未完成的订单记录，可以开始。")
        else:
            self.records_label.setText(
                "\n".join(
                    f"⚠️ {r.time} 状态：{r.status} | {r.config} | ¥{r.total:,} | 订单号 {r.order_id or '未知'}"
                    for r in active
                )
                + "\n存在记录时程序会拒绝再次下单。"
            )

    def _clear_records(self) -> None:
        name = self.product_name.text().strip()
        ans = QMessageBox.question(
            self,
            "确认清除",
            f"确定清除「{name}」的订单记录吗？\n只有在你已确认之前的订单已付款/已取消、且确实需要再次购买时才清除。",
        )
        if ans == QMessageBox.Yes:
            n = self.guard.clear(name)
            log.info("已清除 %d 条记录", n)
            self._refresh_records()

    # ================================================================ 官网辅助
    def _toggle_reminders(self, on: bool) -> None:
        if not on:
            self.reminders = []
            self.remind_btn.setText("启用开售提醒")
            return
        cfg = self._save(quiet=True)
        sale = cfg.sale_datetime() if cfg else None
        if not sale:
            QMessageBox.warning(self, "未设置开售时间", "请先在“购买配置”页勾选并填写开售时间")
            self.remind_btn.setChecked(False)
            return
        self.reminders = real_store.build_reminders(cfg.purchase.product_name, sale, cfg.real.open_seconds_before)
        if not self.reminders:
            QMessageBox.information(self, "提示", "开售时间已过，没有可安排的提醒")
            self.remind_btn.setChecked(False)
            return
        self.remind_btn.setText("已启用开售提醒（点击取消）")
        log.info("已安排 %d 个提醒：%s", len(self.reminders), [r.when.strftime("%H:%M:%S") for r in self.reminders])

    def _tick(self) -> None:
        sale = None
        if self.use_sale_time.isChecked():
            sale = self.sale_time.dateTime().toPython()
        if sale:
            secs = int((sale - datetime.now()).total_seconds())
            if secs > 0:
                self.countdown.setText(f"距离开售：{secs // 3600} 小时 {secs % 3600 // 60} 分 {secs % 60} 秒")
            else:
                self.countdown.setText("已到开售时间")
        else:
            self.countdown.setText("未设置开售时间")
        if self.reminders:
            due = real_store.due_reminders(self.reminders)
            if due:
                notifier = self._notifier(self.cfg, allow_fake=False)
                for r in due:
                    self._send_async(notifier, r.title, r.body, r.level)
                    if r.open_browser:
                        real_store.open_store(self.cfg.real.store_url)
                        log.info("已用默认浏览器打开官网：%s", self.cfg.real.store_url)
            if all(r.fired for r in self.reminders):
                self.reminders = []
                self.remind_btn.setChecked(False)

    def _manual_order_done(self) -> None:
        cfg = self._save(quiet=True)
        if not cfg:
            return
        r = self._current_recommendation()
        options = [c.describe() for c in r.candidates] + ["其他配置（手动填写）"]
        from PySide6.QtWidgets import QInputDialog

        choice, ok = QInputDialog.getItem(self, "登记订单", "你下单的配置：", options, 0, False)
        if not ok:
            return
        total = 0
        if choice == options[-1]:
            choice, ok = QInputDialog.getText(self, "登记订单", "配置描述（如：远峰蓝色 512GB ×1）：")
            if not ok or not choice.strip():
                return
            total, ok = QInputDialog.getInt(self, "登记订单", "总价（元）：", 0, 0, 1_000_000)
            if not ok:
                return
        else:
            total = r.candidates[options.index(choice)].total
        order_id, ok = QInputDialog.getText(self, "登记订单", "订单号（可留空）：")
        if not ok:
            return
        active = self.guard.active(cfg.purchase.product_name)
        if active:
            ans = QMessageBox.question(
                self, "已有记录", "该产品已有订单记录，确定要再登记一笔吗？（请确认没有重复下单）"
            )
            if ans != QMessageBox.Yes:
                return
        self.guard.record_manual(cfg.purchase.product_name, choice, total, order_id.strip())
        self._refresh_records()
        body = (
            f"配置：{choice}\n总价：¥{total:,}\n订单号：{order_id.strip() or '未填写'}\n"
            "进度：已在官网提交订单，等待付款。请尽快在 Apple Store App 或官网完成付款。"
        )
        self._send_async(
            self._notifier(cfg, allow_fake=False), f"✅ {cfg.purchase.product_name} 已下单，待付款", body, "critical"
        )
        QMessageBox.information(self, "已登记", body)

    # ================================================================ 其他
    def _open_dir(self, path) -> None:
        if sys.platform == "win32":
            os.startfile(str(path))  # type: ignore[attr-defined]
        elif sys.platform == "darwin":
            subprocess.Popen(["open", str(path)])
        else:
            subprocess.Popen(["xdg-open", str(path)])

    def closeEvent(self, event) -> None:
        if self.worker and self.worker.isRunning():
            ans = QMessageBox.question(self, "确认退出", "抢购任务仍在运行，确定停止并退出吗？")
            if ans != QMessageBox.Yes:
                event.ignore()
                return
            self.controller.stop()
            self.worker.wait(15000)
        cfg = self._collect()
        if not cfg.validate():
            save_config(cfg)
        if self.mock_server:
            self.mock_server.stop()
        logging.getLogger().removeHandler(self.log_handler)
        event.accept()
