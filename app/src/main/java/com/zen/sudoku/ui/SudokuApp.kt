package com.zen.sudoku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zen.sudoku.SudokuViewModel
import com.zen.sudoku.ui.screens.DailyScreen
import com.zen.sudoku.ui.screens.GameScreen
import com.zen.sudoku.ui.screens.HelpScreen
import com.zen.sudoku.ui.screens.HomeScreen
import com.zen.sudoku.ui.screens.SettingsScreen
import com.zen.sudoku.ui.screens.StatsScreen

enum class Screen { HOME, GAME, DAILY, STATS, SETTINGS, HELP }

/** Minimal back-stack navigation; the stack is kept as a saveable string. */
class Navigator(private val get: () -> String, private val set: (String) -> Unit) {
    val stack get() = get().split(",").map(Screen::valueOf)
    val current get() = stack.last()
    fun push(s: Screen) { if (current != s) set((stack + s).joinToString(",") { it.name }) }
    fun pop() { if (stack.size > 1) set(stack.dropLast(1).joinToString(",") { it.name }) }
    /** Replaces everything above HOME with [s]. */
    fun reset(s: Screen) = set(listOf(Screen.HOME, s).distinct().joinToString(",") { it.name })
}

@Composable
fun SudokuApp(vm: SudokuViewModel) {
    var route by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val nav = Navigator({ route }, { route = it })

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = nav.current,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.HOME -> HomeScreen(vm, nav)
                Screen.GAME -> GameScreen(vm, nav)
                Screen.DAILY -> DailyScreen(vm, nav)
                Screen.STATS -> StatsScreen(vm, nav)
                Screen.SETTINGS -> SettingsScreen(vm, nav)
                Screen.HELP -> HelpScreen(nav)
            }
        }
    }
}
