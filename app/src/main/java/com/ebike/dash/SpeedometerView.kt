package com.ebike.dash

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 透明玻璃质感的圆形码表。
 * 270° 渐变弧形进度、刻度、限速标记、超速脉冲警示，中间大号数字速度。
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

    private var shown = 0f
    private var anim: ValueAnimator? = null

    private val start = 135f
    private val sweep = 270f

    private val cAccent = Color.parseColor("#00E5FF")
    private val cDanger = Color.parseColor("#FF3D57")

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(60, 255, 255, 255)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(34, 255, 255, 255)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        textAlign = Paint.Align.CENTER
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cDanger }
    private val needleDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val arcRect = RectF()
    private var cx = 0f
    private var cy = 0f
    private var r = 0f

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
        r = min(w, h) / 2f * 0.96f
        val ar = r * 0.84f
        arcRect.set(cx - ar, cy - ar, cx + ar, cy + ar)

        discPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.argb(165, 10, 16, 28), Color.argb(125, 12, 20, 36), Color.argb(80, 14, 24, 44)),
            floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
        rimPaint.strokeWidth = r * 0.012f
        trackPaint.strokeWidth = r * 0.085f
        arcPaint.strokeWidth = r * 0.085f
        glowPaint.strokeWidth = r * 0.15f

        val sg = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.parseColor("#00B8FF"), cAccent, Color.parseColor("#00E676"),
                Color.parseColor("#FFEA00"), Color.parseColor("#FF6D00"), cDanger, cDanger,
            ),
            // 渐变从起点前 10° 开始，避免圆角端头取到末端的红色
            floatArrayOf(0f, 0.15f, 0.35f, 0.52f, 0.64f, 0.75f, 1f).map { (10f + it * sweep) / 360f }.toFloatArray()
                .also { it[it.size - 1] = 1f },
        )
        sg.setLocalMatrix(Matrix().apply { setRotate(start - 10f, cx, cy) })
        arcPaint.shader = sg
        glowPaint.shader = sg

        numPaint.textSize = r * 0.1f
        speedPaint.textSize = r * 0.56f
        speedPaint.setShadowLayer(r * 0.05f, 0f, 0f, Color.argb(120, 0, 229, 255))
        unitPaint.textSize = r * 0.1f
        capPaint.textSize = r * 0.085f
    }

    override fun onDraw(canvas: Canvas) {
        val over = shown > limit + 0.5f
        val pulse = if (over) (0.5f + 0.5f * sin(SystemClock.uptimeMillis() / 140.0).toFloat()) else 0f

        // 玻璃底盘
        canvas.drawCircle(cx, cy, r, discPaint)
        canvas.drawCircle(cx, cy, r - rimPaint.strokeWidth, rimPaint)

        // 轨道 + 进度弧
        canvas.drawArc(arcRect, start, sweep, false, trackPaint)
        val frac = (shown / maxSpeed).coerceIn(0f, 1f)
        if (frac > 0.002f) {
            glowPaint.alpha = if (over) (50 + 90 * pulse).toInt() else 30
            canvas.drawArc(arcRect, start, sweep * frac, false, glowPaint)
            canvas.drawArc(arcRect, start, sweep * frac, false, arcPaint)
            // 进度端点亮点
            val a = Math.toRadians((start + sweep * frac).toDouble())
            val ar = arcRect.width() / 2
            canvas.drawCircle(cx + ar * cos(a).toFloat(), cy + ar * sin(a).toFloat(), r * 0.03f, needleDotPaint)
        }

        drawTicks(canvas)
        drawLimitMarker(canvas)

        // 中间数字
        val v = shown.roundToInt()
        speedPaint.color = if (over) blend(Color.WHITE, cDanger, 0.55f + 0.45f * pulse) else Color.WHITE
        val fm = speedPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2 - r * 0.06f
        canvas.drawText(v.toString(), cx, baseline, speedPaint)
        canvas.drawText(unit, cx, baseline + r * 0.17f, unitPaint)
        if (caption.isNotEmpty()) canvas.drawText(caption, cx, cy + r * 0.62f, capPaint)

        if (over) postInvalidateOnAnimation()
    }

    private fun drawTicks(canvas: Canvas) {
        val majorStep = when {
            maxSpeed <= 40 -> 5f
            maxSpeed <= 80 -> 10f
            else -> 20f
        }
        val minorStep = majorStep / 5f
        val outer = r * 0.735f
        var s = 0f
        while (s <= maxSpeed + 0.01f) {
            val major = (s % majorStep) < 0.01f || (majorStep - s % majorStep) < 0.01f
            val a = Math.toRadians((start + sweep * s / maxSpeed).toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            val inner = if (major) outer - r * 0.075f else outer - r * 0.035f
            tickPaint.strokeWidth = if (major) r * 0.016f else r * 0.008f
            tickPaint.color = when {
                s > limit -> Color.argb(if (major) 220 else 120, 255, 61, 87)
                major -> Color.argb(210, 255, 255, 255)
                else -> Color.argb(90, 255, 255, 255)
            }
            canvas.drawLine(cx + inner * ca, cy + inner * sa, cx + outer * ca, cy + outer * sa, tickPaint)
            if (major) {
                val nr = outer - r * 0.15f
                val tx = cx + nr * ca
                val ty = cy + nr * sa - (numPaint.ascent() + numPaint.descent()) / 2
                canvas.drawText(s.roundToInt().toString(), tx, ty, numPaint)
            }
            s += minorStep
        }
    }

    private val markerPath = Path()

    private fun drawLimitMarker(canvas: Canvas) {
        if (limit <= 0 || limit >= maxSpeed) return
        val a = Math.toRadians((start + sweep * limit / maxSpeed).toDouble())
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val tip = r * 0.93f
        val base = r * 1.0f
        val half = r * 0.045f
        markerPath.reset()
        markerPath.moveTo(cx + tip * ca, cy + tip * sa)
        markerPath.lineTo(cx + base * ca - half * sa, cy + base * sa + half * ca)
        markerPath.lineTo(cx + base * ca + half * sa, cy + base * sa - half * ca)
        markerPath.close()
        canvas.drawPath(markerPath, markerPaint)
    }

    private fun blend(c1: Int, c2: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * u).toInt(),
            (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * u).toInt(),
            (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * u).toInt(),
        )
    }
}
