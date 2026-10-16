package com.applestock.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleTest {
    private val sample = javaClass.getResource("/pickup_sample.json")!!.readText()

    private fun available(json: String, store: String) =
        json.replaceFirst(Regex("(\"storeNumber\":\\s*\"$store\"[\\s\\S]*?\"pickupDisplay\":\\s*)\"unavailable\""), "$1\"available\"")

    @Test
    fun parsesRealResponse() {
        val res = Apple.parsePickup(sample, listOf("MK2U4CH/A", "BADXXCH/A"))
        assertEquals(listOf("MK2U4CH/A"), res.keys.toList())
        val stores = res.getValue("MK2U4CH/A")
        assertEquals(12, stores.size)
        assertEquals("香港广场", stores[0].name)
        assertEquals("iPhone Duo 1TB 夜空色", stores[0].title)
        assertEquals("暂无供应", stores[0].quote)
        assertTrue(stores.none { it.available })
        assertEquals(1, Apple.filterStores(stores, "浦东").size)
    }

    @Test(expected = ApiException::class)
    fun errorMessageRaises() {
        Apple.parsePickup("""{"body":{"errorMessage":"请输入有效的省/市名称或邮政编码。"}}""", listOf("X"))
    }

    @Test
    fun parsesParts() {
        val text = "MG8L4CH/A 17Pro 深蓝 256\nhttps://www.apple.com.cn/shop/product/mfyn4ch/a\n乱写\nMG8L4CH/A 重复"
        assertEquals(listOf("MG8L4CH/A" to "17Pro 深蓝 256", "MFYN4CH/A" to ""), Apple.parseParts(text))
    }

    @Test
    fun alertsOnlyOnTransition() {
        val responses = ArrayDeque(listOf(sample, available(sample, "R389"), available(sample, "R389")))
        val alerts = ArrayList<AlertInfo>()
        val logs = ArrayList<String>()
        val m = Monitor(
            listOf("MK2U4CH/A" to ""), "上海 上海 黄浦区", "", 0,
            object : MonitorEvents {
                override fun log(text: String) { logs.add(text) }
                override fun row(row: PartRow) {}
                override fun alert(info: AlertInfo) { alerts.add(info) }
            },
            checker = { parts, _ -> Apple.parsePickup(responses.removeFirst(), parts) },
            pause = {},
        )
        repeat(3) { m.checkRound() }
        assertEquals(1, alerts.size)
        assertEquals("浦东", alerts[0].stores.single().name)
        val (title, body) = alerts[0].text()
        assertTrue(title.contains("iPhone Duo") && body.contains("浦东"))
        assertTrue(logs.last().contains("1 个型号有货"))
    }

    @Test
    fun extractsPartsFromPage() {
        val html = """window.X={"products":[{"partNumber":"MG8L4CH/A","dimensionCapacity":"256gb",""" +
            """"dimensionColor":"deepblue"}],"displayValues":{"dimensionColor":{"deepblue":""" +
            """{"value":"深蓝色"}},"dimensionCapacity":{"256gb":{"value":"256<small>GB</small>"}}}}"""
        assertEquals(listOf("MG8L4CH/A" to "256GB 深蓝色"), Apple.extractParts(html))
    }
}
