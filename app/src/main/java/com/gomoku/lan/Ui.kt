package com.gomoku.lan

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.TextView

object Palette {
    const val PAPER = 0xFFF3EEE5.toInt()
    const val SURFACE = 0xFFFBF8F3.toInt()
    const val INK = 0xFF1C1B19.toInt()
    const val INK_SOFT = 0xFF4A463F.toInt()
    const val MUTED = 0xFF8C857A.toInt()
    const val HAIRLINE = 0xFFE0D8CA.toInt()
    const val ACCENT = 0xFFB0392B.toInt()
    const val ONLINE = 0xFF5D8A5B.toInt()
    const val BOARD_LIGHT = 0xFFEBD6AC.toInt()
    const val BOARD_DARK = 0xFFDDBF8B.toInt()
    const val GRID = 0xFF5E4A2E.toInt()
}

object Fonts {
    var serif: Typeface = Typeface.SERIF
    var serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val sansMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun init(ctx: Context) {
        runCatching { serif = Typeface.createFromAsset(ctx.assets, "fonts/serif_regular.otf") }
        runCatching { serifBold = Typeface.createFromAsset(ctx.assets, "fonts/serif_bold.otf") }
    }
}

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(v: Number): Int = context.dp(v)
fun View.dpf(v: Number): Float = v.toFloat() * resources.displayMetrics.density

fun withAlpha(color: Int, alpha: Float) =
    Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

fun Context.label(
    text: CharSequence,
    sizeSp: Float,
    color: Int = Palette.INK,
    face: Typeface = Fonts.sans,
    spacing: Float = 0f,
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    typeface = face
    letterSpacing = spacing
    includeFontPadding = false
}

enum class PillStyle { PRIMARY, OUTLINE, GHOST }

/** 圆角按钮：墨色实心 / 细描边 / 无边框。按下有轻微缩放。 */
@SuppressLint("ClickableViewAccessibility")
fun Context.pill(text: String, style: PillStyle, onClick: () -> Unit): TextView {
    val tv = label(text, 15f, face = Fonts.sansMedium, spacing = 0.06f)
    tv.gravity = Gravity.CENTER
    tv.minHeight = dp(50)
    tv.setPadding(dp(22), 0, dp(22), 0)
    tv.maxLines = 1
    tv.ellipsize = TextUtils.TruncateAt.END
    val radius = dp(25).toFloat()
    val bg = GradientDrawable().apply { cornerRadius = radius }
    val rippleColor: Int
    when (style) {
        PillStyle.PRIMARY -> {
            bg.setColor(Palette.INK); tv.setTextColor(Palette.PAPER); rippleColor = 0x33FFFFFF
        }
        PillStyle.OUTLINE -> {
            bg.setColor(Color.TRANSPARENT); bg.setStroke(dp(1), withAlpha(Palette.INK, 0.28f))
            tv.setTextColor(Palette.INK); rippleColor = 0x14000000
        }
        PillStyle.GHOST -> {
            bg.setColor(Color.TRANSPARENT); tv.setTextColor(Palette.INK_SOFT); rippleColor = 0x10000000
        }
    }
    val mask = GradientDrawable().apply { cornerRadius = radius; setColor(Color.WHITE) }
    tv.background = RippleDrawable(ColorStateList.valueOf(rippleColor), bg, mask)
    tv.isClickable = true
    tv.setOnClickListener { onClick() }
    tv.setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }
        false
    }
    return tv
}

fun View.setEnabledLook(enabled: Boolean) {
    isEnabled = enabled
    animate().alpha(if (enabled) 1f else 0.32f).setDuration(150).start()
}

fun roundedOutline(radiusPx: Float) = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
    }
}

fun roundedBg(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radiusPx
    if (strokePx > 0) setStroke(strokePx, strokeColor)
}

/** 棋子绘制：带径向高光与柔和投影，在原点绘制，调用者负责平移。 */
class StonePainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var radius = -1f
    private lateinit var blackShader: Shader
    private lateinit var whiteShader: Shader
    private lateinit var shadowShader: Shader

    private fun prepare(r: Float) {
        if (r == radius) return
        radius = r
        blackShader = RadialGradient(
            -0.36f * r, -0.40f * r, 1.35f * r,
            intArrayOf(0xFF6F6E6C.toInt(), 0xFF2A2927.toInt(), 0xFF0B0B0A.toInt()),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP,
        )
        whiteShader = RadialGradient(
            -0.36f * r, -0.40f * r, 1.45f * r,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFF3F0EA.toInt(), 0xFFCDC6B9.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        shadowShader = RadialGradient(
            0f, 0f, 1.18f * r,
            intArrayOf(0x66000000, 0x33000000, 0x00000000),
            floatArrayOf(0.62f, 0.82f, 1f), Shader.TileMode.CLAMP,
        )
        rim.strokeWidth = maxOf(1f, r * 0.04f)
    }

    fun draw(canvas: Canvas, color: Int, r: Float, alpha: Int = 255, withShadow: Boolean = true) {
        prepare(r)
        if (withShadow) {
            canvas.save()
            canvas.translate(r * 0.07f, r * 0.15f)
            shadow.shader = shadowShader
            shadow.alpha = alpha
            canvas.drawCircle(0f, 0f, r * 1.18f, shadow)
            canvas.restore()
        }
        fill.shader = if (color == BLACK) blackShader else whiteShader
        fill.alpha = alpha
        canvas.drawCircle(0f, 0f, r, fill)
        if (color == WHITE) {
            rim.color = 0x22000000
            rim.alpha = (0x22 * alpha / 255)
            canvas.drawCircle(0f, 0f, r - rim.strokeWidth / 2, rim)
        }
    }
}

