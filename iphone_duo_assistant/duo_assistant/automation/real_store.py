"""真实 Apple Store（apple.com.cn）辅助模式。

依据 Apple 中国官网《网站使用条款》：“你不得使用任何‘深层链接’、‘网页搜罗’、
‘网页机器人’、‘网页采集工具’或其他自动设备、程序、算法或方法……来访问、获取、
拷贝或监控本网站的任何组成部分或任何内容”。

因此本模块对官网 **不做任何自动访问、读取、点击或监控**，只提供：
1. 开售提醒（Bark 定时推送，由本机计时，不访问官网）；
2. 到点用系统默认浏览器打开官网页面（等同于你自己点开链接）；
3. 根据你在界面上勾选的“官网显示有货”的配置，按预算和偏好即时给出推荐；
4. 你亲自提交订单后，一键登记（防止重复下单）并推送 Bark 汇总。
"""

from __future__ import annotations

import webbrowser
from dataclasses import dataclass
from datetime import datetime, timedelta

from ..catalog import make_sku
from ..selector import Preferences, SelectionResult, Variant, select_candidates


@dataclass
class Reminder:
    when: datetime
    title: str
    body: str
    level: str
    open_browser: bool = False
    fired: bool = False


def build_reminders(product: str, sale: datetime, open_seconds_before: int) -> list[Reminder]:
    items = [
        Reminder(
            sale - timedelta(minutes=10),
            f"{product} 10 分钟后开售",
            "请准备好电脑和手机，确认已登录 Apple 账户、收货地址与付款方式。",
            "timeSensitive",
        ),
        Reminder(
            sale - timedelta(minutes=1),
            f"{product} 1 分钟后开售",
            "请守在电脑前。",
            "critical",
        ),
        Reminder(
            sale - timedelta(seconds=open_seconds_before),
            f"{product} 即将开售，已打开官网",
            "程序已在电脑上打开官网页面，请手动选择配置并下单。",
            "critical",
            open_browser=True,
        ),
    ]
    now = datetime.now()
    return sorted((r for r in items if r.when > now), key=lambda r: r.when)


def due_reminders(reminders: list[Reminder], now: datetime | None = None) -> list[Reminder]:
    now = now or datetime.now()
    due = [r for r in reminders if not r.fired and r.when <= now]
    for r in due:
        r.fired = True
    return due


def recommend(
    available: list[tuple[str, str]], prices: dict[str, int], prefs: Preferences
) -> SelectionResult:
    """available: 用户勾选的“官网显示有货”的 (颜色, 容量)；prices: 容量 → 单价。"""
    variants = []
    for color, cap in available:
        price = prices.get(cap)
        if not price:
            continue
        variants.append(Variant(make_sku(color, cap), color, cap, int(price), True, None))
    return select_candidates(variants, prefs)


def open_store(url: str) -> bool:
    return webbrowser.open(url, new=2)
