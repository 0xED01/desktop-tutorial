package com.yuandian.nightprotocol.core

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.yuandian.nightprotocol.data.CheckKey
import com.yuandian.nightprotocol.data.CheckStatus
import com.yuandian.nightprotocol.data.EventLogger
import com.yuandian.nightprotocol.data.ProtocolState
import com.yuandian.nightprotocol.data.ProtocolStore
import com.yuandian.nightprotocol.kiosk.SleepKioskActivity
import com.yuandian.nightprotocol.policy.EssentialPackageResolver
import com.yuandian.nightprotocol.policy.LockTaskFeaturePolicy
import com.yuandian.nightprotocol.policy.PolicyController
import com.yuandian.nightprotocol.policy.SuspensionPlanner
import com.yuandian.nightprotocol.schedule.Decision
import com.yuandian.nightprotocol.schedule.DecisionEngine
import com.yuandian.nightprotocol.schedule.TransitionScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * 唯一的策略执行入口。任何触发源（闹钟、开机、时间变化、应用启动、恢复……）
 * 都调用 [reconcile]：读取计划 → 计算期望状态 → 幂等地把设备收敛到期望状态 → 安排下一次唤醒。
 *
 * 关键设计：系统级应用暂停与 Kiosk 界面相互独立。即使由于后台 Activity 启动限制
 * 无法立即弹出锁定界面，应用暂停与 Lock Task 白名单仍会按时生效。
 */
