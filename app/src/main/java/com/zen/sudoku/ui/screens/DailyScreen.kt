package com.zen.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zen.sudoku.SudokuViewModel
import com.zen.sudoku.engine.Daily
import com.zen.sudoku.ui.Navigator
import com.zen.sudoku.ui.Screen
import com.zen.sudoku.ui.components.CardShape
import com.zen.sudoku.ui.components.StatTile
import com.zen.sudoku.ui.components.TopBar
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DailyScreen(vm: SudokuViewModel, nav: Navigator) {
    val today = LocalDate.now()
    val thisMonth = YearMonth.from(today)
    var month by remember { mutableStateOf(thisMonth) }
    var picked by remember { mutableStateOf(today) }
    var confirm by remember { mutableStateOf(false) }
    val done = vm.dailyDone
    val cs = MaterialTheme.colorScheme

    fun play() {
        vm.startDaily(picked)
        nav.push(Screen.GAME)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().systemBarsPadding()) {
            TopBar("每日挑战", onBack = { nav.pop() })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("累计完成", "${done.size} 天", Modifier.weight(1f))
                    StatTile("连续打卡", "${Daily.streak(done)} 天", Modifier.weight(1f))
                    StatTile("本月", "${done.count { it.startsWith(month.toString()) }} 天", Modifier.weight(1f))
                }

                Surface(shape = CardShape, color = cs.surface) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { month = month.minusMonths(1) }) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上个月")
                            }
                            Text(
                                "${month.year}年${month.monthValue}月",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { month = month.plusMonths(1) },
                                enabled = month < thisMonth,
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下个月")
                            }
                        }
                        Row(Modifier.padding(vertical = 6.dp)) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                                Text(
                                    it,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                        val offset = month.atDay(1).dayOfWeek.value - 1
                        val days = month.lengthOfMonth()
                        val rows = (offset + days + 6) / 7
                        for (r in 0 until rows) {
                            Row {
                                for (c in 0 until 7) {
                                    val day = r * 7 + c - offset + 1
                                    if (day !in 1..days) {
                                        Spacer(Modifier.weight(1f))
                                        continue
                                    }
                                    val date = month.atDay(day)
                                    val isDone = date.toString() in done
                                    val future = date.isAfter(today)
                                    val isPicked = date == picked
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(3.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isDone -> cs.primary
                                                    isPicked -> cs.primaryContainer
                                                    else -> Color.Transparent
                                                },
                                            )
                                            .border(
                                                if (date == today) 1.5.dp else 0.dp,
                                                if (date == today) cs.primary else Color.Transparent,
                                                CircleShape,
                                            )
                                            .border(
                                                if (isPicked && isDone) 2.dp else 0.dp,
                                                if (isPicked && isDone) cs.onSurface else Color.Transparent,
                                                CircleShape,
                                            )
                                            .clickable(enabled = !future) { picked = date },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "$day",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = when {
                                                isDone -> cs.onPrimary
                                                future -> cs.onSurfaceVariant.copy(alpha = 0.4f)
                                                else -> cs.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val pickedDone = picked.toString() in done
                val inProgress = vm.hasActiveGame && vm.game?.daily == picked.toString()
                Surface(shape = CardShape, color = cs.surface) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(dateLabel(picked), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "难度：${Daily.difficulty(picked).label}" +
                                if (pickedDone) " · 已完成" else if (inProgress) " · 进行中" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { if (vm.hasActiveGame && !inProgress) confirm = true else play() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                when {
                                    inProgress -> "继续挑战"
                                    pickedDone -> "再玩一次"
                                    else -> "开始挑战"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                Text(
                    "每天一道全新谜题，所有玩家同一天的题目相同。周一最简单，周末最具挑战。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("开始每日挑战？") },
            text = { Text("当前进行中的游戏将被放弃，并计为一次中断。") },
            confirmButton = { TextButton(onClick = { confirm = false; play() }) { Text("开始") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
    }
}
