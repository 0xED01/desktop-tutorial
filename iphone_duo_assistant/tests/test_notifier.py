from duo_assistant.config import BarkConfig
from duo_assistant.notifier import BarkNotifier, LogOnlyNotifier, build_notifier


def test_bark_post_payload(store):
    cfg = BarkConfig(server=store.bark_server, level="critical", volume=9, call=True)
    n = BarkNotifier(cfg, "KEY123")
    assert n.send("标题", "正文 ¥9,999", url="http://x/y")
    msg = store.state.bark_messages[-1]
    assert msg["device_key"] == "KEY123"
    assert msg["title"] == "标题" and msg["body"] == "正文 ¥9,999"
    assert msg["level"] == "critical" and msg["volume"] == "9" and msg["call"] == "1"
    assert msg["url"] == "http://x/y"


def test_passive_level_has_no_call(store):
    n = BarkNotifier(BarkConfig(server=store.bark_server), "K")
    assert n.send("t", "b", level="passive")
    msg = store.state.bark_messages[-1]
    assert "call" not in msg and "volume" not in msg


def test_unreachable_server_returns_false():
    n = BarkNotifier(BarkConfig(server="http://127.0.0.1:9"), "K", timeout=1, retries=1)
    assert n.send("t", "b") is False


def test_build_notifier_without_key():
    assert isinstance(build_notifier(BarkConfig(), ""), LogOnlyNotifier)
