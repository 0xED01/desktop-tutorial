package com.zen.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zen.sudoku.SudokuViewModel
import com.zen.sudoku.data.ThemeMode
import com.zen.sudoku.ui.Navigator
import com.zen.sudoku.ui.components.SectionCard
import com.zen.sudoku.ui.components.SwitchRow
import com.zen.sudoku.ui.components.TopBar
import com.zen.sudoku.ui.theme.Accents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SudokuViewModel, nav: Navigator) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminanceIsDark()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().systemBarsPadding()) {
            TopBar("设置", onBack = { nav.pop() })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SectionCard(title = "外观") {
                    Text(
                        "主题",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 10.dp),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = s.theme == mode,
                                onClick = { vm.updateSettings { it.copy(theme = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(mode.label) }
                        }
                    }
                    Text(
                        "主题色",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 10.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Accents.forEachIndexed { i, a ->
                            val color = if (dark) a.dark else a.light
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .background(color, CircleShape)
                                        .border(
                                            2.dp,
                                            if (s.accent == i) cs.onSurface else Color.Transparent,
                                            CircleShape,
                                        )
                                        .clickable { vm.updateSettings { it.copy(accent = i) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (s.accent == i) Icon(Icons.Rounded.Check, null, tint = cs.onPrimary)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(a.name, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }

                SectionCard(title = "游戏") {
                    SwitchRow("自动检查错误", "填错时立即标红并计入错误次数", s.autoCheck) { v ->
                        vm.updateSettings { it.copy(autoCheck = v) }
                    }
                    SwitchRow("错误上限", "累计 3 次错误后挑战失败", s.mistakeLimit, enabled = s.autoCheck) { v ->
                        vm.updateSettings { it.copy(mistakeLimit = v) }
                    }
                    SwitchRow("显示计时器", null, s.showTimer) { v ->
                        vm.updateSettings { it.copy(showTimer = v) }
                    }
                    SwitchRow("自动移除笔记", "填入数字后清除同行、同列、同宫的该数字笔记", s.autoRemoveNotes) { v ->
                        vm.updateSettings { it.copy(autoRemoveNotes = v) }
                    }
                    SwitchRow("隐藏已完成数字", "某个数字填满 9 个后从键盘隐藏", s.hideCompleted) { v ->
                        vm.updateSettings { it.copy(hideCompleted = v) }
                    }
                }

                SectionCard(title = "辅助") {
                    SwitchRow("高亮行、列与宫", null, s.highlightRegion) { v ->
                        vm.updateSettings { it.copy(highlightRegion = v) }
                    }
                    SwitchRow("高亮相同数字", "包括笔记中的相同数字", s.highlightSame) { v ->
                        vm.updateSettings { it.copy(highlightSame = v) }
                    }
                    SwitchRow("震动反馈", "填错时震动提醒", s.haptics) { v ->
                        vm.updateSettings { it.copy(haptics = v) }
                    }
                }

                SectionCard(title = "关于") {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text("数独 1.0.0", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "所有谜题均由算法实时生成，并保证有且仅有唯一解。无广告，无需联网。",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun Color.luminanceIsDark() = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
