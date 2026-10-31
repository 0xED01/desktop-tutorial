package com.ebike.dash

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DragDismissFrameTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun frameWithList(): Pair<DragDismissFrame, ScrollView> {
        val f = DragDismissFrame(ctx)
        val sv = ScrollView(ctx)
        sv.addView(LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(View(ctx), LinearLayout.LayoutParams(-1, 5000)) })
        f.addView(sv, android.widget.FrameLayout.LayoutParams(-1, -1))
        f.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
        f.layout(0, 0, 800, 1200)
        return f to sv
    }

    private fun drag(f: View, fromY: Float, toY: Float) {
        val t = SystemClock.uptimeMillis()
        f.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 400f, fromY, 0))
        for (i in 1..10) {
            val y = fromY + (toY - fromY) * i / 10
            f.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, 400f, y, 0))
        }
        f.dispatchTouchEvent(MotionEvent.obtain(t, t + 200, MotionEvent.ACTION_UP, 400f, toY, 0))
    }

    @Test
    fun downwardDragOnListAtTopIsTakenOver() {
        val (f, _) = frameWithList()
        var released: Float? = null
        var dragged = 0f
        f.onDrag = { dragged = it }
        f.onRelease = { dy, _ -> released = dy }
        drag(f, 300f, 700f)
        assertTrue(dragged > 300f)
        assertTrue((released ?: 0f) > 300f)
    }

    @Test
    fun scrolledListKeepsItsOwnScroll() {
        val (f, sv) = frameWithList()
        sv.scrollTo(0, 800)
        var released: Float? = null
        f.onRelease = { dy, _ -> released = dy }
        drag(f, 300f, 700f)
        assertNull(released)
        assertEquals(true, sv.scrollY < 800) // 列表自己往上滚了，没有被卡片抢走
    }
}
