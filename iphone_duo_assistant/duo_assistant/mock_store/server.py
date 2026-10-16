"""本地模拟 Apple Store。

只依赖标准库，可在 GUI 内以线程方式运行，也可命令行独立运行：
    python run_mock_store.py --port 8765 --preset normal

页面结构（data-page、data-* 属性）是为了让自动化脚本稳定定位而设计的，
与真实 apple.com.cn 的页面无关。它模拟了：开售倒计时、库存、限购、购物袋、
登录、验证码、排队、访问频率限制、结算时价格变化、待付款订单页。
另外内置一个假的 Bark 服务（/__bark/push），供测试和演示时接收推送。
"""

from __future__ import annotations

import html
import json
import secrets
import threading
import time
import uuid
from dataclasses import asdict, dataclass, field
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, quote, urlparse

from ..catalog import DEFAULT_CAPACITIES, DEFAULT_COLORS, MOCK_PRICES, make_sku

PRODUCT_PATH = "/shop/buy-iphone/iphone-duo"


@dataclass
class StoreSettings:
    sale_delay_sec: float = 0.0
    default_stock: int = 20
    stock_overrides: dict[str, int] = field(default_factory=dict)
    require_login: bool = False
    captcha_at_checkout: bool = False
    queue_seconds: float = 0.0
    rate_limit_per_10s: int = 0  # 0 表示不限
    block_seconds: float = 15.0
    purchase_limit: int = 2
    race_fail_first_add: bool = False  # 模拟“看到有货、加入购物袋时已售罄”
    checkout_surcharge: int = 0  # 模拟结算页价格与商品页不一致
    payment_window_min: int = 30


PRESETS: dict[str, tuple[str, dict]] = {
    "normal": ("正常开售：全部有货，无需登录", {}),
    "presale": ("20 秒后开售（测试开售前等待）", {"sale_delay_sec": 20}),
    "login": ("需要登录（测试暂停并人工登录）", {"require_login": True}),
    "captcha": ("结算时出现验证码（测试暂停并人工验证）", {"captcha_at_checkout": True}),
    "queue": ("开售时排队 15 秒（测试被动等待）", {"queue_seconds": 15}),
    "partial_soldout": (
        "256GB 全部售罄，其他有货（测试自动换配置）",
        {"stock_overrides": {make_sku(c, "256GB"): 0 for c in DEFAULT_COLORS}},
    ),
    "race": ("加购瞬间售罄一次（测试自动尝试下一个配置）", {"race_fail_first_add": True}),
    "all_soldout": ("全部无货（测试持续等待补货）", {"default_stock": 0}),
    "price_change": ("结算页价格上涨 3000 元（测试预算保护）", {"checkout_surcharge": 3000}),
    "ratelimit": ("10 秒内超过 6 次请求即拦截（测试异常访问暂停）", {"rate_limit_per_10s": 6}),
}


@dataclass
class Session:
    sid: str
    logged_in: bool = False
    account: str = ""
    bag: dict[str, int] = field(default_factory=dict)
    captcha_passed: bool = False
    captcha_answer: int = 0
    hits: list[float] = field(default_factory=list)
    blocked_until: float = 0.0
    checkout_token: str = ""


@dataclass
class Order:
    order_id: str
    sid: str
    items: dict[str, int]
    total: int
    payment: str
    status: str  # pending_payment / paid
    created: float
    token: str