/** 小号棋子图标。 */
class StoneIcon(ctx: Context, var color: Int) : View(ctx) {
    private val painter = StonePainter()
    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f * 0.84f
        canvas.translate(width / 2f, height / 2f - r * 0.05f)
        painter.draw(canvas, color, r)
    }
}

/** 等待 / 搜索时的同心涟漪。 */
class PulseView(ctx: Context, private val stoneColor: Int = BLACK) : View(ctx) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val painter = StonePainter()
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(width, height) / 2f
        val stoneR = maxR * 0.17f
        ring.strokeWidth = dpf(1.2f)
        for (i in 0 until 3) {
            val p = (t + i / 3f) % 1f
            val rr = stoneR + (maxR - stoneR) * p
            ring.color = withAlpha(Palette.INK, 0.30f * (1f - p) * (1f - p))
            canvas.drawCircle(cx, cy, rr, ring)
        }
        canvas.save()
        canvas.translate(cx, cy)
        painter.draw(canvas, stoneColor, stoneR)
        canvas.restore()
    }
}

/** 首页的装饰棋盘：渐隐的网格 + 一条黑子五连。 */
class HeroView(ctx: Context) : View(ctx) {
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.GRID; style = Paint.Style.STROKE }
    private val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.ACCENT; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val painter = StonePainter()
    private var reveal = 0f
    private val cells = 8

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fade.shader = RadialGradient(
            w / 2f, h / 2f, minOf(w, h) * 0.55f,
            intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun play() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            startDelay = 200
            addUpdateListener { reveal = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val cell = size / cells
        val ox = (width - size) / 2f + cell / 2f
        val oy = (height - size) / 2f + cell / 2f
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        grid.strokeWidth = dpf(1f)
        grid.alpha = 120
        for (i in 0 until cells) {
            canvas.drawLine(ox, oy + i * cell, ox + (cells - 1) * cell, oy + i * cell, grid)
            canvas.drawLine(ox + i * cell, oy, ox + i * cell, oy + (cells - 1) * cell, grid)
        }
        // 黑子沿反对角线五连，白子穿插其间
        val black = listOf(intArrayOf(1, 5), intArrayOf(2, 4), intArrayOf(3, 3), intArrayOf(4, 2), intArrayOf(5, 1))
        val white = listOf(intArrayOf(2, 2), intArrayOf(3, 4), intArrayOf(4, 4), intArrayOf(2, 5), intArrayOf(5, 3), intArrayOf(3, 1))
        val order = ArrayList<Pair<IntArray, Int>>()
        for (i in 0 until maxOf(black.size, white.size)) {
            if (i < black.size) order.add(black[i] to BLACK)
            if (i < white.size) order.add(white[i] to WHITE)
        }
        val shown = reveal * order.size
        val r = cell * 0.44f
        order.forEachIndexed { i, (p, color) ->
            val a = (shown - i).coerceIn(0f, 1f)
            if (a <= 0f) return@forEachIndexed
            canvas.save()
            canvas.translate(ox + p[1] * cell, oy + p[0] * cell)
            val s = 1.15f - 0.15f * a
            canvas.scale(s, s)
            painter.draw(canvas, color, r, (a * 255).toInt())
            canvas.restore()
        }
        if (reveal >= 1f) {
            line.strokeWidth = cell * 0.08f
            canvas.drawLine(ox + 5 * cell, oy + 1 * cell, ox + 1 * cell, oy + 5 * cell, line)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fade)
        canvas.restoreToCount(layer)
    }
}

/** 返回箭头。 */
class ChevronButton(ctx: Context, onClick: () -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.INK; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    init {
        isClickable = true
        setOnClickListener { onClick() }
        background = RippleDrawable(ColorStateList.valueOf(0x14000000), null, null)
        contentDescription = "返回"
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) * 0.2f
        p.strokeWidth = dpf(1.8f)
        val cx = width / 2f + s * 0.25f
        val cy = height / 2f
        path.reset()
        path.moveTo(cx + s * 0.5f, cy - s)
        path.lineTo(cx - s * 0.5f, cy)
        path.lineTo(cx + s * 0.5f, cy + s)
        canvas.drawPath(path, p)
    }
}

/** 细横线分隔。 */
fun Context.hairline(): View = View(this).apply { setBackgroundColor(Palette.HAIRLINE) }
