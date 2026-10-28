package com.zen.sudoku.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zen.sudoku.SudokuViewModel
import com.zen.sudoku.data.ThemeMode
import com.zen.sudoku.engine.Difficulty
import com.zen.sudoku.ui.screens.DailyScreen
import com.zen.sudoku.ui.screens.GameScreen
import com.zen.sudoku.ui.screens.HomeScreen
import com.zen.sudoku.ui.screens.SettingsScreen
import com.zen.sudoku.ui.screens.StatsScreen
import com.zen.sudoku.ui.theme.SudokuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Renders key screens to PNGs (build/screenshots) for visual review.
 * Run with: ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val out = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun vm(dark: Boolean = false): SudokuViewModel {
        val vm = SudokuViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.updateSettings { it.copy(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        return vm
    }

    private fun playSomeMoves(vm: SudokuViewModel) {
        vm.newGame(Difficulty.MEDIUM)
        rule.waitUntil(10_000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); vm.game != null }
        val g = vm.game!!
        val empty = (0 until 81).filter { g.givens[it] == 0 }
        empty.take(6).forEach { vm.select(it); vm.input(g.solution[it]) }
        // a wrong entry
        val wrong = empty[6]
        vm.select(wrong); vm.input(g.solution[wrong] % 9 + 1)
        // notes
        vm.toggleNotes()
        empty.drop(7).take(4).forEach { i ->
            vm.select(i); vm.input(g.solution[i]); vm.input(g.solution[i] % 9 + 1)
        }
        vm.toggleNotes()
        vm.select(empty[0])
        vm.select(empty[0]); vm.select(empty[0]) // keep selected
    }

    @Test
    fun screens() {
        val vm = vm()
        var screen by androidx.compose.runtime.mutableStateOf(Screen.HOME)
        var dark by androidx.compose.runtime.mutableStateOf(false)
        val nav = Navigator({ screen.name }, {})
        rule.setContent {
            SudokuTheme(dark, vm.settings.accent) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(vm, nav)
                        Screen.GAME -> GameScreen(vm, nav)
                        Screen.DAILY -> DailyScreen(vm, nav)
                        Screen.STATS -> StatsScreen(vm, nav)
                        Screen.SETTINGS -> SettingsScreen(vm, nav)
                        Screen.HELP -> Unit
                    }
                }
            }
        }
        shot("1_home_empty")
        playSomeMoves(vm)
        screen = Screen.GAME
        shot("2_game")
        screen = Screen.HOME
        shot("3_home_continue")
        screen = Screen.DAILY
        shot("4_daily")
        screen = Screen.STATS
        shot("5_stats")
        screen = Screen.SETTINGS
        shot("6_settings")
        dark = true
        vm.updateSettings { it.copy(accent = 1) }
        screen = Screen.GAME
        shot("7_game_dark")
        screen = Screen.HOME
        shot("8_home_dark")
    }
}
