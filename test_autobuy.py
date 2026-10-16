"""用本地模拟的苹果页面测试自动加购流程：python test_autobuy.py

重点验证：能走完加购→结账→选门店→选支付宝，并且停在「立即下单」之前、绝不点击它。
"""

import os
import sys
import tempfile
import urllib.parse

from autobuy import AutoBuyer

PAGES = {
    "product": """<html><body>
<input type=radio name=tradeupinline data-autom=choose-noTradeIn onchange="upd()">
<input type=radio name=applecare-options data-autom=noapplecare onchange="upd()">
<button data-autom=add-to-cart disabled onclick="location='/shop/bag'">添加到购物袋</button>
<script>function upd(){var a=document.querySelector('[data-autom=choose-noTradeIn]').checked,
b=document.querySelector('[data-autom=noapplecare]').checked;
document.querySelector('[data-autom=add-to-cart]').disabled=!(a&&b);
if(a&&b)localStorage.added='1'}</script></body></html>""",
    "bag": """<html><body><script>if(!localStorage.added)document.write(
'<a data-autom=bag-empty-continueshopping-button>继续购物</a>')</script>
<button data-autom=checkout onclick="location='/shop/checkout?_s=Fulfillment-init'">结账</button></body></html>""",
    "Fulfillment-init": """<html><body>
<button onclick="document.getElementById('s').style.display='block'">我要取货</button>
<div id=s style="display:none">
<label><input type=radio name=store value=R390 disabled>香港广场</label>
<label><input type=radio name=store value=R389>浦东</label>
<select><option value="">请选择</option><option value="t1">明天 10:00</option></select></div>
<button id=rs-checkout-continue-button-bottom onclick="
var r=document.querySelector('input[value=R389]').checked, t=document.querySelector('select').value;
if(r&&t)location='/shop/checkout?_s=PickupContact-init'">继续填写取货详情</button></body></html>""",
    "PickupContact-init": """<html><body>
<button id=rs-checkout-continue-button-bottom onclick="location='/shop/checkout?_s=Billing-init'">继续选择付款方式</button></body></html>""",
    "Billing-init": """<html><body>
<label><input type=radio name=pay value=huabei>花呗分期</label>
<label><input type=radio name=pay value=alipay>支付宝</label>
<button id=rs-checkout-continue-button-bottom onclick="
if(document.querySelector('input[value=alipay]').checked)location='/shop/checkout?_s=Review'">检查订单</button></body></html>""",
    "Review": """<html><body>
<button id=rs-checkout-continue-button-bottom onclick="localStorage.placed='1'">立即下单</button></body></html>""",
}


def handler(route, pages):
    u = urllib.parse.urlparse(route.request.url)
    q = urllib.parse.parse_qs(u.query)
    if u.path.startswith("/shop/product/") or u.path.startswith("/shop/buy-"):
        key = "product"
    elif u.path == "/shop/bag":
        key = "bag"
    elif u.path == "/shop/checkout":
        key = q["_s"][0]
    else:
        return route.fulfill(status=404, body="")
    route.fulfill(status=200, content_type="text/html; charset=utf-8", body=pages[key])


def run(pages_override=None):
    events = []
    pages = dict(PAGES, **(pages_override or {}))
    kwargs = {"headless": True}
    if len(sys.argv) > 1:  # 本地用指定的 Chromium 测试
        kwargs.update(channels=(None,), executable_path=sys.argv[1])
    with tempfile.TemporaryDirectory() as d:
        buyer = AutoBuyer(os.path.join(d, "profile"), lambda e, p: events.append((e, p)), **kwargs)
        page = buyer._page()
        buyer._ctx.route("https://www.apple.com.cn/**", lambda route: handler(route, pages))
        buyer._buy("MHU64CH/A", ["R390", "R389"], ["香港广场", "浦东"])
        placed = page.evaluate("localStorage.placed")
        url = page.url
        buyer._ctx.close()
        buyer._pw.stop()
    for e, p in events:
        print(e, p)
    return events, url, placed


def main():
    events, url, placed = run()
    checked_pay = url.endswith("_s=Review")
    final = [p for e, p in events if e == "need_user"]
    assert checked_pay, f"没有走到 Review 页：{url}"
    assert placed is None, "不应该点击「立即下单」！"
    assert final and "最后一步" in final[-1]["title"], final
    assert any("已选择门店 R389" in p for e, p in events if e == "log")
    assert any("已选择支付宝" in p for e, p in events if e == "log")
    # 无货：按钮一直不可点 → 停下并提醒用户接手
    events, url, placed = run({"product": PAGES["product"].replace("disabled=!(a&&b)", "disabled=true")})
    final = [p for e, p in events if e == "need_user"]
    assert final and "卡住" in final[-1]["title"], final
    print("自动加购流程测试通过")


if __name__ == "__main__":
    main()
