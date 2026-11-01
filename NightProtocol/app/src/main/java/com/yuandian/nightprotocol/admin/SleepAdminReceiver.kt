package com.yuandian.nightprotocol.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 晚安协议的设备管理组件。
 *
 * 只有当本组件被设置为 Device Owner（`adb shell dpm set-device-owner ...`）之后，
 * 才允许启用系统级严格模式（应用停用 + Lock Task）。
 */
class SleepAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled; deviceOwner=${DeviceOwnerState.read(context).isDeviceOwner}")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled")
    }

    companion object {
        const val TAG = "NightProtocol"
        fun component(context: Context): ComponentName =
            ComponentName(context.applicationContext, SleepAdminReceiver::class.java)
    }
}
