package com.ebike.dash

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 跳动的音乐柱：播放时一列列胶囊柱随机起伏（中间的更高），
 * 暂停时平滑落回成一排小圆点——与 GPS 信号点、速度能量条同一种设计语言。
 * 柱子从中线向上下两边伸展。
 */
class EqualizerView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    /** 柱子数量，可在布局里用 android:tag 指定（例如 "13"） */
    private val count: Int = (tag as? String)?.toIntOrNull() ?: 4

    private val level = FloatArray(count) { 0.3f }
    private val target = FloatArray(count) { 0.3f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var color = 0xFF0D0D0D.toInt()

    /** 0 = 跳动中，1 = 完全静止成圆点 */
    private var rest = 1f
    private var playing = false
    private var restAnim: ValueAnimator? = null
    private var lastFrame = 0L

    fun setColor(c: Int) {
        color = c
        invalidate()
    }

    fun setPlaying(p: Boolean, animate: Boolean = true) {
        if (p == playing && restAnim == null) return
        playing = p
        restAnim?.cancel()
        val to = if (p) 0f else 1f
        if (!animate) {
            rest = to
            restAnim = null
        } else {
            restAnim = ValueAnimator.ofFloat(rest, to).apply {
                duration = Motion.STANDARD * 2
                interpolator = Motion.EASE
                addUpdateListener { rest = it.animatedValue as Float; invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { restAnim = null }
                })
                start()
            }
        }
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    /** 中间的柱子更高，两边低一些，看起来更像音乐 */
    private fun envelope(i: Int) = 0.55f + 0.45f * sin(Math.PI * (i + 0.5) / count).toFloat()

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        val k = 1f - exp(-dt * 11f)
        for (i in 0 until count) {
            level[i] += (target[i] - level[i]) * k
            if (kotlin.math.abs(target[i] - level[i]) < 0.06f) {
                target[i] = (0.18f + Random.nextFloat() * 0.82f) * envelope(i)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (playing || rest < 1f) step()
        val d = resources.displayMetrics.density
        // 没有底框，柱子可以占满更多空间
        val pad = min(width, height) * 0.1f
        val areaW = width - pad * 2
        val areaH = height - pad * 2
        // 柱宽：尽量粗一些，但不超过 10dp；间距是柱宽的 0.8 倍
        val bw = min(10 * d, areaW / (count + (count - 1) * 0.8f))
        val gap = bw * 0.8f
        val total = count * bw + (count - 1) * gap
        val x0 = (width - total) / 2
        val cy = height / 2f
        val active = 1f - rest
        // 静止时的圆点颜色与 GPS 未亮的点一致
        paint.color = ColorUtils.blendARGB(ColorUtils.setAlphaComponent(color, 70), color, active)
        for (i in 0 until count) {
            val full = areaH * level[i]
            val h = bw + (full - bw).coerceAtLeast(0f) * active
            val x = x0 + i * (bw + gap)
            r.set(x, cy - h / 2, x + bw, cy + h / 2)
            canvas.drawRoundRect(r, bw / 2, bw / 2, paint)
        }
        if ((playing || restAnim != null) && isShown) postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        if (playing) postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && playing) {
            lastFrame = 0L
            postInvalidateOnAnimation()
        }
    }
}
