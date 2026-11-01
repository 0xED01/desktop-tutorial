package com.yuandian.nightprotocol.policy

import android.app.admin.DevicePolicyManager

/** 必要应用解析结果。 */
data class EssentialSet(
    /** 加入 setLockTaskPackages() 的包（可在 Lock Task 中启动界面）。 */
    val lockTaskAllowed: Set<String>,
    /** 永不暂停的包（⊇ lockTaskAllowed）。 */
    val suspendExempt: Set<String>,
    /** 有桌面图标的用户可见应用（暂停候选集）。 */
    val launcherPackages: Set<String>,
    /** 每个受保护包被保护的原因，用于界面与日志。 */
    val reasons: Map<String, Set<String>>,
    val defaultDialer: String?,
)

object SuspensionPlanner {
    /** 需要暂停的应用 = 有桌面入口的应用 − 受保护应用。排序以保证结果稳定。 */
    fun targets(launcherPackages: Collection<String>, exempt: Set<String>): List<String> =
        launcherPackages.asSequence().filter { it.isNotBlank() && it !in exempt }.distinct().sorted().toList()

    /** 解除时：本 App 记录的 ∪ 当前确认为被管理员暂停的；两者取并集保证崩溃后也能修复。 */
    fun restoreSet(recorded: Set<String>, currentlySuspendedByAdmin: Collection<String>): List<String> =
        (recorded + currentlySuspendedByAdmin).sorted()
}

object LockTaskFeaturePolicy {
    /**
     * - SYSTEM_INFO：状态栏显示时间/电量/信号。
     * - GLOBAL_ACTIONS：保留长按电源键菜单（重启、关机、紧急呼叫/SOS）——设备恢复通道。
     * - KEYGUARD：保留安全锁屏；电源键熄屏/唤醒后需要正常解锁，锁屏紧急呼叫可用。
     * - HOME + NOTIFICATIONS：仅当 Kiosk 已成为持久化默认 HOME 时开启。
     *   NOTIFICATIONS 必须依赖 HOME（否则 setLockTaskFeatures 抛 IllegalArgumentException），
     *   而来电的浮动通知 (heads-up) 属于 NOTIFICATIONS 功能；当 HOME 指向本 Kiosk 时，
     *   开启 HOME 不会造成逃逸，且能让来电界面在亮屏使用中正常弹出。
     * - 始终不开启 OVERVIEW（最近任务）。
     */
    fun features(kioskIsHome: Boolean): Int {
        var f = DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
            DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
            DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
        if (kioskIsHome) {
            f = f or DevicePolicyManager.LOCK_TASK_FEATURE_HOME or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
        }
        return f
    }

    fun describe(flags: Int): String = buildList {
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO != 0) add("SYSTEM_INFO")
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS != 0) add("GLOBAL_ACTIONS")
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD != 0) add("KEYGUARD")
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_HOME != 0) add("HOME")
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS != 0) add("NOTIFICATIONS")
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW != 0) add("OVERVIEW")
    }.joinToString("|").ifEmpty { "NONE" }
}
