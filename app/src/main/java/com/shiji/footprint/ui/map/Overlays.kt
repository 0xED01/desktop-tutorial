package com.shiji.footprint.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.shiji.footprint.data.TrackPoint
import com.shiji.footprint.geo.CellIndex
import com.shiji.footprint.geo.Geo
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 把 WGS84 坐标转换为当前底图的显示坐标。 */
internal fun GeoPoint.setDisplay(lat: Double, lon: Double, gcj: Boolean): GeoPoint {
    if (gcj) {
        val g = Geo.wgsToGcj(lat, lon)
        setCoords(g[0], g[1])
    } else setCoords(lat, lon)
    return this
}

/**
 * 战争迷雾：整张地图盖一层雾，已探索的格子处"挖洞"显示底图。
 */
class FogOverlay(private val index: CellIndex) : Overlay() {
    var gcj = false
    private var fogColor = Color.argb(204, 28, 34, 54)

    private val fogPaint = Paint()
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val softPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        alpha = 70
    }
    private val gp = GeoPoint(0.0, 0.0)
    private val pt = Point()

    fun setStyle(alpha: Float, dark: Boolean) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        fogColor = if (dark) Color.argb(a, 8, 10, 18) else Color.argb(a, 28, 34, 54)
    }

    override fun draw(c: Canvas, pj: Projection) {
        if (!isEnabled) return
        val zoom = pj.zoomLevel
        val level = min(Geo.CELL_LEVEL, max(Geo.MIN_LEVEL, floor(zoom).toInt() + 5))
        val bb = pj.boundingBox
        val pad = 0.02
        val span = Geo.lonToX(bb.lonEast, level) - Geo.lonToX(bb.lonWest, level)
        val cellPx = if (span > 0) (pj.width / span).toFloat() else 4f
        val r = max(cellPx * 1.05f, 1.5f)

        val maxIdx = (1 shl level) - 1
        val wrap = bb.lonWest > bb.lonEast
        val x0 = if (wrap) 0 else floor(Geo.lonToX(max(-180.0, bb.lonWest - pad), level)).toInt()
        val x1 = if (wrap) maxIdx else floor(Geo.lonToX(min(180.0, bb.lonEast + pad), level)).toInt()
        val y0 = floor(Geo.latToY(min(85.0, bb.latNorth + pad), level)).toInt()
        val y1 = floor(Geo.latToY(max(-85.0, bb.latSouth - pad), level)).toInt()

        val save = c.saveLayer(null, null)
        fogPaint.color = fogColor
        c.drawPaint(fogPaint)
        for (k in index.cells(level)) {
            val x = Geo.keyX(k)
            val y = Geo.keyY(k)
            if (x < x0 || x > x1 || y < y0 || y > y1) continue
            gp.setDisplay(Geo.yToLat(y + 0.5, level), Geo.xToLon(x + 0.5, level), gcj)
            pj.toPixels(gp, pt)
            val px = pt.x.toFloat()
            val py = pt.y.toFloat()
            c.drawCircle(px, py, r * 1.5f, softPaint)
            c.drawCircle(px, py, r, clearPaint)
        }
        c.restoreToCount(save)
    }
}

/** 轨迹线。 */
class TrackOverlay(density: Float) : Overlay() {
    var gcj = false
    var points: List<TrackPoint> = emptyList()

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(255, 107, 61)
    }
    private val casing = Paint(line).apply {
        strokeWidth = 7f * density
        color = Color.argb(220, 255, 255, 255)
    }
    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
    }
    private val dotR = 5f * density
    private val path = Path()
    private val gp = GeoPoint(0.0, 0.0)
    private val pt = Point()

    override fun draw(c: Canvas, pj: Projection) {
        val pts = points
        if (pts.isEmpty()) return
        path.rewind()
        var prev: TrackPoint? = null
        for (p in pts) {
            pj.toPixels(gp.setDisplay(p.lat, p.lon, gcj), pt)
            val broken = prev == null || prev.seg != p.seg ||
                Geo.distance(prev.lat, prev.lon, p.lat, p.lon) > 20_000
            if (broken) path.moveTo(pt.x.toFloat(), pt.y.toFloat())
            else path.lineTo(pt.x.toFloat(), pt.y.toFloat())
            prev = p
        }
        c.drawPath(path, casing)
        c.drawPath(path, line)
        drawDot(c, pj, pts.first(), Color.rgb(18, 184, 134))
        if (pts.size > 1) drawDot(c, pj, pts.last(), Color.rgb(255, 107, 61))
    }

    private fun drawDot(c: Canvas, pj: Projection, p: TrackPoint, color: Int) {
        pj.toPixels(gp.setDisplay(p.lat, p.lon, gcj), pt)
        dotFill.color = color
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), dotR, dotFill)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), dotR, dotStroke)
    }
}

/** 当前位置圆点。 */
class LocationDotOverlay(private val density: Float) : Overlay() {
    var gcj = false
    var point: TrackPoint? = null
    var active = false

    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gp = GeoPoint(0.0, 0.0)
    private val pt = Point()

    override fun draw(c: Canvas, pj: Projection) {
        val p = point ?: return
        pj.toPixels(gp.setDisplay(p.lat, p.lon, gcj), pt)
        val base = if (active) Color.rgb(79, 107, 237) else Color.rgb(120, 128, 150)
        val accPx = if (p.acc > 0) pj.metersToPixels(p.acc) else 0f
        halo.color = base
        halo.alpha = 40
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), max(16f * density, min(accPx, 120f * density)), halo)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 9f * density, ring)
        core.color = base
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 6.5f * density, core)
    }
}
