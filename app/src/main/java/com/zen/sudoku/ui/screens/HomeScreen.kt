package com.zen.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zen.sudoku.SudokuViewModel
import com.zen.sudoku.data.formatTime
import com.zen.sudoku.engine.Daily
import com.zen.sudoku.engine.Difficulty
import com.zen.sudoku.ui.Navigator
import com.zen.sudoku.ui.Screen
import com.zen.sudoku.ui.components.CardShape
import java.time.LocalDate

private val WEEKDAYS = arrayOf("一", "二", "三", "四", "五", "六", "日")

fun dateLabel(d: LocalDate) = "${d.monthValue}月${d.dayOfMonth}日 星期${WEEKDAYS[d.dayOfWeek.value - 1]}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: SudokuViewModel, nav: Navigator) {
    var showPicker by remember { mutableStateOf(false) }
    var pendingDifficulty by remember { mutableStateOf<Difficulty?>(null) }
    val today = LocalDate.now()
    val todayDone = today.toString() in vm.dailyDone
    val game = vm.game

    fun begin(d: Difficulty) {
        vm.newGame(d)
        nav.push(Screen.GAME)
    }

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        val minHeight = maxHeight
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LogoGrid()
                Spacer(Modifier.height(20.dp))
                Text("数独", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "静心 · 专注 · 每日一局",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Daily challenge
            Surface(
                onClick = { nav.push(Screen.DAILY) },
                shape = CardShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "每日挑战",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${dateLabel(today)} · ${Daily.difficulty(today).label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.LocalFireDepartment, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "连续 ${Daily.streak(vm.dailyDone)} 天",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Box(
                        Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (todayDone) Icons.Rounded.CheckCircle else Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (vm.hasActiveGame && game != null) {
                Button(
                    onClick = { nav.push(Screen.GAME) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("继续游戏", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${if (game.daily != null) "每日挑战" else game.difficulty.label} · ${formatTime(game.elapsed)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("新游戏", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("新游戏", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(Icons.Rounded.BarChart, "统计", Modifier.weight(1f)) { nav.push(Screen.STATS) }
                QuickTile(Icons.AutoMirrored.Rounded.HelpOutline, "玩法", Modifier.weight(1f)) { nav.push(Screen.HELP) }
            }
            Spacer(Modifier.height(48.dp))
        }
        IconButton(
            onClick = { nav.push(Screen.SETTINGS) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "设置")
        }
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Text(
                    "选择难度",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
                Difficulty.entries.forEach { d ->
                    val best = vm.statsFor(d).bestTime
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showPicker = false
                                if (vm.hasActiveGame) pendingDifficulty = d else begin(d)
                            }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DifficultyDots(d.ordinal + 1)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                d.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (best > 0) {
                            Text(
                                "最佳 ${formatTime(best)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    pendingDifficulty?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDifficulty = null },
            title = { Text("开始新游戏？") },
            text = { Text("当前进行中的游戏将被放弃，并计为一次中断。") },
            confirmButton = {
                TextButton(onClick = { pendingDifficulty = null; begin(d) }) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDifficulty = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DifficultyDots(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) {
            Box(
                Modifier
                    .size(width = 5.dp, height = 18.dp)
                    .background(
                        if (it < level) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CardShape, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Small decorative 3×3 block used as the home screen emblem. */
@Composable
private fun LogoGrid() {
    val digits = listOf("5", "", "3", "", "7", "", "1", "", "9")
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .size(96.dp)
            .background(cs.surface, RoundedCornerShape(22.dp))
            .border(1.5.dp, cs.onSurface.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .padding(8.dp),
    ) {
        for (r in 0 until 3) {
            Row(Modifier.weight(1f)) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(2.dp)
                            .background(
                                if (i == 4) cs.primary else cs.surfaceVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            digits[i],
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (i == 4) cs.onPrimary else cs.onSurface,
                        )
                    }
                }
            }
        }
    }
}
