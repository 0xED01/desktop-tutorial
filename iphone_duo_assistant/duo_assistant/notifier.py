"""Bark 推送。使用 Bark 服务端的 POST /push JSON 接口。"""

from __future__ import annotations

import json
import logging
import time
import urllib.error
import urllib.request
from dataclasses import dataclass

from .config import BarkConfig
from .secrets_store import mask

log = logging.getLogger(__name__)


@dataclass
class BarkMessage:
    title: str
    body: str
    level: str | None = None
    url: str | None = None


class Notifier:
    """接口：send() 失败不抛异常，只返回 False，避免通知故障打断主流程。"""

    def send(self, title: str, body: str, level: str | None = None, url: str | None = None) -> bool:
        raise NotImplementedError


class LogOnlyNotifier(Notifier):
    def send(self, title, body, level=None, url=None) -> bool:
        log.warning("[未配置 Bark，仅记录] %s | %s", title, body)
        return False


class BarkNotifier(Notifier):
    def __init__(self, cfg: BarkConfig, device_key: str, timeout: float = 8.0, retries: int = 2):
        if not device_key:
            raise ValueError("Bark 设备密钥为空")
        self.cfg = cfg
        self.key = device_key
        self.timeout = timeout
        self.retries = retries
        self.sent: list[BarkMessage] = []

    def _payload(self, title: str, body: str, level: str | None, url: str | None) -> dict:
        level = level or self.cfg.level
        payload = {
            "device_key": self.key,
            "title": title,
            "body": body,
            "level": level,
            "group": self.cfg.group,
            "isArchive": "1",
        }
        if self.cfg.sound:
            payload["sound"] = self.cfg.sound
        if level == "critical":
            payload["volume"] = str(max(0, min(10, int(self.cfg.volume))))
        if self.cfg.call and level in ("critical", "timeSensitive"):
            payload["call"] = "1"
        if url:
            payload["url"] = url
        return payload

    def send(self, title, body, level=None, url=None) -> bool:
        endpoint = self.cfg.server.rstrip("/") + "/push"
        data = json.dumps(self._payload(title, body, level, url)).encode("utf-8")
        for attempt in range(self.retries + 1):
            req = urllib.request.Request(
                endpoint,
                data=data,
                headers={"Content-Type": "application/json; charset=utf-8"},
                method="POST",
            )
            try:
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                    reply = json.loads(resp.read().decode("utf-8") or "{}")
                if reply.get("code") == 200:
                    log.info("Bark 已推送(key=%s): %s", mask(self.key), title)
                    self.sent.append(BarkMessage(title, body, level, url))
                    return True
                log.error("Bark 返回错误: %s", reply)
            except (urllib.error.URLError, OSError, ValueError) as exc:
                log.error("Bark 推送失败(第 %d 次): %s", attempt + 1, exc)
            if attempt < self.retries:
                time.sleep(1.0 * (attempt + 1))
        return False


def build_notifier(cfg: BarkConfig, device_key: str) -> Notifier:
    if device_key:
        return BarkNotifier(cfg, device_key)
    return LogOnlyNotifier()
