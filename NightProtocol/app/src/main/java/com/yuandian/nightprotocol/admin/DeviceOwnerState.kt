package com.yuandian.nightprotocol.admin

import android.app.admin.DevicePolicyManager
import android.content.Context

/** Device Owner 的实际状态（每次从 DevicePolicyManager 实时读取，不缓存）。 */
data class DeviceOwnerState(
    val isAdminActive: Boolean,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val lockTaskPermitted: Boolean,
) {
    /** 只有真正取得 Device Owner 身份才能启用严格模式。 */
    val canEnforceStrictMode: Boolean get() = isAdminActive && isDeviceOwner

    companion object {
        fun read(context: Context): DeviceOwnerState {
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val admin = SleepAdminReceiver.component(context)
            val pkg = context.packageName
            return DeviceOwnerState(
                isAdminActive = dpm.isAdminActive(admin),
                isDeviceOwner = dpm.isDeviceOwnerApp(pkg),
                isProfileOwner = dpm.isProfileOwnerApp(pkg),
                lockTaskPermitted = dpm.isLockTaskPermitted(pkg),
            )
        }
    }
}
