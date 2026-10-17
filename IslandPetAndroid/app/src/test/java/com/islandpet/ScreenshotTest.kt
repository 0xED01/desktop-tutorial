package com.islandpet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.islandpet.island.IslandView
import com.islandpet.model.GameState
import com.islandpet.model.PetRepo
import com.islandpet.model.Species
import com.islandpet.model.adopt
import com.islandpet.model.pat
import com.islandpet.model.tick
import com.islandpet.ui.HomeScreen
import com.islandpet.ui.IslandSetupScreen
import com.islandpet.ui.ShopScreen
import com.islandpet.ui.Orange
import com.islandpet.ui.backgroundBrush
import com.islandpet.ui.games.GamesScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val out = File("build/shots").apply { mkdirs() }

    private fun state(species: Species = Species.CAT, hungry: Boolean = false): GameState {
        val s = GameState()
        val now = System.currentTimeMillis()
        s.adopt("团子", species, now - 3600_000)
        repeat(6) { s.pat(now - 3600_000) }
        s.tick(now)
        s.pet!!.level = 6
        s.pet!!.accessory = "bow"
        s.pet!!.poops = 1
        if (hungry) s.pet!!.hunger = 18.0
        s.coins = 268
        s.lastMessage = null
        return s
    }

    private fun shootIsland(name: String, s: GameState, act: (IslandView) -> Unit) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = ctx.resources.displayMetrics.density
        val w = (411 * d).toInt()
        val h = (220 * d).toInt()
        val v = IslandView(ctx, 150 * d, 36 * d, w.toFloat(), onAction = {}, onWindowMode = {})
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        v.update(s)
        act(v)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(70, 90, 130))
        // 模拟状态栏和前置摄像头
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13 * d }
        c.drawText("18:30", 20 * d, 24 * d, p)
        c.drawText("📶 85%", w - 70 * d, 24 * d, p)
        v.draw(c)
        p.color = Color.rgb(25, 25, 30)
        c.drawCircle(w / 2f, IslandView.PAD * d + 18 * d, 7 * d, p)
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun islandStates() {
        val s = state()
        shootIsland("island_compact", s) {}
        shootIsland("island_flash", s) { it.flash("🐟 是我的最爱！") }
        shootIsland("island_expanded", s) { v ->
            val t = SystemClock.uptimeMillis()
            val x = v.width / 2f
            val y = (IslandView.PAD + 18) * v.resources.displayMetrics.density
            v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
            v.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
        }
        val egg = GameState().apply { adopt("蛋蛋", Species.DRAGON) }
        shootIsland("island_egg", egg) {}
        val sick = state(Species.BUNNY, hungry = true).apply { pet!!.isSick = true }
        shootIsland("island_sick_compact", sick) {}
    }

    @Composable
    private fun Themed(s: GameState, content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Orange)) {
            Surface(color = androidx.compose.ui.graphics.Color.Transparent, contentColor = androidx.compose.ui.graphics.Color.White) {
                Box(Modifier.fillMaxSize().background(s.backgroundBrush())) { content() }
            }
        }
    }

    @Test
    fun screens() {
        PetRepo.init(ApplicationProvider.getApplicationContext())
        val s = state(Species.DOG)
        compose.mainClock.autoAdvance = false
        compose.setContent { Themed(s) { HomeScreen(s, onOpenSetup = {}) } }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/shots/screen_home.png")
    }

    @Test
    fun screensShop() {
        val s = state(Species.SLIME)
        compose.mainClock.autoAdvance = false
        compose.setContent { Themed(s) { ShopScreen(s) } }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/shots/screen_shop.png")
    }

    @Test
    fun screensGames() {
        val s = state(Species.DRAGON)
        compose.mainClock.autoAdvance = false
        compose.setContent { Themed(s) { GamesScreen(s) } }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/shots/screen_games.png")
    }

    @Test
    fun screensSetup() {
        val s = state()
        compose.mainClock.autoAdvance = false
        compose.setContent { Themed(s) { IslandSetupScreen(onBack = {}) } }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/shots/screen_setup.png")
    }
}
