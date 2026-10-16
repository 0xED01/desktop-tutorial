"""苹果中国大陆官网门店取货库存监控 —— 核心逻辑（不依赖界面，可单独测试）。"""

import html
import json
import random
import re
import threading
import time
import urllib.parse

import requests

BASE_URL = "https://www.apple.com.cn"
FULFILLMENT_URL = BASE_URL + "/shop/fulfillment-messages"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
)

# 型号代码，例如 MG8L4CH/A、MYW33CH/A
PART_RE = re.compile(r"\b([A-Z0-9]{4,6}(?:CH|ZA|ZP)/A)\b", re.I)
STORE_RE = re.compile(r"^R\d{3,4}$", re.I)


class ApiError(Exception):
    pass


def parse_parts(text):
    """从多行文本中解析型号。每行一个，型号后面可以写备注；也可以直接粘贴带型号的链接。

    返回 [(型号, 备注), ...]，自动去重。
    """
    result, seen = [], set()
    for line in (text or "").splitlines():
        m = PART_RE.search(line)
        if not m:
            continue
        part = m.group(1).upper()
        if part in seen:
            continue
        seen.add(part)
        note = (line[: m.start()] + " " + line[m.end():]).strip(" \t-—:：,，|")
        if note.startswith("http"):
            note = ""
        result.append((part, note))
    return result


def strip_html(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip()


def product_url(part):
    return f"{BASE_URL}/shop/product/{part}"


def parse_fulfillment(data, part):
    """解析 fulfillment-messages 接口返回的 JSON，得到每家门店的取货状态。"""
    if not isinstance(data, dict):
        raise ApiError("返回内容格式异常")
    content = (data.get("body") or {}).get("content") or {}
    pm = content.get("pickupMessage") or {}
    if pm.get("errorMessage"):
        raise ApiError(strip_html(pm["errorMessage"]))
    stores = pm.get("stores")
    if stores is None:
        raise ApiError("返回内容中没有门店信息（型号或地区可能不正确）")
    out = []
    for st in stores:
        pa = (st.get("partsAvailability") or {}).get(part) or {}
        mtypes = pa.get("messageTypes") or {}
        title = ""
        for key in ("regular", "compact"):
            title = (mtypes.get(key) or {}).get("storePickupProductTitle") or ""
            if title:
                break
        display = pa.get("pickupDisplay", "")
        out.append({
            "store": st.get("storeNumber", ""),
            "name": st.get("storeName", "") or st.get("storeNumber", ""),
            "city": st.get("city", ""),
            "available": display == "available",
            "display": display,
            "quote": strip_html(pa.get("pickupSearchQuote", "")),
            "title": strip_html(title),
        })
    return out


def filter_stores(results, keywords_text):
    """按门店名关键字过滤（逗号/空格分隔，空则不过滤）。"""
    kws = [k for k in re.split(r"[,，\s]+", keywords_text or "") if k]
    if not kws:
        return results
    return [r for r in results
            if any(k.lower() in (r["name"] + r["store"] + r["city"]).lower() for k in kws)]


class AppleClient:
    def __init__(self, timeout=15):
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": USER_AGENT,
            "Accept-Language": "zh-CN,zh;q=0.9",
            "Referer": BASE_URL + "/shop/buy-iphone",
        })
        self._warmed = False

    def warm_up(self):
        """先访问一次官网拿 cookie，更像正常浏览器。"""
        try:
            self.session.get(BASE_URL + "/shop/buy-iphone", timeout=self.timeout)
        except requests.RequestException:
            pass
        self._warmed = True

    def check(self, part, location):
        if not self._warmed:
            self.warm_up()
        params = {
            "fae": "true",
            "pl": "true",
            "mts.0": "regular",
            "mts.1": "compact",
            "parts.0": part,
            "searchNearby": "true",
        }
        loc = (location or "").strip()
        if STORE_RE.match(loc):
            params["store"] = loc.upper()
        else:
            params["location"] = loc
        try:
            r = self.session.get(
                FULFILLMENT_URL, params=params, timeout=self.timeout,
                headers={"Accept": "application/json, text/plain, */*"},
            )
        except requests.RequestException as e:
            raise ApiError(f"网络错误：{e.__class__.__name__}") from e
        if r.status_code == 541:
            raise ApiError("HTTP 541：苹果拒绝了请求，通常是开了 VPN/代理（非中国大陆 IP），请关闭后重试")
        if r.status_code in (403, 429, 503):
            self._warmed = False
            raise ApiError(f"HTTP {r.status_code}：请求太频繁被限制，将自动降速重试")
        if r.status_code != 200:
            raise ApiError(f"HTTP {r.status_code}")
        try:
            data = r.json()
        except ValueError as e:
            self._warmed = False
            raise ApiError("返回的不是 JSON（可能被风控），将自动重试") from e
        return parse_fulfillment(data, part)

    def lookup_parts(self, page_url):
        """从官网购买页面（如 /shop/buy-iphone/iphone-17-pro）里提取所有型号代码及其配置。"""
        r = self.session.get(page_url, timeout=20)
        r.raise_for_status()
        return extract_parts_from_html(r.text)


