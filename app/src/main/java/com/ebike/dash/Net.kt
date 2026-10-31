package com.ebike.dash

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Place(
    val name: String,
    /** 简短地址，例如“北京市东城区东长安街” */
    val address: String,
    val lat: Double,
    val lon: Double,
    /** 中文类别，例如“餐厅”“地铁站”，可能为空 */
    val kind: String = "",
)

/** 网络服务：OSRM 骑行路线规划 + Nominatim 地点搜索（均为免费开放服务） */
object Net {
    private const val UA = "Driver/0.4 (Android e-bike dashboard)"
    private const val ROUTER = "https://routing.openstreetmap.de/routed-bike/route/v1/driving/"
    private const val GEOCODER = "https://nominatim.openstreetmap.org/search"
    private const val REVERSE = "https://nominatim.openstreetmap.org/reverse"

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.5")
        try {
            if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route {
        val url = "$ROUTER$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=geojson&steps=true"
        val json = JSONObject(get(url))
        if (json.optString("code") != "Ok") throw RuntimeException(json.optString("message", "路线规划失败"))
        val r = json.getJSONArray("routes").getJSONObject(0)
        val geo = r.getJSONObject("geometry").getJSONArray("coordinates")
        val coords = ArrayList<DoubleArray>(geo.length())
        for (i in 0 until geo.length()) {
            val p = geo.getJSONArray(i)
            coords.add(doubleArrayOf(p.getDouble(1), p.getDouble(0)))
        }
        val steps = ArrayList<RouteStep>()
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val arr = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val m = s.getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                steps.add(
                    RouteStep(
                        type = m.optString("type"),
                        modifier = if (m.has("modifier")) m.getString("modifier") else null,
                        name = s.optString("name"),
                        exit = m.optInt("exit", 0),
                        lat = loc.getDouble(1),
                        lon = loc.getDouble(0),
                    )
                )
            }
        }
        if (coords.size < 2) throw RuntimeException("未找到可骑行的路线")
        return Route(coords, steps, r.optDouble("duration", 0.0))
    }

    fun search(query: String, nearLat: Double, nearLon: Double): List<Place> {
        val q = URLEncoder.encode(query, "UTF-8")
        // 以当前位置为中心约 ±0.5° 的范围优先，但不限制在范围内
        val vb = "${nearLon - 0.5},${nearLat + 0.5},${nearLon + 0.5},${nearLat - 0.5}"
        val url = "$GEOCODER?q=$q&format=jsonv2&limit=15&accept-language=zh-CN&viewbox=$vb"
        val arr = JSONArray(get(url))
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) out.add(parse(arr.getJSONObject(i)))
        // 去掉同名且相距很近的重复结果，近处的排在前面
        val uniq = ArrayList<Place>()
        for (p in out.sortedBy { Geo.distance(nearLat, nearLon, it.lat, it.lon) }) {
            if (uniq.none { it.name == p.name && Geo.distance(it.lat, it.lon, p.lat, p.lon) < 150 }) uniq.add(p)
        }
        return uniq
    }

    /** 根据坐标查地址（长按地图时使用） */
    fun reverse(lat: Double, lon: Double): Place? {
        val url = "$REVERSE?lat=$lat&lon=$lon&format=jsonv2&zoom=18&accept-language=zh-CN"
        val o = JSONObject(get(url))
        if (o.has("error")) return null
        return parse(o)
    }

    private fun parse(o: JSONObject): Place {
        val full = o.optString("display_name")
        val parts = full.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val name = o.optString("name").ifBlank { parts.firstOrNull() ?: "未命名地点" }
        return Place(
            name = name,
            address = shortAddress(parts.drop(1)),
            lat = o.getString("lat").toDouble(),
            lon = o.getString("lon").toDouble(),
            kind = kindName(o.optString("category"), o.optString("type")),
        )
    }

    /** Nominatim 的地址是从小到大、逗号分隔；转成中文习惯的从大到小，去掉邮编和国家 */
    private fun shortAddress(parts: List<String>): String {
        val useful = parts.filter { p -> p.none { it.isDigit() } || p.any { it.code > 0x2E80 } }
            .filter { it != "中国" && it != "China" }
        val cjk = useful.any { s -> s.any { it.code in 0x4E00..0x9FFF } }
        val top = useful.takeLast(4).reversed()
        return if (cjk) top.joinToString("") else top.reversed().joinToString(", ")
    }

    private fun kindName(category: String, type: String): String = when (type) {
        "restaurant", "fast_food", "food_court" -> "餐厅"
        "cafe" -> "咖啡"
        "bar", "pub" -> "酒吧"
        "school", "kindergarten" -> "学校"
        "university", "college" -> "大学"
        "hospital", "clinic", "doctors" -> "医院"
        "pharmacy" -> "药店"
        "bank", "atm" -> "银行"
        "fuel" -> "加油站"
        "charging_station" -> "充电站"
        "parking", "bicycle_parking", "motorcycle_parking" -> "停车场"
        "bus_stop", "bus_station" -> "公交站"
        "subway_entrance" -> "地铁口"
        "station", "halt" -> if (category == "railway") "车站" else "站点"
        "supermarket", "convenience" -> "超市"
        "mall", "department_store" -> "商场"
        "hotel", "hostel", "motel", "guest_house" -> "酒店"
        "park", "garden" -> "公园"
        "residential", "apartments" -> "小区"
        "toilets" -> "厕所"
        "police" -> "派出所"
        "post_office" -> "邮局"
        "cinema" -> "影院"
        "attraction", "museum", "viewpoint" -> "景点"
        else -> when (category) {
            "highway" -> "道路"
            "shop" -> "商店"
            "building" -> "建筑"
            "place", "boundary" -> "地区"
            else -> ""
        }
    }
}
