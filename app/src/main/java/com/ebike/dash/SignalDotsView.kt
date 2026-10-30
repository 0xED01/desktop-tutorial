package com.ebike.dash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** GPS 信号强度：5 个圆点，亮点数 = 信号格数 */
class SignalDotsView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    var level = 0
        set(v) { if (field != v) { field = v.coerceIn(0, COUNT); invalidate() } }

    private val on = Paint(Paint.ANTI_ALIAS_FLAG)
    private val off = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density
    private val dot = 5.5f * d
    private val gap = 3.5f * d

    fun setColors(onColor: Int, offColor: Int) {
        on.color = onColor
        off.color = offColor
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((COUNT * dot + (COUNT - 1) * gap).toInt() + 1, dot.toInt() + 1)
    }

    override fun onDraw(canvas: Canvas) {
        val r = dot / 2
        for (i in 0 until COUNT) {
            canvas.drawCircle(r + i * (dot + gap), height / 2f, r, if (i < level) on else off)
        }
    }

    companion object {
        const val COUNT = 5

        /** 按定位精度换算信号格数 */
        fun levelFor(accuracyMeters: Float): Int = when {
            accuracyMeters <= 5 -> 5
            accuracyMeters <= 10 -> 4
            accuracyMeters <= 20 -> 3
            accuracyMeters <= 35 -> 2
            else -> 1
        }
    }
}
