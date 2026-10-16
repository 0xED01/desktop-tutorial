from __future__ import annotations

import logging
import logging.handlers
import sys

from . import paths

_FORMAT = "%(asctime)s [%(levelname)s] %(name)s: %(message)s"


def setup_logging(level: int = logging.INFO) -> logging.Logger:
    root = logging.getLogger()
    if getattr(root, "_duo_configured", False):
        return root
    root.setLevel(level)
    fh = logging.handlers.RotatingFileHandler(
        paths.logs_dir() / "app.log", maxBytes=2_000_000, backupCount=5, encoding="utf-8"
    )
    fh.setFormatter(logging.Formatter(_FORMAT))
    root.addHandler(fh)
    if sys.stderr is not None:  # 打包成 --windowed EXE 时没有控制台
        sh = logging.StreamHandler()
        sh.setFormatter(logging.Formatter(_FORMAT))
        root.addHandler(sh)
    root._duo_configured = True  # type: ignore[attr-defined]
    return root
