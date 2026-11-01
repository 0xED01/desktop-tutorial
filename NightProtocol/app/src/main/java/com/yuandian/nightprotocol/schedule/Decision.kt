package com.yuandian.nightprotocol.schedule

import com.yuandian.nightprotocol.data.ProtocolState
import java.time.Instant
import java.time.ZoneId

enum class LockReason { SLEEP_WINDOW, TEST_SESSION, NONE, NOT_DEVICE_OWNER, OVERRIDDEN }

/** 期望状态：由当前时间与持久化计划唯一决定，因此重复执行是幂等的。 */
data class Decision(
    val lock: Boolean,
    val reason: LockReason,
    val lockUntil: Instant?,
    /** 下一次需要被唤醒重新核对的时刻（开始或结束），null 表示无需调度。 */
    val nextCheck: Instant?,
    val isTest: Boolean,
)

object DecisionEngine {

    fun decide(state: ProtocolState, now: Instant, zone: ZoneId, deviceOwner: Boolean): Decision {
        if (!deviceOwner) return Decision(false, LockReason.NOT_DEVICE_OWNER, null, null, false)

        val testEnd = Instant.ofEpochMilli(state.testSessionEnd)
        val testActive = state.testSessionEnd > 0 && now.isBefore(testEnd)
        val window = if (state.strictEnabled) SleepWindowCalculator.windowContaining(now, state.schedule, zone) else null
        val overridden = window != null && state.overrideUntil > now.toEpochMilli()
        val windowLock = window != null && !overridden

        val until = listOfNotNull(if (testActive) testEnd else null, if (windowLock) window!!.end else null).maxOrNull()
        if (until != null) {
            val reason = if (windowLock) LockReason.SLEEP_WINDOW else LockReason.TEST_SESSION
            return Decision(true, reason, until, until, isTest = !windowLock)
        }

        // 已被紧急恢复覆盖的窗口：nextWindow(now) 返回严格晚于 now 开始的窗口，即下一晚。
        val next = if (state.strictEnabled) SleepWindowCalculator.nextWindow(now, state.schedule, zone)?.start else null
        return Decision(false, if (overridden) LockReason.OVERRIDDEN else LockReason.NONE, null, next, false)
    }
}
