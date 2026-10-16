"""产品目录。

注意：截至编写时（2026-10）作者无法确认 "iPhone Duo" 的真实颜色、容量与官方价格。
以下数据只用于本地模拟商店和界面默认值，正式开售后请在界面里按官网公布的信息修改。
"""

from __future__ import annotations

DEFAULT_PRODUCT_NAME = "iPhone Duo"

DEFAULT_COLORS = ["深空黑色", "云白色", "远峰蓝色", "沙漠金色"]
DEFAULT_CAPACITIES = ["256GB", "512GB", "1TB"]

# 模拟商店价格（虚构，单位：元）
MOCK_PRICES = {"256GB": 7999, "512GB": 9999, "1TB": 11999}

_COLOR_CODES = {"深空黑色": "BLK", "云白色": "WHT", "远峰蓝色": "BLU", "沙漠金色": "GLD"}


def normalize(name: str) -> str:
    """用于比较颜色/容量名称：忽略空白与大小写，'1 TB' == '1tb'。"""
    return "".join(name.split()).lower()


def make_sku(color: str, capacity: str) -> str:
    code = _COLOR_CODES.get(color, normalize(color).upper()[:6])
    return f"DUO-{code}-{normalize(capacity).upper()}"
