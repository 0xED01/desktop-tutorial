package com.yuandian.nightprotocol.data

/** 睡眠计划：以“当天零点起的分钟数”表示的本地墙钟时间。 */
data class SleepSchedule(
    val startMinute: Int = DEFAULT_START,
    val endMinute: Int = DEFAULT_END,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute out of range" }
    }

    val isValid: Boolean get() = startMinute != endMinute

    fun format(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    val startText: String get() = format(startMinute)
    val endText: String get() = format(endMinute)

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val DEFAULT_START = 23 * 60 + 30
        const val DEFAULT_END = 7 * 60 + 30
    }
}

enum class CheckStatus(val label: String) { PASSED("已验证"), FAILED("失败"), UNTESTED("未验证") }

/**
 * 严格模式正式启用前必须全部为 PASSED 的检查项。
 * auto=true 由 App 自行测得（实时读取或在“测试封锁”过程中记录）；
 * auto=false 只能由用户在真机测试封锁期间亲自操作后确认，App 无法替用户验证。
 */
enum class CheckKey(val label: String, val auto: Boolean, val hint: String = "") {
    DEVICE_OWNER("已取得 Device Owner 身份", true),
    EXACT_ALARM("可设置精确闹钟", true),
    DIALER_ALLOWED("默认拨号器已加入 Lock Task 白名单", true),
    RECOVERY_CODE("紧急恢复码已设置", true),
    SUSPEND_APPLY("测试：系统级应用停用已生效", true),
    LOCKTASK_ENTER("测试：真正进入 Lock Task（非屏幕固定）", true),
    LOCKTASK_EXIT("测试：Lock Task 已自动退出", true),
    SUSPEND_RESTORE("测试：应用停用已全部解除", true),
    HOME_RESTORE("测试：原桌面已恢复", true),
    RECOVERY_CODE_TEST("测试：恢复码可解除锁定", true, "在测试封锁中用恢复码解除一次"),
    ADB_RECOVERY_TEST("测试：ADB 恢复命令可解除锁定", true, "在测试封锁中执行 scripts/adb_recover.sh"),
    MANUAL_INCOMING_CALL("手动：锁定中可正常接听来电", false, "测试封锁时用另一部手机拨入，亮屏与熄屏各一次"),
    MANUAL_OUTGOING_CALL("手动：锁定中可正常拨打电话", false, "从锁定界面「电话」拨出并挂断"),
    MANUAL_EMERGENCY_DIALER("手动：紧急拨号流程可用", false, "锁屏与锁定界面均能打开紧急拨号；请勿真实拨打紧急号码"),
    MANUAL_POWER_WAKE("手动：电源键可正常熄屏、唤醒并解锁", false, "亦确认长按电源键菜单可重启"),
    MANUAL_ALARM("手动：锁定中闹钟正常响铃并可关闭", false, "测试封锁前设一个 2 分钟后的闹钟"),
}

data class CheckRecord(val status: CheckStatus, val atMillis: Long, val note: String) {
    fun encode(): String = "${status.name}|$atMillis|${note.replace('|', '/').replace('\n', ' ')}"

    companion object {
        val UNTESTED = CheckRecord(CheckStatus.UNTESTED, 0L, "")
        fun decode(raw: String?): CheckRecord {
            if (raw.isNullOrBlank()) return UNTESTED
            val parts = raw.split('|', limit = 3)
            val status = runCatching { CheckStatus.valueOf(parts[0]) }.getOrNull() ?: return UNTESTED
            val at = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            return CheckRecord(status, at, parts.getOrNull(2).orEmpty())
        }
    }
}

/** 持久化的完整协议状态（存放于设备加密存储区，以便 LOCKED_BOOT_COMPLETED 阶段可读）。 */
data class ProtocolState(
    val schedule: SleepSchedule = SleepSchedule(),
    val strictEnabled: Boolean = false,
    val extraAllowed: Set<String> = emptySet(),
    val recoveryHash: String? = null,
    /** 紧急恢复后，本次睡眠窗口内不再锁定，直到该时刻（epoch ms）。 */
    val overrideUntil: Long = 0L,
    /** 测试封锁结束时刻（epoch ms），0 表示无测试。 */
    val testSessionEnd: Long = 0L,
    /** 本 App 实际施加过暂停的应用（用于幂等解除）。 */
    val suspendedByUs: Set<String> = emptySet(),
    /** 最近一次实际施加的状态。 */
    val enforcedLock: Boolean = false,
    val lockedUntil: Long = 0L,
    val lastLockWasTest: Boolean = false,
    val homeOverrideActive: Boolean = false,
    val checks: Map<CheckKey, CheckRecord> = emptyMap(),
    val failedRecoveryAttempts: Int = 0,
    val recoveryLockoutUntil: Long = 0L,
) {
    fun check(key: CheckKey): CheckRecord = checks[key] ?: CheckRecord.UNTESTED
}
