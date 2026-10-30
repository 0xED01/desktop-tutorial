package com.ebike.dash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * 速度能量条：一排胶囊小格，速度越快亮起的格子越多。
 * 与 GPS 信号的圆点同一种设计语言；最前面一格按比例渐亮，变化是连续的。
 */
class EnergyBarView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    /** 0..1 */
    var level = 0f
        set(v) {
            val c = v.coerceIn(0f, 1f)
            if (c != field) { field = c; invalidate() }
        }

    private var onColor = 0xFF0D0D0D.toInt()
    private val off = Paint(Paint.ANTI_ALIAS_FLAG)
    private val on = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val gap = 4f * resources.displayMetrics.density

    fun setColors(onColor: Int, offColor: Int) {
        this.onColor = onColor
        off.color = offColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val n = COUNT
        val segW = (width - gap * (n - 1)) / n
        val h = height.toFloat()
        val lit = level * n
        for (i in 0 until n) {
            val x = i * (segW + gap)
            r.set(x, 0f, x + segW, h)
            canvas.drawRoundRect(r, h / 2, h / 2, off)
            val f = (lit - i).coerceIn(0f, 1f)
            if (f > 0f) {
                on.color = ColorUtils.setAlphaComponent(onColor, (255 * f).toInt())
                canvas.drawRoundRect(r, h / 2, h / 2, on)
            }
        }
    }

    companion object {
        const val COUNT = 16
    }
}
