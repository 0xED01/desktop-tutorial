"""程序入口。

    iPhoneDuoAssistant.exe                    启动图形界面
    iPhoneDuoAssistant.exe --mock-store       只运行模拟商店（默认端口 8765）
    iPhoneDuoAssistant.exe --self-test        无界面自检：模拟商店 + 浏览器 + 假 Bark
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from pathlib import Path


def run_self_test(out: Path | None, headed: bool = False, channel: str | None = None) -> int:
    from .automation.mock_flow import PurchaseFlow
    from .config import AppConfig
    from .mock_store import MockStoreServer
    from .notifier import BarkNotifier
    from .order_guard import OrderGuard

    log = logging.getLogger("selftest")
    srv = MockStoreServer(port=0, preset="race").start()
    report: dict = {"steps": []}
    ok = False
    try:
        cfg = AppConfig()
        cfg.purchase.product_name = f"自检-{int(time.time())}"  # 独立活动名，不影响真实记录
        cfg.purchase.max_run_minutes = 3
        cfg.browser.headless = not headed
        if channel:
            cfg.browser.channel = channel
        cfg.bark.server = srv.bark_server
        flow = PurchaseFlow(
            cfg,
            srv.product_url,
            BarkNotifier(cfg.bark, "SELFTEST", retries=0),
            guard=OrderGuard(),
            emit=lambda k, m: report["steps"].append(f"[{k}] {m}"),
            keep_browser_open=False,
        )
        result = flow.run()
        OrderGuard().clear(cfg.purchase.product_name)
        orders = srv.state.orders
        ok = (
            result.success
            and len(orders) == 1
            and orders[0].status == "pending_payment"
            and any("待付款" in m.get("title", "") for m in srv.state.bark_messages)
        )
        report.update(
            success=ok,
            message=result.message,
            orders=[o.__dict__ for o in orders],
            bark=srv.state.bark_messages,
        )
    except Exception as exc:
        log.exception("自检异常")
        report.update(success=False, message=f"{exc.__class__.__name__}: {exc}")
    finally:
        srv.stop()
    text = json.dumps(report, ensure_ascii=False, indent=2, default=str)
    if out:
        out.write_text(text, encoding="utf-8")
    try:
        print(text)
    except Exception:
        pass
    return 0 if ok else 1


def run_mock_store(port: int, preset: str) -> int:
    from .mock_store import MockStoreServer

    srv = MockStoreServer(port=port, preset=preset)
    print(f"模拟商店：{srv.product_url}\n管理页：{srv.base_url}/__admin\n按 Ctrl+C 退出")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


def run_gui() -> int:
    from PySide6.QtWidgets import QApplication

    from .gui.main_window import MainWindow

    app = QApplication(sys.argv)
    app.setApplicationName("iPhoneDuoAssistant")
    win = MainWindow()
    win.show()
    return app.exec()


def main(argv: list[str] | None = None) -> int:
    from .logging_setup import setup_logging

    parser = argparse.ArgumentParser(description="iPhone Duo 抢购助手")
    parser.add_argument("--self-test", action="store_true", help="无界面自检")
    parser.add_argument("--self-test-out", type=Path, help="自检结果 JSON 输出文件")
    parser.add_argument("--headed", action="store_true", help="自检时显示浏览器")
    parser.add_argument("--channel", choices=["msedge", "chrome", "chromium"], help="自检使用的浏览器")
    parser.add_argument("--mock-store", action="store_true", help="只运行模拟商店")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--preset", default="normal")
    args = parser.parse_args(argv)

    setup_logging()
    if args.self_test:
        return run_self_test(args.self_test_out, args.headed, args.channel)
    if args.mock_store:
        return run_mock_store(args.port, args.preset)
    return run_gui()


if __name__ == "__main__":
    sys.exit(main())
