package com.ebike.dash

import android.animation.ArgbEvaluator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * 全 App 统一的动效规范：同一条缓动曲线、两档时长。
 * - STANDARD：卡片出现 / 消失、位置移动、颜色切换
 * - SHORT：按压、图标和文字的切换
 */
object Motion {
    const val STANDARD = 320L
    const val SHORT = 160L
    /** 先快后慢的“强调”曲线，所有动画共用 */
    val EASE: TimeInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    private const val PRESS_SCALE = 0.92f
    private const val CARD_SCALE = 0.96f
    private const val OFFSET_DP = 16f

    private fun dp(v: View, x: Float) = x * v.resources.displayMetrics.density

    // ---------------------------------------------------------------- 卡片出现 / 消失

    /**
     * 显示或隐藏一张卡片：从下方轻轻浮起 + 淡入 + 微缩放；
     * 同一列里的其它卡片平滑让位，而不是跳动。
     */
    fun setVisible(v: View, show: Boolean) {
        val parent = v.parent as? ViewGroup ?: return
        val hiding = v.getTag(R.id.motion_hiding) == true
        val shownNow = v.visibility == View.VISIBLE && !hiding
        if (show == shownNow) return
        v.animate().cancel()
        v.setTag(R.id.motion_hiding, null)
        val siblings = (0 until parent.childCount).map { parent.getChildAt(it) }
            .filter { it !== v && it.visibility == View.VISIBLE }

        if (show) {
            val before = siblings.associateWith { screenY(it) }
            if (v.visibility != View.VISIBLE) {
                v.alpha = 0f
                v.translationY = dp(v, OFFSET_DP)
                v.scaleX = CARD_SCALE
                v.scaleY = CARD_SCALE
            }
            v.visibility = View.VISIBLE
            v.doOnPreDraw {
                v.pivotX = v.width / 2f
                v.pivotY = v.height.toFloat()
                slide(before)
                v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(STANDARD).setInterpolator(EASE).start()
            }
        } else {
            v.setTag(R.id.motion_hiding, true)
            v.pivotX = v.width / 2f
            v.pivotY = v.height.toFloat()
            v.animate().alpha(0f).translationY(dp(v, OFFSET_DP)).scaleX(CARD_SCALE).scaleY(CARD_SCALE)
                .setDuration(SHORT + 60).setInterpolator(EASE)
                .withEndAction {
                    val before = siblings.associateWith { screenY(it) }
                    v.setTag(R.id.motion_hiding, null)
                    v.visibility = View.GONE
                    v.alpha = 1f; v.translationY = 0f; v.scaleX = 1f; v.scaleY = 1f
                    parent.doOnPreDraw { slide(before) }
                }
                .start()
        }
    }

    private fun screenY(v: View): Float {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return loc[1].toFloat()
    }

    /** 布局变化后，把位置变动的卡片从旧位置平滑移到新位置 */
    private fun slide(before: Map<View, Float>) {
        for ((s, oldY) in before) {
            val layoutY = screenY(s) - s.translationY
            val dy = oldY - layoutY
            if (kotlin.math.abs(dy) < 0.5f) continue
            s.translationY = dy
            s.animate().translationY(0f).setDuration(STANDARD).setInterpolator(EASE).start()
        }
    }

    // ---------------------------------------------------------------- 悬浮面板

    private const val PANEL_SCALE = 0.92f
    private const val PANEL_OFFSET_DP = 36f

    /** 弹簧参数：几乎不回弹、略带惯性的自然停稳，所有悬浮窗共用 */
    private const val SPRING_STIFFNESS = 380f
    private const val SPRING_DAMPING = 0.86f

