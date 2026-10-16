"""不用浏览器，直接用 HTTP 验证模拟商店的规则（限购、幂等、开售时间）。"""

import http.cookiejar
import json
import re
import urllib.parse
import urllib.request

from duo_assistant.catalog import make_sku


class Client:
    def __init__(self, base):
        self.base = base
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )

    def get(self, path):
        with self.opener.open(self.base + path) as r:
            return r.geturl(), r.read().decode("utf-8")

    def post(self, path, data):
        body = urllib.parse.urlencode(data).encode()
        with self.opener.open(self.base + path, body) as r:
            return r.geturl(), r.read().decode("utf-8")


def admin_preset(store, name):
    req = urllib.request.Request(
        store.base_url + "/__admin/preset",
        data=json.dumps({"name": name}).encode(),
        headers={"Content-Type": "application/json"},
    )
    return json.loads(urllib.request.urlopen(req).read())


def test_presale_then_open(store):
    store.state.apply_preset("presale", sale_delay_sec=3600)
    c = Client(store.base_url)
    _, html = c.get("/shop/buy-iphone/iphone-duo")
    assert 'data-page="product-presale"' in html
    admin_preset(store, "normal")
    _, html = c.get("/shop/buy-iphone/iphone-duo")
    assert 'data-page="product"' in html


def test_full_order_and_idempotent_token(store):
    c = Client(store.base_url)
    url, html = c.post("/shop/bag/add", {"color": "云白色", "capacity": "512GB", "qty": "1"})
    assert url.endswith("/shop/bag")
    url, html = c.post("/shop/bag/checkout", {})
    token = re.search(r'name="token" value="(\w+)"', html).group(1)
    url, html = c.post("/shop/checkout/place", {"token": token, "payment": "alipay"})
    assert 'data-page="pending-payment"' in html
    # 重复提交同一 token 不会生成第二个订单
    url2, _ = c.post("/shop/checkout/place", {"token": token, "payment": "alipay"})
    assert url2 == url
    assert len(store.state.orders) == 1
    assert store.state.stock[make_sku("云白色", "512GB")] == 19


def test_purchase_limit(store):
    c = Client(store.base_url)
    c.post("/shop/bag/add", {"color": "云白色", "capacity": "512GB", "qty": "2"})
    _, html = c.post("/shop/bag/add", {"color": "云白色", "capacity": "256GB", "qty": "1"})
    assert "限购" in html


def test_rate_limit_block(store):
    store.state.apply_preset("ratelimit")
    c = Client(store.base_url)
    blocked = False
    for _ in range(10):
        try:
            c.get("/shop/buy-iphone/iphone-duo")
        except urllib.error.HTTPError as e:
            assert e.code == 429
            assert "访问过于频繁" in e.read().decode("utf-8")
            blocked = True
            break
    assert blocked
