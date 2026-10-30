package com.ebike.dash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** 导航转向箭头（矢量绘制，任意尺寸清晰） */
class TurnArrowView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    private var type = "turn"
    private var modifier: String? = "straight"

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    fun setColors(fg: Int, bg: Int) {
        stroke.color = fg
        fill.color = fg
        hole.color = bg
        invalidate()
    }
    private val path = Path()

    fun set(type: String, modifier: String?) {
        if (type == this.type && modifier == this.modifier) return
        this.type = type
        this.modifier = modifier
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        stroke.strokeWidth = w * 0.1f

        if (type == "arrive") {
            canvas.drawCircle(cx, h * 0.42f, w * 0.26f, fill)
            canvas.drawCircle(cx, h * 0.42f, w * 0.1f, hole)
            path.reset()
            path.moveTo(cx - w * 0.2f, h * 0.58f)
            path.lineTo(cx, h * 0.92f)
            path.lineTo(cx + w * 0.2f, h * 0.58f)
            path.close()
            canvas.drawPath(path, fill)
            return
        }

        if (modifier == "uturn") {
            val x1 = cx + w * 0.17f
            val x2 = cx - w * 0.17f
            path.reset()
            path.moveTo(x1, h * 0.9f)
            path.lineTo(x1, h * 0.4f)
            path.arcTo(RectF(x2, h * 0.4f - w * 0.17f, x1, h * 0.4f + w * 0.17f), 0f, -180f)
            path.lineTo(x2, h * 0.58f)
            canvas.drawPath(path, stroke)
            head(canvas, x2, h * 0.66f, 180.0, w)
            return
        }

        val angle = when (modifier) {
            "slight right" -> 40.0
            "right" -> 90.0
            "sharp right" -> 135.0
            "slight left" -> -40.0
            "left" -> -90.0
            "sharp left" -> -135.0
            else -> 0.0
        }
        val pivotY = h * 0.55f
        val len = h * 0.27f
        val rad = Math.toRadians(angle)
        val ex = cx + (sin(rad) * len).toFloat()
        val ey = pivotY - (cos(rad) * len).toFloat()

        if (type.startsWith("roundabout") || type.startsWith("rotary")) {
            stroke.strokeWidth = w * 0.07f
            canvas.drawCircle(cx, pivotY, w * 0.14f, stroke)
            stroke.strokeWidth = w * 0.11f
        }

        path.reset()
        path.moveTo(cx, h * 0.92f)
        path.lineTo(cx, pivotY)
        path.lineTo(ex, ey)
        canvas.drawPath(path, stroke)
        head(canvas, ex, ey, angle, w)
    }

    private fun head(canvas: Canvas, x: Float, y: Float, angleDeg: Double, w: Float) {
        val a = Math.toRadians(angleDeg)
        val dx = sin(a).toFloat()
        val dy = -cos(a).toFloat()
        val len = w * 0.2f
        val half = w * 0.17f
        path.reset()
        path.moveTo(x + dx * len * 0.8f, y + dy * len * 0.8f)
        path.lineTo(x - dx * len * 0.4f - dy * half, y - dy * len * 0.4f + dx * half)
        path.lineTo(x - dx * len * 0.4f + dy * half, y - dy * len * 0.4f - dx * half)
        path.close()
        canvas.drawPath(path, fill)
    }
}
