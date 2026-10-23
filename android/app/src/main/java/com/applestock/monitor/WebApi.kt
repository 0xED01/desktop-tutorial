package com.applestock.monitor

import org.json.JSONArray
import org.json.JSONObject

/** 网页控制要用到的操作，由 MonitorService 实现（都会切回主线程执行）。 */
interface WebControl {
    fun startMonitoring()
    fun stopMonitoring()
    fun testAlert()
    fun stopAlarm()
}

/** 网页控制的路由。所有 /api/ 请求都必须带正确的访问码，防止同一 Wi-Fi 下的其他人操作。 */
class WebApi(
    private val prefs: Prefs,
    private val control: WebControl,
    private val page: () -> ByteArray,
) {
    fun handle(req: HttpRequest): HttpResponse {
        if (req.method == "GET" && (req.path == "/" || req.path == "/index.html")) {
            return HttpResponse(200, "text/html; charset=utf-8", page())
        }
        if (!req.path.startsWith("/api/")) return HttpResponse.text(404, "Not Found")
        val key = req.headers["x-key"] ?: req.query["key"] ?: ""
        if (key.isEmpty() || key != prefs.webKey) {
            Thread.sleep(1000) // 拖慢暴力猜访问码
            return HttpResponse.json("""{"ok":false,"error":"访问码不正确"}""", 401)
        }
        return when ("${req.method} ${req.path}") {
            "GET /api/status" -> HttpResponse.json(status().toString())
            "POST /api/config" -> saveConfig(req.body)
            "POST /api/start" -> {
                if (Apple.parseParts(prefs.parts).isEmpty() || prefs.location.isBlank()) {
                    HttpResponse.json("""{"ok":false,"error":"请先填写型号和取货地区"}""", 400)
                } else {
                    control.startMonitoring()
                    ok()
                }
            }
            "POST /api/stop" -> { control.stopMonitoring(); ok() }
            "POST /api/test" -> { control.testAlert(); ok() }
            "POST /api/stopalarm" -> { control.stopAlarm(); ok() }
            else -> HttpResponse.text(404, "Not Found")
        }
    }

    private fun ok() = HttpResponse.json("""{"ok":true}""")

    private fun status(): JSONObject {
        val rows = JSONArray()
        for (r in State.rows()) {
            rows.put(JSONObject()
                .put("part", r.part)
                .put("label", r.label)
                .put("total", r.total)
                .put("time", r.time)
                .put("url", Apple.productUrl(r.part))
                .put("inStock", JSONArray(r.inStock.map { JSONObject().put("name", it.name).put("quote", it.quote) })))
        }
        val bark = prefs.barkUrl
        return JSONObject()
            .put("running", State.running)
            .put("status", if (State.running) State.status else "未在监控")
            .put("alarm", AlarmPlayer.isPlaying)
            .put("rows", rows)
            .put("logs", JSONArray(State.logs().takeLast(80).reversed()))
            .put("config", JSONObject()
                .put("parts", prefs.parts)
                .put("location", prefs.location)
                .put("storeFilter", prefs.storeFilter)
                .put("interval", prefs.interval)
                .put("repeatMinutes", prefs.repeatMinutes)
                // Bark 地址里含密钥，只回传打码后的样子
                .put("bark", if (bark.isBlank()) "" else bark.take(22) + "****"))
    }

    private fun saveConfig(body: String): HttpResponse {
        val o = try {
            JSONObject(body)
        } catch (_: Exception) {
            return HttpResponse.json("""{"ok":false,"error":"数据格式不对"}""", 400)
        }
        if (o.has("parts")) prefs.parts = o.optString("parts").trim()
        if (o.has("location")) prefs.location = o.optString("location").trim()
        if (o.has("storeFilter")) prefs.storeFilter = o.optString("storeFilter").trim()
        if (o.has("interval")) prefs.interval = o.optInt("interval", 30).coerceIn(15, 3600)
        if (o.has("repeatMinutes")) prefs.repeatMinutes = o.optInt("repeatMinutes", 5).coerceIn(0, 600)
        val bark = o.optString("bark", "").trim()
        // 打码的值原样传回来时不覆盖；填空字符串表示清除
        if (o.has("bark") && !bark.endsWith("****")) prefs.barkUrl = bark
        State.log("网页上修改了设置" + if (State.running) "（重新开始监控后生效）" else "")
        return ok()
    }
}
