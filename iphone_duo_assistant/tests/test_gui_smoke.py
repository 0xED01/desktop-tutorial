"""界面冒烟测试（无窗口模式）。"""

import os

import pytest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
pytest.importorskip("PySide6")
pytestmark = pytest.mark.gui

from PySide6.QtWidgets import QApplication  # noqa: E402

from duo_assistant import paths  # noqa: E402
from duo_assistant.config import load_config  # noqa: E402


@pytest.fixture(scope="module")
def app():
    return QApplication.instance() or QApplication([])


def test_window_builds_saves_and_recommends(app):
    from duo_assistant.gui.main_window import MainWindow

    w = MainWindow()
    w.budget.setValue(10000)
    w.bark_key.setText("ABCDEFG")
    assert w._save(quiet=True) is not None
    assert load_config().purchase.budget_total == 10000
    assert "ABCDEFG" not in paths.config_path().read_text(encoding="utf-8")

    # 官网辅助：填价格、勾选有货 → 推荐
    for i in range(w.price_table.rowCount()):
        cap = w.price_table.item(i, 0).text()
        w.price_table.item(i, 1).setText({"256GB": "7999", "512GB": "9999", "1TB": "11999"}[cap])
    w.avail_checks[("深空黑色", "256GB")].setChecked(True)
    w.avail_checks[("云白色", "512GB")].setChecked(True)
    assert "512GB" in w.recommend_label.text()
    w.close()


def test_mock_store_toggle(app):
    from duo_assistant.gui.main_window import MainWindow

    w = MainWindow()
    w.port.setValue(0 + 18765)
    w._toggle_mock()
    assert w.mock_server is not None
    w._toggle_mock()
    assert w.mock_server is None
    w.close()
