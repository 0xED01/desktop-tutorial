package com.shiji.footprint.ui.map

import androidx.compose.ui.graphics.Color
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

class UrlTileSource(
    name: String,
    maxZoom: Int,
    ext: String,
    private val template: String,
    private val subdomains: List<String>,
) : OnlineTileSourceBase(name, 2, maxZoom, 256, ext, arrayOf(template)) {
    override fun getTileURLString(index: Long): String {
        val x = MapTileIndex.getX(index)
        val y = MapTileIndex.getY(index)
        val z = MapTileIndex.getZoom(index)
        val s = if (subdomains.isEmpty()) "" else subdomains[(x + y) % subdomains.size]
        return template.replace("{s}", s).replace("{x}", x.toString())
            .replace("{y}", y.toString()).replace("{z}", z.toString())
    }
}

/**
 * 地图样式。[gcj] 为 true 表示底图使用 GCJ-02 坐标（高德），绘制时需要把 WGS84 轨迹转换过去。
 */
class MapStyle(
    val id: String,
    val title: String,
    val gcj: Boolean,
    val dark: Boolean,
    val maxZoom: Int,
    val preview: List<Color>,
    val source: OnlineTileSourceBase,
)

object MapStyles {
    val all = listOf(
        MapStyle(
            "amap", "高德标准", gcj = true, dark = false, maxZoom = 18,
            preview = listOf(Color(0xFFF4F1EA), Color(0xFFB7D9F5)),
            source = UrlTileSource(
                "AmapStd", 18, ".png",
                "https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
                listOf("1", "2", "3", "4")
            )
        ),
        MapStyle(
            "amap_sat", "高德卫星", gcj = true, dark = true, maxZoom = 18,
            preview = listOf(Color(0xFF2F4A2A), Color(0xFF1E3A52)),
            source = UrlTileSource(
                "AmapSat", 18, ".jpg",
                "https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}",
                listOf("1", "2", "3", "4")
            )
        ),
        MapStyle(
            "light", "简约浅色", gcj = false, dark = false, maxZoom = 19,
            preview = listOf(Color(0xFFF7F7F5), Color(0xFFD5DCE3)),
            source = UrlTileSource(
                "CartoLight", 19, ".png",
                "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
                listOf("a", "b", "c", "d")
            )
        ),
        MapStyle(
            "dark", "简约深色", gcj = false, dark = true, maxZoom = 19,
            preview = listOf(Color(0xFF242629), Color(0xFF3B3F45)),
            source = UrlTileSource(
                "CartoDark", 19, ".png",
                "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
                listOf("a", "b", "c", "d")
            )
        ),
        MapStyle(
            "osm", "OpenStreetMap", gcj = false, dark = false, maxZoom = 19,
            preview = listOf(Color(0xFFF2EFE9), Color(0xFFAAD3DF)),
            source = UrlTileSource(
                "OSM", 19, ".png",
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                emptyList()
            )
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}
