package com.ebike.dash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max

/** 月度报告里的足迹缩略图：把这个月骑过的路画成线条，自动缩放居中 */
class TrackThumbView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    private var segments: List<List<DoubleArray>> = emptyList()
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.2f * d
    }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 13 * d }
    private val path = Path()

    fun set(segs: List<List<DoubleArray>>, color: Int, dimColor: Int) {
        segments = segs
        paint.color = color
        empty.color = dimColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val pts = segments.flatten()
        if (pts.isEmpty()) {
            canvas.drawText("这个月还没有骑行记录", width / 2f, height / 2f, empty)
            return
        }
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        for (p in pts) {
            minLat = minOf(minLat, p[0]); maxLat = maxOf(maxLat, p[0])
            minLon = minOf(minLon, p[1]); maxLon = maxOf(maxLon, p[1])
        }
        val k = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = max((maxLon - minLon) * k, 1e-6)
        val spanY = max(maxLat - minLat, 1e-6)
        val pad = 10 * d
        val scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY)
        val ox = (width - spanX * scale) / 2
        val oy = (height - spanY * scale) / 2
        for (seg in segments) {
            if (seg.size < 2) continue
            path.reset()
            seg.forEachIndexed { i, p ->
                val x = (ox + (p[1] - minLon) * k * scale).toFloat()
                val y = (oy + (maxLat - p[0]) * scale).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
    }
}
