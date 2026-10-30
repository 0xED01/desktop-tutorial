package com.ebike.dash

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.ebike.dash.databinding.PanelConfirmBinding
import kotlin.math.min

/**
 * 悬浮面板：居中的圆角卡片浮在地图上，背后一层淡淡的暗幕。
 * 菜单、全屏播放器和所有确认提示都用它，外观和动画完全一致。
 * 直接画在当前界面里（不另开窗口），动画更顺滑，也不会弹出系统状态栏。
 */
class FloatingPanel(
    private val act: AppCompatActivity,
    private val content: View,
    private val maxWidthDp: Int = 400,
) {
    private val host = act.findViewById<ViewGroup>(android.R.id.content)
    private val d = act.resources.displayMetrics.density
    private val root = FrameLayout(act)
    private val scrim = View(act)
    private val card = FrameLayout(act)
    private val back = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = dismiss()
    }

    var onDismiss: (() -> Unit)? = null
    var isShowing = false; private set

    fun show() {
        if (isShowing) return
        isShowing = true
        val night = (act.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        scrim.setBackgroundColor(if (night) Color.argb(140, 0, 0, 0) else Color.argb(90, 0, 0, 0))
        scrim.setOnClickListener { dismiss() }

        card.background = ContextCompat.getDrawable(act, R.drawable.bg_card)
        card.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(act, R.color.bg))
        card.elevation = 16 * d
        card.clipToOutline = true
        card.isClickable = true // 点卡片内部空白处不关闭
        card.addView(content, FrameLayout.LayoutParams(-1, -2))

        val screenW = act.resources.displayMetrics.widthPixels
        val w = min(screenW - (32 * d).toInt(), (maxWidthDp * d).toInt())
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        root.addView(card, FrameLayout.LayoutParams(w, -2, Gravity.CENTER).apply {
            topMargin = (40 * d).toInt()
            bottomMargin = (40 * d).toInt()
        })
        host.addView(root, FrameLayout.LayoutParams(-1, -1))
        act.onBackPressedDispatcher.addCallback(back)
        Motion.panelIn(scrim, card)
    }

    fun dismiss() {
        if (!isShowing) return
        isShowing = false
        back.remove()
        Motion.panelOut(scrim, card) {
            card.removeAllViews()
            host.removeView(root)
            onDismiss?.invoke()
        }
    }

    companion object {
        /** 统一样式的确认提示：标题、说明、两个胶囊按钮 */
        fun confirm(
            act: AppCompatActivity,
            title: String,
            message: String,
            positive: String,
            negative: String = "取消",
            onPositive: () -> Unit,
        ) {
            val b = PanelConfirmBinding.inflate(LayoutInflater.from(act))
            b.tvTitle.text = title
            b.tvMessage.text = message
            b.tvMessage.isVisible = message.isNotEmpty()
            b.btnPositive.text = positive
            b.btnNegative.text = negative
            val panel = FloatingPanel(act, b.root, maxWidthDp = 360)
            Motion.pressable(b.btnPositive, b.btnNegative)
            b.btnNegative.setOnClickListener { panel.dismiss() }
            b.btnPositive.setOnClickListener {
                panel.dismiss()
                onPositive()
            }
            panel.show()
        }
    }
}
