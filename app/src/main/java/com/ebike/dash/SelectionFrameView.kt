package com.ebike.dash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 离线地图框选：框外压暗、框内清晰，圆角描边与卡片同一圆角。
 * frame 为框在本 View 里的位置。
 */
class SelectionFrameView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    val frame = RectF()
    private val d = resources.displayMetrics.density
    private val radius = 26 * d
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
    }
    private val path = Path()

    /** 框的四边离屏幕边缘的距离（像素） */
    var insetLeft = 0f; private set
    var insetTop = 0f; private set
    var insetRight = 0f; private set
    var insetBottom = 0f; private set

    fun setInsets(left: Float, top: Float, right: Float, bottom: Float) {
        insetLeft = left; insetTop = top; insetRight = right; insetBottom = bottom
        updateFrame()
        invalidate()
    }

    private fun updateFrame() = frame.set(insetLeft, insetTop, width - insetRight, height - insetBottom)

    fun setColors(dimColor: Int, strokeColor: Int) {
        dim.color = dimColor
        stroke.color = strokeColor
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateFrame()

    override fun onDraw(canvas: Canvas) {
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRoundRect(frame, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, dim)
        canvas.drawRoundRect(frame, radius, radius, stroke)
    }
}
