"""半自动抢购：有货时用你自己登录的 Edge 浏览器自动加购、走结账流程，停在「立即下单」之前，由你本人下单付款。

设计原则：
- 用独立的浏览器资料夹（你登录一次 Apple ID 后会记住），浏览器窗口可见，不做任何伪装。
- 每次开启只自动处理一单；任何一步卡住都立刻停下，把浏览器留在当前页面让你手动接着操作。
- 绝不点击「下单 / 支付」类按钮——最后一步永远由你本人完成。
"""

import os
import queue
import re
import threading
import time
import urllib.parse

BASE = "https://www.apple.com.cn"
SIGNIN_URL = BASE + "/shop/signIn"
BAG_URL = BASE + "/shop/bag"
# 最终下单/支付按钮（只匹配真正提交订单的按钮，不误伤「继续选择付款方式」这类按钮）
FINAL_BUTTON_RE = re.compile(r"下单|提交订单|^\s*(立即|去|确认)?(支付|付款)\s*$|Place Order|Pay Now", re.I)
CONTINUE_SELECTORS = [
    "#rs-checkout-continue-button-bottom",
    "[data-autom=continue-button-label]",
    "button[data-autom*=continue]",
    "button:has-text('继续')",
]


class StepFailed(Exception):
    pass


class AutoBuyer:
    """独占一个线程运行 Playwright（Playwright 同步 API 必须在同一线程里使用）。

    emit(event, payload)：
      log       str
      need_user dict(title, body)   需要你接手（登录 / 某一步卡住 / 已到下单前一步）
    """

    def __init__(self, profile_dir, emit, headless=False, channels=("msedge", "chrome"), executable_path=None):
        self.profile_dir = profile_dir
        self.headless = headless
        self.channels = channels
        self.executable_path = executable_path
        self.emit = emit
        self.jobs = queue.Queue()
        self.thread = None
        self._pw = None
        self._ctx = None
        self.busy = False

    # ---------- 对外接口（任意线程调用） ----------
    def open_for_login(self):
        self._submit(("login",))

    def buy(self, part, store_numbers, store_names):
        if self.busy:
            self.emit("log", "自动加购正在进行中，忽略新的触发")
            return
        self.busy = True
        self._submit(("buy", part, list(store_numbers), list(store_names)))

    # ---------- 内部 ----------
    def _submit(self, job):
        if not self.thread or not self.thread.is_alive():
            self.thread = threading.Thread(target=self._run, daemon=True)
            self.thread.start()
        self.jobs.put(job)

    def _run(self):
        while True:
            job = self.jobs.get()
            try:
                if job[0] == "login":
                    page = self._page()
                    page.goto(SIGNIN_URL, wait_until="domcontentloaded", timeout=60000)
                    page.bring_to_front()
                    self.emit("log", "已打开抢购浏览器，请在里面登录 Apple ID（勾选保持登录），之后可以不关这个窗口")
                elif job[0] == "buy":
                    self._buy(*job[1:])
            except Exception as e:  # noqa: BLE001  任何异常都交给用户接手
                self.emit("log", f"抢购浏览器出错：{e.__class__.__name__}: {str(e)[:200]}")
                self.emit("need_user", {"title": "自动加购出错，请手动操作",
                                        "body": "浏览器已停在当前页面，请接着手动完成。"})
            finally:
                if job[0] == "buy":
                    self.busy = False

    def _page(self):
        """启动（或复用）可见的 Edge 浏览器，返回一个标签页。"""
        if self._ctx is not None:
            try:
                pages = self._ctx.pages
                return pages[0] if pages else self._ctx.new_page()
            except Exception:  # 浏览器被用户关掉了
                self._ctx = None
        if self._pw is None:
            from playwright.sync_api import sync_playwright
            self._pw = sync_playwright().start()
        os.makedirs(self.profile_dir, exist_ok=True)
        last_err = None
        for channel in self.channels:
            try:
                self._ctx = self._pw.chromium.launch_persistent_context(
                    self.profile_dir, channel=channel, executable_path=self.executable_path,
                    headless=self.headless, no_viewport=True, locale="zh-CN", args=["--start-maximized"])
                break
            except Exception as e:  # noqa: BLE001
                last_err = e
        if self._ctx is None:
            raise RuntimeError(f"无法启动 Edge/Chrome 浏览器：{last_err}")
        self._ctx.set_default_timeout(15000)
        pages = self._ctx.pages
        return pages[0] if pages else self._ctx.new_page()

    def _log(self, s):
        self.emit("log", "[自动加购] " + s)

    # ---------- 抢购流程 ----------
    def _buy(self, part, store_numbers, store_names):
        page = self._page()
        page.bring_to_front()
        steps = [
            ("打开商品页并加入购物袋", lambda: self._add_to_bag(page, part)),
            ("进入购物袋并结账", lambda: self._checkout(page)),
            ("结账流程（取货门店 / 联系人 / 支付方式）", lambda: self._walk_checkout(page, store_numbers, store_names)),
        ]
        t0 = time.time()
        for name, fn in steps:
            self._log(f"开始：{name}")
            try:
                fn()
            except StepFailed as e:
                self._log(f"卡在「{name}」：{e}")
                self.emit("need_user", {
                    "title": "有货！自动流程卡住了，请马上手动接手",
                    "body": f"卡在：{name}（{e}）\n浏览器已停在当前页面，请接着手动操作并付款。"})
                return
        self._log(f"已到达下单前一步，用时 {time.time() - t0:.0f} 秒")
        self.emit("need_user", {
            "title": "已帮你走到最后一步！请立即下单付款",
            "body": "请在浏览器里核对门店、取货时间和金额，然后点击「立即下单」并用支付宝付款。"})

    def _add_to_bag(self, page, part):
        page.goto(f"{BASE}/shop/product/{urllib.parse.quote(part)}", wait_until="domcontentloaded", timeout=45000)
        try:
            page.wait_for_selector("[data-autom=add-to-cart]", state="attached", timeout=20000)
        except Exception as e:
            raise StepFailed("商品页没有加载出「添加到购物袋」按钮") from e
        # 不折抵旧机、不要 AppleCare+（有这些选项时才选）
        for sel in ("[data-autom=choose-noTradeIn]", "[data-autom=noapplecare]"):
            loc = page.locator(sel)
            try:
                loc.first.wait_for(state="attached", timeout=8000)
                loc.first.check(force=True)
                page.wait_for_timeout(800)
            except Exception:
                pass
        btn = page.locator("[data-autom=add-to-cart]").first
        for _ in range(20):
            if btn.is_enabled():
                break
            page.wait_for_timeout(500)
        else:
            raise StepFailed("「添加到购物袋」按钮一直不可点（可能已无货或需要选择其他选项）")
        btn.click()
        try:
            page.wait_for_url(re.compile(r"/shop/(bag|buy-.*attach|.*step=attach)"), timeout=20000)
        except Exception:
            pass  # 有的商品加购后停在原页面，下一步直接去购物袋检查
        self._log("已点击「添加到购物袋」")

    def _checkout(self, page):
        if "/shop/bag" not in page.url:
            page.goto(BAG_URL, wait_until="domcontentloaded", timeout=45000)
        page.wait_for_timeout(1500)
        if page.locator("[data-autom=bag-empty-signin-button], [data-autom=bag-empty-continueshopping-button]").count():
            raise StepFailed("购物袋是空的，加购可能没有成功（被风控或已无货）")
        btn = page.locator("[data-autom=checkout], button#shoppingCart\\.actions\\.checkout, button:has-text('结账')").first
        try:
            btn.wait_for(state="visible", timeout=15000)
        except Exception as e:
            raise StepFailed("购物袋里找不到「结账」按钮") from e
        btn.click()
        page.wait_for_url(re.compile(r"/shop/(checkout|signIn)"), timeout=30000)
        if "/signIn" in page.url:
            self._log("需要登录，等待你在浏览器里登录（最多 3 分钟）")
            self.emit("need_user", {"title": "有货！请在浏览器里登录 Apple ID",
                                    "body": "登录后程序会自动继续结账流程。"})
            try:
                page.wait_for_url(re.compile(r"/shop/checkout"), timeout=180000)
            except Exception as e:
                raise StepFailed("3 分钟内没有完成登录") from e

    @staticmethod
    def _stage(page):
        q = urllib.parse.parse_qs(urllib.parse.urlparse(page.url).query)
        return (q.get("_s") or [""])[0]

    def _walk_checkout(self, page, store_numbers, store_names):
        last_stage, repeats = None, 0
        deadline = time.time() + 150
        while time.time() < deadline:
            page.wait_for_load_state("domcontentloaded")
            page.wait_for_timeout(1500)
            stage = self._stage(page)
            if stage.startswith("Review") or self._visible_text(page, "立即下单"):
                return
            if stage == last_stage:
                repeats += 1
                if repeats >= 3:
                    raise StepFailed(f"在「{stage or page.url}」这一步点「继续」没有反应，可能有必填项需要你确认")
            else:
                last_stage, repeats = stage, 0
                self._log(f"结账步骤：{stage or '未知'}")
            if stage.startswith("Fulfillment"):
                self._choose_pickup(page, store_numbers, store_names)
            elif stage.startswith("Billing"):
                self._choose_alipay(page)
            if self._click_continue(page) == "final":
                return
        raise StepFailed("结账流程超时")

    def _visible_text(self, page, text):
        try:
            return page.get_by_role("button", name=re.compile(text)).first.is_visible()
        except Exception:
            return False

    def _choose_pickup(self, page, store_numbers, store_names):
        # 选择「我要取货」
        for loc in (page.get_by_role("button", name=re.compile("我要取货|取货")),
                    page.get_by_text("我要取货"),
                    page.locator("[data-autom*=pickup]")):
            try:
                if loc.first.is_visible():
                    loc.first.click()
                    page.wait_for_timeout(2000)
                    break
            except Exception:
                pass
        # 选择有货的门店：优先门店编号，其次门店名
        chosen = False
        for num in store_numbers:
            loc = page.locator(f"input[type=radio][value='{num}']")
            try:
                if loc.count() and loc.first.is_enabled():
                    loc.first.check(force=True)
                    chosen = True
                    self._log(f"已选择门店 {num}")
                    break
            except Exception:
                pass
        if not chosen:
            for name in store_names:
                try:
                    loc = page.get_by_text(name, exact=False)
                    if loc.count():
                        loc.first.click()
                        chosen = True
                        self._log(f"已选择门店 {name}")
                        break
                except Exception:
                    pass
        page.wait_for_timeout(1500)
        # 选择第一个可用的取货时间
        try:
            sel = page.locator("select:visible").first
            if sel.count():
                opts = sel.locator("option").all()
                values = [o.get_attribute("value") for o in opts if o.get_attribute("value")]
                if values:
                    sel.select_option(values[0])
                    self._log("已选择最早的取货时间")
        except Exception:
            pass
        try:
            slot = page.locator("input[type=radio][name*=timeSlot], input[type=radio][name*=TimeSlot]").first
            if slot.count():
                slot.check(force=True)
        except Exception:
            pass

    def _choose_alipay(self, page):
        for loc in (page.locator("input[type=radio][value*=alipay i]"),
                    page.get_by_label(re.compile("^支付宝$")),
                    page.get_by_text("支付宝", exact=True)):
            try:
                if loc.count():
                    loc.first.check(force=True) if loc.first.evaluate("e=>e.tagName") == "INPUT" else loc.first.click()
                    self._log("已选择支付宝")
                    page.wait_for_timeout(1000)
                    return
            except Exception:
                pass
        self._log("没找到「支付宝」选项，保持默认支付方式")

    def _click_continue(self, page):
        for sel in CONTINUE_SELECTORS:
            loc = page.locator(sel)
            try:
                if not loc.count() or not loc.first.is_visible():
                    continue
                text = loc.first.inner_text().strip()
            except Exception:
                continue
            if FINAL_BUTTON_RE.search(text):
                return "final"  # 到了下单按钮：不点，交给用户
            loc.first.click()
            try:
                page.wait_for_load_state("domcontentloaded", timeout=15000)
            except Exception:
                pass
            return "clicked"
        raise StepFailed("找不到「继续」按钮")
