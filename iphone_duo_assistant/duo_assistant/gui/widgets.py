from __future__ import annotations

import logging

from PySide6.QtCore import QObject, Qt, Signal
from PySide6.QtWidgets import (
    QHBoxLayout,
    QInputDialog,
    QListWidget,
    QListWidgetItem,
    QPushButton,
    QVBoxLayout,
    QWidget,
)


class PriorityList(QWidget):
    """可勾选、可排序的列表：勾选 = 可接受；从上到下 = 优先级从高到低。"""

    changed = Signal()

    def __init__(self, item_name: str, parent=None):
        super().__init__(parent)
        self.item_name = item_name
        self.list = QListWidget()
        self.list.setMaximumHeight(150)
        self.list.itemChanged.connect(lambda *_: self.changed.emit())
        btns = QVBoxLayout()
        for text, fn in (
            ("上移", lambda: self._move(-1)),
            ("下移", lambda: self._move(1)),
            (f"添加{item_name}", self._add),
            ("删除", self._remove),
        ):
            b = QPushButton(text)
            b.clicked.connect(fn)
            btns.addWidget(b)
        btns.addStretch()
        lay = QHBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.addWidget(self.list, 1)
        lay.addLayout(btns)

    def set_items(self, all_items: list[str], checked: list[str]) -> None:
        self.list.blockSignals(True)
        self.list.clear()
        ordered = [c for c in checked if c in all_items] + [a for a in all_items if a not in checked]
        for name in ordered:
            it = QListWidgetItem(name)
            it.setFlags(it.flags() | Qt.ItemIsUserCheckable)
            it.setCheckState(Qt.Checked if name in checked else Qt.Unchecked)
            self.list.addItem(it)
        self.list.blockSignals(False)
        self.changed.emit()

    def all_items(self) -> list[str]:
        return [self.list.item(i).text() for i in range(self.list.count())]

    def checked_items(self) -> list[str]:
        return [
            self.list.item(i).text()
            for i in range(self.list.count())
            if self.list.item(i).checkState() == Qt.Checked
        ]

    def _move(self, delta: int) -> None:
        row = self.list.currentRow()
        new = row + delta
        if row < 0 or not 0 <= new < self.list.count():
            return
        it = self.list.takeItem(row)
        self.list.insertItem(new, it)
        self.list.setCurrentRow(new)
        self.changed.emit()

    def _add(self) -> None:
        name, ok = QInputDialog.getText(self, f"添加{self.item_name}", f"{self.item_name}名称：")
        name = name.strip()
        if ok and name and name not in self.all_items():
            it = QListWidgetItem(name)
            it.setFlags(it.flags() | Qt.ItemIsUserCheckable)
            it.setCheckState(Qt.Checked)
            self.list.addItem(it)
            self.changed.emit()

    def _remove(self) -> None:
        row = self.list.currentRow()
        if row >= 0:
            self.list.takeItem(row)
            self.changed.emit()


class LogBridge(QObject):
    line = Signal(str)


class QtLogHandler(logging.Handler):
    """把日志安全地转发到界面线程。"""

    def __init__(self):
        super().__init__(logging.INFO)
        self.bridge = LogBridge()
        self.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s", "%H:%M:%S"))

    def emit(self, record):
        try:
            self.bridge.line.emit(self.format(record))
        except RuntimeError:
            pass
