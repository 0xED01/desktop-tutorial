"""端到端：真实浏览器（Playwright）+ 本地模拟商店 + 假 Bark 服务。"""

import threading
import time

import pytest

from duo_assistant.automation.mock_flow import PurchaseFlow
from duo_assistant.controller import RunController
from duo_assistant.notifier import BarkNotifier
from duo_assistant.order_guard import OrderGuard

pytestmark = pytest.mark.e2e


def make_flow(cfg, store, **kw):
    events = []
    flow = PurchaseFlow(
        cfg,
        store.product_url,
        BarkNotifier(cfg.bark, "TESTKEY", timeout=3, retries=0),
        guard=OrderGuard(),
        controller=kw.pop("controller", RunController()),
        emit=lambda k, m: events.append((k, m)),
        keep_browser_open=False,
        **kw,
    )
    return flow, events


def run_in_thread(flow):
    box = {}
    t = threading.Thread(target=lambda: box.setdefault("r", flow.run()), daemon=True)
    t.start()
    return t, box


def wait_until(cond, timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        if cond():
            return True
        time.sleep(0.2)
    return False


def titles(store):
    return [m["title"] for m in store.state.bark_messages]


def test_happy_path_stops_at_pending_payment(cfg, store):
    flow, events = make_flow(cfg, store)
    r = flow.run()
    assert r.success, r.message
    # capacity_first + 颜色优先级 远峰蓝色 → 远峰蓝色 512GB
    assert "远峰蓝色 512GB ×1" in r.config and r.total == 9999
    order = store.state.orders[0]
    assert order.status == "pending_payment"  # 没有付款
    assert order.payment == "alipay"
    msg = store.state.bark_messages[-1]
    assert "待付款" in msg["title"] and msg["level"] == "critical"
    assert "远峰蓝色 512GB" in msg["body"] and "¥9,999" in msg["body"] and order.order_id in msg["body"]
    assert OrderGuard().active("iPhone Duo")[0].status == "pending_payment"


def test_duplicate_run_is_refused(cfg, store):
    flow, _ = make_flow(cfg, store)
    assert flow.run().success
    flow2, _ = make_flow(cfg, store)
    r = flow2.run()
    assert not r.success and "防止重复下单" in r.message
    assert len(store.state.orders) == 1


def test_partial_soldout_picks_next_choice(cfg, store):
    store.state.apply_preset("partial_soldout")
    cfg.purchase.acceptable_capacities = ["256GB", "512GB"]
    flow, _ = make_flow(cfg, store)
    r = flow.run()
    assert r.success and "512GB" in r.config


def test_race_soldout_falls_back(cfg, store):
    store.state.apply_preset("race")
    flow, events = make_flow(cfg, store)
    r = flow.run()
    assert r.success
    # 第一次加购失败后自动换到下一个候选
    assert any("加入购物袋失败" in m for _, m in events)
    assert "深空黑色 512GB" in r.config


def test_price_change_never_submits(cfg, store):
    store.state.apply_preset("price_change")
    flow, _ = make_flow(cfg, store)
    r = flow.run()
    assert not r.success and "超出预算" in r.message
    assert store.state.orders == []
    assert any("超出预算" in t for t in titles(store))


def test_presale_waits_then_buys(cfg, store):
    store.state.apply_preset("presale", sale_delay_sec=5)
    flow, events = make_flow(cfg, store)
    r = flow.run()
    assert r.success
    assert any("尚未开售" in m for _, m in events)


def test_queue_waits_passively(cfg, store):
    store.state.apply_preset("queue", queue_seconds=6)
    flow, events = make_flow(cfg, store)
    r = flow.run()
    assert r.success
    assert any("排队" in t for t in titles(store))


def test_nothing_acceptable_keeps_waiting_then_stops(cfg, store):
    store.state.apply_preset("all_soldout")
    ctl = RunController()
    flow, events = make_flow(cfg, store, controller=ctl)
    t, box = run_in_thread(flow)
    assert wait_until(lambda: any("暂无符合条件" in x for x in titles(store)), 40)
    ctl.stop()
    t.join(30)
    assert not box["r"].success and store.state.orders == []


def test_login_pauses_for_human_then_continues(cfg, store):
    store.state.apply_preset("login")
    ctl = RunController()
    flow, events = make_flow(cfg, store, controller=ctl)
    t, box = run_in_thread(flow)
    assert wait_until(lambda: ctl.paused, 60), events
    assert any("登录" in x for x in titles(store))
    # 模拟用户在浏览器中手动登录（测试中直接修改服务端会话），然后点“继续”
    for s in store.state.sessions.values():
        s.logged_in = True
    ctl.resume()
    t.join(90)
    assert box["r"].success, box["r"].message


def test_captcha_pauses_and_is_never_solved_by_program(cfg, store):
    store.state.apply_preset("captcha")
    ctl = RunController()
    flow, events = make_flow(cfg, store, controller=ctl)
    t, box = run_in_thread(flow)
    assert wait_until(lambda: ctl.paused, 60)
    assert any("验证码" in x for x in titles(store))
    time.sleep(4)  # 暂停期间程序不得自行通过验证
    assert ctl.paused and store.state.orders == []
    assert not any(s.captcha_passed for s in store.state.sessions.values())
    for s in store.state.sessions.values():
        s.captcha_passed = True  # 模拟人工完成验证
    ctl.resume()
    t.join(90)
    assert box["r"].success, box["r"].message


def test_rate_limit_pauses(cfg, store):
    store.state.apply_preset("ratelimit", rate_limit_per_10s=1, block_seconds=3)
    ctl = RunController()
    flow, events = make_flow(cfg, store, controller=ctl)
    t, box = run_in_thread(flow)
    assert wait_until(lambda: ctl.paused, 60)
    assert any("异常访问" in x for x in titles(store))
    ctl.stop()
    t.join(30)
    assert store.state.orders == []


def test_user_pause_and_stop(cfg, store):
    store.state.apply_preset("presale", sale_delay_sec=3600)
    ctl = RunController()
    flow, events = make_flow(cfg, store, controller=ctl)
    t, box = run_in_thread(flow)
    assert wait_until(lambda: any("尚未开售" in m for _, m in events), 60)
    ctl.pause()
    time.sleep(1)
    ctl.stop()
    t.join(30)
    assert not t.is_alive()
    assert box["r"].message == "用户已停止"