class StoreState:
    def __init__(self):
        self.lock = threading.RLock()
        self.settings = StoreSettings()
        self.preset = "normal"
        self.opened_at = time.time()
        self.stock: dict[str, int] = {}
        self.sessions: dict[str, Session] = {}
        self.orders: list[Order] = []
        self.bark_messages: list[dict] = []
        self.race_used: set[str] = set()
        self.variants = [
            (make_sku(c, p), c, p, MOCK_PRICES[p]) for c in DEFAULT_COLORS for p in DEFAULT_CAPACITIES
        ]
        self.apply_preset("normal")

    def apply_preset(self, name: str, **overrides) -> None:
        if name not in PRESETS:
            raise KeyError(name)
        with self.lock:
            s = StoreSettings(**PRESETS[name][1])
            for k, v in overrides.items():
                setattr(s, k, v)
            self.settings = s
            self.preset = name
            self.opened_at = time.time()
            self.stock = {
                sku: s.stock_overrides.get(sku, s.default_stock) for sku, *_ in self.variants
            }
            self.sessions.clear()
            self.orders.clear()
            self.race_used.clear()

    def sale_open(self) -> bool:
        return time.time() >= self.opened_at + self.settings.sale_delay_sec

    def seconds_to_sale(self) -> float:
        return max(0.0, self.opened_at + self.settings.sale_delay_sec - time.time())

    def in_queue(self) -> bool:
        return time.time() < self.opened_at + self.settings.sale_delay_sec + self.settings.queue_seconds

    def variant(self, sku: str):
        for v in self.variants:
            if v[0] == sku:
                return v
        return None

    def sku_for(self, color: str, capacity: str) -> str | None:
        for sku, c, p, _ in self.variants:
            if c == color and p == capacity:
                return sku
        return None

    def bag_total(self, bag: dict[str, int]) -> int:
        return sum(self.variant(sku)[3] * q for sku, q in bag.items())

    def purchased_qty(self, sid: str) -> int:
        return sum(sum(o.items.values()) for o in self.orders if o.sid == sid)

    def snapshot(self) -> dict:
        with self.lock:
            return {
                "preset": self.preset,
                "settings": asdict(self.settings),
                "sale_open": self.sale_open(),
                "seconds_to_sale": round(self.seconds_to_sale(), 1),
                "stock": dict(self.stock),
                "orders": [asdict(o) for o in self.orders],
                "sessions": len(self.sessions),
            }


# ---------------------------------------------------------------- HTML ----

_CSS = """
body{font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;margin:0;background:#f5f5f7;color:#1d1d1f}
header{background:#1d1d1f;color:#fff;padding:10px 24px;font-size:14px}
header a{color:#fff;margin-right:16px;text-decoration:none}
main{max-width:860px;margin:24px auto;background:#fff;border-radius:18px;padding:24px 32px}
.banner{background:#fff3cd;border:1px solid #e0c36b;padding:8px 12px;border-radius:8px;font-size:13px}
table{border-collapse:collapse;width:100%;margin:12px 0}td,th{border-bottom:1px solid #ddd;padding:6px;text-align:left}
.error{color:#b00020;font-weight:bold}.ok{color:#1a7f37}
button,.btn{background:#0071e3;color:#fff;border:0;border-radius:980px;padding:10px 22px;font-size:15px;cursor:pointer;text-decoration:none;display:inline-block}
button.secondary{background:#86868b}fieldset{border:0;margin:10px 0;padding:0}label{margin-right:14px}
"""


def page(title: str, data_page: str, body: str, extra_head: str = "") -> str:
    return f"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<title>{html.escape(title)} - 模拟 Apple Store</title><style>{_CSS}</style>{extra_head}</head>
