from __future__ import annotations

import logging

from PySide6.QtCore import QThread, Signal

from ..automation.mock_flow import FlowResult, PurchaseFlow
from ..config import AppConfig
from ..controller import RunController
from ..notifier import Notifier

log = logging.getLogger(__name__)


class FlowWorker(QThread):
    """在独立线程运行 Playwright 同步流程，界面不会卡住。"""

    event = Signal(str, str)
    done = Signal(object)

    def __init__(self, cfg: AppConfig, product_url: str, notifier: Notifier, controller: RunController):
        super().__init__()
        self.cfg = cfg
        self.product_url = product_url
        self.notifier = notifier
        self.controller = controller

    def run(self) -> None:
        def emit(kind: str, msg: str) -> None:
            log.info("[%s] %s", kind, msg)
            self.event.emit(kind, msg)

        try:
            flow = PurchaseFlow(
                self.cfg, self.product_url, self.notifier, controller=self.controller, emit=emit
            )
            result = flow.run()
        except Exception as exc:  # 兜底，确保界面能收到结束信号
            log.exception("运行线程异常")
            result = FlowResult(False, f"运行线程异常：{exc}")
        self.done.emit(result)
