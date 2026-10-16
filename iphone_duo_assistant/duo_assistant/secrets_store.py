"""Bark 设备密钥的本地存储。

- Windows：使用 DPAPI（CryptProtectData）加密，只有当前 Windows 用户能解密。
- 其他系统：明文保存在用户目录，文件权限设为 600（仅用于开发测试）。
密钥永远不会写入日志或配置文件。
"""

from __future__ import annotations

import base64
import json
import logging
import os
import sys
from pathlib import Path

from . import paths

log = logging.getLogger(__name__)


def _dpapi_protect(data: bytes) -> bytes:
    import ctypes
    from ctypes import wintypes

    class DATA_BLOB(ctypes.Structure):
        _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_char))]

    crypt32 = ctypes.windll.crypt32
    kernel32 = ctypes.windll.kernel32
    kernel32.LocalFree.argtypes = [ctypes.c_void_p]
    buf = ctypes.create_string_buffer(data, len(data))
    blob_in = DATA_BLOB(len(data), ctypes.cast(buf, ctypes.POINTER(ctypes.c_char)))
    blob_out = DATA_BLOB()
    # 0x1 = CRYPTPROTECT_UI_FORBIDDEN
    if not crypt32.CryptProtectData(
        ctypes.byref(blob_in), "iPhoneDuoAssistant", None, None, None, 0x1, ctypes.byref(blob_out)
    ):
        raise ctypes.WinError()
    try:
        return ctypes.string_at(blob_out.pbData, blob_out.cbData)
    finally:
        kernel32.LocalFree(ctypes.cast(blob_out.pbData, ctypes.c_void_p))


def _dpapi_unprotect(data: bytes) -> bytes:
    import ctypes
    from ctypes import wintypes

    class DATA_BLOB(ctypes.Structure):
        _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_char))]

    crypt32 = ctypes.windll.crypt32
    kernel32 = ctypes.windll.kernel32
    kernel32.LocalFree.argtypes = [ctypes.c_void_p]
    buf = ctypes.create_string_buffer(data, len(data))
    blob_in = DATA_BLOB(len(data), ctypes.cast(buf, ctypes.POINTER(ctypes.c_char)))
    blob_out = DATA_BLOB()
    if not crypt32.CryptUnprotectData(
        ctypes.byref(blob_in), None, None, None, None, 0x1, ctypes.byref(blob_out)
    ):
        raise ctypes.WinError()
    try:
        return ctypes.string_at(blob_out.pbData, blob_out.cbData)
    finally:
        kernel32.LocalFree(ctypes.cast(blob_out.pbData, ctypes.c_void_p))


def save_bark_key(key: str, path: Path | None = None) -> str:
    """保存密钥，返回所用的保护方式（dpapi / plain）。"""
    path = path or paths.secrets_path()
    raw = key.strip().encode("utf-8")
    scheme = "plain"
    blob = raw
    if sys.platform == "win32":
        try:
            blob = _dpapi_protect(raw)
            scheme = "dpapi"
        except OSError:
            log.exception("DPAPI 加密失败，改为明文保存")
    record = {"bark_key": {"scheme": scheme, "data": base64.b64encode(blob).decode("ascii")}}
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(record), encoding="utf-8")
    if sys.platform != "win32":
        os.chmod(tmp, 0o600)
    tmp.replace(path)
    return scheme


def load_bark_key(path: Path | None = None) -> str:
    path = path or paths.secrets_path()
    if not path.exists():
        return ""
    try:
        entry = json.loads(path.read_text(encoding="utf-8"))["bark_key"]
        blob = base64.b64decode(entry["data"])
        if entry["scheme"] == "dpapi":
            blob = _dpapi_unprotect(blob)
        return blob.decode("utf-8")
    except (OSError, ValueError, KeyError):
        log.exception("读取 Bark 密钥失败，请在界面中重新输入")
        return ""


def mask(key: str) -> str:
    if not key:
        return "(未设置)"
    if len(key) <= 6:
        return "***"
    return key[:3] + "***" + key[-2:]