<body data-page="{data_page}">
<header><a href="{PRODUCT_PATH}">模拟 Apple Store（本地测试用，非官网）</a><a href="/shop/bag">购物袋</a><a href="/__admin">管理</a></header>
<main><p class="banner">这是本机运行的模拟商店，用于测试抢购辅助程序。所有商品、价格与订单均为虚构。</p>
{body}</main></body></html>"""


def esc(s) -> str:
    return html.escape(str(s), quote=True)


def yuan(n: int) -> str:
    return f"¥{n:,}"


class Handler(BaseHTTPRequestHandler):
    store: StoreState  # 由 make_handler 注入
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # 安静模式
        pass

    # ---- 基础工具
    def _session(self) -> tuple[Session, bool]:
        cookie = SimpleCookie(self.headers.get("Cookie", ""))
        sid = cookie["mock_sid"].value if "mock_sid" in cookie else ""
        with self.store.lock:
            if sid and sid in self.store.sessions:
                return self.store.sessions[sid], False
            sess = Session(sid=uuid.uuid4().hex)
            self.store.sessions[sess.sid] = sess
            return sess, True

    def _send(self, status: int, body: str | bytes, ctype="text/html; charset=utf-8", headers=None):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        if self._new_sid:
            self.send_header("Set-Cookie", f"mock_sid={self._sess.sid}; Path=/; HttpOnly")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(data)

    def _redirect(self, location: str):
        self._send(303, "", headers={"Location": location})

    def _json(self, obj, status=200):
        self._send(status, json.dumps(obj, ensure_ascii=False), "application/json; charset=utf-8")

    def _form(self) -> dict[str, str]:
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length).decode("utf-8") if length else ""
        if "json" in (self.headers.get("Content-Type") or ""):
            return json.loads(raw or "{}")
        return {k: v[0] for k, v in parse_qs(raw).items()}

    def _rate_limited(self) -> bool:
        s = self.store.settings
        if not s.rate_limit_per_10s:
            return False
        now = time.time()
        sess = self._sess
        if now < sess.blocked_until:
            return True
        sess.hits = [t for t in sess.hits if now - t < 10] + [now]
        if len(sess.hits) > s.rate_limit_per_10s:
            sess.blocked_until = now + s.block_seconds
            sess.hits.clear()
            return True
        return False

    # ---- 分发
    def do_GET(self):
        self._dispatch("GET")

    def do_POST(self):
        self._dispatch("POST")

    def _dispatch(self, method: str):
        url = urlparse(self.path)
        path = url.path
        self.query = {k: v[0] for k, v in parse_qs(url.query).items()}
        if path.startswith("/__bark/"):
            self._sess, self._new_sid = Session(sid=""), False
            return self._bark(method, path)
        self._sess, self._new_sid = self._session()
        if path.startswith("/__admin"):
            return self._admin(method, path)
        with self.store.lock:
            if self._rate_limited():
                return self._send(
                    429,
                    page(
                        "访问受限",
                        "blocked",
                        '<h2 data-security-check="rate-limit">访问过于频繁</h2>'
                        "<p>系统检测到异常访问，请稍后再试。</p>",
                    ),
                )
            routes = {
                ("GET", "/"): lambda: self._redirect(PRODUCT_PATH),
                ("GET", PRODUCT_PATH): self._product,
                ("POST", "/shop/bag/add"): self._bag_add,
                ("GET", "/shop/bag"): self._bag,
                ("POST", "/shop/bag/clear"): self._bag_clear,
                ("POST", "/shop/bag/checkout"): self._bag_checkout,
                ("GET", "/shop/signin"): self._signin,
                ("POST", "/shop/signin"): self._signin_post,
                ("GET", "/shop/verify"): self._verify,
                ("POST", "/shop/verify"): self._verify_post,
                ("GET", "/shop/checkout"): self._checkout,
                ("POST", "/shop/checkout/place"): self._place_order,
            }
            fn = routes.get((method, path))
            if fn:
                return fn()
            if path.startswith("/shop/order/"):
                return self._order(method, path)
            self._send(404, page("未找到", "error", "<h2>页面不存在</h2>"))

    # ---- 商品页
    def _product(self, error: str = ""):
        st = self.store
        if not st.sale_open():
            secs = int(st.seconds_to_sale())
            body = (
                f"<h1>iPhone Duo</h1><h2 id='presale' data-seconds='{secs}'>即将开售</h2>"
                f"<p>距离开售还有约 {secs} 秒。请稍后刷新页面。</p>"
            )
            return self._send(200, page("即将开售", "product-presale", body))
        if st.in_queue():
            body = (
                "<h1>iPhone Duo</h1><h2 id='queue'>当前访问人数较多，正在排队</h2>"
                "<p>请勿关闭页面，轮到你时页面会自动进入购买页。</p>"
            )
            head = '<meta http-equiv="refresh" content="3">'
            return self._send(200, page("排队中", "queue", body, head))
        rows = []
        for sku, c, p, price in st.variants:
            n = st.stock[sku]
            status = "无货" if n <= 0 else ("库存紧张" if n < 5 else "有货")
            rows.append(
                f'<tr data-variant data-sku="{esc(sku)}" data-color="{esc(c)}" '
                f'data-capacity="{esc(p)}" data-price="{price}" data-available="{1 if n > 0 else 0}" '
                f'data-stock="{n}"><td>{esc(c)}</td><td>{esc(p)}</td><td>{yuan(price)}</td>'
                f"<td>{status}</td></tr>"
            )
        colors = "".join(
            f'<label><input type="radio" name="color" value="{esc(c)}"> {esc(c)}</label>'
            for c in DEFAULT_COLORS
        )
        caps = "".join(
            f'<label><input type="radio" name="capacity" value="{esc(p)}"> {esc(p)}</label>'
            for p in DEFAULT_CAPACITIES
        )
        qty = "".join(
            f'<option value="{i}">{i}</option>' for i in range(1, st.settings.purchase_limit + 1)
        )
        err = f'<p class="error" data-error>{esc(error)}</p>' if error else ""
        body = f"""<h1>购买 iPhone Duo</h1>{err}
