package com.yuandian.nightprotocol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuandian.nightprotocol.data.CheckKey
import com.yuandian.nightprotocol.data.CheckRecord
import com.yuandian.nightprotocol.data.CheckStatus
import com.yuandian.nightprotocol.data.ExecutionLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val MDHMS = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
private fun Instant.hm() = atZone(ZoneId.systemDefault()).format(HM)
private fun Long.ts() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(MDHMS)

class MainActions(
    val setSchedule: (Int, Int) -> Unit,
    val setStrict: (Boolean) -> Unit,
    val startTest: (Int) -> Unit,
    val generateCode: () -> Unit,
    val markManual: (CheckKey, Boolean) -> Unit,
    val resetChecks: () -> Unit,
    val toggleExtra: (String) -> Unit,
    val removeOwner: () -> Unit,
    val openExactAlarm: () -> Unit,
    val openNotifications: () -> Unit,
    val openFullScreen: () -> Unit,
    val openBattery: () -> Unit,
)

@Composable
fun MainScreen(u: MainUiState, a: MainActions) {
    var editing by remember { mutableStateOf<Boolean?>(null) } // true=开始 false=结束
    var showApps by remember { mutableStateOf(false) }
    var showRecoveryHelp by remember { mutableStateOf(false) }
    var showConfirmRemove by remember { mutableStateOf(false) }
    val p = u.protocol

    LazyColumn(
        Modifier.fillMaxSize().background(NightColors.Bg).systemBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("晚安协议", fontSize = 28.sp, fontWeight = FontWeight.Light, color = NightColors.Text)
            Text("到点即睡。电话、紧急呼叫与闹钟始终可用。", fontSize = 13.sp, color = NightColors.TextDim)
        }

        // —— 计划 ——
        item {
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TimeBlock("今晚开始", p.schedule.startText, u.nextStart?.hm()) { editing = true }
                    TimeBlock("明早结束", p.schedule.endText, u.nextEnd?.hm()) { editing = false }
                }
                if (u.editingLocked) Dim("睡眠已开始：计划锁定，不能修改。")
                else Dim("点击时间可修改；睡眠开始后不可修改。")
            }
        }

        // —— 严格模式 ——
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Title("严格模式")
                        Text(
                            when {
                                p.enforcedLock -> "锁定中（至 ${Instant.ofEpochMilli(p.lockedUntil).hm()}）"
                                p.strictEnabled -> "已激活，每晚自动执行"
                                u.ready -> "可启用"
                                else -> "未激活：${blockerCount(u.checks)} 项检查未通过"
                            },
                            color = if (p.strictEnabled) NightColors.Ok else NightColors.Warn, fontSize = 14.sp,
                        )
                    }
                    Switch(checked = p.strictEnabled, onCheckedChange = a.setStrict)
                }
            }
        }

        // —— Device Owner 与系统能力 ——
        item {
            Card {
                Title("设备管理与系统能力")
                val o = u.live.owner
                KV("Device Owner", if (o.isDeviceOwner) "是" else "否", o.isDeviceOwner)
                KV("设备管理已激活", yn(o.isAdminActive), o.isAdminActive)
                KV("Lock Task 已许可（本包）", yn(o.lockTaskPermitted), null)
                KV("精确闹钟", yn(u.live.exactAlarm), u.live.exactAlarm, a.openExactAlarm)
                KV("通知", yn(u.live.notifications), u.live.notifications, a.openNotifications)
                KV("全屏通知（锁定界面兜底）", yn(u.live.fullScreenIntent), u.live.fullScreenIntent, a.openFullScreen)
                KV("忽略电池优化", yn(u.live.batteryExempt), u.live.batteryExempt, a.openBattery)
                KV("当前 Lock Task 状态", u.live.lockTaskMode, null)
                KV("Lock Task 功能位", u.live.lockTaskFeatures, null)
                KV("当前默认 HOME", u.live.homePackage ?: "-", null)
                KV("默认拨号器", u.live.essentials?.defaultDialer ?: "-", null)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showApps = true }) { Text("查看受保护应用 / 额外保留应用") }
            }
        }

        // —— 测试封锁 ——
        item {
            Card {
                Title("测试封锁")
                Dim("与真实睡眠锁定走完全相同的流程，到时自动恢复。测试中请完成下方“手动”项目。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10).forEach { m ->
                        OutlinedButton(onClick = { a.startTest(m) }, enabled = !p.enforcedLock) { Text("$m 分钟") }
                    }
                }
            }
        }

        // —— 检查清单 ——
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title("正式启用前检查", Modifier.weight(1f))
                    TextButton(onClick = a.resetChecks) { Text("重置", fontSize = 12.sp) }
                }
                CheckKey.entries.forEach { k -> CheckRow(k, u.checks[k] ?: CheckRecord.UNTESTED, a.markManual) }
            }
        }

        // —— 故障恢复 ——
        item {
            Card {
                Title("故障恢复")
                Dim(if (p.recoveryHash != null) "恢复码：已设置（只保存哈希）" else "恢复码：未设置")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = a.generateCode, enabled = !u.editingLocked) {
                        Text(if (p.recoveryHash == null) "生成恢复码" else "更换恢复码")
                    }
                    OutlinedButton(onClick = { showRecoveryHelp = true }) { Text("ADB 恢复说明") }
                }
                TextButton(onClick = { showConfirmRemove = true }, enabled = !p.strictEnabled && !u.editingLocked) {
                    Text("移除 Device Owner（卸载前）", color = NightColors.TextDim, fontSize = 12.sp)
                }
            }
        }

        // —— 执行记录 ——
        item { Title("执行记录") }
        items(u.logs, key = { it.id }) { LogRow(it) }
        item { Spacer(Modifier.height(32.dp)) }
    }

    editing?.let { isStart ->
        TimeDialog(
            initial = if (isStart) p.schedule.startMinute else p.schedule.endMinute,
            onDismiss = { editing = null },
            onConfirm = { m ->
                editing = null
                if (isStart) a.setSchedule(m, p.schedule.endMinute) else a.setSchedule(p.schedule.startMinute, m)
            },
        )
    }
    if (showApps) AppsDialog(u, a.toggleExtra) { showApps = false }
    if (showRecoveryHelp) InfoDialog("ADB 恢复（电脑执行）", ADB_HELP) { showRecoveryHelp = false }
    if (showConfirmRemove) AlertDialog(
        onDismissRequest = { showConfirmRemove = false }, containerColor = NightColors.Surface,
        title = { Text("移除 Device Owner？") },
        text = { Text("将先解除所有限制，再放弃设备管理身份。之后需重新通过 ADB 设置才能使用严格模式。") },
        confirmButton = { TextButton(onClick = { showConfirmRemove = false; a.removeOwner() }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { showConfirmRemove = false }) { Text("取消") } },
    )
}

