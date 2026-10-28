package com.zen.sudoku.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.zen.sudoku.SudokuViewModel
import com.zen.sudoku.data.formatTime
import com.zen.sudoku.engine.Difficulty
import com.zen.sudoku.ui.Navigator
import com.zen.sudoku.ui.components.CardShape
import com.zen.sudoku.ui.components.StatTile
import com.zen.sudoku.ui.components.TopBar

@Composable
fun StatsScreen(vm: SudokuViewModel, nav: Navigator) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    val d = Difficulty.entries[tab]
    val s = vm.statsFor(d)
    val all = Difficulty.entries.map { vm.statsFor(it) }
    val cs = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().systemBarsPadding()) {
            TopBar("统计", onBack = { nav.pop() }) {
                IconButton(onClick = { confirmReset = true }) { Icon(Icons.Rounded.DeleteOutline, "重置统计") }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("总对局", "${all.sumOf { it.played }}", Modifier.weight(1f))
                    StatTile("总胜局", "${all.sumOf { it.won }}", Modifier.weight(1f))
                    StatTile("每日挑战", "${vm.dailyDone.size}", Modifier.weight(1f))
                }

                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Difficulty.entries.forEachIndexed { i, diff ->
                        FilterChip(
                            selected = tab == i,
                            onClick = { tab = i },
                            label = { Text(diff.label) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cs.primary,
                                selectedLabelColor = cs.onPrimary,
                            ),
                        )
                    }
                }

                Surface(shape = CardShape, color = cs.surface) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("胜率", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text("${s.winRate}%", style = MaterialTheme.typography.headlineMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { s.winRate / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "胜 ${s.won} / 共 ${s.played} 局",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                val tiles = listOf(
                    "最佳用时" to if (s.bestTime > 0) formatTime(s.bestTime) else "--",
                    "平均用时" to if (s.won > 0) formatTime(s.averageTime) else "--",
                    "当前连胜" to "${s.streak}",
                    "最佳连胜" to "${s.bestStreak}",
                    "完美通关" to "${s.perfect}",
                    "已玩局数" to "${s.played}",
                )
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                    }
                }
                Text(
                    "完美通关：不出错且不使用提示完成的对局。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Surface(shape = CardShape, color = cs.surface) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            "各难度最佳",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                        Difficulty.entries.forEach { diff ->
                            val st = vm.statsFor(diff)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Text(diff.label, modifier = Modifier.weight(1f))
                                Text(
                                    if (st.bestTime > 0) formatTime(st.bestTime) else "--",
                                    color = if (st.bestTime > 0) cs.primary else cs.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("重置统计？") },
            text = { Text("所有难度的统计数据都将被清除，此操作无法撤销。每日挑战记录会保留。") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resetStats() }) {
                    Text("重置", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }
}
