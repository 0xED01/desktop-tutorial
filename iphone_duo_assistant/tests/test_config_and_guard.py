import json
import multiprocessing
import sys

import pytest

from duo_assistant import paths, secrets_store
from duo_assistant.config import AppConfig, load_config, save_config
from duo_assistant.order_guard import AlreadyRunningError, DuplicateOrderError, OrderGuard, RunLock


def test_config_roundtrip_and_unknown_keys():
    c = AppConfig()
    c.purchase.budget_total = 9999
    c.purchase.acceptable_colors = ["云白色"]
    save_config(c)
    data = json.loads(paths.config_path().read_text(encoding="utf-8"))
    data["purchase"]["future_field"] = 1
    data["unknown_section"] = {}
    paths.config_path().write_text(json.dumps(data), encoding="utf-8")
    c2 = load_config()
    assert c2.purchase.budget_total == 9999
    assert c2.purchase.acceptable_colors == ["云白色"]
    assert c2.validate() == []


def test_config_validation():
    c = AppConfig()
    c.purchase.quantity = 5
    c.purchase.poll_interval_sec = 0.5
    c.purchase.acceptable_colors = []
    c.purchase.use_sale_time = True
    c.purchase.sale_time = "not a time"
    errs = "\n".join(c.validate())
    assert "数量" in errs and "刷新间隔" in errs and "颜色" in errs and "开售时间" in errs


def test_bark_key_never_in_config():
    secrets_store.save_bark_key("SECRETKEY123")
    save_config(AppConfig())
    assert "SECRETKEY123" not in paths.config_path().read_text(encoding="utf-8")
    assert secrets_store.load_bark_key() == "SECRETKEY123"
    assert secrets_store.mask("SECRETKEY123") == "SEC***23"


@pytest.mark.skipif(sys.platform != "win32", reason="DPAPI 仅 Windows")
def test_dpapi_encrypts_on_windows():
    scheme = secrets_store.save_bark_key("SECRETKEY123")
    assert scheme == "dpapi"
    assert "SECRETKEY123" not in paths.secrets_path().read_text(encoding="utf-8")
    assert secrets_store.load_bark_key() == "SECRETKEY123"


def test_guard_blocks_duplicates_until_cleared():
    g = OrderGuard()
    g.ensure_can_submit("iPhone Duo")
    g.mark_submitting("iPhone Duo", "黑 512GB", 9999)
    with pytest.raises(DuplicateOrderError):
        g.ensure_can_submit("iPhone Duo")
    with pytest.raises(DuplicateOrderError):
        g.mark_submitting("iPhone Duo", "白 256GB", 7999)
    g.mark_pending_payment("iPhone Duo", "W1")
    assert g.active("iPhone Duo")[0].order_id == "W1"
    # 其他活动不受影响
    g.ensure_can_submit("另一个产品")
    assert g.clear("iPhone Duo") == 1
    g.ensure_can_submit("iPhone Duo")
    # 历史记录保留
    assert g.records()[0].status == "cleared"


def test_guard_rejected_does_not_block():
    g = OrderGuard()
    g.mark_submitting("P", "x", 1)
    g.mark_rejected("P", "库存不足")
    g.ensure_can_submit("P")


def test_guard_persists_across_instances():
    OrderGuard().mark_submitting("P", "x", 1)
    with pytest.raises(DuplicateOrderError):
        OrderGuard().ensure_can_submit("P")


def _try_lock(path, q):
    try:
        RunLock(path).acquire()
        q.put("acquired")
    except AlreadyRunningError:
        q.put("blocked")


def test_run_lock_is_cross_process(tmp_path):
    path = tmp_path / "run.lock"
    with RunLock(path):
        q = multiprocessing.Queue()
        p = multiprocessing.Process(target=_try_lock, args=(path, q))
        p.start()
        p.join(20)
        assert q.get(timeout=5) == "blocked"
    q = multiprocessing.Queue()
    p = multiprocessing.Process(target=_try_lock, args=(path, q))
    p.start()
    p.join(20)
    assert q.get(timeout=5) == "acquired"