<table id="availability"><tr><th>颜色</th><th>容量</th><th>价格</th><th>状态</th></tr>{''.join(rows)}</table>
<form id="buy-form" method="post" action="/shop/bag/add">
<fieldset><legend>颜色</legend>{colors}</fieldset>
<fieldset><legend>容量</legend>{caps}</fieldset>
<fieldset><legend>数量（每人限购 {st.settings.purchase_limit} 台）</legend><select name="qty" id="qty">{qty}</select></fieldset>
<button id="add-to-bag" type="submit">添加到购物袋</button></form>"""
        self._send(200, page("购买 iPhone Duo", "product", body))

    def _bag_add(self):
        st = self.store
        f = self._form()
        if not st.sale_open() or st.in_queue():
            return self._redirect(PRODUCT_PATH)
        sku = st.sku_for(f.get("color", ""), f.get("capacity", ""))
        try:
            qty = int(f.get("qty", "1"))
        except ValueError:
            qty = 0
        if not sku:
            return self._product("请选择颜色和容量")
        if qty < 1:
            return self._product("数量无效")
        in_bag = sum(self._sess.bag.values())
        if in_bag + qty + st.purchased_qty(self._sess.sid) > st.settings.purchase_limit:
            return self._product(f"超出限购数量（每人限购 {st.settings.purchase_limit} 台）")
        if st.settings.race_fail_first_add and not st.race_used:
            st.race_used.add(sku)
            st.stock[sku] = 0
            return self._product("很抱歉，所选配置刚刚售罄")
        if st.stock[sku] < qty:
            return self._product("很抱歉，所选配置库存不足")
        self._sess.bag[sku] = self._sess.bag.get(sku, 0) + qty
        self._redirect("/shop/bag")

    # ---- 购物袋
    def _bag(self):
        st = self.store
        bag = self._sess.bag
        items = "".join(
            f'<li data-bag-item data-sku="{esc(sku)}" data-qty="{q}">'
            f"iPhone Duo {esc(st.variant(sku)[1])} {esc(st.variant(sku)[2])} × {q} — "
            f"{yuan(st.variant(sku)[3] * q)}</li>"
            for sku, q in bag.items()
        )
        if not bag:
            body = "<h1>购物袋</h1><p id='bag-empty'>你的购物袋是空的。</p>"
        else:
            body = f"""<h1>购物袋</h1><ul id="bag-items">{items}</ul>