def _json_str(s):
    try:
        return json.loads('"' + s + '"')
    except ValueError:
        return s


def extract_parts_from_html(text):
    """尽力从购买页源码中解析出 [(型号, 配置描述)]。"""
    # 维度代码 -> 中文显示名，例如 "deepblue": {"value": "深蓝色"}
    names = {}
    for m in re.finditer(r'"([A-Za-z0-9_\-]+)"\s*:\s*\{\s*"value"\s*:\s*"((?:[^"\\]|\\.)*)"', text):
        names.setdefault(m.group(1), _json_str(m.group(2)))
    items = {}
    for m in re.finditer(r'\{[^{}]*?"partNumber"\s*:\s*"([A-Z0-9]+/A)"[^{}]*\}', text):
        part = m.group(1)
        if part in items:
            continue
        try:
            obj = json.loads(m.group(0))
        except ValueError:
            obj = {}
        dims = []
        for k, v in obj.items():
            if k.startswith("dimension") and isinstance(v, str) and v:
                dims.append(names.get(v, v))
        items[part] = " ".join(dims)
    if not items:
        for m in PART_RE.finditer(text):
            items.setdefault(m.group(1).upper(), "")
    return sorted(items.items(), key=lambda kv: kv[1])


class Monitor(threading.Thread):
    """后台监控线程。通过 emit(event, payload) 把结果交给界面。

    事件：
      log     (str)
      row     dict(part, note, store, name, city, available, quote, title, time)
      alert   dict(part, note, title, stores=[...], url)
      round   dict(count, time)
    """

    def __init__(self, parts, location, store_filter, interval, emit,
                 repeat_minutes=5, client=None):
        super().__init__(daemon=True)
        self.parts = parts
        self.location = location
        self.store_filter = store_filter
        self.interval = max(10, int(interval))
        self.emit = emit
        self.repeat_seconds = max(0, float(repeat_minutes)) * 60
        self.client = client or AppleClient()
        self.stop_event = threading.Event()
        self.state = {}        # (part, store) -> available
        self.last_alert = {}   # part -> timestamp
        self.errors = 0
        self.rounds = 0

    def stop(self):
        self.stop_event.set()

    def run(self):
        self.emit("log", f"开始监控 {len(self.parts)} 个型号，地区：{self.location}，间隔约 {self.interval} 秒")
        while not self.stop_event.is_set():
            ok = self.check_round()
            self.rounds += 1
            self.emit("round", {"count": self.rounds, "time": time.strftime("%H:%M:%S")})
            if ok:
                self.errors = 0
                wait = self.interval * random.uniform(0.85, 1.15)
            else:
                self.errors += 1
                wait = self.interval * min(2 ** self.errors, 10)
                self.emit("log", f"连续出错 {self.errors} 次，{int(wait)} 秒后重试")
            self.stop_event.wait(wait)
        self.emit("log", "监控已停止")

    def check_round(self):
        all_ok = True
        for i, (part, note) in enumerate(self.parts):
            if self.stop_event.is_set():
                break
            if i:
                self.stop_event.wait(random.uniform(1.0, 2.5))
            try:
                results = self.client.check(part, self.location)
            except ApiError as e:
                all_ok = False
                self.emit("log", f"[{part}] 查询失败：{e}")
                continue
            except Exception as e:  # 防止线程意外退出
                all_ok = False
                self.emit("log", f"[{part}] 未知错误：{e!r}")
                continue
            self.handle_results(part, note, filter_stores(results, self.store_filter))
        return all_ok

    def handle_results(self, part, note, results):
        now = time.strftime("%H:%M:%S")
        newly, in_stock = [], []
        title = next((r["title"] for r in results if r["title"]), "")
        for r in results:
            key = (part, r["store"])
            prev = self.state.get(key)
            self.state[key] = r["available"]
            self.emit("row", dict(r, part=part, note=note, time=now))
            if r["available"]:
                in_stock.append(r)
                if not prev:
                    newly.append(r)
        label = note or title or part
        if in_stock:
            self.emit("log", f"[{part}] {label}：{len(in_stock)} 家门店有货 → "
                             + "、".join(r["name"] for r in in_stock))
        else:
            self.emit("log", f"[{part}] {label}：{len(results)} 家门店均无货")
        t = time.time()
        due = self.repeat_seconds and in_stock and t - self.last_alert.get(part, 0) >= self.repeat_seconds
        if newly or due:
            self.last_alert[part] = t
            self.emit("alert", {
                "part": part, "note": note, "title": title,
                "stores": in_stock, "new": bool(newly), "url": product_url(part),
            })