    /** 让 view 的某个属性用弹簧动画到 target（会先停掉同一属性上正在跑的弹簧） */
    fun spring(v: View, prop: DynamicAnimation.ViewProperty, target: Float, startVelocity: Float = 0f) {
        @Suppress("UNCHECKED_CAST")
        val map = (v.getTag(R.id.motion_springs) as? HashMap<DynamicAnimation.ViewProperty, SpringAnimation>)
            ?: HashMap<DynamicAnimation.ViewProperty, SpringAnimation>().also { v.setTag(R.id.motion_springs, it) }
        val anim = map.getOrPut(prop) {
            SpringAnimation(v, prop).apply {
                spring = SpringForce().setStiffness(SPRING_STIFFNESS).setDampingRatio(SPRING_DAMPING)
            }
        }
        anim.cancel()
        anim.setStartVelocity(startVelocity)
        anim.animateToFinalPosition(target)
    }

    fun cancelSprings(v: View) {
        @Suppress("UNCHECKED_CAST")
        (v.getTag(R.id.motion_springs) as? HashMap<DynamicAnimation.ViewProperty, SpringAnimation>)?.values?.forEach { it.cancel() }
    }

    /**
     * 悬浮面板打开：暗幕渐显；卡片从下方弹簧浮起、放大、淡入；
     * 卡片里的内容一行一行依次浮现。
     */
    fun panelIn(scrim: View, card: View, content: View? = null) {
        scrim.alpha = 0f
        card.alpha = 0f
        card.scaleX = PANEL_SCALE
        card.scaleY = PANEL_SCALE
        card.translationY = dp(card, PANEL_OFFSET_DP)
        val rows = content?.let { rowsOf(it) }.orEmpty()
        rows.forEach { it.alpha = 0f; it.translationY = dp(it, 10f) }
        card.doOnPreDraw {
            card.pivotX = card.width / 2f
            card.pivotY = card.height / 2f
            scrim.animate().alpha(1f).setDuration(STANDARD).setInterpolator(EASE).start()
            card.animate().alpha(1f).setDuration(SHORT + 40).setInterpolator(EASE).start()
            spring(card, DynamicAnimation.TRANSLATION_Y, 0f)
            spring(card, DynamicAnimation.SCALE_X, 1f)
            spring(card, DynamicAnimation.SCALE_Y, 1f)
            rows.forEachIndexed { i, r ->
                r.animate().alpha(1f).translationY(0f).setStartDelay(70L + i * 28L)
                    .setDuration(STANDARD).setInterpolator(EASE).start()
            }
        }
    }

    /** 卡片里“一行一行”的内容：ScrollView 里的那一层，或内容本身的直接子项（最多 12 行） */
    private fun rowsOf(content: View): List<View> {
        var g: View = content
        if (g is android.widget.ScrollView && g.childCount == 1) g = g.getChildAt(0)
        if (g !is ViewGroup) return emptyList()
        return (0 until g.childCount).map { g.getChildAt(it) }.filter { it.visibility == View.VISIBLE }.take(12)
    }