<p>合计：<strong data-total="{st.bag_total(bag)}">{yuan(st.bag_total(bag))}</strong></p>
<form method="post" action="/shop/bag/checkout"><button id="checkout-btn" type="submit">结账</button></form>
<form method="post" action="/shop/bag/clear"><button id="clear-bag" class="secondary" type="submit">清空购物袋</button></form>"""
        self._send(200, page("购物袋", "bag", body))

    def _bag_clear(self):
        self._sess.bag.clear()
        self._redirect("/shop/bag")

    def _bag_checkout(self):
        if not self._sess.bag:
            return self._redirect("/shop/bag")
        if self.store.settings.require_login and not self._sess.logged_in:
            return self._redirect("/shop/signin?next=" + quote("/shop/checkout"))
        self._redirect("/shop/checkout")

    # ---- 登录（模拟账号：任意非空账号 + 密码 "test"）
    def _signin(self, error: str = ""):
        if self._sess.logged_in and not error:
            nxt = self.query.get("next", "/shop/checkout")
            return self._redirect(nxt if nxt.startswith("/") else "/shop/checkout")
        nxt = esc(self.query.get("next", "/shop/checkout"))
        err = f'<p class="error">{esc(error)}</p>' if error else ""
        body = f"""<h1>登录 Apple 账户（模拟）</h1>{err}
<p>模拟账号：任意邮箱，密码 <code>test</code>。抢购程序不会代填密码，请人工登录。</p>
<form id="signin-form" method="post" action="/shop/signin?next={nxt}">
<p><input name="account" id="account" placeholder="Apple 账户"></p>
<p><input name="password" id="password" type="password" placeholder="密码"></p>
<button id="signin-btn" type="submit">登录</button></form>"""
        self._send(200, page("登录", "signin", body))

    def _signin_post(self):
        f = self._form()
        if not f.get("account") or f.get("password") != "test":
            return self._signin("账号或密码错误")
        self._sess.logged_in = True
        self._sess.account = f["account"]
        nxt = self.query.get("next", "/shop/checkout")
        self._redirect(nxt if nxt.startswith("/") else "/shop/checkout")

    # ---- 验证码（只能人工完成）
    def _verify(self, error: str = ""):
        if self._sess.captcha_passed and not error:
            return self._redirect("/shop/checkout")
        a, b = secrets.randbelow(9) + 1, secrets.randbelow(9) + 1
        self._sess.captcha_answer = a + b
        err = f'<p class="error">{esc(error)}</p>' if error else ""
        body = f"""<h1 data-security-check="captcha">安全验证</h1>{err}
<p>为保障账户安全，请完成验证：<strong id="question">{a} + {b} = ?</strong></p>
<form method="post" action="/shop/verify"><input name="answer" id="captcha-answer">
<button id="verify-btn" type="submit">验证</button></form>"""
        self._send(200, page("安全验证", "captcha", body))

    def _verify_post(self):
        f = self._form()
        if f.get("answer", "").strip() == str(self._sess.captcha_answer):
            self._sess.captcha_passed = True
            return self._redirect("/shop/checkout")
        self._verify("验证失败，请重试")

    # ---- 结算
    def _checkout(self, error: str = ""):
        st = self.store
        sess = self._sess
        if not sess.bag:
            return self._redirect("/shop/bag")
        if st.settings.require_login and not sess.logged_in:
            return self._redirect("/shop/signin?next=" + quote("/shop/checkout"))
        if st.settings.captcha_at_checkout and not sess.captcha_passed:
            return self._redirect("/shop/verify")
        total = st.bag_total(sess.bag) + st.settings.checkout_surcharge
        sess.checkout_token = uuid.uuid4().hex
        items = "".join(
            f'<li data-checkout-item data-sku="{esc(sku)}" data-qty="{q}">'
            f"iPhone Duo {esc(st.variant(sku)[1])} {esc(st.variant(sku)[2])} × {q}</li>"
            for sku, q in sess.bag.items()
        )
        err = f'<p class="error" data-error>{esc(error)}</p>' if error else ""
        body = f"""<h1>结账</h1>{err}<ul>{items}</ul>