def build_alert_text(info):
    name = info.get("note") or info.get("title") or info["part"]
    stores = "、".join(f'{s["name"]}（{s["quote"] or "可取货"}）' for s in info["stores"])
    title = f"有货啦！{name}"
    body = f"型号 {info['part']} 可门店取货：{stores}\n赶快下单：{info['url']}"
    return title, body


def send_push(cfg, title, body, url, log=lambda s: None):
    """推送到手机：Server酱 / PushPlus（微信）、Bark（iPhone）。在后台线程调用。"""
    sent = []
    key = (cfg.get("serverchan_key") or "").strip()
    if key:
        try:
            requests.post(f"https://sctapi.ftqq.com/{key}.send",
                          data={"title": title[:32], "desp": body}, timeout=10)
            sent.append("Server酱")
        except requests.RequestException as e:
            log(f"Server酱推送失败：{e.__class__.__name__}")
    token = (cfg.get("pushplus_token") or "").strip()
    if token:
        try:
            requests.post("https://www.pushplus.plus/send",
                          json={"token": token, "title": title, "content": body, "template": "txt"},
                          timeout=10)
            sent.append("PushPlus")
        except requests.RequestException as e:
            log(f"PushPlus推送失败：{e.__class__.__name__}")
    bark = (cfg.get("bark_url") or "").strip().rstrip("/")
    if bark:
        try:
            q = urllib.parse.quote
            # level=critical 静音模式也会响；call=1 持续响铃 30 秒
            requests.get(f"{bark}/{q(title, safe='')}/{q(body, safe='')}",
                         params={"url": url, "level": "critical", "volume": "10",
                                 "call": "1", "group": "苹果库存"}, timeout=10)
            sent.append("Bark")
        except requests.RequestException as e:
            log(f"Bark推送失败：{e.__class__.__name__}")
    if sent:
        log("已推送到手机：" + "、".join(sent))
    return sent