const val ADB_HELP = """1) 结束当前锁定（保留严格模式，下一晚照常）：
adb shell am broadcast -a com.yuandian.nightprotocol.action.ADB_RECOVER -n com.yuandian.nightprotocol/.recovery.AdbRecoveryReceiver --es reason "故障说明"

2) 结束锁定并关闭严格模式：
在上一条命令末尾追加 --ez disable_strict true

3) 仅 Debug(testOnly) 构建：彻底撤销测试管理身份
adb shell dpm remove-active-admin com.yuandian.nightprotocol/.admin.SleepAdminReceiver

4) 若界面仍卡在 Lock Task：
adb shell am task lock stop

详见项目 docs/RECOVERY.md。"""

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(NightColors.Surface, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable private fun Title(t: String, modifier: Modifier = Modifier) =
    Text(t, modifier = modifier, color = NightColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)

@Composable private fun Dim(t: String) = Text(t, color = NightColors.TextDim, fontSize = 12.sp)

private fun yn(b: Boolean) = if (b) "是" else "否"
private fun blockerCount(c: Map<CheckKey, CheckRecord>) = CheckKey.entries.count { c[it]?.status != CheckStatus.PASSED }

@Composable
private fun TimeBlock(label: String, time: String, actual: String?, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Text(label, color = NightColors.TextDim, fontSize = 12.sp)
        Text(time, color = NightColors.Text, fontSize = 36.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun KV(k: String, v: String, ok: Boolean?, onFix: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().let { if (onFix != null && ok == false) it.clickable(onClick = onFix) else it },
        verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = NightColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(v + if (onFix != null && ok == false) "  去设置 ›" else "", fontSize = 13.sp,
            color = when (ok) { true -> NightColors.Ok; false -> NightColors.Warn; null -> NightColors.Text })
    }
}

private fun statusColor(s: CheckStatus): Color = when (s) {
    CheckStatus.PASSED -> NightColors.Ok; CheckStatus.FAILED -> NightColors.Bad; CheckStatus.UNTESTED -> NightColors.TextDim
}

@Composable
private fun CheckRow(k: CheckKey, r: CheckRecord, mark: (CheckKey, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(k.label, color = NightColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(r.status.label, color = statusColor(r.status), fontSize = 12.sp)
        }
        val sub = listOfNotNull(k.hint.takeIf { it.isNotEmpty() && r.status != CheckStatus.PASSED },
            r.note.takeIf { it.isNotEmpty() }, if (r.atMillis > 0 && !k.auto) r.atMillis.ts() else null)
        if (sub.isNotEmpty()) Dim(sub.joinToString(" · "))
        if (!k.auto) Row {
            TextButton(onClick = { mark(k, true) }) { Text("通过", fontSize = 12.sp) }
            TextButton(onClick = { mark(k, false) }) { Text("失败", fontSize = 12.sp, color = NightColors.Bad) }
        }
    }
}

@Composable
private fun LogRow(l: ExecutionLog) {
    Column(Modifier.fillMaxWidth().background(NightColors.Surface, RoundedCornerShape(8.dp)).padding(10.dp)) {
        Row {
            Text(l.event, color = if (l.success) NightColors.Accent else NightColors.Bad, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text(l.atMillis.ts(), color = NightColors.TextDim, fontSize = 11.sp)
        }
        Text(l.detail, color = NightColors.TextDim, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val st = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NightColors.Surface,
        text = { TimePicker(state = st) },
        confirmButton = { TextButton(onClick = { onConfirm(st.hour * 60 + st.minute) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AppsDialog(u: MainUiState, toggle: (String) -> Unit, onDismiss: () -> Unit) {
    val reasons = u.live.essentials?.reasons.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NightColors.Surface,
        title = { Text("应用保护") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                item { Dim("自动受保护（不会被暂停）：") }
                items(reasons.entries.sortedBy { it.key }.toList(), key = { "r" + it.key }) { (pkg, why) ->
                    Text("$pkg\n  ${why.joinToString("、")}", color = NightColors.Text, fontSize = 12.sp)
                }
                item { Spacer(Modifier.height(12.dp)); Dim("额外保留（睡眠期间仍可在锁定界面外使用，请谨慎）：") }
                items(u.live.launcherApps, key = { "l" + it.first }) { (pkg, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pkg in u.protocol.extraAllowed, onCheckedChange = { toggle(pkg) }, enabled = !u.editingLocked)
                        Column { Text(label, fontSize = 13.sp); Dim(pkg) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NightColors.Surface,
        title = { Text(title) },
        text = { SelectionContainer { LazyColumn(Modifier.heightIn(max = 480.dp)) { item { Text(body, fontSize = 12.sp, fontFamily = FontFamily.Monospace) } } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {}, containerColor = NightColors.Surface,
        title = { Text("紧急恢复码（只显示这一次）") },
        text = {
            Column {
                SelectionContainer { Text(code, fontSize = 22.sp, fontFamily = FontFamily.Monospace, color = NightColors.Text) }
                Spacer(Modifier.height(12.dp))
                Dim("请抄写在纸上并交由他人或放在卧室以外保管。不要存在本机。它只用于故障或必要情况，每次使用都会被记录。")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Spacer(Modifier.width(4.dp)); Text("我已离线保管", fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone, enabled = confirmed) { Text("完成") } },
    )
}
