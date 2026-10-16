from datetime import datetime, timedelta

from duo_assistant.automation.real_store import build_reminders, due_reminders, recommend
from duo_assistant.selector import Preferences


def test_reminders_schedule():
    sale = datetime.now() + timedelta(hours=1)
    rs = build_reminders("iPhone Duo", sale, 30)
    assert [r.when for r in rs] == sorted(r.when for r in rs)
    assert rs[-1].open_browser and rs[-1].when == sale - timedelta(seconds=30)
    assert due_reminders(rs, sale - timedelta(minutes=5)) == [rs[0]]
    assert due_reminders(rs, sale - timedelta(minutes=5)) == []  # 不重复触发
    assert len(due_reminders(rs, sale)) == 2


def test_past_reminders_dropped():
    sale = datetime.now() + timedelta(seconds=90)
    rs = build_reminders("P", sale, 30)
    assert len(rs) == 2  # 10 分钟提醒已过


def test_recommend_from_manual_availability():
    prefs = Preferences(("云白色", "深空黑色"), ("512GB", "256GB"), 10000, 1, "capacity_first")
    r = recommend(
        [("深空黑色", "512GB"), ("云白色", "256GB"), ("云白色", "1TB")],
        {"256GB": 7999, "512GB": 9999, "1TB": 11999},
        prefs,
    )
    assert r.best.variant.color == "深空黑色" and r.best.variant.capacity == "512GB"
    assert r.best.total == 9999
