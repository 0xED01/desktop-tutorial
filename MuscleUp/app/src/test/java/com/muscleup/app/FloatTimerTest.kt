package com.muscleup.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.muscleup.app.data.Store
import com.muscleup.app.ui.LiveTicker
import com.muscleup.app.ui.Nav
import com.muscleup.app.ui.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FloatTimerTest {
    init {
        LiveTicker.enabled = false
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx: Context get() = rule.activity.applicationContext

    @Before
    fun reset() {
        rule.runOnUiThread {
            Store.resetAll()
            Store.updateProfile(Store.profile.copy(onboarded = true))
            FloatTimer.stop(ctx, false)
            Nav.stack.clear(); Nav.stack.add(Route.Main); Nav.tab = 0
        }
        rule.waitForIdle()
    }

    /** 模拟一个已经计时 2 分钟的状态 */
    private fun fakeRunning(minutesAgo: Int) {
        val start = System.currentTimeMillis() - minutesAgo * 60_000L
        ctx.getSharedPreferences("float_timer", Context.MODE_PRIVATE).edit()
            .putBoolean("running", true).putBoolean("paused", false).putLong("acc", 0)
            .putLong("seg", start).putLong("start", start).putString("label", "腿").commit()
        FloatTimer.restore(ctx)
    }

    @Test
    fun timerLogic() {
        rule.runOnUiThread {
            fakeRunning(2)
            assertTrue(FloatTimer.running)
            assertEquals(120L, FloatTimer.elapsedMs() / 1000)
            FloatTimer.togglePause(ctx)
            assertTrue(FloatTimer.paused)
            val frozen = FloatTimer.elapsedMs()
            Thread.sleep(50)
            assertEquals(frozen, FloatTimer.elapsedMs())
            FloatTimer.togglePause(ctx)
            assertFalse(FloatTimer.paused)
            val log = FloatTimer.stop(ctx, true)!!
            assertEquals("腿", log.label)
            assertEquals(120, log.seconds)
            assertEquals(1, Store.timerLogs.size)
            assertFalse(FloatTimer.running)
            // 少于 10 秒不保存
            FloatTimer.start(ctx)
            FloatTimer.stop(ctx, true)
            assertEquals(1, Store.timerLogs.size)
        }
    }

    @Test
    fun serviceShowPauseStop() {
        ShadowSettings.setCanDrawOverlays(true)
        rule.runOnUiThread {
            fakeRunning(5)
            val controller = Robolectric.buildService(FloatTimerService::class.java, Intent().setAction(FloatTimerService.ACTION_SHOW)).create()
            val service = controller.get()
            assertTrue(FloatTimerService.alive)
            controller.startCommand(0, 1)
            service.onStartCommand(Intent().setAction(FloatTimerService.ACTION_TOGGLE), 0, 2)
            assertTrue(FloatTimer.paused)
            service.onStartCommand(Intent().setAction(FloatTimerService.ACTION_HIDE), 0, 3)
            service.onStartCommand(Intent().setAction(FloatTimerService.ACTION_SHOW), 0, 4)
            service.onStartCommand(Intent().setAction(FloatTimerService.ACTION_STOP), 0, 5)
            assertFalse(FloatTimer.running)
            assertEquals(1, Store.timerLogs.size)
            assertEquals(300, Store.timerLogs[0].seconds)
            assertTrue(shadowOf(service).isStoppedBySelf)
            controller.destroy()
            assertFalse(FloatTimerService.alive)
        }
    }

    @Test
    fun screenStartsTimer() {
        ShadowSettings.setCanDrawOverlays(true)
        rule.runOnUiThread { Nav.go(Route.FloatTimerR) }
        rule.onNodeWithText("开始计时（显示悬浮窗）", substring = true).performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(FloatTimer.running)
        val started = shadowOf(rule.activity.application).nextStartedService
        assertEquals(FloatTimerService::class.java.name, started.component?.className)
        rule.onNodeWithText("结束并保存").assertExists()
    }

    @Test
    fun screenAsksPermission() {
        ShadowSettings.setCanDrawOverlays(false)
        rule.runOnUiThread { Nav.go(Route.FloatTimerR) }
        rule.onNodeWithText("开始计时（显示悬浮窗）", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("需要悬浮窗权限").assertExists()
        assertFalse(FloatTimer.running)
        rule.onNodeWithText("仅通知栏计时").performClick()
        rule.waitForIdle()
        assertTrue(FloatTimer.running)
    }
}
