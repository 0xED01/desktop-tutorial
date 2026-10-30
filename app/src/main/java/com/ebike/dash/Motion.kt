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
