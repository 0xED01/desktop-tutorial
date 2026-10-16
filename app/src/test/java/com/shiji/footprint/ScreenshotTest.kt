package com.shiji.footprint

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import com.shiji.footprint.data.Prefs
import com.shiji.footprint.data.Repository
import com.shiji.footprint.data.TrackPoint
import com.shiji.footprint.geo.Geo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/**
 * 用示例数据渲染各页面截图（输出到 screenshots/ 目录），便于检查界面效果。
 * 运行：./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun walk(day: LocalDate, startHour: Int, pts: List<Pair<Double, Double>>): List<TrackPoint> {
        var t = day.atStartOfDay(ZoneId.systemDefault()).plusHours(startHour.toLong()).toInstant().toEpochMilli()
        return pts.map { (lat, lon) -> t += 10_000; TrackPoint(t, lat, lon, 8f, 1.4f) }
    }

    private fun line(a: Pair<Double, Double>, b: Pair<Double, Double>): List<Pair<Double, Double>> {
        val d = Geo.distance(a.first, a.second, b.first, b.second)
        val n = (d / 15).toInt().coerceAtLeast(1)
        return (0..n).map { i -> val f = i.toDouble() / n; (a.first + (b.first - a.first) * f) to (a.second + (b.second - a.second) * f) }
    }

    private fun path(vararg c: Double) = c.toList().chunked(2).map { it[0] to it[1] }.zipWithNext().flatMap { (a, b) -> line(a, b) }

    private fun arc(cLat: Double, cLon: Double, rKm: Double, from: Double, to: Double): List<Pair<Double, Double>> {
        val n = (rKm * 1000 * (to - from) / 15).toInt()
        return (0..n).map { i ->
            val a = from + (to - from) * i / n
            val wobble = 1 + 0.08 * sin(a * 7)
            (cLat + rKm * wobble / 111.0 * sin(a)) to (cLon + rKm * wobble / (111.0 * cos(Math.toRadians(cLat))) * cos(a))
        }
    }

    private fun seed() {
        val today = LocalDate.now()
        val segs = listOf(
            walk(today, 9, arc(30.2437, 120.1405, 1.55, -0.6, 3.6)),
            walk(today, 15, path(30.2575, 120.1640, 30.2575, 120.1720, 30.2520, 120.1720, 30.2520, 120.1800)),
            walk(today.minusDays(1), 8, path(30.2600, 120.1560, 30.2660, 120.1560, 30.2660, 120.1700, 30.2740, 120.1700, 30.2740, 120.1820)),
            walk(today.minusDays(2), 18, path(30.2490, 120.1650, 30.2410, 120.1690, 30.2330, 120.1660, 30.2290, 120.1560)),
            walk(today.minusDays(4), 7, arc(30.2600, 120.1700, 0.6, 0.0, 6.2)),
            walk(today.minusDays(6), 10, path(30.2800, 120.1300, 30.2700, 120.1450, 30.2620, 120.1500)),
            walk(today.minusDays(9), 10, arc(31.2304, 121.4737, 0.8, 0.0, 5.0)),
        )
        Repository.importSegments(segs) {}
        val deadline = System.currentTimeMillis() + 20_000
        while (Repository.stats.value.points == 0L && System.currentTimeMillis() < deadline) Thread.sleep(100)
        Thread.sleep(500)
        val g = Geo.wgsToGcj(30.2500, 120.1580)
        Prefs.saveCamera(g[0], g[1], 13.6)
    }

    private fun settle(ms: Long = 1500) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(100)
        }
        rule.waitForIdle()
    }

    @Test
    fun screens() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            settle(8000)
            rule.onRoot().captureRoboImage("../screenshots/1_map.png")
            rule.onNodeWithText("足迹").performClick()
            settle(6000)
            rule.onRoot().captureRoboImage("../screenshots/2_timeline.png")
            rule.onNodeWithText("统计").performClick()
            settle()
            rule.onRoot().captureRoboImage("../screenshots/3_stats.png")
            rule.onNodeWithText("我的").performClick()
            settle()
            rule.onRoot().captureRoboImage("../screenshots/4_settings.png")
        }
    }
}
