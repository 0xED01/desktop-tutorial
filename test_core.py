"""核心逻辑测试：python test_core.py"""

import monitor_core as mc

SAMPLE = {
    "head": {"status": "200"},
    "body": {"content": {"pickupMessage": {"stores": [
        {"storeNumber": "R705", "storeName": "七宝", "city": "上海",
         "partsAvailability": {"MG8L4CH/A": {
             "pickupDisplay": "available", "pickupSearchQuote": "今天可取货",
             "messageTypes": {"regular": {"storePickupProductTitle": "iPhone 17 Pro 256GB 深蓝色"}}}}},
        {"storeNumber": "R389", "storeName": "浦东", "city": "上海",
         "partsAvailability": {"MG8L4CH/A": {
             "pickupDisplay": "unavailable", "pickupSearchQuote": "<span>暂无供应</span>"}}},
    ]}}},
}


class FakeClient:
    def __init__(self):
        self.responses = []

    def check(self, parts, location):
        return mc.parse_pickup(self.responses.pop(0), parts)


def test_parse_parts():
    text = "MG8L4CH/A 17Pro 深蓝 256\nhttps://www.apple.com.cn/shop/product/mfyn4ch/a\n乱写一行\nMG8L4CH/A 重复"
    assert mc.parse_parts(text) == [("MG8L4CH/A", "17Pro 深蓝 256"), ("MFYN4CH/A", "")]


def test_parse_fulfillment():
    res = mc.parse_fulfillment(SAMPLE, "MG8L4CH/A")
    assert [r["available"] for r in res] == [True, False]
    assert res[0]["title"] == "iPhone 17 Pro 256GB 深蓝色"
    assert res[1]["quote"] == "暂无供应"
    assert mc.filter_stores(res, "浦东") == [res[1]]
    try:
        mc.parse_fulfillment({"body": {"content": {"pickupMessage": {"errorMessage": "无效"}}}}, "X")
        raise AssertionError("should raise")
    except mc.ApiError:
        pass


def test_monitor_alerts_on_transition():
    import copy
    events = []
    unavailable = copy.deepcopy(SAMPLE)
    unavailable["body"]["content"]["pickupMessage"]["stores"][0]["partsAvailability"]["MG8L4CH/A"]["pickupDisplay"] = "unavailable"
    client = FakeClient()
    client.responses = [unavailable, SAMPLE, SAMPLE, unavailable]
    m = mc.Monitor([("MG8L4CH/A", "")], "上海 上海 闵行区", "", 30,
                   lambda e, p: events.append((e, p)), repeat_minutes=0, client=client)
    for _ in range(4):
        m.check_round()
    assert any("这次有货最多持续约" in p for e, p in events if e == "log")
    alerts = [p for e, p in events if e == "alert"]
    assert len(alerts) == 1 and alerts[0]["stores"][0]["name"] == "七宝"
    title, body = mc.build_alert_text(alerts[0])
    assert "有货" in title and "七宝" in body


def test_backoff_and_outage():
    events = []
    m = mc.Monitor([("MG8L4CH/A", "")], "上海 上海 黄浦区", "", 20,
                   lambda e, p: events.append((e, p)), outage_minutes=3, client=FakeClient())
    m.last_error = mc.ApiError("HTTP 541", kind="limited")
    t = 1000.0
    waits = []
    for _ in range(6):  # 被限制：间隔翻倍，最长 300 秒
        waits.append(m.after_round(False, now=t))
        t += waits[-1]
    assert waits == [40, 80, 160, 300, 300, 300]
    assert [p for e, p in events if e == "outage"], "连续失败超过 3 分钟应发出中断通知"
    assert sum(1 for e, _ in events if e == "outage") == 1
    m.after_round(True, now=t)
    assert [p for e, p in events if e == "recovered"]
    assert m.cur_interval == 240  # 恢复后逐步调回
    m.last_error = mc.ApiError("网络错误", kind="network")
    assert m.after_round(False, now=t + 10) == 20  # 断网不降速


def test_parse_pickup_new_format():
    data = {"head": {"status": "200"}, "body": {"stores": SAMPLE["body"]["content"]["pickupMessage"]["stores"]}}
    res = mc.parse_pickup(data, ["MG8L4CH/A", "BADXXCH/A"])
    assert list(res) == ["MG8L4CH/A"] and res["MG8L4CH/A"][0]["available"]
    assert mc.parse_pickup({"body": {"content": {}}}, ["X"]) == {}
    try:
        mc.parse_pickup({"body": {"errorMessage": "请输入有效的省/市名称或邮政编码。"}}, ["X"])
        raise AssertionError("should raise")
    except mc.ApiError:
        pass


def test_extract_parts_from_html():
    html = ('window.X={"products":[{"partNumber":"MG8L4CH/A","dimensionColor":"deepblue",'
            '"dimensionCapacity":"256gb"}],"displayValues":{"dimensionColor":{"deepblue":'
            '{"value":"\\u6df1\\u84dd\\u8272"}},"dimensionCapacity":{"256gb":{"value":"256GB"}}}}')
    assert mc.extract_parts_from_html(html) == [("MG8L4CH/A", "深蓝色 256GB")]


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("OK", name)
    print("全部通过")
