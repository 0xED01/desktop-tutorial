package com.yuandian.nightprotocol.ui

import android.app.ActivityManager
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuandian.nightprotocol.admin.DeviceOwnerState
import com.yuandian.nightprotocol.core.Enforcer
import com.yuandian.nightprotocol.core.Graph
import com.yuandian.nightprotocol.data.CheckKey
import com.yuandian.nightprotocol.data.CheckRecord
import com.yuandian.nightprotocol.data.CheckStatus
import com.yuandian.nightprotocol.data.ExecutionLog
import com.yuandian.nightprotocol.data.ProtocolState
import com.yuandian.nightprotocol.data.Readiness
import com.yuandian.nightprotocol.data.SleepSchedule
import com.yuandian.nightprotocol.policy.EssentialSet
import com.yuandian.nightprotocol.policy.LockTaskFeaturePolicy
import com.yuandian.nightprotocol.recovery.RecoveryCode
import com.yuandian.nightprotocol.schedule.SleepWindowCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** 只在刷新时读取一次的系统实时能力。 */
data class LiveSystem(
    val owner: DeviceOwnerState = DeviceOwnerState(false, false, false, false),
    val exactAlarm: Boolean = false,
    val notifications: Boolean = false,
    val fullScreenIntent: Boolean = false,
    val batteryExempt: Boolean = false,
    val essentials: EssentialSet? = null,
    val lockTaskPackages: List<String> = emptyList(),
    val lockTaskFeatures: String = "-",
    val lockTaskMode: String = "-",
    val homePackage: String? = null,
    val launcherApps: List<Pair<String, String>> = emptyList(),
)

