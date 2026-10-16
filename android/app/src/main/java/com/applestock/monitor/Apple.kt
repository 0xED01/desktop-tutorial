package com.applestock.monitor

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 苹果中国大陆官网门店取货库存查询（与 Windows 版 monitor_core.py 逻辑一致）。 */

data class StoreStatus(
    val store: String,
    val name: String,
    val city: String,
    val available: Boolean,
    val quote: String,
    val title: String,
)

class ApiException(message: String) : Exception(message)

object Apple {
    const val BASE = "https://www.apple.com.cn"
    const val BATCH_SIZE = 10
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

    val PART_RE = Regex("""\b([A-Z0-9]{4,6}(?:CH|ZA|ZP)/A)\b""", RegexOption.IGNORE_CASE)
    private val STORE_RE = Regex("""^R\d{3,4}$""", RegexOption.IGNORE_CASE)

    @Volatile
    private var warmed = false

    init {
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
        }
    }

    /** 每行一个型号，后面可写备注；也可以粘贴带型号的链接。返回 [(型号, 备注)]，自动去重。 */
    fun parseParts(text: String): List<Pair<String, String>> {
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        for (line in text.lines()) {
            val m = PART_RE.find(line) ?: continue
            val part = m.groupValues[1].uppercase()
            if (!seen.add(part)) continue
            var note = (line.substring(0, m.range.first) + " " + line.substring(m.range.last + 1))
                .trim { it.isWhitespace() || it in "-—:：,，|" }
            if (note.startsWith("http")) note = ""
            out.add(part to note)
        }
        return out
    }

    fun stripHtml(s: String): String = s.replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        .trim()

    fun productUrl(part: String) = "$BASE/shop/product/$part"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun httpGet(url: String, accept: String = "*/*"): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        c.setRequestProperty("Referer", "$BASE/shop/buy-iphone")
        c.setRequestProperty("Accept", accept)
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to body
        } finally {
            c.disconnect()
        }
    }

    private fun warmUp() {
        try {
            httpGet("$BASE/shop/buy-iphone")
        } catch (_: IOException) {
        }
        warmed = true
    }

    /** 查询一批型号（最多 BATCH_SIZE 个），返回 {型号: [门店状态]}。 */
    fun check(parts: List<String>, location: String): Map<String, List<StoreStatus>> {
        if (!warmed) warmUp()
        val url = StringBuilder("$BASE/shop/retail/pickup-message?pl=true&searchNearby=true")
        parts.forEachIndexed { i, p -> url.append("&parts.$i=").append(enc(p)) }
        val loc = location.trim()
        if (STORE_RE.matches(loc)) url.append("&store=").append(loc.uppercase())
        else url.append("&location=").append(enc(loc))
        val (code, body) = try {
            httpGet(url.toString(), "application/json, text/plain, */*")
        } catch (e: IOException) {
            throw ApiException("网络错误：${e.javaClass.simpleName}")
        }
        if (code in listOf(403, 429, 503, 541)) {
            // 被限制时不再重新访问首页“预热”，少发请求才能更快恢复
            throw ApiException("HTTP $code：查询太频繁被苹果暂时限制，自动降速中")
        }
        if (code != 200) throw ApiException("HTTP $code")
        return parsePickup(body, parts)
    }

    fun parsePickup(text: String, parts: List<String>): Map<String, List<StoreStatus>> {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw ApiException("返回的不是 JSON（可能被苹果暂时限制），自动降速中")
        }
        var body = root.optJSONObject("body") ?: JSONObject()
        if (!body.has("stores")) {
            body = body.optJSONObject("content")?.optJSONObject("pickupMessage") ?: body
        }
        val err = body.optString("errorMessage", "")
        if (err.isNotEmpty()) throw ApiException(stripHtml(err))
        val out = LinkedHashMap<String, MutableList<StoreStatus>>()
        val stores = body.optJSONArray("stores") ?: return out
        for (i in 0 until stores.length()) {
            val st = stores.optJSONObject(i) ?: continue
            val pa = st.optJSONObject("partsAvailability") ?: continue
            val number = st.optString("storeNumber", "")
            for (part in parts) {
                val p = pa.optJSONObject(part) ?: continue
                val mt = p.optJSONObject("messageTypes")
                var title = ""
                for (k in listOf("regular", "compact")) {
                    title = mt?.optJSONObject(k)?.optString("storePickupProductTitle", "") ?: ""
                    if (title.isNotEmpty()) break
                }
                out.getOrPut(part) { mutableListOf() }.add(
                    StoreStatus(
                        store = number,
                        name = st.optString("storeName", "").trim().ifEmpty { number },
                        city = st.optString("city", ""),
                        available = p.optString("pickupDisplay", "") == "available",
                        quote = stripHtml(p.optString("pickupSearchQuote", "")),
                        title = stripHtml(title),
                    )
                )
            }
        }
        return out
    }

    /** 按门店名关键字过滤（逗号/空格分隔，空则不过滤）。 */
    fun filterStores(list: List<StoreStatus>, keywords: String): List<StoreStatus> {
        val kws = keywords.split(Regex("[,，\\s]+")).filter { it.isNotEmpty() }
        if (kws.isEmpty()) return list
        return list.filter { s -> kws.any { (s.name + s.store + s.city).contains(it, ignoreCase = true) } }
    }

    /** 从官网购买页（如 /shop/buy-iphone/iphone-17-pro）提取所有型号及配置。 */
    fun lookupParts(pageUrl: String): List<Pair<String, String>> {
        val (code, html) = httpGet(pageUrl, "text/html")
        if (code != 200) throw ApiException("HTTP $code")
        return extractParts(html)
    }

    private fun jsonStr(s: String): String = try {
        JSONObject("{\"v\":\"$s\"}").getString("v")
    } catch (e: JSONException) {
        s
    }

    fun extractParts(html: String): List<Pair<String, String>> {
        val names = HashMap<String, String>()
        val nameRe = Regex("\"([A-Za-z0-9_\\-]+)\"\\s*:\\s*\\{\\s*\"value\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        for (m in nameRe.findAll(html)) names.putIfAbsent(m.groupValues[1], jsonStr(m.groupValues[2]))
        val items = LinkedHashMap<String, String>()
        val partRe = Regex("\\{[^{}]*?\"partNumber\"\\s*:\\s*\"([A-Z0-9]+/A)\"[^{}]*\\}")
        for (m in partRe.findAll(html)) {
            val part = m.groupValues[1]
            if (items.containsKey(part)) continue
            val obj = try {
                JSONObject(m.value)
            } catch (e: JSONException) {
                null
            }
            val dims = ArrayList<String>()
            obj?.keys()?.forEach { k ->
                val v = obj.opt(k)
                if (k.startsWith("dimension") && v is String && v.isNotEmpty()) dims.add(names[v] ?: v)
            }
            val desc = dims.joinToString(" ")
                .replace(Regex("<as-footnote.*?</as-footnote>", RegexOption.DOT_MATCHES_ALL), "")
            items[part] = stripHtml(desc).replace(Regex("\\s+"), " ")
        }
        if (items.isEmpty()) {
            for (m in PART_RE.findAll(html)) items.putIfAbsent(m.groupValues[1].uppercase(), "")
        }
        return items.entries.map { it.key to it.value }.sortedBy { it.second }
    }
}