class Enforcer(
    private val ctx: Context,
    private val store: ProtocolStore,
    private val policy: PolicyController,
    private val resolver: EssentialPackageResolver,
    private val scheduler: TransitionScheduler,
    private val notifier: LockNotifier,
    private val logger: EventLogger,
) {
    private val mutex = Mutex()

    suspend fun reconcile(trigger: String, launchKiosk: Boolean = true): Decision = mutex.withLock {
        val state = store.snapshot()
        val isDo = runCatching { policy.isDeviceOwner() }.getOrDefault(false)
        val decision = DecisionEngine.decide(state, Instant.now(), ZoneId.systemDefault(), isDo)

        if (!isDo) {
            if (state.enforcedLock) logger.log("RECONCILE", false, "trigger=$trigger 不是 Device Owner，无法施加或解除策略")
            scheduler.cancel()
            return decision
        }

        if (decision.lock) applyLock(state, decision, trigger, launchKiosk) else applyUnlock(state, trigger)

        val scheduled = scheduler.schedule(decision.nextCheck)
        if (decision.lock != state.enforcedLock || trigger !in QUIET_TRIGGERS) {
            logger.log("RECONCILE", true,
                "trigger=$trigger 期望=${if (decision.lock) "锁定" else "正常"} 原因=${decision.reason} " +
                    "至=${decision.lockUntil ?: "-"} 下次核对=${decision.nextCheck ?: "无"} 调度=$scheduled")
        }
        decision
    }

    private suspend fun applyLock(state: ProtocolState, d: Decision, trigger: String, launchKiosk: Boolean) {
        val firstEntry = !state.enforcedLock
        if (!ctx.getSystemService(UserManager::class.java).isUserUnlocked) {
            // 直接启动阶段：包查询结果不完整，重写白名单可能误删拨号器。
            // 应用暂停、Lock Task 白名单与持久化 HOME 均跨重启保持，此时只更新状态，待 BOOT_COMPLETED 再完整核对。
            store.setEnforced(true, d.lockUntil?.toEpochMilli() ?: 0L, d.isTest, state.homeOverrideActive)
            if (firstEntry) logger.log("RECONCILE", true, "trigger=$trigger 用户尚未解锁：沿用重启前的持久化策略")
            return
        }
        val ess = resolver.resolve(state.extraAllowed)

        // 1) 系统级应用暂停 —— 不依赖任何界面，最先执行。
        runCatching {
            val targets = SuspensionPlanner.targets(ess.launcherPackages, ess.suspendExempt)
            val failed = policy.setPackagesSuspended(targets, true).toSet()
            val ok = targets.filter { it !in failed }
            store.addSuspended(ok)
            val verified = ok.count { policy.isSuspendedByAdmin(it) == true }
            val detail = "目标 ${targets.size}，系统接受 ${ok.size}，拒绝 ${failed.size}${if (failed.isNotEmpty()) " ${failed.sorted()}" else ""}；复核已暂停 $verified"
            if (firstEntry || failed.isNotEmpty()) logger.log("SUSPEND", failed.isEmpty() && verified == ok.size, detail)
            if (d.isTest && firstEntry) {
                val pass = ok.isNotEmpty() && verified == ok.size
                store.recordCheck(CheckKey.SUSPEND_APPLY, if (pass) CheckStatus.PASSED else CheckStatus.FAILED, detail)
            }
        }.onFailure { logger.log("SUSPEND", false, "异常：$it") }

        // 2) Lock Task 白名单与默认 HOME
        var kioskHome = false
        runCatching {
            policy.setLockTaskPackages(ess.lockTaskAllowed)
            kioskHome = policy.setKioskAsHome(true)
            val features = LockTaskFeaturePolicy.features(kioskHome)
            policy.setLockTaskFeatures(features)
            if (firstEntry) logger.log("LOCKTASK_POLICY", kioskHome,
                "白名单 ${ess.lockTaskAllowed.sorted()}；Kiosk 设为 HOME=$kioskHome；features=${LockTaskFeaturePolicy.describe(features)}" +
                    if (!kioskHome) "（HOME 未接管：已关闭 HOME/NOTIFICATIONS，亮屏来电浮窗可能不可见）" else "")
        }.onFailure { logger.log("LOCKTASK_POLICY", false, "异常：$it") }

        store.setEnforced(true, d.lockUntil?.toEpochMilli() ?: 0L, d.isTest, kioskHome)
        notifier.showLocked(d.lockUntil)

        // 3) 尝试进入锁定界面（可能受后台 Activity 启动限制；失败不影响 1、2）。
        if (launchKiosk) launchKiosk(trigger)
    }

    private suspend fun applyUnlock(state: ProtocolState, trigger: String) {
        val wasLocked = state.enforcedLock
        // 1) 解除本 App 施加的应用暂停（记录集 ∪ 实际仍被管理员暂停的有桌面入口应用）
        runCatching {
            val stillSuspended = resolver.launcherPackages().filter { policy.isSuspendedByAdmin(it) == true }
            val restore = SuspensionPlanner.restoreSet(state.suspendedByUs, stillSuspended)
            if (restore.isNotEmpty()) {
                val failed = policy.setPackagesSuspended(restore, false).filter { policy.isInstalled(it) }.toSet()
                store.setSuspended(failed)
                val remaining = restore.count { policy.isSuspendedByAdmin(it) == true }
                val detail = "解除 ${restore.size}，拒绝 ${failed.size}${if (failed.isNotEmpty()) " ${failed.sorted()}" else ""}；复核仍暂停 $remaining"
                logger.log("UNSUSPEND", failed.isEmpty() && remaining == 0, detail)
                if (wasLocked && state.lastLockWasTest) store.recordCheck(CheckKey.SUSPEND_RESTORE,
                    if (failed.isEmpty() && remaining == 0) CheckStatus.PASSED else CheckStatus.FAILED, detail)
            } else if (state.suspendedByUs.isNotEmpty()) store.setSuspended(emptySet())
        }.onFailure { logger.log("UNSUSPEND", false, "异常：$it") }

        // 2) 移出 Lock Task 白名单：系统会自动结束本应用的 Lock Task；Kiosk 自己也会 stopLockTask()。
        runCatching {
            if (policy.getLockTaskPackages().isNotEmpty()) policy.setLockTaskPackages(emptyList())
        }.onFailure { logger.log("LOCKTASK_POLICY", false, "清空白名单异常：$it") }

        // 3) 恢复原桌面
        val homeOk = policy.setKioskAsHome(false)
        if (wasLocked || !homeOk) logger.log("HOME_RESTORE", homeOk, "当前 HOME=${policy.currentHomePackage()}")

        notifier.cancel()
        // 4) 保持 Device Owner 身份不变；只记录状态。
        store.setEnforced(false, 0L, false, false)
        if (wasLocked) {
            ctx.sendBroadcast(Intent(SleepKioskActivity.ACTION_RELEASE).setPackage(ctx.packageName))
            if (state.lastLockWasTest) verifyExitAfterTest()
            logger.log("RESTORE", true, "trigger=$trigger 已恢复正常使用")
        }
    }

    private suspend fun verifyExitAfterTest() {
        delay(1500) // Lock Task 状态变化是异步的
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mode = am.lockTaskModeState
        store.recordCheck(CheckKey.LOCKTASK_EXIT,
            if (mode == ActivityManager.LOCK_TASK_MODE_NONE) CheckStatus.PASSED else CheckStatus.FAILED,
            "退出后 lockTaskModeState=${lockModeName(mode)}")
        val home = policy.currentHomePackage()
        store.recordCheck(CheckKey.HOME_RESTORE,
            if (home != ctx.packageName) CheckStatus.PASSED else CheckStatus.FAILED,
            "HOME 解析为 $home${if (home == "android") "（系统选择器：用户未设默认桌面）" else ""}")
    }

    /** 直接启动 Kiosk 并请求进入 Lock Task。Device Owner 属于后台 Activity 启动豁免范围，但厂商系统可能另有限制。 */
    suspend fun launchKiosk(trigger: String): Boolean {
        // 已在 Lock Task 中（可能正处于白名单内的通话/闹钟界面）时不再拉起，避免遮挡来电界面。
        if (ctx.getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return true
        val intent = Intent(ctx, SleepKioskActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val withLockTask = runCatching {
            val opts = ActivityOptions.makeBasic().setLockTaskEnabled(true)
            ctx.startActivity(intent, opts.toBundle()); true
        }.getOrElse { e ->
            logger.log("KIOSK_LAUNCH", false, "setLockTaskEnabled 启动失败：$e；改为普通启动（Activity 会在前台自行 startLockTask）")
            false
        }
        if (withLockTask) return true
        return runCatching { ctx.startActivity(intent); true }.getOrElse { e ->
            logger.log("KIOSK_LAUNCH", false, "trigger=$trigger 无法启动锁定界面：$e；已依赖：应用暂停 + 全屏通知 + Kiosk HOME 兜底")
            false
        }
    }

    companion object {
        private val QUIET_TRIGGERS = setOf("app_start", "main_resume", "user_present", "kiosk_resume")
        fun lockModeName(mode: Int) = when (mode) {
            ActivityManager.LOCK_TASK_MODE_LOCKED -> "LOCKED(Lock Task)"
            ActivityManager.LOCK_TASK_MODE_PINNED -> "PINNED(屏幕固定)"
            else -> "NONE"
        }
    }
}
