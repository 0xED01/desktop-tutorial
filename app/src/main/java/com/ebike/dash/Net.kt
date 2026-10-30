package com.ebike.dash

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

/** 网络服务：OSRM 骑行路线规划 + Nominatim 地点搜索（均为免费开放服务） */
object Net {
    private const val UA = "Driver/0.4 (Android e-bike dashboard)"
    private const val ROUTER = "https://routing.openstreetmap.de/routed-bike/route/v1/driving/"
    private const val GEOCODER = "https://nominatim.openstreetmap.org/search"

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
        // 以当前位置为中心约 ±0.5° 的范围优先
        val vb = "${nearLon - 0.5},${nearLat + 0.5},${nearLon + 0.5},${nearLat - 0.5}"
        val url = "$GEOCODER?q=$q&format=jsonv2&limit=10&accept-language=zh-CN&viewbox=$vb"
        val arr = JSONArray(get(url))
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val full = o.optString("display_name")
            val name = o.optString("name").ifBlank { full.substringBefore(",") }
            out.add(Place(name, full, o.getString("lat").toDouble(), o.getString("lon").toDouble()))
        }
        return out.sortedBy { Geo.distance(nearLat, nearLon, it.lat, it.lon) }
    }
}
