"""配置选择逻辑（纯函数，不依赖浏览器，便于测试）。"""

from __future__ import annotations

from dataclasses import dataclass, field

from .catalog import normalize

STRATEGIES = {
    "cheapest": "总价最低优先",
    "capacity_first": "容量偏好优先（其次颜色）",
    "color_first": "颜色偏好优先（其次容量）",
}


@dataclass(frozen=True)
class Variant:
    sku: str
    color: str
    capacity: str
    unit_price: int
    available: bool
    stock: int | None = None  # None 表示页面不显示具体库存数量


@dataclass(frozen=True)
class Preferences:
    acceptable_colors: tuple[str, ...]  # 按优先级排序
    acceptable_capacities: tuple[str, ...]  # 按优先级排序
    budget_total: int  # 总预算（元，含数量）
    quantity: int
    strategy: str = "capacity_first"


@dataclass(frozen=True)
class Candidate:
    variant: Variant
    quantity: int
    total: int

    def label(self) -> str:
        return f"{self.variant.color} {self.variant.capacity} ×{self.quantity}"

    def describe(self) -> str:
        v = self.variant
        return (
            f"{v.color} {v.capacity} ×{self.quantity}，"
            f"单价 ¥{v.unit_price:,}，总价 ¥{self.total:,}"
        )


@dataclass
class SelectionResult:
    candidates: list[Candidate] = field(default_factory=list)
    rejected: list[tuple[Variant, str]] = field(default_factory=list)

    @property
    def best(self) -> Candidate | None:
        return self.candidates[0] if self.candidates else None


def _index(name: str, ordered: tuple[str, ...]) -> int | None:
    key = normalize(name)
    for i, item in enumerate(ordered):
        if normalize(item) == key:
            return i
    return None


def select_candidates(variants: list[Variant], prefs: Preferences) -> SelectionResult:
    """筛选并排序所有可买的配置。返回的 candidates 已按策略排好序。"""
    if prefs.strategy not in STRATEGIES:
        raise ValueError(f"未知策略: {prefs.strategy}")
    if prefs.quantity < 1:
        raise ValueError("购买数量至少为 1")

    result = SelectionResult()
    keyed: list[tuple[tuple, Candidate]] = []
    for v in variants:
        ci = _index(v.color, prefs.acceptable_colors)
        pi = _index(v.capacity, prefs.acceptable_capacities)
        total = v.unit_price * prefs.quantity
        if ci is None:
            result.rejected.append((v, "颜色不在可接受范围"))
        elif pi is None:
            result.rejected.append((v, "容量不在可接受范围"))
        elif not v.available:
            result.rejected.append((v, "无货"))
        elif v.stock is not None and v.stock < prefs.quantity:
            result.rejected.append((v, f"库存 {v.stock} 少于购买数量 {prefs.quantity}"))
        elif total > prefs.budget_total:
            result.rejected.append((v, f"总价 ¥{total:,} 超出预算 ¥{prefs.budget_total:,}"))
        else:
            if prefs.strategy == "cheapest":
                key = (total, pi, ci)
            elif prefs.strategy == "capacity_first":
                key = (pi, ci, total)
            else:
                key = (ci, pi, total)
            keyed.append((key, Candidate(v, prefs.quantity, total)))
    keyed.sort(key=lambda kc: kc[0])
    result.candidates = [c for _, c in keyed]
    return result
