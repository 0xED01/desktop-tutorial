"""安全检查识别：验证码、登录、异常访问、排队。

识别到之后只做三件事：截图、Bark 通知、暂停等待人工处理（排队则被动等待）。
本模块不包含、也不会加入任何破解验证码或绕过排队/限流的逻辑。
"""

from __future__ import annotations

from dataclasses import dataclass

KIND_TEXT = {
    "captcha": "验证码 / 安全验证",
    "login": "需要登录 / 账户验证",
    "blocked": "异常访问提示 / 访问受限",
    "queue": "排队等候",
}

# 通用特征（对任何网站都只做识别，不做处理）
_SELECTORS = {
    "captcha": [
        "[data-security-check='captcha']",
        "iframe[src*='captcha' i]",
        "iframe[src*='geetest' i]",
        "iframe[title*='验证']",
        "input[name*='captcha' i]",
        "#captcha",
    ],
    "blocked": ["[data-security-check='rate-limit']"],
}
_KEYWORDS = {
    "captcha": ["请完成安全验证", "请完成验证", "人机验证", "拖动滑块", "verify you are human"],
    "blocked": ["访问过于频繁", "异常访问", "请求过于频繁", "unusual activity", "Access Denied"],
    "queue": ["正在排队", "排队中", "当前访问人数较多"],
}
_LOGIN_URL_PARTS = ["/signin", "idmsa.apple.com", "appleid.apple.com"]


@dataclass(frozen=True)
class SecurityCheck:
    kind: str
    detail: str

    @property
    def title(self) -> str:
        return KIND_TEXT.get(self.kind, self.kind)


def detect_security_check(page) -> SecurityCheck | None:
    """检查当前页面。页面正在跳转时可能抛出异常，由调用方处理。"""
    data_page = page.evaluate("() => document.body ? document.body.dataset.page || '' : ''")
    if data_page == "captcha":
        return SecurityCheck("captcha", "页面要求完成安全验证")
    if data_page == "blocked":
        return SecurityCheck("blocked", "网站提示访问过于频繁/异常访问")
    if data_page == "signin":
        return SecurityCheck("login", "需要登录账户")
    if data_page == "queue":
        return SecurityCheck("queue", "网站正在排队")

    for kind, sels in _SELECTORS.items():
        for sel in sels:
            if page.locator(sel).count() > 0:
                return SecurityCheck(kind, f"页面包含元素 {sel}")

    url = page.url.lower()
    if any(part in url for part in _LOGIN_URL_PARTS):
        return SecurityCheck("login", "跳转到了登录页面")

    text = page.evaluate(
        "() => document.body ? document.body.innerText.slice(0, 6000) : ''"
    )
    for kind, words in _KEYWORDS.items():
        for w in words:
            if w.lower() in text.lower():
                return SecurityCheck(kind, f"页面出现“{w}”")
    return None
