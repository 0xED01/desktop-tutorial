package com.ebike.dash

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 可以向下拖动关闭的卡片容器。
 * 只在“明显向下”的拖动时接管，并且手指下的列表已经滚到顶部时才接管，
 * 所以不会影响列表滚动、横向滑块和按钮点击。
 */
class DragDismissFrame(ctx: Context) : FrameLayout(ctx) {

    /** 拖动中：dy 为手指向下移动的距离（向上为负） */
    var onDrag: ((dy: Float) -> Unit)? = null
    /** 松手：dy 与向下的速度（像素/秒） */
    var onRelease: ((dy: Float, velocity: Float) -> Unit)? = null

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startY = 0f
    private var dragging = false
    private var vt: VelocityTracker? = null

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                dragging = false
                vt?.recycle()
                vt = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (dy > slop && dy > abs(dx) * 1.5f && !canScrollUpAt(this, e.x, e.y)) {
                    dragging = true
                    startY = e.rawY
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(e)
        vt?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> onDrag?.invoke(e.rawY - startY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.computeCurrentVelocity(1000)
                val v = vt?.yVelocity ?: 0f
                dragging = false
                vt?.recycle()
                vt = null
                onRelease?.invoke(e.rawY - startY, v)
            }
        }
        return true
    }

    /** 手指下面是否有还能往上滚的列表（x、y 为 v 自身坐标） */
    private fun canScrollUpAt(v: View, x: Float, y: Float): Boolean {
        if (v !== this && v.canScrollVertically(-1)) return true
        if (v is ViewGroup) {
            val px = x + v.scrollX
            val py = y + v.scrollY
            for (i in v.childCount - 1 downTo 0) {
                val c = v.getChildAt(i)
                if (c.visibility != View.VISIBLE) continue
                if (px >= c.left && px < c.right && py >= c.top && py < c.bottom) {
                    if (canScrollUpAt(c, px - c.left, py - c.top)) return true
                }
            }
        }
        return false
    }
}
