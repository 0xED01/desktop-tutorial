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

    def check(self, part, location):
        return mc.parse_fulfillment(self.responses.pop(0), part)


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
    client.responses = [unavailable, SAMPLE, SAMPLE]
    m = mc.Monitor([("MG8L4CH/A", "")], "上海 上海 闵行区", "", 30,
                   lambda e, p: events.append((e, p)), repeat_minutes=0, client=client)
    for _ in range(3):
        m.check_round()
    alerts = [p for e, p in events if e == "alert"]
    assert len(alerts) == 1 and alerts[0]["stores"][0]["name"] == "七宝"
    title, body = mc.build_alert_text(alerts[0])
    assert "有货" in title and "七宝" in body


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
