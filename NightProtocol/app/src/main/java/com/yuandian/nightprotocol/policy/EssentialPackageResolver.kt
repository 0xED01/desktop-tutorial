package com.yuandian.nightprotocol.policy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * 根据设备的真实组件动态解析必要应用，不硬编码某一厂商的包名
 * （硬编码列表仅作为“若已安装则补充”的兜底）。
 */
class EssentialPackageResolver(context: Context) {
    private val ctx = context.applicationContext
    private val pm = ctx.packageManager

    fun resolve(extraAllowed: Set<String>): EssentialSet {
        val lockTask = linkedMapOf<String, MutableSet<String>>()
        val exemptOnly = linkedMapOf<String, MutableSet<String>>()
        fun lt(pkg: String?, why: String) { if (!pkg.isNullOrBlank() && installed(pkg)) lockTask.getOrPut(pkg) { linkedSetOf() } += why }
        fun ex(pkg: String?, why: String) { if (!pkg.isNullOrBlank() && installed(pkg)) exemptOnly.getOrPut(pkg) { linkedSetOf() } += why }

        lt(ctx.packageName, "本应用（锁定界面与设备管理）")

        // —— 通信与紧急能力 ——
        val telecom = ctx.getSystemService(TelecomManager::class.java)
        val defaultDialer = runCatching { telecom?.defaultDialerPackage }.getOrNull()
        lt(defaultDialer, "默认拨号器")
        lt(runCatching { telecom?.systemDialerPackage }.getOrNull(), "系统拨号器")
        activities(Intent(Intent.ACTION_DIAL)).forEach { lt(it, "处理 ACTION_DIAL") }
        activities(Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))).forEach { lt(it, "处理 tel: 拨号") }
        activities(Intent(ACTION_EMERGENCY_DIAL)).forEach { lt(it, "紧急拨号界面") }
        services(Intent(InCallService.SERVICE_INTERFACE)).forEach { lt(it, "通话界面 InCallService") }
        TELEPHONY_FALLBACK.forEach { lt(it, "电话/紧急系统组件") }

        // —— 闹钟 ——
        listOf(AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM,
            AlarmClock.ACTION_DISMISS_ALARM, AlarmClock.ACTION_SNOOZE_ALARM)
            .forEach { a -> activities(Intent(a)).forEach { lt(it, "闹钟") } }

        // —— 用户额外指定（如医疗类应用）——
        extraAllowed.forEach { lt(it, "用户指定保留") }

        // —— 仅免于暂停（不允许在 Lock Task 中打开界面）——
        activities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)).forEach { ex(it, "桌面 HOME（恢复后需要）") }
        activities(Intent(Settings.ACTION_SETTINGS)).forEach { ex(it, "系统设置（设备恢复依赖）") }
        runCatching { ctx.getSystemService(InputMethodManager::class.java).enabledInputMethodList }
            .getOrDefault(emptyList()).forEach { ex(it.packageName, "输入法") }
        SYSTEM_FALLBACK.forEach { ex(it, "系统安全/恢复组件") }

        val reasons = (lockTask.keys + exemptOnly.keys).associateWith { (lockTask[it].orEmpty() + exemptOnly[it].orEmpty()) }
        val launcher = activities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).toSet()
        return EssentialSet(
            lockTaskAllowed = lockTask.keys.toSet(),
            suspendExempt = lockTask.keys + exemptOnly.keys,
            launcherPackages = launcher,
            reasons = reasons,
            defaultDialer = defaultDialer,
        )
    }

    fun launcherPackages(): Set<String> =
        activities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).toSet()

    private fun activities(i: Intent): List<String> =
        pm.queryIntentActivities(i, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.distinct()

    private fun services(i: Intent): List<String> =
        pm.queryIntentServices(i, PackageManager.MATCH_ALL).map { it.serviceInfo.packageName }.distinct()

    private fun installed(pkg: String): Boolean =
        runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    companion object {
        /** AOSP 锁屏“紧急呼叫”按钮使用的 Action（com.android.phone/.EmergencyDialer）。 */
        const val ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL"

        val TELEPHONY_FALLBACK = listOf(
            "com.android.server.telecom", "com.android.phone", "com.android.incallui",
            "com.android.emergency", "com.google.android.apps.safetyhub",
            // 无线紧急警报（地震/灾害预警弹窗）
            "com.android.cellbroadcastreceiver", "com.google.android.cellbroadcastreceiver",
            "com.android.cellbroadcastreceiver.module",
        )

        val SYSTEM_FALLBACK = listOf(
            "com.android.systemui", "com.android.settings",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.android.shell", "com.android.providers.settings",
        )
    }
}
