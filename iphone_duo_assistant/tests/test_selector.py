import pytest

from duo_assistant.selector import Preferences, Variant, select_candidates


def V(color, cap, price, available=True, stock=None):
    return Variant(f"{color}-{cap}", color, cap, price, available, stock)


VARIANTS = [
    V("黑", "256GB", 7999),
    V("黑", "512GB", 9999),
    V("白", "256GB", 7999, available=False),
    V("白", "512GB", 9999, stock=1),
    V("蓝", "1TB", 11999),
]


def prefs(**kw):
    base = dict(
        acceptable_colors=("白", "黑"),
        acceptable_capacities=("512GB", "256GB"),
        budget_total=20000,
        quantity=1,
        strategy="capacity_first",
    )
    base.update(kw)
    return Preferences(**base)


def skus(result):
    return [c.variant.sku for c in result.candidates]


def test_capacity_first_respects_priority():
    r = select_candidates(VARIANTS, prefs())
    assert skus(r) == ["白-512GB", "黑-512GB", "黑-256GB"]


def test_color_first():
    r = select_candidates(VARIANTS, prefs(strategy="color_first"))
    assert skus(r) == ["白-512GB", "黑-512GB", "黑-256GB"]
    r = select_candidates(VARIANTS, prefs(strategy="color_first", acceptable_colors=("黑", "白")))
    assert skus(r) == ["黑-512GB", "黑-256GB", "白-512GB"]


def test_cheapest():
    r = select_candidates(VARIANTS, prefs(strategy="cheapest"))
    assert skus(r)[0] == "黑-256GB"


def test_budget_is_total_including_quantity():
    r = select_candidates(VARIANTS, prefs(quantity=2, budget_total=17000))
    # 2 × 9999 = 19998 超预算；2 × 7999 = 15998 可以；白-512 库存 1 不足 2
    assert skus(r) == ["黑-256GB"]
    reasons = dict((v.sku, why) for v, why in r.rejected)
    assert "超出预算" in reasons["黑-512GB"]
    assert "库存" in reasons["白-512GB"]


def test_unavailable_and_unacceptable_rejected():
    r = select_candidates(VARIANTS, prefs())
    reasons = dict((v.sku, why) for v, why in r.rejected)
    assert reasons["白-256GB"] == "无货"
    assert "颜色" in reasons["蓝-1TB"]


def test_name_normalization():
    r = select_candidates([V("黑", "1 TB", 100)], prefs(acceptable_capacities=("1tb",)))
    assert r.best is not None


def test_nothing_available():
    r = select_candidates(VARIANTS, prefs(budget_total=100))
    assert r.best is None and len(r.rejected) == len(VARIANTS)


def test_invalid_strategy():
    with pytest.raises(ValueError):
        select_candidates(VARIANTS, prefs(strategy="xxx"))