data class MainUiState(
    val protocol: ProtocolState = ProtocolState(),
    val live: LiveSystem = LiveSystem(),
    val checks: Map<CheckKey, CheckRecord> = emptyMap(),
    val ready: Boolean = false,
    val inWindowNow: Boolean = false,
    val nextStart: Instant? = null,
    val nextEnd: Instant? = null,
    val logs: List<ExecutionLog> = emptyList(),
) {
    /** 睡眠开始之后（或锁定中）不允许通过普通设置修改计划或关闭严格模式。 */
    val editingLocked: Boolean get() = protocol.enforcedLock || (protocol.strictEnabled && inWindowNow && protocol.overrideUntil <= System.currentTimeMillis())
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val live = MutableStateFlow(LiveSystem())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _newRecoveryCode = MutableStateFlow<String?>(null)
    val newRecoveryCode: StateFlow<String?> = _newRecoveryCode.asStateFlow()

    val ui: StateFlow<MainUiState> = combine(Graph.store.state, live, Graph.logDb.dao().recent(100)) { st, lv, logs ->
        val merged = Readiness.merge(st.checks, liveChecks(st, lv))
        val now = Instant.now(); val zone = ZoneId.systemDefault()
        val current = SleepWindowCalculator.windowContaining(now, st.schedule, zone)
        val next = current ?: SleepWindowCalculator.nextWindow(now, st.schedule, zone)
        MainUiState(st, lv, merged, Readiness.isReady(merged), current != null, next?.start, next?.end, logs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    fun consumeMessage() { _message.value = null }
    fun consumeRecoveryCode() { _newRecoveryCode.value = null }

    fun refresh() = viewModelScope.launch {
        val extra = Graph.store.snapshot().extraAllowed
        live.value = withContext(Dispatchers.Default) { readLive(extra) }
        Graph.enforcer.reconcile("main_resume", launchKiosk = true)
    }

    private fun readLive(extraAllowed: Set<String>): LiveSystem {
        val owner = DeviceOwnerState.read(ctx)
        val ess = runCatching { Graph.resolver.resolve(extraAllowed) }.getOrNull()
        val pm = ctx.packageManager
        val launcher = runCatching {
            pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }.sortedBy { it.second }
        }.getOrDefault(emptyList())
        return LiveSystem(
            owner = owner,
            exactAlarm = Graph.scheduler.canScheduleExact(),
            notifications = Graph.notifier.notificationsEnabled(),
            fullScreenIntent = Graph.notifier.canUseFullScreen(),
            batteryExempt = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
            essentials = ess,
            lockTaskPackages = if (owner.isDeviceOwner) runCatching { Graph.policy.getLockTaskPackages() }.getOrDefault(emptyList()) else emptyList(),
            lockTaskFeatures = if (owner.isDeviceOwner) runCatching { LockTaskFeaturePolicy.describe(Graph.policy.getLockTaskFeatures()) }.getOrDefault("-") else "-",
            lockTaskMode = Enforcer.lockModeName(ctx.getSystemService(ActivityManager::class.java).lockTaskModeState),
            homePackage = Graph.policy.currentHomePackage(),
            launcherApps = launcher,
        )
    }

    private fun liveChecks(st: ProtocolState, lv: LiveSystem): Map<CheckKey, CheckRecord> {
        val now = System.currentTimeMillis()
        fun r(ok: Boolean, note: String) = CheckRecord(if (ok) CheckStatus.PASSED else CheckStatus.FAILED, now, note)
        val dialer = lv.essentials?.defaultDialer
        return mapOf(
            CheckKey.DEVICE_OWNER to r(lv.owner.canEnforceStrictMode, if (lv.owner.isDeviceOwner) "Device Owner" else "未取得（见 adb 配置命令）"),
            CheckKey.EXACT_ALARM to r(lv.exactAlarm, if (lv.exactAlarm) "可用" else "不可用：开始/结束时间可能被系统推迟"),
            CheckKey.DIALER_ALLOWED to r(dialer != null && lv.essentials.lockTaskAllowed.contains(dialer) && lv.owner.isDeviceOwner,
                "默认拨号器=${dialer ?: "未知"}"),
            CheckKey.RECOVERY_CODE to r(st.recoveryHash != null, if (st.recoveryHash != null) "已设置" else "未设置"),
        )
    }

    // ———— 操作 ————

    fun setSchedule(start: Int, end: Int) = guardedEdit {
        val s = runCatching { SleepSchedule(start, end) }.getOrNull()
        if (s == null || !s.isValid) return@guardedEdit "开始与结束时间不能相同"
        Graph.store.setSchedule(s)
        Graph.logger.log("SCHEDULE", true, "计划改为 ${s.startText} → ${s.endText}")
        Graph.enforcer.reconcile("schedule_changed")
        "计划已保存：${s.startText} → ${s.endText}"
    }

    fun setStrict(enable: Boolean) = viewModelScope.launch {
        val u = ui.value
        _message.value = when {
            !enable && u.editingLocked -> "睡眠时间内不能关闭严格模式。故障请使用紧急恢复。"
            enable && !u.ready -> "仍有未通过的检查项，不能正式启用：\n" + Readiness.blockers(u.checks).joinToString("\n") { "· " + it.label }
            else -> {
                Graph.store.setStrictEnabled(enable)
                Graph.logger.log("STRICT_MODE", true, if (enable) "严格模式已启用" else "严格模式已关闭")
                Graph.enforcer.reconcile("strict_toggled")
                if (enable) "严格模式已启用" else "严格模式已关闭"
            }
        }
    }

    fun startTest(minutes: Int) = viewModelScope.launch {
        val u = ui.value
        _message.value = when {
            !u.live.owner.isDeviceOwner -> "需要先取得 Device Owner 身份"
            u.protocol.enforcedLock -> "当前已处于锁定状态"
            else -> {
                Graph.store.setTestSessionEnd(System.currentTimeMillis() + minutes * 60_000L)
                Graph.logger.log("TEST_LOCK", true, "开始测试封锁 $minutes 分钟")
                Graph.enforcer.reconcile("test_start", launchKiosk = true)
                null
            }
        }
    }

    fun generateRecoveryCode() = viewModelScope.launch {
        if (ui.value.editingLocked) { _message.value = "睡眠时间内不能更换恢复码"; return@launch }
        val code = RecoveryCode.generate()
        val hash = withContext(Dispatchers.Default) { RecoveryCode.hash(code) }
        Graph.store.setRecoveryHash(hash)
        Graph.logger.log("RECOVERY_CODE", true, "已生成新的恢复码（旧码作废）")
        _newRecoveryCode.value = code
    }

    fun markManual(key: CheckKey, passed: Boolean) = guarded {
        if (key.auto) return@guarded "自动检查项不能手动标记"
        if (ui.value.protocol.check(CheckKey.LOCKTASK_ENTER).status != CheckStatus.PASSED)
            return@guarded "请先完成一次测试封锁（真正进入 Lock Task）再进行手动验证"
        Graph.store.recordCheck(key, if (passed) CheckStatus.PASSED else CheckStatus.FAILED, "用户确认（真机操作）")
        if (!passed && ui.value.protocol.strictEnabled && !ui.value.editingLocked) {
            Graph.store.setStrictEnabled(false)
            Graph.logger.log("STRICT_MODE", false, "${key.label} 失败，已自动关闭严格模式")
        }
        null
    }

    fun resetChecks() = guarded {
        if (ui.value.protocol.strictEnabled) return@guarded "请先关闭严格模式再重置测试结果"
        Graph.store.resetChecks(); "测试结果已重置"
    }

    fun toggleExtraAllowed(pkg: String) = guardedEdit {
        val cur = ui.value.protocol.extraAllowed
        Graph.store.setExtraAllowed(if (pkg in cur) cur - pkg else cur + pkg)
        refresh(); null
    }

    fun removeDeviceOwner() = guarded {
        val u = ui.value
        if (u.protocol.strictEnabled || u.editingLocked) return@guarded "请先在非睡眠时间关闭严格模式"
        Graph.enforcer.reconcile("before_remove_owner", launchKiosk = false)
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        runCatching {
            @Suppress("DEPRECATION") dpm.clearDeviceOwnerApp(ctx.packageName)
        }.fold(
            { Graph.logger.log("REMOVE_OWNER", true, "已移除 Device Owner"); refresh(); "已移除 Device Owner，现在可以卸载" },
            { Graph.logger.log("REMOVE_OWNER", false, "$it"); "移除失败：$it" },
        )
    }

    private fun guarded(block: suspend () -> String?) = viewModelScope.launch {
        _message.value = runCatching { block() }.getOrElse { "操作失败：$it" }
    }

    /** 计划与白名单类修改：睡眠开始后一律拒绝。 */
    private fun guardedEdit(block: suspend () -> String?) = viewModelScope.launch {
        _message.value = if (ui.value.editingLocked) "睡眠开始后不能修改计划；如设备故障请使用紧急恢复。"
        else runCatching { block() }.getOrElse { "操作失败：$it" }
    }
}
