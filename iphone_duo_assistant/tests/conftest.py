import os
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from duo_assistant.config import AppConfig  # noqa: E402
from duo_assistant.mock_store import MockStoreServer  # noqa: E402


@pytest.fixture(autouse=True)
def tmp_home(tmp_path, monkeypatch):
    home = tmp_path / "home"
    home.mkdir()
    monkeypatch.setenv("DUO_ASSISTANT_HOME", str(home))
    return home


@pytest.fixture
def store():
    srv = MockStoreServer(port=0).start()
    yield srv
    srv.stop()


@pytest.fixture
def cfg(store):
    c = AppConfig()
    c.purchase.acceptable_colors = ["远峰蓝色", "深空黑色"]
    c.purchase.acceptable_capacities = ["512GB", "256GB"]
    c.purchase.budget_total = 12000
    c.purchase.quantity = 1
    c.purchase.strategy = "capacity_first"
    c.purchase.poll_interval_sec = 2
    c.purchase.max_run_minutes = 3
    c.bark.server = store.bark_server
    # CI 可用 DUO_TEST_CHANNEL=msedge 测试 Windows 自带的 Edge
    c.browser.channel = os.environ.get("DUO_TEST_CHANNEL", "chromium")
    c.browser.headless = os.environ.get("DUO_TEST_HEADED") != "1"
    return c
