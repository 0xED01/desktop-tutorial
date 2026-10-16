"""本地数据目录。所有配置、密钥、日志、截图都只保存在本机。"""

from __future__ import annotations

import os
import sys
from pathlib import Path

ENV_HOME = "DUO_ASSISTANT_HOME"


def data_dir() -> Path:
    override = os.environ.get(ENV_HOME)
    if override:
        base = Path(override)
    elif sys.platform == "win32":
        base = Path(os.environ.get("APPDATA", Path.home())) / "iPhoneDuoAssistant"
    else:
        base = Path.home() / ".iphone_duo_assistant"
    base.mkdir(parents=True, exist_ok=True)
    return base


def config_path() -> Path:
    return data_dir() / "config.json"


def secrets_path() -> Path:
    return data_dir() / "secrets.json"


def ledger_path() -> Path:
    return data_dir() / "order_ledger.json"


def lock_path() -> Path:
    return data_dir() / "run.lock"


def logs_dir() -> Path:
    d = data_dir() / "logs"
    d.mkdir(parents=True, exist_ok=True)
    return d


def screenshots_dir() -> Path:
    d = data_dir() / "screenshots"
    d.mkdir(parents=True, exist_ok=True)
    return d
