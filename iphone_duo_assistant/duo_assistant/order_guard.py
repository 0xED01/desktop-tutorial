"""防止重复下单。

原则是“最多一次”：
1. 进程锁：同一时间只允许一个抢购任务运行（跨进程文件锁）。
2. 订单账本：点击“提交订单”之前先落盘 submitting 记录；
   成功后改为 pending_payment；结果不明时保持 unknown。
   只要账本中存在未清除的记录，就拒绝再次提交，必须由用户确认后手动清除。
3. 出错时绝不自动重试提交订单。
"""

from __future__ import annotations

import json
import os
import sys
import threading
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path

from . import paths

ACTIVE_STATUSES = {"submitting", "pending_payment", "unknown", "manual"}


class DuplicateOrderError(RuntimeError):
    pass


class AlreadyRunningError(RuntimeError):
    pass


@dataclass
class OrderRecord:
    campaign: str
    status: str
    config: str
    total: int
    order_id: str = ""
    note: str = ""
    time: str = ""


class RunLock:
    """跨进程互斥锁，进程退出后由操作系统自动释放。"""

    def __init__(self, path: Path | None = None):
        self.path = path or paths.lock_path()
        self._fh = None

    def acquire(self) -> None:
        fh = open(self.path, "a+")
        try:
            if sys.platform == "win32":
                import msvcrt

                fh.seek(0)
                msvcrt.locking(fh.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl

                fcntl.flock(fh.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            fh.close()
            raise AlreadyRunningError("已有一个抢购任务在运行") from exc
        fh.seek(0)
        fh.truncate()
        fh.write(str(os.getpid()))
        fh.flush()
        self._fh = fh

    def release(self) -> None:
        if self._fh is None:
            return
        try:
            if sys.platform == "win32":
                import msvcrt

                self._fh.seek(0)
                msvcrt.locking(self._fh.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl

                fcntl.flock(self._fh.fileno(), fcntl.LOCK_UN)
        finally:
            self._fh.close()
            self._fh = None

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, *exc):
        self.release()


class OrderGuard:
    def __init__(self, path: Path | None = None):
        self.path = path or paths.ledger_path()
        self._lock = threading.Lock()

    def _read(self) -> list[OrderRecord]:
        if not self.path.exists():
            return []
        data = json.loads(self.path.read_text(encoding="utf-8"))
        return [OrderRecord(**r) for r in data.get("records", [])]

    def _write(self, records: list[OrderRecord]) -> None:
        tmp = self.path.with_suffix(".tmp")
        tmp.write_text(
            json.dumps({"records": [asdict(r) for r in records]}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        tmp.replace(self.path)

    def records(self) -> list[OrderRecord]:
        with self._lock:
            return self._read()

    def active(self, campaign: str) -> list[OrderRecord]:
        return [r for r in self.records() if r.campaign == campaign and r.status in ACTIVE_STATUSES]

    def ensure_can_submit(self, campaign: str) -> None:
        active = self.active(campaign)
        if active:
            r = active[-1]
            raise DuplicateOrderError(
                f"「{campaign}」已有未清除的订单记录（状态：{r.status}，{r.config}，"
                f"订单号：{r.order_id or '未知'}）。为防止重复下单，程序拒绝再次提交。"
                "如确认需要再次购买，请先在界面中清除记录。"
            )

    def _set(self, campaign: str, status: str, **kw) -> OrderRecord:
        with self._lock:
            records = self._read()
            rec = None
            for r in reversed(records):
                if r.campaign == campaign and r.status in ("submitting", "unknown"):
                    rec = r
                    break
            if rec is None:
                rec = OrderRecord(campaign=campaign, status=status, config="", total=0)
                records.append(rec)
            rec.status = status
            for k, v in kw.items():
                if v is not None:
                    setattr(rec, k, v)
            rec.time = datetime.now().isoformat(timespec="seconds")
            self._write(records)
            return rec

    def mark_submitting(self, campaign: str, config: str, total: int) -> OrderRecord:
        with self._lock:
            records = self._read()
            if any(r.campaign == campaign and r.status in ACTIVE_STATUSES for r in records):
                raise DuplicateOrderError(f"「{campaign}」已有未清除的订单记录")
            rec = OrderRecord(
                campaign=campaign,
                status="submitting",
                config=config,
                total=total,
                time=datetime.now().isoformat(timespec="seconds"),
            )
            records.append(rec)
            self._write(records)
            return rec

    def mark_pending_payment(self, campaign: str, order_id: str, config=None, total=None):
        return self._set(campaign, "pending_payment", order_id=order_id, config=config, total=total)

    def mark_unknown(self, campaign: str, note: str):
        return self._set(campaign, "unknown", note=note)

    def mark_rejected(self, campaign: str, note: str):
        """商店明确拒绝了提交（页面显示错误、订单未生成），记录不再阻止下次提交。"""
        return self._set(campaign, "rejected", note=note)

    def record_manual(self, campaign: str, config: str, total: int, order_id: str = "") -> OrderRecord:
        """官网辅助模式：用户手动下单后登记。"""
        with self._lock:
            records = self._read()
            rec = OrderRecord(
                campaign=campaign,
                status="manual",
                config=config,
                total=total,
                order_id=order_id,
                time=datetime.now().isoformat(timespec="seconds"),
            )
            records.append(rec)
            self._write(records)
            return rec

    def clear(self, campaign: str) -> int:
        """用户确认后清除该活动的所有未结记录（标记为 cleared，保留历史）。"""
        with self._lock:
            records = self._read()
            n = 0
            for r in records:
                if r.campaign == campaign and r.status in ACTIVE_STATUSES:
                    r.note = (r.note + f" [由用户清除，原状态 {r.status}]").strip()
                    r.status = "cleared"
                    n += 1
            self._write(records)
            return n
