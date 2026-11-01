package com.yuandian.nightprotocol.recovery

import com.yuandian.nightprotocol.core.Enforcer
import com.yuandian.nightprotocol.data.CheckKey
import com.yuandian.nightprotocol.data.CheckStatus
import com.yuandian.nightprotocol.data.EventLogger
import com.yuandian.nightprotocol.data.ProtocolStore

sealed interface RecoveryResult {
    data object Success : RecoveryResult
    data class WrongCode(val remainingFree: Int, val lockoutUntil: Long) : RecoveryResult
    data class LockedOut(val until: Long) : RecoveryResult
    data object NoCodeConfigured : RecoveryResult
    data object ReasonRequired : RecoveryResult
    data object NotLocked : RecoveryResult
}

/**
 * 紧急恢复：仅结束“当前这一次”锁定（测试或当晚睡眠窗口），不关闭严格模式，下一晚照常生效。
 * 每次使用都必须填写原因并写入执行日志。
 */
class RecoveryManager(
    private val store: ProtocolStore,
    private val enforcer: Enforcer,
    private val logger: EventLogger,
) {
    suspend fun recoverWithCode(code: String, reason: String, now: Long = System.currentTimeMillis()): RecoveryResult {
        val st = store.snapshot()
        if (!st.enforcedLock) return RecoveryResult.NotLocked
        if (reason.trim().length < 4) return RecoveryResult.ReasonRequired
        val hash = st.recoveryHash ?: return RecoveryResult.NoCodeConfigured
        if (st.recoveryLockoutUntil > now) return RecoveryResult.LockedOut(st.recoveryLockoutUntil)

        if (!RecoveryCode.verify(code, hash)) {
            val failed = st.failedRecoveryAttempts + 1
            val lockout = RecoveryCode.lockoutMillis(failed).let { if (it > 0) now + it else 0L }
            store.setRecoveryAttempts(failed, lockout)
            logger.log("EMERGENCY_RECOVERY", false, "恢复码错误（累计 $failed 次）原因：${reason.trim()}")
            return RecoveryResult.WrongCode((RecoveryCode.MAX_FREE_ATTEMPTS - failed).coerceAtLeast(0), lockout)
        }
        store.setRecoveryAttempts(0, 0L)
        release("恢复码", reason.trim(), CheckKey.RECOVERY_CODE_TEST)
        return RecoveryResult.Success
    }

    /** 来自 ADB（shell 身份，受 DUMP 权限保护）的恢复。 */
    suspend fun recoverFromAdb(reason: String, disableStrict: Boolean) {
        if (disableStrict) store.setStrictEnabled(false)
        release("ADB", reason.ifBlank { "未填写" } + if (disableStrict) "（同时关闭严格模式）" else "", CheckKey.ADB_RECOVERY_TEST)
    }

    private suspend fun release(channel: String, reason: String, testCheck: CheckKey) {
        val st = store.snapshot()
        val wasTest = st.enforcedLock && st.lastLockWasTest
        // 覆盖到当前锁定结束时刻；同时结束测试会话。
        store.setOverrideUntil(maxOf(st.lockedUntil, System.currentTimeMillis()))
        store.setTestSessionEnd(0L)
        logger.log("EMERGENCY_RECOVERY", true, "通道=$channel 原因：$reason")
        if (wasTest) store.recordCheck(testCheck, CheckStatus.PASSED, "测试封锁中经$channel 成功解除")
        enforcer.reconcile("recovery_$channel", launchKiosk = false)
    }
}