<p>送货地址：{esc(sess.account or '模拟用户')}，上海市（模拟地址）</p>
<form id="place-form" method="post" action="/shop/checkout/place">
<input type="hidden" name="token" value="{sess.checkout_token}">
<fieldset><legend>付款方式（提交订单后再付款）</legend>
<label><input type="radio" name="payment" value="alipay"> 支付宝</label>
<label><input type="radio" name="payment" value="wechat"> 微信支付</label>
<label><input type="radio" name="payment" value="unionpay"> 银联</label></fieldset>
<p>应付总额：<strong data-total="{total}">{yuan(total)}</strong></p>
<button id="place-order" type="submit">提交订单</button></form>"""
        self._send(200, page("结账", "checkout", body))

    def _place_order(self):
        st = self.store
        sess = self._sess
        f = self._form()
        token = f.get("token", "")
        # 服务器端幂等：同一 token 只会生成一个订单
        for o in st.orders:
            if o.token and o.token == token:
                return self._redirect(f"/shop/order/{o.order_id}")
        if not sess.bag or token != sess.checkout_token:
            return self._redirect("/shop/bag")
        if f.get("payment") not in ("alipay", "wechat", "unionpay"):
            return self._checkout("请选择付款方式")
        if sum(sess.bag.values()) + st.purchased_qty(sess.sid) > st.settings.purchase_limit:
            return self._checkout("超出限购数量")
        for sku, q in sess.bag.items():
            if st.stock[sku] < q:
                return self._checkout("很抱歉，部分商品库存不足")
        for sku, q in sess.bag.items():
            st.stock[sku] -= q
        order = Order(
            order_id="W" + str(int(time.time() * 1000))[-9:],
            sid=sess.sid,
            items=dict(sess.bag),
            total=st.bag_total(sess.bag) + st.settings.checkout_surcharge,
            payment=f["payment"],
            status="pending_payment",
            created=time.time(),
            token=token,
        )
        st.orders.append(order)
        sess.bag.clear()
        sess.checkout_token = ""
        self._redirect(f"/shop/order/{order.order_id}")

    # ---- 订单页
    def _order(self, method: str, path: str):
        st = self.store
        parts = path.strip("/").split("/")  # shop/order/<id>[/pay]
        oid = parts[2] if len(parts) > 2 else ""
        order = next((o for o in st.orders if o.order_id == oid and o.sid == self._sess.sid), None)
        if not order:
            return self._send(404, page("未找到", "error", "<h2>订单不存在</h2>"))
        if len(parts) == 4 and parts[3] == "pay":
            if method == "POST":
                order.status = "paid"
                return self._redirect(f"/shop/order/{oid}")
            body = f"""<h1>模拟付款</h1><p>订单 {esc(oid)}，金额 {yuan(order.total)}。</p>
<form method="post"><button id="confirm-pay" type="submit">确认付款（模拟，需人工点击）</button></form>"""
            return self._send(200, page("付款", "payment", body))
        items = "".join(
            f'<li data-order-item data-sku="{esc(sku)}" data-qty="{q}">'
            f"iPhone Duo {esc(st.variant(sku)[1])} {esc(st.variant(sku)[2])} × {q}</li>"
            for sku, q in order.items.items()
        )
        if order.status == "paid":
            body = f"<h1 class='ok'>付款成功</h1><p id='order-id' data-order-id='{esc(oid)}'>订单号 {esc(oid)}</p>"
            return self._send(200, page("订单", "paid", body))
        deadline = time.strftime(
            "%H:%M", time.localtime(order.created + st.settings.payment_window_min * 60)
        )
        body = f"""<h1>订单已提交，等待付款</h1>
