"""运行控制：暂停 / 继续 / 停止。线程安全。"""

from __future__ import annotations

import threading
import time


class StopRequested(Exception):
    pass


class RunController:
    def __init__(self):
        self._running = threading.Event()
        self._running.set()
        self._stop = threading.Event()
        self.pause_reason = ""

    def pause(self, reason: str = "用户暂停") -> None:
        self.pause_reason = reason
        self._running.clear()

    def resume(self) -> None:
        self.pause_reason = ""
        self._running.set()

    def stop(self) -> None:
        self._stop.set()
        self._running.set()  # 唤醒等待中的线程

    @property
    def paused(self) -> bool:
        return not self._running.is_set()

    @property
    def stopped(self) -> bool:
        return self._stop.is_set()

    def checkpoint(self) -> None:
        """在每个步骤之间调用：暂停时阻塞，停止时抛出 StopRequested。"""
        while not self._running.wait(0.2):
            pass
        if self._stop.is_set():
            raise StopRequested()

    def sleep(self, seconds: float) -> None:
        """可被停止打断、并遵守暂停的等待。"""
        end = time.monotonic() + seconds
        while True:
            self.checkpoint()
            remaining = end - time.monotonic()
            if remaining <= 0:
                return
            self._stop.wait(min(0.2, remaining))
