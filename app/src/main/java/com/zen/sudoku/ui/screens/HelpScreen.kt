package com.zen.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zen.sudoku.engine.Difficulty
import com.zen.sudoku.ui.Navigator
import com.zen.sudoku.ui.components.CardShape
import com.zen.sudoku.ui.components.TopBar

private val SECTIONS = listOf(
    "基本规则" to "在 9×9 的棋盘中填入数字 1–9，使每一行、每一列以及每个 3×3 宫内的数字都不重复。每道谜题有且只有一个解。",
    "如何操作" to "点击空白格选中它，再点击下方数字键填入。选中格所在的行、列、宫会被淡淡高亮，相同数字也会一并标出，方便观察。",
    "笔记模式" to "打开「笔记」后，点击数字会在格子里记下小号的候选数，再点一次即可取消。填入正式数字时，同行、同列、同宫的相同笔记会被自动清除（可在设置中关闭）。",
    "撤销与擦除" to "「撤销」可以逐步回退之前的操作；「擦除」会清空选中格的数字和笔记。题目自带的数字无法修改。",
    "提示" to "每局有 3 次提示机会。若选中了空白或填错的格子，提示会直接填出正确答案；否则会为你挑选当前最容易推出的格子。",
    "错误与失败" to "开启「自动检查错误」后，填错的数字会标红并计一次错误，累计 3 次即挑战失败，但你可以选择再给自己一次机会。关闭自动检查后，只会标出互相冲突的数字。",
    "每日挑战" to "每天都有一道新谜题，难度随星期变化：周一简单，周二、三中等，周四、五困难，周末专家。完成后会在日历上点亮，连续完成可累积打卡天数。",
    "解题技巧" to "· 唯一候选：某格只剩一个可能的数字。\n· 隐性唯一：某数字在一行、一列或一宫中只能放在一个位置。\n· 区块排除：某数字在一宫内只出现在同一行（列），则该行（列）的其他宫可排除它。\n· 数对：同一单元中两格的候选恰好是相同的两个数，其他格可排除它们。",
)

@Composable
fun HelpScreen(nav: Navigator) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().systemBarsPadding()) {
            TopBar("玩法说明", onBack = { nav.pop() })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SECTIONS.forEach { (title, body) -> HelpCard(title, body) }
                HelpCard(
                    "难度说明",
                    Difficulty.entries.joinToString("\n") { "· ${it.label}：约 ${it.targetClues} 个已知数，${it.description}" } +
                        "\n简单和中等难度只需基础技巧即可解出。",
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HelpCard(title: String, body: String) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
            )
        }
    }
}
