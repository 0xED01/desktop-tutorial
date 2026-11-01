package com.yuandian.nightprotocol.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuandian.nightprotocol.data.ProtocolState
import com.yuandian.nightprotocol.recovery.RecoveryResult
import com.yuandian.nightprotocol.ui.NightColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun KioskScreen(
    stateFlow: Flow<ProtocolState>,
    onDial: () -> Unit,
    onEmergency: () -> Unit,
    onAlarms: () -> Unit,
    onRecover: suspend (String, String) -> RecoveryResult,
) {
    val state by stateFlow.collectAsState(initial = ProtocolState())
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
    var showRecovery by remember { mutableStateOf(false) }

    val until = if (state.lockedUntil > 0)
        Instant.ofEpochMilli(state.lockedUntil).atZone(ZoneId.systemDefault()).format(HM) else "--:--"

    Column(
        Modifier.fillMaxSize().background(NightColors.Bg).systemBarsPadding().padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(now.format(HM), fontSize = 72.sp, fontWeight = FontWeight.Light, color = NightColors.Text)
        Spacer(Modifier.height(8.dp))
        Text(if (state.lastLockWasTest) "测试封锁中" else "睡眠中", fontSize = 20.sp, color = NightColors.TextDim)
        Text("预计 $until 恢复", fontSize = 16.sp, color = NightColors.TextDim)
        Spacer(Modifier.height(48.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KioskButton("电话", onDial, Modifier.weight(1f))
            KioskButton("闹钟", onAlarms, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        KioskButton("紧急呼叫", onEmergency, Modifier.fillMaxWidth())

        Spacer(Modifier.height(40.dp))
        Text(
            "电源键可正常熄屏与唤醒；长按电源键可使用系统紧急求助与重启。\n" +
                "锁屏界面的“紧急呼叫”保持可用。\n" +
                "设备故障时，可使用预先离线保管的恢复码，或在电脑上执行 ADB 恢复命令。",
            fontSize = 13.sp, color = NightColors.TextDim, textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { showRecovery = true }) {
            Text("故障恢复", color = NightColors.TextDim, fontSize = 13.sp)
        }
    }

    if (showRecovery) RecoveryDialog(onDismiss = { showRecovery = false }, onRecover = onRecover)
}

@Composable
private fun KioskButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(64.dp)) {
        Text(label, color = NightColors.Text, fontSize = 18.sp)
    }
}

@Composable
fun RecoveryDialog(onDismiss: () -> Unit, onRecover: suspend (String, String) -> RecoveryResult) {
    var code by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NightColors.Surface,
        title = { Text("紧急恢复", color = NightColors.Text) },
        text = {
            Column {
                Text("仅用于设备故障或必要情况。会结束本次锁定（下一晚照常生效），并记录原因。",
                    color = NightColors.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(code, { code = it }, label = { Text("恢复码") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(reason, { reason = it }, label = { Text("原因（必填）") })
                message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = NightColors.Warn, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    message = when (val r = onRecover(code, reason)) {
                        RecoveryResult.Success -> { onDismiss(); null }
                        RecoveryResult.ReasonRequired -> "请填写原因（至少 4 个字）"
                        RecoveryResult.NoCodeConfigured -> "未设置恢复码，请使用 ADB 恢复流程"
                        RecoveryResult.NotLocked -> "当前未处于锁定状态"
                        is RecoveryResult.LockedOut -> "尝试次数过多，请在 ${fmt(r.until)} 之后再试，或使用 ADB 恢复"
                        is RecoveryResult.WrongCode -> if (r.lockoutUntil > 0) "恢复码错误，已暂停至 ${fmt(r.lockoutUntil)}"
                            else "恢复码错误，还可尝试 ${r.remainingFree} 次"
                    }
                }
            }) { Text("解除本次锁定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun fmt(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(HM)
