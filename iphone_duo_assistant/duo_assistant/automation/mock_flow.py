"""模拟商店自动选货 → 购物袋 → 结算 → 提交订单（待付款）流程。

仅针对本地模拟商店（页面带 data-page / data-* 标记）。
程序在“等待付款”页面停止，绝不点击任何付款按钮。
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Callable

from .. import paths
from ..config import AppConfig
from ..controller import RunController, StopRequested
from ..notifier import Notifier
from ..order_guard import AlreadyRunningError, DuplicateOrderError, OrderGuard, RunLock
from ..selector import Candidate, Variant, select_candidates
from .safety import SecurityCheck, detect_security_check

log = logging.getLogger(__name__)

# 任何文字包含这些词的按钮/链接都禁止由程序点击
FORBIDDEN_CLICK_WORDS = ("付款", "支付", "pay")

EmitFn = Callable[[str, str], None]


class ForbiddenActionError(RuntimeError):
    pass


class FlowAbort(RuntimeError):
    """流程主动终止（预算、限购等），message 会推送给用户。"""


@dataclass
class FlowResult:
    success: bool
    message: str
    order_id: str = ""
    config: str = ""
    total: int = 0
    screenshot: str = ""


def _default_emit(kind: str, msg: str) -> None:
    log.info("[%s] %s", kind, msg)


def assert_click_allowed(text: str) -> None:
    low = text.lower()
    for w in FORBIDDEN_CLICK_WORDS:
        if w in low:
            raise ForbiddenActionError(f"安全保护：拒绝点击包含“{w}”的元素（{text.strip()[:30]}）")


class PurchaseFlow:
    def __init__(
        self,
        cfg: AppConfig,
        product_url: str,
        notifier: Notifier,
        guard: OrderGuard | None = None,
        controller: RunController | None = None,
        emit: EmitFn | None = None,
        keep_browser_open: bool = True,
        profile_dir: Path | None = None,
        run_lock: RunLock | None = None,
    ):
        self.cfg = cfg
        self.prefs = cfg.preferences()
        self.campaign = cfg.purchase.product_name
        self.product_url = product_url
        self.notifier = notifier
        self.guard = guard or OrderGuard()
        self.ctl = controller or RunController()
        self.emit = emit or _default_emit
        self.keep_browser_open = keep_browser_open
        self.profile_dir = profile_dir or (paths.data_dir() / "browser_profile")
        self.run_lock = run_lock or RunLock()
        self.poll = max(2.0, float(cfg.purchase.poll_interval_sec))
        self.chosen: Candidate | None = None
        self._submitted = False
        self._failed_skus: set[str] = set()
        self._notified: set[str] = set()
        self._unknown_pages = 0
        self.page = None

    # ------------------------------------------------------------ 工具
    def _notify(self, title: str, body: str, level: str | None = None, once_key: str = "") -> None:
        if once_key:
            if once_key in self._notified:
                return
            self._notified.add(once_key)
        ok = self.notifier.send(title, body, level=level, url=self.page.url if self.page else None)
        self.emit("notify", f"{'已推送' if ok else '推送失败/未配置'}：{title}")

    def _screenshot(self, tag: str) -> str:
        if not self.page:
            return ""
        path = paths.screenshots_dir() / f"{datetime.now():%Y%m%d_%H%M%S}_{tag}.png"
        try:
            self.page.screenshot(path=str(path), full_page=True)
            self.emit("log", f"已截图：{path}")
            return str(path)
        except Exception as exc:  # 截图失败不影响主流程
            log.warning("截图失败: %s", exc)
            return ""

    def _page_state(self) -> str:
        try:
            return self.page.evaluate(
                "() => document.body ? document.body.dataset.page || '' : ''"
            )
        except Exception:
            return ""

    def _safe_click(self, locator, timeout: float = 15000) -> None:
        text = (locator.inner_text(timeout=timeout) or "") + " " + (
            locator.get_attribute("value") or ""
        )
        assert_click_allowed(text)
        locator.click(timeout=timeout)

    def _goto_product(self) -> None:
        self.page.goto(self.product_url, wait_until="domcontentloaded")

    # ------------------------------------------------------------ 主入口
    def run(self) -> FlowResult:
        try:
            self.run_lock.acquire()
        except AlreadyRunningError as exc:
            self.emit("error", str(exc))
            return FlowResult(False, str(exc))
        try:
            self.guard.ensure_can_submit(self.campaign)
            self._wait_for_sale_time()
            return self._run_browser()
        except DuplicateOrderError as exc:
            self.emit("error", str(exc))
            return FlowResult(False, str(exc))
        except StopRequested:
            self.emit("status", "已停止")
            return FlowResult(False, "用户已停止")
        finally:
            self.run_lock.release()

    def _wait_for_sale_time(self) -> None:
        sale = self.cfg.sale_datetime()
        if not sale:
            return
        start_at = sale.timestamp() - self.cfg.purchase.prewarm_seconds
        last = 0.0
        while time.time() < start_at:
            remaining = start_at - time.time()
            if time.monotonic() - last > 10:
                self.emit("status", f"等待开售：{int(remaining)} 秒后打开页面（开售 {sale:%m-%d %H:%M:%S}）")
                last = time.monotonic()
            self.ctl.sleep(min(1.0, remaining))
        self._notify(
            f"{self.campaign} 即将开售",
            f"程序已开始准备，开售时间 {sale:%H:%M:%S}。请保持手机在身边。",
            level="timeSensitive",
        )

    def _launch(self, pw):
        b = self.cfg.browser
        self.profile_dir.mkdir(parents=True, exist_ok=True)
        kwargs = dict(headless=b.headless, viewport=None if not b.headless else {"width": 1280, "height": 900})
        if b.channel != "chromium":
            try:
                return pw.chromium.launch_persistent_context(
                    str(self.profile_dir), channel=b.channel, **kwargs
                )
            except Exception as exc:
                self.emit("log", f"无法启动 {b.channel}（{exc.__class__.__name__}），改用 Playwright 自带 Chromium")
        return pw.chromium.launch_persistent_context(str(self.profile_dir), **kwargs)

    def _run_browser(self) -> FlowResult:
        from playwright.sync_api import sync_playwright

        result: FlowResult | None = None
        with sync_playwright() as pw:
            context = self._launch(pw)
            try:
                context.set_default_timeout(20000)
                self.page = context.pages[0] if context.pages else context.new_page()
                result = self._main_loop()
                if result.success and self.keep_browser_open:
                    self.emit("status", "已停在待付款页面。请你亲自付款；完成后点击“停止”关闭浏览器。")
                    while not self.ctl.stopped:
                        time.sleep(0.5)
                return result
            except StopRequested:
                if result is not None:
                    return result
                raise
            except ForbiddenActionError as exc:
                shot = self._screenshot("forbidden")
                self._notify("⚠️ 程序已停止", str(exc), level="critical")
                return FlowResult(False, str(exc), screenshot=shot)
            except FlowAbort as exc:
                return FlowResult(False, str(exc))
            except Exception as exc:
                log.exception("流程异常")
                shot = self._screenshot("error")
                msg = f"程序异常：{exc.__class__.__name__}: {str(exc)[:200]}"
                self._notify("❌ 抢购程序出错", msg + "\n请尽快人工查看。", level="critical")
                self.emit("error", msg)
                return FlowResult(False, msg, screenshot=shot)
            finally:
                try:
                    context.close()
                except Exception:
                    pass
                self.page = None

    # ------------------------------------------------------------ 状态机
    def _main_loop(self) -> FlowResult:
        deadline = time.monotonic() + self.cfg.purchase.max_run_minutes * 60
        self.emit("status", f"打开商品页：{self.product_url}")
        self._goto_product()
        while True:
            self.ctl.checkpoint()
            if time.monotonic() > deadline:
                msg = f"已运行 {self.cfg.purchase.max_run_minutes} 分钟仍未完成，程序停止。"
                self._notify("⏱ 抢购超时", msg, level="timeSensitive")
                return FlowResult(False, msg, screenshot=self._screenshot("timeout"))

            try:
                check = detect_security_check(self.page)
            except Exception:
                time.sleep(0.5)  # 页面正在跳转
                continue
            if check:
                self._handle_security(check)
                continue

            state = self._page_state()
            if state == "product-presale":
                self.emit("status", "尚未开售，稍后刷新…")
                self.ctl.sleep(self.poll)
                self.page.reload(wait_until="domcontentloaded")
            elif state == "product":
                self._on_product()
            elif state == "bag":
                self._on_bag()
            elif state == "checkout":
                self._on_checkout()
            elif state == "pending-payment":
                return self._on_pending_payment()
            elif state in ("payment", "paid"):
                raise FlowAbort("当前处于付款页面，程序不处理付款，已停止。")
            else:
                self._unknown_pages += 1
                self.emit("log", f"无法识别的页面：{self.page.url}")
                if self._unknown_pages >= 3:
                    self._pause_for_human(
                        SecurityCheck("unknown", f"多次遇到无法识别的页面：{self.page.url}")
                    )
                    self._unknown_pages = 0
                self.ctl.sleep(self.poll)
                self._goto_product()

    # ------------------------------------------------------------ 安全检查
    def _handle_security(self, check: SecurityCheck) -> None:
        if check.kind == "queue":
            self.emit("status", "网站正在排队，程序被动等待（不会绕过排队）…")
            self._notify(
                f"⏳ {self.campaign} 排队中",
                "官网/商店正在排队，程序正常等待，无需操作。",
                level="timeSensitive",
                once_key="queue",
            )
            self.ctl.sleep(self.poll)
            return
        self._pause_for_human(check)

    def _pause_for_human(self, check: SecurityCheck) -> None:
        shot = self._screenshot(check.kind)
        reason = f"需要人工处理：{check.title}（{check.detail}）"
        self.ctl.pause(reason)
        self.emit("paused", reason)
        self._notify(
            f"🛑 请人工处理：{check.title}",
            f"{check.detail}。\n程序已暂停，请在电脑浏览器中完成后点击“继续”"
            "（验证/登录完成后程序也会自动继续）。",
            level="critical",
        )
        log.info("暂停原因: %s 截图: %s", reason, shot)
        reloaded = False
        while True:
            if self.ctl.stopped:
                raise StopRequested()
            if check.kind == "unknown":
                # 非网站安全检查（如购物袋异常），只有用户点“继续”才恢复
                if not self.ctl.paused:
                    self.emit("resumed", "用户已确认，继续执行")
                    return
                time.sleep(0.5)
                continue
            try:
                still = detect_security_check(self.page)
            except Exception:
                still = check  # 页面跳转中，下一轮再判断
            if still is None:
                self.ctl.resume()
                self.emit("resumed", "安全检查已解除，继续执行")
                return
            if not self.ctl.paused:
                # 用户点了“继续”但检查仍在：按用户要求重新加载一次页面再判断
                if not reloaded:
                    reloaded = True
                    self.emit("log", "按用户要求重新加载页面")
                    try:
                        if still.kind == "blocked":
                            self._goto_product()
                        else:
                            self.page.reload(wait_until="domcontentloaded")
                    except Exception:
                        pass
                    continue
                reloaded = False
                self.emit("paused", f"仍检测到{still.title}，请先在浏览器中完成再点“继续”")
                self.ctl.pause(reason)
            time.sleep(1.5)

    # ------------------------------------------------------------ 商品页
    def _read_variants(self) -> list[Variant]:
        rows = self.page.eval_on_selector_all(
            "tr[data-variant]", "els => els.map(e => Object.assign({}, e.dataset))"
        )
        out = []
        for r in rows:
            stock = r.get("stock")
            out.append(
                Variant(
                    sku=r["sku"],
                    color=r["color"],
                    capacity=r["capacity"],
                    unit_price=int(r["price"]),
                    available=r.get("available") == "1",
                    stock=int(stock) if stock not in (None, "") else None,
                )
            )
        return out

    def _on_product(self) -> None:
        if self.page.locator("[data-error]").count():
            self.emit("log", "商店提示：" + self.page.locator("[data-error]").inner_text())
        variants = self._read_variants()
        sel = select_candidates(variants, self.prefs)
        cands = [c for c in sel.candidates if c.variant.sku not in self._failed_skus]
        if not cands:
            self._failed_skus.clear()
            reason = "；".join(f"{v.color}{v.capacity}:{why}" for v, why in sel.rejected[:6])
            self.emit("status", f"暂无符合条件的配置，{self.poll:.0f} 秒后重试。（{reason}）")
            self._notify(
                f"{self.campaign} 暂无符合条件的配置",
                "已开售，但没有满足颜色/容量/预算/库存条件的配置。程序继续等待补货。",
                level="active",
                once_key="nothing",
            )
            self.ctl.sleep(self.poll)
            self._goto_product()
            return
        cand = cands[0]
        self.emit("status", f"尝试加入购物袋：{cand.describe()}")
        self.ctl.checkpoint()
        v = cand.variant
        self.page.check(f'input[name="color"][value="{v.color}"]')
        self.page.check(f'input[name="capacity"][value="{v.capacity}"]')
        self.page.select_option("#qty", str(cand.quantity))
        with self.page.expect_navigation(wait_until="domcontentloaded"):
            self._safe_click(self.page.locator("#add-to-bag"))
        if self._page_state() == "bag":
            self.chosen = cand
            self.emit("selected", f"已选择：{cand.describe()}")
            return
        err = ""
        if self.page.locator("[data-error]").count():
            err = self.page.locator("[data-error]").inner_text()
        self.emit("log", f"加入购物袋失败（{err or '未知原因'}），尝试下一个配置")
        if "限购" in err:
            msg = f"商店提示：{err}。程序不会拆单或规避限购，已停止。"
            self._notify("⚠️ 触发限购", msg, level="timeSensitive")
            raise FlowAbort(msg)
        self._failed_skus.add(v.sku)

    # ------------------------------------------------------------ 购物袋
    def _on_bag(self) -> None:
        items = self.page.eval_on_selector_all(
            "[data-bag-item]", "els => els.map(e => [e.dataset.sku, Number(e.dataset.qty)])"
        )
        if not items:
            self.emit("log", "购物袋为空，返回商品页")
            self.chosen = None
            self._goto_product()
            return
        bag = {sku: qty for sku, qty in items}
        if self.chosen is None or bag != {self.chosen.variant.sku: self.chosen.quantity}:
            self._pause_for_human(
                SecurityCheck("unknown", f"购物袋内容与程序选择不一致：{bag}，请手动处理后点继续")
            )
            self.page.reload(wait_until="domcontentloaded")
            return
        total = int(self.page.get_attribute("[data-total]", "data-total"))
        if total > self.prefs.budget_total:
            self._abort_over_budget(total, "购物袋")
        self.emit("status", f"购物袋确认无误（¥{total:,}），进入结账")
        with self.page.expect_navigation(wait_until="domcontentloaded"):
            self._safe_click(self.page.locator("#checkout-btn"))

    def _abort_over_budget(self, total: int, where: str) -> None:
        msg = f"{where}显示总价 ¥{total:,}，超出预算 ¥{self.prefs.budget_total:,}，已停止且未提交订单。"
        self._screenshot("over_budget")
        self._notify("⚠️ 价格超出预算", msg, level="timeSensitive")
        self.emit("error", msg)
        raise FlowAbort(msg)

    # ------------------------------------------------------------ 结账
    def _on_checkout(self) -> None:
        if self._submitted:
            # 绝不重复提交
            msg = "订单提交后仍停留在结账页，结果不明。为防止重复下单，程序已停止，请人工检查订单。"
            self.guard.mark_unknown(self.campaign, msg)
            self._screenshot("checkout_unknown")
            self._notify("⚠️ 订单状态不明", msg, level="critical")
            raise FlowAbort(msg)
        if self.chosen is None:
            self.emit("log", "未经程序选择就进入了结账页，返回购物袋核对")
            self.page.goto(self.product_url.split("/shop/")[0] + "/shop/bag")
            return
        items = self.page.eval_on_selector_all(
            "[data-checkout-item]", "els => els.map(e => [e.dataset.sku, Number(e.dataset.qty)])"
        )
        if {s: q for s, q in items} != {self.chosen.variant.sku: self.chosen.quantity}:
            raise FlowAbort(f"结账页商品与选择不一致：{items}，已停止。")
        total = int(self.page.get_attribute("[data-total]", "data-total"))
        if total > self.prefs.budget_total:
            self._abort_over_budget(total, "结账页")
        self.page.check(f'input[name="payment"][value="{self.cfg.purchase.payment_method}"]')
        self.ctl.checkpoint()
        # 先落盘，再点击：即使程序在此刻崩溃，下次启动也会拒绝重复提交
        self.guard.mark_submitting(self.campaign, self.chosen.describe(), total)
        self._submitted = True
        self.emit("status", f"提交订单（不付款）：{self.chosen.describe()}")
        try:
            with self.page.expect_navigation(wait_until="domcontentloaded", timeout=30000):
                self._safe_click(self.page.locator("#place-order"))
        except Exception as exc:
            msg = f"提交订单后页面无响应（{exc.__class__.__name__}），结果不明。为防止重复下单，程序不会重试，请人工检查订单。"
            self.guard.mark_unknown(self.campaign, msg)
            self._screenshot("submit_unknown")
            self._notify("⚠️ 订单状态不明", msg, level="critical")
            raise FlowAbort(msg) from exc
        if self._page_state() == "checkout" and self.page.locator("[data-error]").count():
            err = self.page.locator("[data-error]").inner_text()
            msg = f"商店拒绝了订单：{err}。订单未生成，程序已停止。"
            self.guard.mark_rejected(self.campaign, msg)
            self._screenshot("submit_rejected")
            self._notify("❌ 下单失败", msg, level="timeSensitive")
            raise FlowAbort(msg)

    # ------------------------------------------------------------ 待付款
    def _on_pending_payment(self) -> FlowResult:
        order_id = self.page.get_attribute("#order-id", "data-order-id") or ""
        total = int(self.page.get_attribute("[data-order-total]", "data-order-total") or 0)
        deadline = ""
        if self.page.locator("#pay-deadline").count():
            deadline = self.page.locator("#pay-deadline").inner_text()
        config = self.chosen.label() if self.chosen else "（未知配置）"
        unit = f"¥{self.chosen.variant.unit_price:,}" if self.chosen else "未知"
        self.guard.mark_pending_payment(self.campaign, order_id, config=config, total=total)
        shot = self._screenshot("pending_payment")
        body = (
            f"配置：{config}\n单价：{unit}\n总价：¥{total:,}\n订单号：{order_id}\n"
            f"进度：订单已提交，等待你本人付款。{deadline}\n程序不会自动付款。"
        )
        self._notify(f"✅ {self.campaign} 已下单，待付款", body, level="critical")
        self.emit("success", body.replace("\n", "；"))
        return FlowResult(True, body, order_id=order_id, config=config, total=total, screenshot=shot)
