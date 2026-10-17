package com.gomoku.lan

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 棋盘。点击一次预选（显示虚影与十字准线），再次点击同一交叉点确认落子。
 */
class BoardView(ctx: Context, private val game: Game) : View(ctx) {

    /** 是否允许当前用户落子。 */
    var interactive = false
        set(value) { field = value; if (!value) clearPending() }

    /** 确认落子回调。 */
    var onPlace: ((Int, Int) -> Unit)? = null
    /** 预选状态变化回调。 */
    var onPendingChanged: ((Boolean) -> Unit)? = null

    private var pending = -1

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.GRID; style = Paint.Style.STROKE }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.GRID }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT; style = Paint.Style.STROKE }
    private val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.ACCENT; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val stones = StonePainter()
    private val rect = RectF()
    private val grain = Path()

    private var cell = 0f
    private var origin = 0f

    private var dropIndex = -1
    private var dropT = 1f
    private var winT = 0f
    private var dropAnim: ValueAnimator? = null
    private var winAnim: ValueAnimator? = null

    init {
        elevation = dpf(6)
        outlineProvider = roundedOutline(dpf(4))
        contentDescription = "棋盘"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) w else minOf(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cell = w / (N - 1 + 1.3f)
        origin = cell * 0.65f
        boardPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            Palette.BOARD_LIGHT, Palette.BOARD_DARK, Shader.TileMode.CLAMP,
        )
        // 极淡的木纹
        grain.reset()
        val rnd = Random(7)
        var y = 0f
        while (y < h) {
            grain.moveTo(0f, y)
            val amp = h * 0.004f + rnd.nextFloat() * h * 0.006f
            var x = 0f
            val step = w / 6f
            while (x < w) {
                grain.rQuadTo(step / 2, if (rnd.nextBoolean()) amp else -amp, step, 0f)
                x += step
            }
            y += h * (0.012f + rnd.nextFloat() * 0.03f)
        }
    }

    private fun px(i: Int) = origin + i * cell

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        rect.set(0f, 0f, w, w)
        val radius = dpf(4)
        canvas.drawRoundRect(rect, radius, radius, boardPaint)
        grainPaint.strokeWidth = dpf(0.8f)
        grainPaint.color = 0x0F5E3B12
        canvas.drawPath(grain, grainPaint)

        // 网格
        val end = px(N - 1)
        gridPaint.alpha = 190
        gridPaint.strokeWidth = maxOf(1f, dpf(0.75f))
        for (i in 1 until N - 1) {
            canvas.drawLine(origin, px(i), end, px(i), gridPaint)
            canvas.drawLine(px(i), origin, px(i), end, gridPaint)
        }
        gridPaint.strokeWidth = dpf(1.5f)
        canvas.drawRect(origin, origin, end, end, gridPaint)
        for ((r, c) in STARS) canvas.drawCircle(px(c), px(r), cell * 0.1f, starPaint)

        // 预选十字准线
        val pr = pending / N
        val pc = pending % N
        if (pending >= 0) {
            crossPaint.strokeWidth = dpf(1f)
            crossPaint.alpha = 70
            canvas.drawLine(origin, px(pr), end, px(pr), crossPaint)
            canvas.drawLine(px(pc), origin, px(pc), end, crossPaint)
        }

        // 棋子
        val sr = cell * 0.46f
        val last = game.lastMove
        for (r in 0 until N) for (c in 0 until N) {
            val color = game.at(r, c)
            if (color == EMPTY) continue
            val idx = r * N + c
            canvas.save()
            canvas.translate(px(c), px(r))
            if (idx == dropIndex && dropT < 1f) {
                val s = 1.22f - 0.22f * dropT
                canvas.scale(s, s)
                stones.draw(canvas, color, sr, (255 * minOf(1f, dropT * 1.6f)).toInt())
            } else {
                stones.draw(canvas, color, sr)
            }
            canvas.restore()
        }

        if (pending >= 0) {
            canvas.save()
            canvas.translate(px(pc), px(pr))
            stones.draw(canvas, game.turn, sr, 120, withShadow = false)
            canvas.restore()
        }

        if (last >= 0 && game.winLine == null) {
            canvas.drawCircle(px(last % N), px(last / N), sr * 0.17f, markPaint)
        }

        game.winLine?.let { l ->
            val x1 = px(l[1]); val y1 = px(l[0]); val x2 = px(l[3]); val y2 = px(l[2])
            winPaint.strokeWidth = cell * 0.11f
            winPaint.alpha = 235
            canvas.drawLine(x1, y1, x1 + (x2 - x1) * winT, y1 + (y2 - y1) * winT, winPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val c = ((e.x - origin) / cell).roundToInt()
                val r = ((e.y - origin) / cell).roundToInt()
                if (abs(e.x - px(c)) > cell * 0.6f || abs(e.y - px(r)) > cell * 0.6f) return true
                if (!game.canPlay(r, c)) return true
                val idx = r * N + c
                if (idx == pending) {
                    clearPending()
                    onPlace?.invoke(r, c)
                } else {
                    pending = idx
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onPendingChanged?.invoke(true)
                    invalidate()
                }
                return true
            }
        }
        return true
    }

    fun clearPending() {
        if (pending < 0) return
        pending = -1
        onPendingChanged?.invoke(false)
        invalidate()
    }

    /** 新落子后调用，播放落子动画；若决出胜负则播放连线动画。 */
    fun animateLastMove() {
        dropAnim?.cancel()
        dropIndex = game.lastMove
        dropT = 0f
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        dropAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { dropT = it.animatedValue as Float; invalidate() }
            start()
        }
        if (game.winLine != null) animateWin()
        invalidate()
    }

    private fun animateWin() {
        winAnim?.cancel()
        winT = 0f
        winAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 200
            duration = 480
            interpolator = DecelerateInterpolator()
            addUpdateListener { winT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** 状态整体变化（悔棋、新局）后调用。 */
    fun refresh() {
        dropAnim?.cancel(); winAnim?.cancel()
        dropT = 1f
        winT = if (game.winLine != null) 1f else 0f
        if (pending >= 0 && game.at(pending / N, pending % N) != EMPTY) clearPending()
        invalidate()
    }

    companion object {
        private val STARS = listOf(3 to 3, 3 to 11, 7 to 7, 11 to 3, 11 to 11)
    }
}