<p id="order-id" data-order-id="{esc(oid)}">订单号：{esc(oid)}</p><ul>{items}</ul>
<p>应付金额：<strong data-order-total="{order.total}">{yuan(order.total)}</strong></p>
<p id="pay-deadline">请在 {deadline} 前完成付款，否则订单将自动取消。</p>
<a id="pay-now" class="btn" href="/shop/order/{esc(oid)}/pay">立即付款</a>"""
        self._send(200, page("等待付款", "pending-payment", body))

    # ---- 管理接口
    def _admin(self, method: str, path: str):
        st = self.store
        if path == "/__admin/state":
            return self._json(st.snapshot())
        if path == "/__admin/preset" and method == "POST":
            f = self._form()
            try:
                st.apply_preset(f.get("name", "normal"))
            except KeyError:
                return self._json({"error": "unknown preset"}, 400)
            if "json" in (self.headers.get("Content-Type") or ""):
                return self._json(st.snapshot())
            return self._redirect("/__admin")
        if path == "/__admin/settings" and method == "POST":
            f = self._form()
            with st.lock:
                for k, v in f.items():
                    if hasattr(st.settings, k):
                        setattr(st.settings, k, v)
                if "stock" in f:
                    st.stock.update(f["stock"])
            return self._json(st.snapshot())
        snap = st.snapshot()
        presets = "".join(
            f'<form method="post" action="/__admin/preset" style="display:inline">'
            f'<input type="hidden" name="name" value="{k}"><button type="submit" '
            f'title="{esc(v[0])}">{esc(k)}</button></form> {esc(v[0])}<br>'
            for k, v in PRESETS.items()
        )
        body = f"""<h1>模拟商店管理</h1><p>当前场景：<b>{esc(snap['preset'])}</b>，
{'已开售' if snap['sale_open'] else f"{snap['seconds_to_sale']} 秒后开售"}</p>
<h3>切换场景（会重置库存、会话与订单）</h3>{presets}
<h3>状态</h3><pre>{esc(json.dumps(snap, ensure_ascii=False, indent=2))}</pre>
<h3>假 Bark 收到的推送</h3><pre>{esc(json.dumps(st.bark_messages[-10:], ensure_ascii=False, indent=2))}</pre>"""
        self._send(200, page("管理", "admin", body))

    # ---- 假 Bark 服务
    def _bark(self, method: str, path: str):
        if path == "/__bark/push" and method == "POST":
            msg = self._form()
            with self.store.lock:
                self.store.bark_messages.append(msg)
            return self._json({"code": 200, "message": "success", "timestamp": int(time.time())})
        if path == "/__bark/messages":
            with self.store.lock:
                return self._json(list(self.store.bark_messages))
        self._json({"code": 404, "message": "not found"}, 404)


class MockStoreServer:
    """在后台线程运行模拟商店。port=0 时自动选择空闲端口。"""

    def __init__(self, host: str = "127.0.0.1", port: int = 8765, preset: str = "normal"):
        self.state = StoreState()
        self.state.apply_preset(preset)
        handler = type("BoundHandler", (Handler,), {"store": self.state})
        self.httpd = ThreadingHTTPServer((host, port), handler)
        self.httpd.daemon_threads = True
        self._thread: threading.Thread | None = None

    @property
    def port(self) -> int:
        return self.httpd.server_address[1]

    @property
    def base_url(self) -> str:
        return f"http://127.0.0.1:{self.port}"

    @property
    def product_url(self) -> str:
        return self.base_url + PRODUCT_PATH

    @property
    def bark_server(self) -> str:
        return self.base_url + "/__bark"

    def start(self) -> "MockStoreServer":
        self._thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self._thread.start()
        return self

    def stop(self) -> None:
        self.httpd.shutdown()
        self.httpd.server_close()
        if self._thread:
            self._thread.join(timeout=5)

    def serve_forever(self) -> None:
        self.httpd.serve_forever()
