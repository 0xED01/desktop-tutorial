package com.islandpet.island

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextUtils
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.islandpet.model.GameState
import com.islandpet.model.Mood
import com.islandpet.model.PetRenderer
import com.islandpet.model.ShopItem
import com.islandpet.model.alertEmoji
import com.islandpet.model.speech
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class IslandAction { FEED, PLAY, PAT, CLEAN, SLEEP, HEAL, OPEN_APP }

/**
 * 灵动岛本体：一个围绕前置摄像头的黑色胶囊。
 * - 收起：左边宠物（会呼吸、眨眼），右边饱腹圆环 + 当前最需要处理的状态
 * - 提醒：胶囊变宽变高，显示一句话（互动反馈 / 宠物饿了等）
 * - 展开：点击胶囊后展开成卡片，有属性条和互动按钮
 */
@SuppressLint("ViewConstructor")
class IslandView(
    context: Context,
    /** 收起状态胶囊的宽高（px） */
    var compactW: Float,
    var compactH: Float,
    /** 屏幕宽度，用来计算展开后的尺寸 */
    var screenW: Float,
    private val onAction: (IslandAction) -> Unit,
    /** 需要大窗口（展开 / 提醒）或小窗口（收起）时回调 */
    private val onWindowMode: (large: Boolean) -> Unit,
) : View(context) {

    enum class Mode { COMPACT, FLASH, EXPANDED }

    private val d = resources.displayMetrics.density
    var mode = Mode.COMPACT
        private set

    private var state: GameState? = null
    private var flashText = ""

    private val fromRect = RectF()
    private val curRect = RectF()
    private val toRect = RectF()
    private var morph = 1f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 420
        interpolator = OvershootInterpolator(1.1f)
        addUpdateListener {
            morph = it.animatedValue as Float
            lerpRect(fromRect, toRect, morph, curRect)
            invalidate()
        }
    }

    private val startTime = SystemClock.uptimeMillis()
    private var nextBlink = 0L
    private var blinkUntil = 0L
    private val hearts = mutableListOf<Pair<Long, Float>>()
    private var lastTouch = 0L
    private var animating = true

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val keyline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val tmp = RectF()

    private data class Button(val action: IslandAction, val emoji: String, val label: String, val color: Int, val rect: RectF = RectF())
    private var buttons = listOf<Button>()
    private val petHit = RectF()

    private val collapseRunnable = Runnable { if (mode != Mode.COMPACT) setMode(Mode.COMPACT) }

    init {
        targetRect(Mode.COMPACT, curRect)
    }

    fun update(s: GameState) {
        state = s
        buttons = buildButtons(s)
        invalidate()
    }

    /** 胶囊变宽显示一句话，3 秒后收回 */
    fun flash(text: String) {
        if (mode == Mode.EXPANDED) {
            invalidate()
            return
        }
        flashText = text
        setMode(Mode.FLASH)
        removeCallbacks(collapseRunnable)
        postDelayed(collapseRunnable, 3200)
    }

    fun setAnimating(on: Boolean) {
        animating = on
        if (on) invalidate()
    }

    fun relayout(cw: Float, ch: Float, sw: Float) {
        compactW = cw; compactH = ch; screenW = sw
        targetRect(mode, curRect)
        invalidate()
    }

    private fun setMode(m: Mode) {
        if (m == mode && !animator.isRunning) return
        val growing = m != Mode.COMPACT
        if (growing) onWindowMode(true)
        mode = m
        fromRect.set(curRect)
        targetRect(m, toRect)
        animator.removeAllListeners()
        if (!growing) {
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (mode == Mode.COMPACT) onWindowMode(false)
                }
            })
        }
        animator.start()
    }

    // 窗口在大小模式间切换时，保持胶囊在屏幕上的位置不变
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val dx = (w - oldw) / 2f
        if (oldw != 0) {
            curRect.offset(dx, 0f); fromRect.offset(dx, 0f)
        }
        targetRect(mode, toRect)
        if (!animator.isRunning) curRect.set(toRect)
    }

    private fun targetRect(m: Mode, out: RectF) {
        val cx = if (width > 0) width / 2f else compactW / 2f + PAD * d
        val top = PAD * d
        val (w, h) = when (m) {
            Mode.COMPACT -> compactW to compactH
            Mode.FLASH -> min(screenW - 24 * d, max(compactW + 120 * d, 300 * d)) to compactH + 32 * d
            Mode.EXPANDED -> (screenW - 20 * d) to EXPANDED_H * d
        }
        out.set(cx - w / 2, top, cx + w / 2, top + h)
    }

    private fun lerpRect(a: RectF, b: RectF, t: Float, out: RectF) {
        out.set(a.left + (b.left - a.left) * t, a.top + (b.top - a.top) * t, a.right + (b.right - a.right) * t, a.bottom + (b.bottom - a.bottom) * t)
    }

    // ---------- 绘制 ----------

    override fun onDraw(c: Canvas) {
        val s = state ?: return
        val pet = s.pet ?: return
        val now = SystemClock.uptimeMillis()
        val t = (now - startTime) / 1000f
        if (now > nextBlink) {
            blinkUntil = now + 140
            nextBlink = now + 2500 + Random.nextLong(3000)
        }
        val blink = now < blinkUntil

        val r = curRect
        val radius = min(r.height() / 2, if (mode == Mode.EXPANDED) 32 * d else r.height() / 2)
        c.drawRoundRect(r, radius, radius, bg)
        keyline.color = s.themeColors.accent
        keyline.alpha = 70
        keyline.strokeWidth = 1 * d
        c.drawRoundRect(r, radius, radius, keyline)

        val contentAlpha = ((morph - 0.35f) / 0.65f).coerceIn(0f, 1f)
        c.save()
        c.clipRect(r)
        val mood = pet.mood()
        val accessory = ShopItem.find(pet.accessory)?.emoji
        when (mode) {
            Mode.COMPACT -> drawCompact(c, r, s, mood, accessory, t, blink, if (animator.isRunning) contentAlpha else 1f)
            Mode.FLASH -> drawFlash(c, r, s, mood, accessory, t, blink, contentAlpha)
            Mode.EXPANDED -> drawExpanded(c, r, s, mood, accessory, t, blink, contentAlpha)
        }
        c.restore()
        drawHearts(c, now)

        if (animating || animator.isRunning || hearts.isNotEmpty()) postInvalidateDelayed(33)
    }

    private fun drawCompact(c: Canvas, r: RectF, s: GameState, mood: Mood, acc: String?, t: Float, blink: Boolean, alpha: Float) {
        val pet = s.pet!!
        val h = r.height()
        val layer = c.saveLayerAlpha(r, (alpha * 255).toInt())
        PetRenderer.draw(c, r.left + h * 0.58f, r.centerY() + h * 0.02f, h * 0.95f, pet.species, pet.stage, mood, acc, t, blink, pet.eggTaps)

        // 右侧：饱腹圆环 + 状态
        val rcx = r.right - h * 0.55f
        val rr = h * 0.33f
        ring.strokeWidth = max(2f, h * 0.08f)
        ring.color = 0x33FFFFFF
        c.drawCircle(rcx, r.centerY(), rr, ring)
        if (!pet.isEgg) {
            ring.color = statColor(pet.hunger, 0xFF4CD964.toInt())
            tmp.set(rcx - rr, r.centerY() - rr, rcx + rr, r.centerY() + rr)
            c.drawArc(tmp, -90f, (360 * pet.hunger / 100).toFloat(), false, ring)
        }
        tp.textSize = h * 0.36f
        tp.textAlign = Paint.Align.CENTER
        c.drawText(s.alertEmoji(), rcx, r.centerY() + tp.textSize * 0.36f, tp)
        c.restoreToCount(layer)
    }

    private fun drawFlash(c: Canvas, r: RectF, s: GameState, mood: Mood, acc: String?, t: Float, blink: Boolean, alpha: Float) {
        val pet = s.pet!!
        val layer = c.saveLayerAlpha(r, (alpha * 255).toInt())
        val size = r.height() * 0.85f
        PetRenderer.draw(c, r.left + size * 0.62f, r.centerY(), size, pet.species, pet.stage, mood, acc, t, blink, pet.eggTaps)
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = 14 * d
        tp.isFakeBoldText = true
        val x = r.left + size * 1.2f
        c.drawText(pet.name, x, r.top + compactH + 2 * d, tp)
        tp.isFakeBoldText = false
        tp.textSize = 13 * d
        tp.alpha = 220
        val avail = r.right - x - 16 * d
        c.drawText(TextUtils.ellipsize(flashText, tp, avail, TextUtils.TruncateAt.END).toString(), x, r.top + compactH + 20 * d, tp)
        tp.alpha = 255
        c.restoreToCount(layer)
    }

    private fun drawExpanded(c: Canvas, r: RectF, s: GameState, mood: Mood, acc: String?, t: Float, blink: Boolean, alpha: Float) {
        val pet = s.pet!!
        val layer = c.saveLayerAlpha(r, (alpha * 255).toInt())
        val pad = 16 * d
        val accent = s.themeColors.accent

        // 宠物
        val petSize = 76 * d
        val pcx = r.left + pad + petSize / 2 - 4 * d
        val pcy = r.top + pad + petSize / 2 - 2 * d
        p.color = 0x14FFFFFF
        c.drawCircle(pcx, pcy, petSize * 0.5f, p)
        PetRenderer.draw(c, pcx, pcy, petSize, pet.species, pet.stage, mood, acc, t, blink, pet.eggTaps)
        petHit.set(pcx - petSize / 2, pcy - petSize / 2, pcx + petSize / 2, pcy + petSize / 2)

        // 金币（右上，避开摄像头）
        tp.textAlign = Paint.Align.RIGHT
        tp.textSize = 13 * d
        tp.isFakeBoldText = true
        tp.color = 0xFFFFD54F.toInt()
        c.drawText("💰 ${s.coins}", r.right - pad, r.top + pad + 10 * d, tp)
        tp.color = Color.WHITE

        // 名字 / 等级 / 心情（在摄像头下方）
        val x = r.left + pad + petSize + 6 * d
        val row1 = r.top + max(compactH + 14 * d, 46 * d)
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = 16 * d
        c.drawText(pet.name, x, row1, tp)
        val nameW = tp.measureText(pet.name)
        tp.isFakeBoldText = false
        tp.textSize = 12 * d
        tp.color = accent
        c.drawText("  Lv.${pet.level}  ", x + nameW, row1, tp)
        val lvW = tp.measureText("  Lv.${pet.level}  ")
        tp.color = 0xCCFFFFFF.toInt()
        c.drawText("${mood.emoji} ${mood.title}", x + nameW + lvW, row1, tp)

        // 对话气泡
        tp.textSize = 13 * d
        val bubble = TextUtils.ellipsize(s.speech(), tp, r.right - x - pad - 16 * d, TextUtils.TruncateAt.END).toString()
        val bw = tp.measureText(bubble) + 20 * d
        tmp.set(x, row1 + 8 * d, x + bw, row1 + 32 * d)
        p.color = 0x22FFFFFF
        c.drawRoundRect(tmp, 12 * d, 12 * d, p)
        tp.color = Color.WHITE
        c.drawText(bubble, x + 10 * d, tmp.bottom - 7 * d, tp)

        // 属性条
        val statsTop = r.top + 112 * d
        val stats = listOf(
            Triple("🍖", pet.hunger, 0xFFFF9F43.toInt()),
            Triple("💗", pet.happiness, 0xFFFF6B9A.toInt()),
            Triple("⚡", pet.energy, 0xFFFFD54F.toInt()),
            Triple("🧽", pet.cleanliness, 0xFF4DD0E1.toInt()),
        )
        val gap = 10 * d
        val segW = (r.width() - pad * 2 - gap * 3) / 4
        stats.forEachIndexed { i, (icon, v, col) ->
            val sx = r.left + pad + i * (segW + gap)
            tp.textSize = 11 * d
            c.drawText(icon, sx, statsTop + 4 * d, tp)
            val bx = sx + 18 * d
            tmp.set(bx, statsTop - 3 * d, sx + segW, statsTop + 3 * d)
            p.color = 0x26FFFFFF
            c.drawRoundRect(tmp, 3 * d, 3 * d, p)
            tmp.right = bx + max(3 * d, (sx + segW - bx) * (v / 100).toFloat())
            p.color = statColor(v, col)
            c.drawRoundRect(tmp, 3 * d, 3 * d, p)
        }

        // 按钮
        val bTop = statsTop + 16 * d
        val bH = 38 * d
        val bGap = 8 * d
        val bW = (r.width() - pad * 2 - bGap * (buttons.size - 1)) / buttons.size
        buttons.forEachIndexed { i, b ->
            b.rect.set(r.left + pad + i * (bW + bGap), bTop, r.left + pad + i * (bW + bGap) + bW, bTop + bH)
            p.color = b.color
            p.alpha = 90
            c.drawRoundRect(b.rect, bH / 2, bH / 2, p)
            tp.textAlign = Paint.Align.CENTER
            tp.textSize = 13 * d
            tp.isFakeBoldText = true
            c.drawText("${b.emoji} ${b.label}", b.rect.centerX(), b.rect.centerY() + 4.5f * d, tp)
            tp.isFakeBoldText = false
        }
        c.restoreToCount(layer)
    }

    private fun drawHearts(c: Canvas, now: Long) {
        hearts.removeAll { now - it.first > 1100 }
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = 18 * d
        for ((start, dx) in hearts) {
            val k = (now - start) / 1100f
            tp.alpha = ((1 - k) * 255).toInt()
            c.drawText("💗", petHit.centerX() + dx, petHit.centerY() - 20 * d - k * 50 * d, tp)
        }
        tp.alpha = 255
    }

    private fun statColor(v: Double, normal: Int) = when {
        v < 25 -> 0xFFFF5252.toInt()
        v < 50 -> 0xFFFFA726.toInt()
        else -> normal
    }

    private fun buildButtons(s: GameState): List<Button> {
        val pet = s.pet ?: return emptyList()
        val orange = 0xFFFF9F43.toInt()
        val green = 0xFF4CD964.toInt()
        val pink = 0xFFFF6B9A.toInt()
        val cyan = 0xFF4DD0E1.toInt()
        val indigo = 0xFF7C4DFF.toInt()
        val red = 0xFFFF5252.toInt()
        val yellow = 0xFFFFD54F.toInt()
        return when {
            pet.isEgg -> listOf(Button(IslandAction.PAT, "👆", "敲敲蛋", orange), Button(IslandAction.OPEN_APP, "🏠", "打开", indigo))
            pet.isSleeping -> listOf(Button(IslandAction.PAT, "🤲", "摸摸", pink), Button(IslandAction.SLEEP, "☀️", "叫醒", yellow), Button(IslandAction.OPEN_APP, "🏠", "打开", indigo))
            else -> listOf(
                Button(IslandAction.FEED, "🍖", "喂食", orange),
                if (pet.isSick) Button(IslandAction.HEAL, "💊", "吃药", red) else Button(IslandAction.PLAY, "🎾", "玩耍", green),
                Button(IslandAction.PAT, "🤲", "摸摸", pink),
                if (pet.poops > 0 || pet.cleanliness < 50) Button(IslandAction.CLEAN, "🧼", "清洁", cyan) else Button(IslandAction.SLEEP, "🌙", "睡觉", indigo),
            )
        }
    }

    // ---------- 触摸 ----------

    private var downAt = 0L
    private val longPress = Runnable {
        if (mode == Mode.EXPANDED) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onAction(IslandAction.OPEN_APP)
            setMode(Mode.COMPACT)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (mode == Mode.EXPANDED) setMode(Mode.COMPACT)
                return false
            }
            MotionEvent.ACTION_DOWN -> {
                if (!curRect.contains(e.x, e.y)) {
                    if (mode == Mode.EXPANDED) setMode(Mode.COMPACT)
                    return false
                }
                downAt = SystemClock.uptimeMillis()
                postDelayed(longPress, 600)
                return true
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (SystemClock.uptimeMillis() - downAt >= 600) return true
                lastTouch = SystemClock.uptimeMillis()
                removeCallbacks(collapseRunnable)
                if (mode != Mode.EXPANDED) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    setMode(Mode.EXPANDED)
                } else {
                    val hit = buttons.firstOrNull { it.rect.contains(e.x, e.y) }
                    when {
                        hit != null -> {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (hit.action == IslandAction.PAT) addHeart()
                            onAction(hit.action)
                            if (hit.action == IslandAction.OPEN_APP) setMode(Mode.COMPACT)
                        }
                        petHit.contains(e.x, e.y) -> {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            addHeart()
                            onAction(IslandAction.PAT)
                        }
                    }
                }
                if (mode == Mode.EXPANDED) postDelayed(collapseRunnable, 8000)
                return true
            }
        }
        return true
    }

    private fun addHeart() {
        hearts += SystemClock.uptimeMillis() to (Random.nextFloat() - 0.5f) * 40 * d
        invalidate()
    }

    companion object {
        const val PAD = 4f
        const val EXPANDED_H = 186f
    }
}
