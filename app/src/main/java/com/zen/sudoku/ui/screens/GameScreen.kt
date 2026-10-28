package com.zen.sudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.zen.sudoku.SudokuViewModel
import com.zen.sudoku.data.Game
import com.zen.sudoku.data.GameStatus
import com.zen.sudoku.data.MAX_HINTS
import com.zen.sudoku.data.MAX_MISTAKES
import com.zen.sudoku.data.formatTime
import com.zen.sudoku.ui.Navigator
import com.zen.sudoku.ui.Screen
import com.zen.sudoku.ui.components.SudokuBoard
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun GameScreen(vm: SudokuViewModel, nav: Navigator) {
    val game = vm.game
    val lifeState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = game?.status == GameStatus.PLAYING && !vm.paused &&
        lifeState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            vm.tick()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.save()
            vm.closeFinished()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val boardSize = min(min(maxWidth - 24.dp, maxHeight - 300.dp), 560.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            GameTopBar(vm, nav, game)
            if (game == null) {
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (vm.generating) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("正在生成谜题…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Spacer(Modifier.weight(0.6f))
                GameBody(vm, game, boardSize)
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (game != null) ResultDialogs(vm, nav, game)
}

@Composable
private fun GameTopBar(vm: SudokuViewModel, nav: Navigator, game: Game?) {
    var menu by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    val title = when {
        game == null -> ""
        game.daily != null -> "每日挑战"
        else -> game.difficulty.label
    }
    com.zen.sudoku.ui.components.TopBar(title, onBack = { nav.pop() }) {
        if (game?.status == GameStatus.PLAYING) {
            IconButton(onClick = { vm.togglePause() }) {
                Icon(
                    if (vm.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (vm.paused) "继续" else "暂停",
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (game?.status == GameStatus.PLAYING) {
                    DropdownMenuItem(
                        text = { Text("重新开始") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                        onClick = { menu = false; confirmRestart = true },
                    )
                }
                DropdownMenuItem(
                    text = { Text("设置") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                    onClick = { menu = false; nav.push(Screen.SETTINGS) },
                )
                DropdownMenuItem(
                    text = { Text("玩法说明") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, null) },
                    onClick = { menu = false; nav.push(Screen.HELP) },
                )
            }
        }
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("重新开始？") },
            text = { Text("将清除所有已填数字、笔记与计时，谜题保持不变。") },
            confirmButton = {
                TextButton(onClick = { confirmRestart = false; vm.restart() }) { Text("重新开始") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun GameBody(vm: SudokuViewModel, game: Game, boardSize: Dp) {
    val settings = vm.settings
    val haptic = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var seenSignal by remember { mutableIntStateOf(vm.mistakeSignal) }
    LaunchedEffect(vm.mistakeSignal) {
        if (vm.mistakeSignal == seenSignal) return@LaunchedEffect
        seenSignal = vm.mistakeSignal
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        for (x in listOf(10f, -8f, 6f, -4f, 0f)) shake.animateTo(x, tween(45))
    }

    val playing = game.status == GameStatus.PLAYING
    val hidden = vm.paused && playing

    // Info row
    Row(
        Modifier.width(boardSize).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val mistakesText = when {
            !settings.autoCheck -> "关闭"
            settings.mistakeLimit -> "${game.mistakes}/$MAX_MISTAKES"
            else -> "${game.mistakes}"
        }
        InfoItem("难度", game.difficulty.label, Modifier.weight(1f))
        InfoItem(
            "错误", mistakesText, Modifier.weight(1f),
            highlight = settings.autoCheck && game.mistakes > 0,
        )
        InfoItem(
            "时间",
            if (settings.showTimer) formatTime(game.elapsed) else "--:--",
            Modifier.weight(1f),
            alignEnd = true,
        )
    }

    Spacer(Modifier.height(4.dp))
    Box(
        Modifier.size(boardSize).graphicsLayer { translationX = shake.value.dp.toPx() },
        contentAlignment = Alignment.Center,
    ) {
        SudokuBoard(
            game = game,
            selected = vm.selected,
            settings = settings,
            hidden = hidden,
            celebrate = game.status == GameStatus.WON,
            onSelect = vm::select,
            modifier = Modifier.fillMaxSize(),
        )
        if (hidden) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = { vm.togglePause() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PlayArrow, "继续",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("已暂停", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    // Tools
    Row(Modifier.width(boardSize), horizontalArrangement = Arrangement.SpaceEvenly) {
        ToolButton(Icons.AutoMirrored.Rounded.Undo, "撤销", enabled = playing && vm.canUndo, onClick = vm::undo)
        ToolButton(Icons.AutoMirrored.Rounded.Backspace, "擦除", enabled = playing, onClick = vm::erase)
        ToolButton(
            Icons.Rounded.EditNote,
            if (vm.notesMode) "笔记 开" else "笔记",
            enabled = playing,
            active = vm.notesMode,
            onClick = vm::toggleNotes,
        )
        ToolButton(
            Icons.Rounded.Lightbulb, "提示",
            enabled = playing && game.hintsUsed < MAX_HINTS,
            badge = "${MAX_HINTS - game.hintsUsed}",
            onClick = vm::hint,
        )
    }

    Spacer(Modifier.height(16.dp))

    // Number pad
    val counts = remember(game.values, settings.autoCheck) {
        IntArray(10).also { c ->
            for (i in 0 until 81) {
                val v = game.values[i]
                if (v != 0 && (!settings.autoCheck || v == game.solution[i])) c[v]++
            }
        }
    }
    Row(Modifier.width(boardSize)) {
        for (d in 1..9) {
            val remaining = (9 - counts[d]).coerceAtLeast(0)
            val gone = remaining == 0 && settings.hideCompleted
            Column(
                Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = playing && !gone) { vm.input(d) }
                    .alpha(if (gone) 0f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "$d",
                    fontSize = if (vm.notesMode) 24.sp else 32.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (vm.notesMode) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (remaining > 0) "$remaining" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier,
    highlight: Boolean = false,
    alignEnd: Boolean = false,
) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgedBox(badge = {
            if (badge != null) Badge(containerColor = cs.primary, contentColor = cs.onPrimary) { Text(badge) }
        }) {
            Box(
                Modifier.size(48.dp).background(if (active) cs.primary else cs.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, label, tint = if (active) cs.onPrimary else cs.onSurface)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun ResultDialogs(vm: SudokuViewModel, nav: Navigator, game: Game) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(game.status) {
        show = false
        when (game.status) {
            GameStatus.WON -> { delay(1400); show = true }
            GameStatus.LOST -> { delay(450); show = true }
            GameStatus.PLAYING -> Unit
        }
    }
    if (!show) return

    if (game.status == GameStatus.WON) {
        AlertDialog(
            onDismissRequest = {},
            icon = {
                Icon(
                    Icons.Rounded.EmojiEvents, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            },
            title = { Text(if (game.daily != null) "今日挑战完成！" else "恭喜完成！") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (game.daily != null) ResultRow("日期", dateLabel(LocalDate.parse(game.daily)))
                    ResultRow("难度", game.difficulty.label)
                    ResultRow("用时", formatTime(game.elapsed), badge = if (vm.newRecord) "新纪录" else null)
                    ResultRow("错误", "${game.mistakes} 次")
                    ResultRow("提示", "${game.hintsUsed} 次")
                    ResultRow("连胜", "${vm.statsFor(game.difficulty).streak} 局")
                }
            },
            confirmButton = {
                if (game.daily == null) {
                    TextButton(onClick = { vm.newGame(game.difficulty) }) { Text("再来一局") }
                } else {
                    TextButton(onClick = { nav.pop() }) { Text("完成") }
                }
            },
            dismissButton = {
                if (game.daily == null) TextButton(onClick = { nav.pop() }) { Text("返回") }
            },
        )
    } else if (game.status == GameStatus.LOST) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("挑战失败") },
            text = { Text("你已经犯了 $MAX_MISTAKES 次错误。要再试一次吗？") },
            confirmButton = { TextButton(onClick = { vm.secondChance() }) { Text("再给一次机会") } },
            dismissButton = {
                TextButton(onClick = { vm.giveUp(); nav.pop() }) { Text("放弃") }
            },
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String, badge: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
