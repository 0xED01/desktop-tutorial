"""配置读写与校验。Bark 密钥不在这里保存，见 secrets_store.py。"""

from __future__ import annotations

import json
import sys
from dataclasses import asdict, dataclass, field, fields
from datetime import datetime
from pathlib import Path
from typing import Any

from . import paths
from .catalog import DEFAULT_CAPACITIES, DEFAULT_COLORS, DEFAULT_PRODUCT_NAME
from .selector import STRATEGIES, Preferences

MAX_QUANTITY = 2  # 程序上限；商店若设更低的限购，以商店为准，程序不会拆单规避
MIN_POLL_INTERVAL = 2.0  # 秒；避免对服务器造成压力
BROWSER_CHANNELS = ["msedge", "chrome", "chromium"]
BARK_LEVELS = ["critical", "timeSensitive", "active", "passive"]


def _default_channel() -> str:
    # Windows 11 自带 Edge，用它即可，无需额外下载浏览器
    return "msedge" if sys.platform == "win32" else "chromium"


@dataclass
class PurchaseConfig:
    product_name: str = DEFAULT_PRODUCT_NAME
    all_colors: list[str] = field(default_factory=lambda: list(DEFAULT_COLORS))
    all_capacities: list[str] = field(default_factory=lambda: list(DEFAULT_CAPACITIES))
    acceptable_colors: list[str] = field(default_factory=lambda: list(DEFAULT_COLORS))
    acceptable_capacities: list[str] = field(default_factory=lambda: ["256GB", "512GB"])
    budget_total: int = 12000
    quantity: int = 1
    strategy: str = "capacity_first"
    payment_method: str = "alipay"  # 只是在结算页“选择”付款方式，不会付款
    use_sale_time: bool = False
    sale_time: str = ""  # ISO 格式，例如 2026-10-20T20:00:00
    prewarm_seconds: int = 60  # 开售前多少秒开始打开页面
    poll_interval_sec: float = 3.0
    max_run_minutes: int = 30


@dataclass
class MockStoreConfig:
    port: int = 8765
    preset: str = "normal"

    @property
    def product_url(self) -> str:
        return f"http://127.0.0.1:{self.port}/shop/buy-iphone/iphone-duo"


@dataclass
class RealStoreConfig:
    store_url: str = "https://www.apple.com.cn/shop/buy-iphone"
    open_seconds_before: int = 30
    # 官网公布价格后手动填写：容量 -> 单价（元）
    prices: dict[str, int] = field(default_factory=dict)


@dataclass
class BarkConfig:
    server: str = "https://api.day.app"
    level: str = "critical"
    volume: int = 8
    sound: str = "alarm"
    group: str = "iPhone抢购"
    call: bool = True  # 重复响铃 30 秒


@dataclass
class BrowserConfig:
    channel: str = field(default_factory=_default_channel)
    headless: bool = False


@dataclass
class AppConfig:
    purchase: PurchaseConfig = field(default_factory=PurchaseConfig)
    mock: MockStoreConfig = field(default_factory=MockStoreConfig)
    real: RealStoreConfig = field(default_factory=RealStoreConfig)
    bark: BarkConfig = field(default_factory=BarkConfig)
    browser: BrowserConfig = field(default_factory=BrowserConfig)

    def preferences(self) -> Preferences:
        p = self.purchase
        return Preferences(
            acceptable_colors=tuple(p.acceptable_colors),
            acceptable_capacities=tuple(p.acceptable_capacities),
            budget_total=int(p.budget_total),
            quantity=int(p.quantity),
            strategy=p.strategy,
        )

    def sale_datetime(self) -> datetime | None:
        if not self.purchase.use_sale_time or not self.purchase.sale_time:
            return None
        return datetime.fromisoformat(self.purchase.sale_time)

    def validate(self) -> list[str]:
        p = self.purchase
        errors: list[str] = []
        if not p.product_name.strip():
            errors.append("产品名称不能为空")
        if not p.acceptable_colors:
            errors.append("至少选择一种可接受的颜色")
        if not p.acceptable_capacities:
            errors.append("至少选择一种可接受的容量")
        if not 1 <= p.quantity <= MAX_QUANTITY:
            errors.append(f"购买数量需在 1~{MAX_QUANTITY} 之间")
        if p.budget_total <= 0:
            errors.append("预算必须大于 0")
        if p.strategy not in STRATEGIES:
            errors.append(f"未知的选择策略: {p.strategy}")
        if p.poll_interval_sec < MIN_POLL_INTERVAL:
            errors.append(f"刷新间隔不能小于 {MIN_POLL_INTERVAL} 秒")
        if p.max_run_minutes <= 0:
            errors.append("最长运行时间必须大于 0")
        if p.use_sale_time:
            try:
                datetime.fromisoformat(p.sale_time)
            except ValueError:
                errors.append("开售时间格式无效")
        if self.bark.level not in BARK_LEVELS:
            errors.append("Bark 通知级别无效")
        if self.browser.channel not in BROWSER_CHANNELS:
            errors.append("浏览器类型无效")
        return errors


def _from_dict(cls: type, data: dict[str, Any] | None):
    """宽松地从字典构造 dataclass：忽略未知键，缺失键用默认值。"""
    obj = cls()
    if not isinstance(data, dict):
        return obj
    for f in fields(cls):
        if f.name not in data:
            continue
        current = getattr(obj, f.name)
        value = data[f.name]
        if hasattr(current, "__dataclass_fields__"):
            setattr(obj, f.name, _from_dict(type(current), value))
        else:
            setattr(obj, f.name, value)
    return obj


def config_from_dict(data: dict[str, Any]) -> AppConfig:
    return _from_dict(AppConfig, data)


def load_config(path: Path | None = None) -> AppConfig:
    path = path or paths.config_path()
    if not path.exists():
        return AppConfig()
    with path.open("r", encoding="utf-8") as fh:
        return config_from_dict(json.load(fh))


def save_config(cfg: AppConfig, path: Path | None = None) -> Path:
    path = path or paths.config_path()
    tmp = path.with_suffix(".tmp")
    with tmp.open("w", encoding="utf-8") as fh:
        json.dump(asdict(cfg), fh, ensure_ascii=False, indent=2)
    tmp.replace(path)
    return path
