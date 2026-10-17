package com.islandpet.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin

/**
 * 用 Canvas 画出宠物，不依赖图片素材。灵动岛悬浮窗和 App 界面共用。
 * 表情随心情变化：开心眯眼、睡觉闭眼、生病晕眼、难过流泪……
 */
object PetRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val rect = RectF()
    private const val INK = 0xFF2E2429.toInt()

    /**
     * @param cx,cy 中心点
     * @param size 画布边长
     * @param t 动画时间（秒），用于呼吸摆动
     * @param blink 是否眨眼
     */
    fun draw(
        c: Canvas, cx: Float, cy: Float, size: Float,
        species: Species, stage: Stage, mood: Mood,
        accessory: String? = null, t: Float = 0f, blink: Boolean = false, eggCracks: Int = 0,
    ) {
        c.save()
        if (stage == Stage.EGG) {
            c.rotate(sin(t * 3f) * 6f, cx, cy + size * 0.35f)
            drawEgg(c, cx, cy, size, species, eggCracks)
            c.restore()
            return
        }
        val sleeping = mood == Mood.SLEEPING
        val bob = if (sleeping) sin(t * 1.2f) * size * 0.012f else -kotlin.math.abs(sin(t * 2.4f)) * size * 0.04f
        val sway = if (sleeping) 0f else sin(t * 1.2f) * 3f
        c.translate(cx, cy + bob + size * (1 - stage.scale) * 0.25f)
        c.rotate(sway)
        c.scale(stage.scale, stage.scale)
        val s = size
        drawEars(c, s, species, mood)
        drawBody(c, s, species)
        drawFace(c, s, mood, blink)
        if (mood == Mood.DIRTY) drawDirt(c, s)
        accessory?.let {
            text.textSize = s * 0.3f
            text.color = Color.WHITE
            c.drawText(it, s * 0.12f, -s * 0.28f, text)
        }
        drawMark(c, s, mood, t)
        c.restore()
    }

    // ---------- 身体 ----------

    private fun drawBody(c: Canvas, s: Float, sp: Species) {
        fill.shader = LinearGradient(0f, -s * 0.3f, 0f, s * 0.45f, sp.body, sp.shade, Shader.TileMode.CLAMP)
        if (sp == Species.SLIME) {
            fill.shader = null
            fill.color = sp.body
            c.drawCircle(0f, -s * 0.3f, s * 0.11f, fill)
            fill.shader = LinearGradient(0f, -s * 0.25f, 0f, s * 0.45f, sp.body, sp.shade, Shader.TileMode.CLAMP)
            path.reset()
            rect.set(-s * 0.43f, -s * 0.23f, s * 0.43f, s * 0.43f)
            path.addRoundRect(rect, floatArrayOf(s * 0.4f, s * 0.4f, s * 0.4f, s * 0.4f, s * 0.12f, s * 0.12f, s * 0.12f, s * 0.12f), Path.Direction.CW)
            c.drawPath(path, fill)
            fill.shader = null
            fill.color = 0x73FFFFFF
            c.drawOval(-s * 0.32f, -s * 0.13f, -s * 0.16f, -s * 0.03f, fill)
            return
        }
        c.drawOval(-s * 0.41f, -s * 0.28f, s * 0.41f, s * 0.44f, fill)
        fill.shader = null
        fill.color = if (sp == Species.BUNNY) 0x80FFFFFF.toInt() else 0x59FFFFFF
        c.drawOval(-s * 0.21f, s * 0.11f, s * 0.21f, s * 0.41f, fill)
        if (sp == Species.DRAGON) {
            fill.color = 0xFFFFD966.toInt()
            for (i in 0..2) {
                val y = -s * 0.12f + i * s * 0.14f
                val x = s * 0.36f + i * s * 0.03f
                path.reset()
                path.moveTo(x, y - s * 0.05f)
                path.lineTo(x + s * 0.1f, y)
                path.lineTo(x, y + s * 0.05f)
                path.close()
                c.drawPath(path, fill)
            }
        }
    }

    private fun triangle(c: Canvas, x: Float, y: Float, w: Float, h: Float, rot: Float, color: Int) {
        c.save()
        c.rotate(rot, x, y)
        path.reset()
        path.moveTo(x, y - h / 2)
        path.lineTo(x + w / 2, y + h / 2)
        path.lineTo(x - w / 2, y + h / 2)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
        c.restore()
    }

    private fun drawEars(c: Canvas, s: Float, sp: Species, mood: Mood) {
        fill.shader = null
        when (sp) {
            Species.CAT -> {
                triangle(c, -s * 0.27f, -s * 0.27f, s * 0.24f, s * 0.26f, -12f, sp.shade)
                triangle(c, s * 0.27f, -s * 0.27f, s * 0.24f, s * 0.26f, 12f, sp.shade)
            }
            Species.DOG -> {
                fill.color = sp.shade
                for (dir in listOf(-1f, 1f)) {
                    c.save()
                    c.rotate(-20f * dir, dir * s * 0.4f, -s * 0.08f)
                    c.drawOval(dir * s * 0.4f - s * 0.1f, -s * 0.26f, dir * s * 0.4f + s * 0.1f, s * 0.1f, fill)
                    c.restore()
                }
            }
            Species.BUNNY -> {
                val droop = if (mood == Mood.SAD || mood == Mood.SICK) 35f else 8f
                for (dir in listOf(-1f, 1f)) {
                    c.save()
                    c.rotate(droop * dir, dir * s * 0.14f, -s * 0.2f)
                    rect.set(dir * s * 0.14f - s * 0.075f, -s * 0.57f, dir * s * 0.14f + s * 0.075f, -s * 0.15f)
                    fill.color = sp.body
                    c.drawRoundRect(rect, s * 0.075f, s * 0.075f, fill)
                    rect.inset(s * 0.035f, s * 0.035f)
                    fill.color = sp.shade
                    c.drawRoundRect(rect, s * 0.04f, s * 0.04f, fill)
                    c.restore()
                }
            }
            Species.DRAGON -> {
                triangle(c, -s * 0.22f, -s * 0.3f, s * 0.12f, s * 0.2f, -18f, 0xFFFFE080.toInt())
                triangle(c, s * 0.22f, -s * 0.3f, s * 0.12f, s * 0.2f, 18f, 0xFFFFE080.toInt())
            }
            Species.SLIME -> Unit
        }
    }

    // ---------- 表情 ----------

    private fun drawFace(c: Canvas, s: Float, mood: Mood, blink: Boolean) {
        val lw = max(1.5f, s * 0.035f)
        stroke.strokeWidth = lw
        stroke.color = INK
        val eyeY = s * 0.02f
        val w = s * 0.11f
        for (dir in listOf(-1f, 1f)) {
            val ex = dir * (s * 0.12f + w / 2)
            when {
                mood == Mood.HAPPY || mood == Mood.LOVED -> arc(c, ex, eyeY, w, w * 0.55f, up = true)
                mood == Mood.SLEEPING || (blink && mood != Mood.SICK) -> arc(c, ex, eyeY, w, w * 0.45f, up = false)
                mood == Mood.SLEEPY -> {
                    fill.color = INK
                    rect.set(ex - w / 2, eyeY - w * 0.14f, ex + w / 2, eyeY + w * 0.14f)
                    c.drawRoundRect(rect, w * 0.14f, w * 0.14f, fill)
                }
                mood == Mood.SICK -> {
                    val r = w * 0.4f
                    c.drawLine(ex - r, eyeY - r, ex + r, eyeY + r, stroke)
                    c.drawLine(ex + r, eyeY - r, ex - r, eyeY + r, stroke)
                }
                mood == Mood.SAD -> {
                    fill.color = INK
                    c.drawCircle(ex, eyeY, w * 0.45f, fill)
                    if (dir < 0) {
                        fill.color = 0xCC66E0FF.toInt()
                        c.drawOval(ex - w * 0.3f, eyeY + w * 0.5f, ex + w * 0.1f, eyeY + w * 1.1f, fill)
                    }
                }
                else -> {
                    fill.color = INK
                    c.drawCircle(ex, eyeY, w / 2, fill)
                    fill.color = Color.WHITE
                    c.drawCircle(ex + w * 0.15f, eyeY - w * 0.15f, w * 0.17f, fill)
                }
            }
        }
        // 腮红
        if (mood == Mood.HAPPY || mood == Mood.LOVED || mood == Mood.NORMAL) {
            fill.color = 0x73FF6B9A
            for (dir in listOf(-1f, 1f)) {
                c.drawOval(dir * s * 0.22f - s * 0.05f, s * 0.1f, dir * s * 0.22f + s * 0.05f, s * 0.16f, fill)
            }
        }
        // 嘴
        val my = s * 0.13f
        val mw = s * 0.12f
        when (mood) {
            Mood.HAPPY, Mood.LOVED -> {
                fill.color = 0xFFD94D59.toInt()
                path.reset()
                path.moveTo(-mw / 2, my)
                path.lineTo(mw / 2, my)
                path.quadTo(0f, my + mw * 0.9f, -mw / 2, my)
                c.drawPath(path, fill)
            }
            Mood.HUNGRY -> {
                fill.color = 0xFF993340.toInt()
                c.drawOval(-mw * 0.3f, my - mw * 0.1f, mw * 0.3f, my + mw * 0.6f, fill)
            }
            Mood.SAD, Mood.SICK -> arc(c, 0f, my + mw * 0.15f, mw * 0.7f, mw * 0.3f, up = true)
            Mood.SLEEPING -> {
                stroke.strokeWidth = lw * 0.8f
                c.drawCircle(0f, my + mw * 0.1f, mw * 0.15f, stroke)
                stroke.strokeWidth = lw
            }
            else -> arc(c, 0f, my, mw * 0.7f, mw * 0.3f, up = false)
        }
    }

    /** up = ^ 形，否则 ‿ 形 */
    private fun arc(c: Canvas, x: Float, y: Float, w: Float, h: Float, up: Boolean) {
        path.reset()
        if (up) {
            path.moveTo(x - w / 2, y + h / 2)
            path.quadTo(x, y - h * 1.1f, x + w / 2, y + h / 2)
        } else {
            path.moveTo(x - w / 2, y - h / 2)
            path.quadTo(x, y + h * 1.1f, x + w / 2, y - h / 2)
        }
        c.drawPath(path, stroke)
    }

    private fun drawDirt(c: Canvas, s: Float) {
        fill.color = 0x80795548.toInt()
        c.drawCircle(-s * 0.25f, s * 0.22f, s * 0.035f, fill)
        c.drawCircle(s * 0.2f, s * 0.3f, s * 0.025f, fill)
        c.drawCircle(s * 0.28f, s * 0.08f, s * 0.03f, fill)
    }

    private fun drawMark(c: Canvas, s: Float, mood: Mood, t: Float) {
        val (str, color) = when (mood) {
            Mood.SLEEPING -> "z" to Color.WHITE
            Mood.LOVED -> "♥" to 0xFFFF6B9A.toInt()
            Mood.HUNGRY -> "!" to 0xFFFF9800.toInt()
            else -> return
        }
        text.textSize = s * 0.24f
        text.color = color
        text.isFakeBoldText = true
        val float = (sin(t * 2f) * s * 0.03f)
        c.drawText(str, s * 0.38f, -s * 0.24f + float, text)
        text.isFakeBoldText = false
    }

    // ---------- 蛋 ----------

    private fun drawEgg(c: Canvas, cx: Float, cy: Float, s: Float, sp: Species, cracks: Int) {
        fill.shader = LinearGradient(0f, cy - s * 0.4f, 0f, cy + s * 0.4f, 0xFFFFFAEB.toInt(), 0xFFEDE0C7.toInt(), Shader.TileMode.CLAMP)
        c.drawOval(cx - s * 0.31f, cy - s * 0.4f, cx + s * 0.31f, cy + s * 0.4f, fill)
        fill.shader = null
        fill.color = sp.body
        c.drawCircle(cx - s * 0.12f, cy + s * 0.08f, s * 0.07f, fill)
        c.drawCircle(cx + s * 0.14f, cy - s * 0.12f, s * 0.05f, fill)
        c.drawCircle(cx + s * 0.08f, cy + s * 0.24f, s * 0.04f, fill)
        if (cracks > 0) {
            stroke.color = 0xFF735940.toInt()
            stroke.strokeWidth = max(1f, s * 0.025f)
            stroke.alpha = (255 * (cracks / 3f).coerceAtMost(1f)).toInt()
            path.reset()
            val left = cx - s * 0.2f
            path.moveTo(left, cy - s * 0.04f)
            for (i in 1..6) {
                val y = when (i % 4) { 1 -> -0.1f; 3 -> 0.02f; else -> -0.04f }
                path.lineTo(left + s * 0.4f * i / 6, cy + s * y)
            }
            c.drawPath(path, stroke)
            stroke.alpha = 255
        }
    }
}
