package com.ebike.dash

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.ebike.dash.databinding.PanelConfirmBinding
import kotlin.math.min

/**
 * 悬浮面板：居中的圆角卡片浮在地图上，背后一层淡淡的暗幕。
 * 菜单、播放器和所有确认提示都用它，外观和动画完全一致。
 *
 * 内容由 build 按当前配色生成；切换浅色 / 深色时，已打开的面板会
 * 用新配色重建内容并渐变过去，和地图同步变色。
 */
class FloatingPanel(
    private val act: AppCompatActivity,
    private val maxWidthDp: Int = 400,
    /** 靠上显示（搜索面板用，避免被键盘挡住），并占屏幕高度的这一比例；null = 居中、高度随内容 */
    private val topHeightFraction: Float? = null,
    private val build: (inflater: LayoutInflater, panel: FloatingPanel) -> View,
) {
    private val host = act.findViewById<ViewGroup>(android.R.id.content)
    private val d = act.resources.displayMetrics.density
    private val root = FrameLayout(act)
    private val scrim = View(act)
    private val card = FrameLayout(act)
    private var content: View? = null
    private var shownPalette = palette
    private val back = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = dismiss()
    }

    var onDismiss: (() -> Unit)? = null
    var isShowing = false; private set

    /** 按当前配色生成的 inflater（保留 AppCompat / Material 控件的转换） */
    private fun inflater(): LayoutInflater = act.layoutInflater.cloneInContext(palette.themed(act))

    private fun scrimColor(p: Palette) = if (p.dark) Color.argb(140, 0, 0, 0) else Color.argb(90, 0, 0, 0)

    fun show() {
        if (isShowing) return
        isShowing = true
        open.add(this)
        shownPalette = palette
        scrim.setBackgroundColor(scrimColor(palette))
        scrim.setOnClickListener { dismiss() }

        card.background = ContextCompat.getDrawable(act, R.drawable.bg_card)
        card.backgroundTintList = ColorStateList.valueOf(palette.surface)
        card.elevation = 16 * d
        card.clipToOutline = true
        card.isClickable = true // 点卡片内部空白处不关闭
        val v = build(inflater(), this)
        content = v
        card.addView(v, FrameLayout.LayoutParams(-1, if (topHeightFraction != null) -1 else -2))

        val screenW = act.resources.displayMetrics.widthPixels
        val screenH = act.resources.displayMetrics.heightPixels
        val w = min(screenW - (32 * d).toInt(), (maxWidthDp * d).toInt())
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        val lp = topHeightFraction?.let { f ->
            FrameLayout.LayoutParams(w, (screenH * f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = (52 * d).toInt()
            }
        } ?: FrameLayout.LayoutParams(w, -2, Gravity.CENTER).apply {
            topMargin = (40 * d).toInt()
            bottomMargin = (40 * d).toInt()
        }
        root.addView(card, lp)
        host.addView(root, FrameLayout.LayoutParams(-1, -1))
        act.onBackPressedDispatcher.addCallback(back)
        Motion.panelIn(scrim, card)
    }

    fun dismiss() {
        if (!isShowing) return
        isShowing = false
        open.remove(this)
        back.remove()
        Motion.panelOut(scrim, card) {
            card.removeAllViews()
            host.removeView(root)
            onDismiss?.invoke()
        }
    }

    /** 配色变化：用新配色重建内容，旧内容淡出、新内容淡入，卡片和暗幕颜色渐变 */
    private fun restyle() {
        val from = shownPalette
        val to = palette
        if (from === to) return
        shownPalette = to
        val old = content ?: return
        val oldScroll = (old as? ScrollView)?.scrollY ?: 0
        val fresh = build(inflater(), this)
        content = fresh
        fresh.alpha = 0f
        card.addView(fresh, FrameLayout.LayoutParams(-1, if (topHeightFraction != null) -1 else -2))
        (fresh as? ScrollView)?.post { fresh.scrollTo(0, oldScroll) }
        old.animate().alpha(0f).setDuration(Motion.SHORT).setInterpolator(Motion.EASE)
            .withEndAction { card.removeView(old) }.start()
        fresh.animate().alpha(1f).setDuration(Motion.STANDARD).setInterpolator(Motion.EASE).start()
        val ev = ArgbEvaluator()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.STANDARD
            interpolator = Motion.EASE
            addUpdateListener {
                val f = it.animatedFraction
                card.backgroundTintList = ColorStateList.valueOf(ev.evaluate(f, from.surface, to.surface) as Int)
                scrim.setBackgroundColor(ev.evaluate(f, scrimColor(from), scrimColor(to)) as Int)
            }
            start()
        }
    }

    companion object {
        /** 当前配色，由主界面在切换浅色 / 深色时更新 */
        var palette: Palette = Palette.LIGHT
            set(v) {
                if (field === v) return
                field = v
                open.toList().forEach { it.restyle() }
            }

        private val open = mutableListOf<FloatingPanel>()

        /** 统一样式的选项列表：标题 + 一列胶囊按钮（代替系统的选择对话框） */
        fun actions(act: AppCompatActivity, title: String, items: List<String>, onPick: (Int) -> Unit) {
            FloatingPanel(act, maxWidthDp = 360) { inf, panel ->
                val b = com.ebike.dash.databinding.PanelActionsBinding.inflate(inf)
                b.tvTitle.text = title
                items.forEachIndexed { i, label ->
                    val btn = com.ebike.dash.databinding.ItemActionBinding.inflate(inf, b.list, false).root
                    btn.text = label
                    Motion.pressable(btn)
                    btn.setOnClickListener {
                        panel.dismiss()
                        onPick(i)
                    }
                    b.list.addView(btn)
                }
                b.btnCancel.setOnClickListener { panel.dismiss() }
                Motion.pressable(b.btnCancel)
                b.root
            }.show()
        }

        /** 统一样式的确认提示：标题、说明、两个胶囊按钮 */
        fun confirm(
            act: AppCompatActivity,
            title: String,
            message: String,
            positive: String,
            negative: String = "取消",
            onNegative: (() -> Unit)? = null,
            onPositive: () -> Unit,
        ) {
            FloatingPanel(act, maxWidthDp = 360) { inf, panel ->
                val b = PanelConfirmBinding.inflate(inf)
                b.tvTitle.text = title
                b.tvMessage.text = message
                b.tvMessage.isVisible = message.isNotEmpty()
                b.btnPositive.text = positive
                b.btnNegative.text = negative
                Motion.pressable(b.btnPositive, b.btnNegative)
                b.btnNegative.setOnClickListener {
                    panel.dismiss()
                    onNegative?.invoke()
                }
                b.btnPositive.setOnClickListener {
                    panel.dismiss()
                    onPositive()
                }
                b.root
            }.show()
        }
    }
}
