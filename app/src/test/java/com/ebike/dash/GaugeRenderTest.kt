package com.ebike.dash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

/** 把码表和转向箭头渲染成 PNG，便于检查视觉效果（输出到 app/build/gauge-preview） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GaugeRenderTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val out = File("build/gauge-preview").apply { mkdirs() }

    private fun render(v: View, size: Int, name: String, bg: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        v.draw(c)
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderGauges() {
        for ((name, speed) in listOf("gauge_18" to 18f, "gauge_32_over" to 32f)) {
            val g = SpeedometerView(ctx)
            g.caption = "东北 · 海拔 52m"
            g.setSpeed(speed)
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
            render(g, 720, name, Color.parseColor("#6A8CAF"))
            render(g, 720, name + "_alpha", Color.TRANSPARENT)
        }
        for (m in listOf("left", "slight right", "uturn", "straight")) {
            val t = TurnArrowView(ctx)
            t.set("turn", m)
            render(t, 160, "turn_" + m.replace(" ", "_"), Color.TRANSPARENT)
        }
        TurnArrowView(ctx).also { it.set("arrive", null); render(it, 160, "turn_arrive", Color.TRANSPARENT) }
    }
}