    /** 悬浮面板关闭：与打开相反，略快 */
    fun panelOut(scrim: View, card: View, end: () -> Unit) {
        cancelSprings(card)
        scrim.animate().cancel()
        card.animate().cancel()
        scrim.animate().alpha(0f).setDuration(SHORT + 60).setInterpolator(EASE).start()
        card.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).translationY(card.translationY + dp(card, PANEL_OFFSET_DP / 2))
            .setDuration(SHORT + 60).setInterpolator(EASE).withLayer()
            .withEndAction(end).start()
    }

    /** 向下甩走：顺着手指的速度滑出屏幕 */
    fun panelFling(scrim: View, card: View, velocity: Float, end: () -> Unit) {
        cancelSprings(card)
        val parentH = (card.parent as? View)?.height ?: card.height
        val distance = (parentH - card.top).toFloat() - card.translationY
        val speed = maxOf(velocity, dp(card, 2400f))
        val ms = (distance / speed * 1000).toLong().coerceIn(140L, 300L)
        scrim.animate().alpha(0f).setDuration(ms).setInterpolator(EASE).start()
        card.animate().translationY(card.translationY + distance).setDuration(ms)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).withLayer()
            .withEndAction(end).start()
    }

    /** 拖动松手但没到关闭的程度：弹回原位 */
    fun panelSettle(scrim: View, card: View, velocity: Float) {
        spring(card, DynamicAnimation.TRANSLATION_Y, 0f, velocity)
        spring(card, DynamicAnimation.SCALE_X, 1f)
        spring(card, DynamicAnimation.SCALE_Y, 1f)
        scrim.animate().alpha(1f).setDuration(STANDARD).setInterpolator(EASE).start()
    }

    /** 卡片内容变化（高度变了）时，平滑过渡而不是跳一下 */
    fun smoothResize(vararg groups: ViewGroup) {
        for (g in groups) {
            g.layoutTransition = android.animation.LayoutTransition().apply {
                disableTransitionType(android.animation.LayoutTransition.APPEARING)
                disableTransitionType(android.animation.LayoutTransition.DISAPPEARING)
                disableTransitionType(android.animation.LayoutTransition.CHANGE_APPEARING)
                disableTransitionType(android.animation.LayoutTransition.CHANGE_DISAPPEARING)
                enableTransitionType(android.animation.LayoutTransition.CHANGING)
                setDuration(STANDARD)
                setInterpolator(android.animation.LayoutTransition.CHANGING, EASE)
            }
        }
    }

    /** 启动时依次浮现 */
    fun enter(views: List<View>, stagger: Long = 40) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dp(v, OFFSET_DP / 2)
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * stagger)
                .setDuration(STANDARD).setInterpolator(EASE).start()
        }
    }

    // ---------------------------------------------------------------- 按压 / 颜色 / 图标 / 文字

    /** 按下时轻微缩小，松手回弹；不影响点击事件 */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(vararg views: View, scale: Float = PRESS_SCALE) {
        for (v in views) v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(scale).scaleY(scale).setDuration(SHORT).setInterpolator(EASE).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(STANDARD).setInterpolator(EASE).start()
            }
            false
        }
    }

    /** 圆形按钮在“开 / 关”两种配色之间渐变 */
    fun tint(btn: ImageView, bg: Int, fg: Int, animate: Boolean = true) {
        val fromBg = btn.backgroundTintList?.defaultColor ?: bg
        val fromFg = btn.imageTintList?.defaultColor ?: fg
        (btn.getTag(R.id.motion_tint) as? ValueAnimator)?.cancel()
        if (!animate || (fromBg == bg && fromFg == fg)) {
            btn.backgroundTintList = ColorStateList.valueOf(bg)
            btn.imageTintList = ColorStateList.valueOf(fg)
            return
        }
        val ev = ArgbEvaluator()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = STANDARD
            interpolator = EASE
            addUpdateListener {
                val f = it.animatedFraction
                btn.backgroundTintList = ColorStateList.valueOf(ev.evaluate(f, fromBg, bg) as Int)
                btn.imageTintList = ColorStateList.valueOf(ev.evaluate(f, fromFg, fg) as Int)
            }
            btn.setTag(R.id.motion_tint, this)
            start()
        }
    }

    /** 换图标（如播放 ⇄ 暂停）：缩小淡出，再放大淡入 */
    fun swapIcon(iv: ImageView, res: Int) {
        if (iv.getTag(R.id.motion_icon) == res) return
        val first = iv.getTag(R.id.motion_icon) == null
        iv.setTag(R.id.motion_icon, res)
        if (first) {
            iv.setImageResource(res)
            return
        }
        iv.animate().cancel()
        iv.animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(SHORT / 2).setInterpolator(EASE)
            .withEndAction {
                iv.setImageResource(res)
                iv.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(SHORT).setInterpolator(EASE).start()
            }.start()
    }

    /** 换文字（如切歌）：淡出旧文字，淡入新文字 */
    fun setText(tv: TextView, text: CharSequence) {
        if (tv.text.toString() == text.toString()) return
        if (tv.text.isNullOrEmpty()) {
            tv.text = text
            return
        }
        tv.animate().cancel()
        tv.animate().alpha(0f).setDuration(SHORT / 2).setInterpolator(EASE).withEndAction {
            tv.text = text
            tv.animate().alpha(1f).setDuration(SHORT).setInterpolator(EASE).start()
        }.start()
    }
}
