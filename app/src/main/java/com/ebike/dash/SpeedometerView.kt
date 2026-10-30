package com.ebike.dash

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 简约圆形码表：半透明圆盘、细圆弧、大号数字。
 * 只用前景色 + 灰色，超速时圆弧和数字变红。
 */
class SpeedometerView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
) : View(ctx, attrs) {

    var maxSpeed = 60f
        set(v) { field = v; invalidate() }
    var limit = 25f
        set(v) { field = v; invalidate() }
    var unit = "km/h"
        set(v) { field = v; invalidate() }
    /** 数字下方的一行小字，例如“东北 · 海拔 52m” */
    var caption = ""
        set(v) { if (field != v) { field = v; invalidate() } }

    private var palette = Palette.LIGHT
    private var shown = 0f
    private var anim: ValueAnimator? = null

    private val start = 135f
    private val sweep = 270f

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
        letterSpacing = -0.02f
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private val arcRect = RectF()
    private var cx = 0f
    private var cy = 0f
    private var r = 0f

    init {
        // 圆形阴影，与卡片的柔和投影一致
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val s = min(view.width, view.height)
                outline.setOval((view.width - s) / 2, (view.height - s) / 2, (view.width + s) / 2, (view.height + s) / 2)
            }
        }
        applyColors()
    }

    fun setPalette(p: Palette) {
        palette = p
        applyColors()
        invalidate()
    }

    private fun applyColors() {
        discPaint.color = ColorUtils.setAlphaComponent(palette.surface, 225)
        trackPaint.color = palette.chip
        speedPaint.color = palette.onSurface
        unitPaint.color = palette.dim
        capPaint.color = palette.dim
        numPaint.color = palette.dim
        markerPaint.color = palette.danger
    }

    fun setSpeed(v: Float) {
        val target = v.coerceAtLeast(0f)
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, target).apply {
            duration = 480
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        r = min(w, h) / 2f
        val ar = r * 0.82f
        arcRect.set(cx - ar, cy - ar, cx + ar, cy + ar)
        trackPaint.strokeWidth = r * 0.055f
        arcPaint.strokeWidth = r * 0.055f
        tickPaint.strokeWidth = r * 0.014f
        numPaint.textSize = r * 0.085f
        speedPaint.textSize = r * 0.52f
        unitPaint.textSize = r * 0.095f
        capPaint.textSize = r * 0.08f
    }

    override fun onDraw(canvas: Canvas) {
        val over = shown > limit + 0.5f
        val fg = if (over) palette.danger else palette.onSurface

        canvas.drawCircle(cx, cy, r, discPaint)

        canvas.drawArc(arcRect, start, sweep, false, trackPaint)
        val frac = (shown / maxSpeed).coerceIn(0f, 1f)
        if (frac > 0.002f) {
            arcPaint.color = fg
            canvas.drawArc(arcRect, start, sweep * frac, false, arcPaint)
        }

        drawScale(canvas)

        speedPaint.color = fg
        val fm = speedPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2 - r * 0.05f
        canvas.drawText(shown.roundToInt().toString(), cx, baseline, speedPaint)
        canvas.drawText(unit, cx, baseline + r * 0.19f, unitPaint)
        if (caption.isNotEmpty()) canvas.drawText(caption, cx, cy + r * 0.66f, capPaint)
    }

    /** 只画主刻度的小点和数字，外加一个限速标记 */
    private fun drawScale(canvas: Canvas) {
        val step = when {
            maxSpeed <= 40 -> 10f
            maxSpeed <= 80 -> 20f
            else -> 30f
        }
        val dotR = r * 0.66f
        val numR = r * 0.55f
        tickPaint.color = palette.dim
        var s = 0f
        while (s <= maxSpeed + 0.01f) {
            val a = Math.toRadians((start + sweep * s / maxSpeed).toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            canvas.drawCircle(cx + dotR * ca, cy + dotR * sa, r * 0.012f, tickPaint)
            canvas.drawText(
                s.roundToInt().toString(),
                cx + numR * ca, cy + numR * sa - (numPaint.ascent() + numPaint.descent()) / 2, numPaint,
            )
            s += step
        }
        if (limit in 0f..maxSpeed) {
            val a = Math.toRadians((start + sweep * limit / maxSpeed).toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            val inner = r * 0.9f
            val outer = r * 0.96f
            markerPaint.strokeWidth = r * 0.022f
            canvas.drawLine(cx + inner * ca, cy + inner * sa, cx + outer * ca, cy + outer * sa, markerPaint)
        }
    }
}
