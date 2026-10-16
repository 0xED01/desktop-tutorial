package com.shiji.footprint.data

import android.content.Context
import android.location.Geocoder
import android.os.SystemClock
import com.shiji.footprint.geo.Geo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.floor

/**
 * 逆地理编码：把足迹点解析为 国家/省/市，用于"点亮城市"。
 * 优先使用系统 Geocoder，不可用时（多数国产手机无 GMS）回退到 OpenStreetMap Nominatim。
 * 以约 5km 的粗网格去重，每个网格只解析一次。
 */
class PlaceResolver(
    private val ctx: Context,
    private val onResolved: (key: Long, place: Place) -> Unit,
) {
    private val exec = Executors.newSingleThreadExecutor { Thread(it, "footprint-geo") }
    private val done = ConcurrentHashMap.newKeySet<Long>()
    private val pending = ConcurrentHashMap.newKeySet<Long>()
    private val failedAt = ConcurrentHashMap<Long, Long>()
    private var lastNominatim = 0L

    fun geoKey(lat: Double, lon: Double): Long =
        Geo.key(floor(lon * 20).toInt(), floor(lat * 20).toInt())

    fun markDone(keys: Collection<Long>) {
        done.addAll(keys)
    }

    fun reset() {
        done.clear()
        failedAt.clear()
    }

    fun request(lat: Double, lon: Double) {
        val k = geoKey(lat, lon)
        if (k in done) return
        val failed = failedAt[k]
        if (failed != null && SystemClock.elapsedRealtime() - failed < 3 * 60_000) return
        if (!pending.add(k)) return
        exec.execute {
            val place = try {
                resolve(lat, lon)
            } catch (_: Exception) {
                null
            }
            pending.remove(k)
            if (place != null) {
                done.add(k)
                failedAt.remove(k)
                onResolved(k, place)
            } else {
                failedAt[k] = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun resolve(lat: Double, lon: Double): Place? =
        resolveWithGeocoder(lat, lon) ?: resolveWithNominatim(lat, lon)

    @Suppress("DEPRECATION")
    private fun resolveWithGeocoder(lat: Double, lon: Double): Place? {
        if (!Geocoder.isPresent()) return null
        return try {
            val a = Geocoder(ctx, Locale.SIMPLIFIED_CHINESE).getFromLocation(lat, lon, 1)?.firstOrNull()
                ?: return null
            val country = a.countryName?.takeIf { it.isNotBlank() } ?: return null
            val province = a.adminArea ?: a.subAdminArea ?: a.locality ?: country
            val city = a.locality ?: a.subAdminArea ?: province
            Place(country, province, city)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveWithNominatim(lat: Double, lon: Double): Place? {
        // Nominatim 使用政策：每秒最多 1 次请求
        val wait = 1100 - (SystemClock.elapsedRealtime() - lastNominatim)
        if (wait > 0) Thread.sleep(wait)
        lastNominatim = SystemClock.elapsedRealtime()
        val url = URL(
            String.format(
                Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=10&accept-language=zh-CN,zh",
                lat, lon
            )
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "ShijiFootprint/1.0 (Android footprint app)")
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val addr = JSONObject(body).optJSONObject("address") ?: return null
            fun s(vararg keys: String) = keys.firstNotNullOfOrNull { k ->
                addr.optString(k).takeIf { it.isNotBlank() }
            }
            val country = s("country") ?: return null
            val province = s("state", "province", "region", "city") ?: country
            val city = s("city", "town", "municipality", "county", "state_district", "village") ?: province
            return Place(country, province, city)
        } finally {
            conn.disconnect()
        }
    }
}
