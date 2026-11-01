package com.yuandian.nightprotocol.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.yuandian.nightprotocol.core.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 晚安协议的设备管理组件。
 *
 * 只有当本组件被设置为 Device Owner（`adb shell dpm set-device-owner ...`）之后，
 * 才允许启用系统级严格模式（应用停用 + Lock Task）。
 */
class SleepAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Graph.init(context)
        val state = DeviceOwnerState.read(context)
        async {
            val granted = if (state.isDeviceOwner) Graph.policy.grantOwnRuntimePermissions() else emptyList()
            Graph.logger.log("ADMIN_ENABLED", state.isDeviceOwner, "deviceOwner=${state.isDeviceOwner} 自动授予=$granted")
            Graph.enforcer.reconcile("admin_enabled")
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Graph.init(context)
        async { Graph.logger.log("ADMIN_DISABLED", true, "设备管理已停用") }
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Graph.init(context)
        async { Graph.logger.log("LOCKTASK_ENTER", true, "系统回调：进入 Lock Task，pkg=$pkg") }
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Graph.init(context)
        async {
            val st = Graph.store.snapshot()
            Graph.logger.log("LOCKTASK_EXIT", true, "系统回调：退出 Lock Task；当前应锁定=${st.enforcedLock}")
            // 若仍处于锁定期却意外退出（例如进程崩溃），重新核对并尝试拉起 Kiosk。
            if (st.enforcedLock) Graph.enforcer.reconcile("locktask_exited_unexpectedly")
        }
    }

    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { block() } finally { pending.finish() }
        }
    }

    companion object {
        const val TAG = "NightProtocol"
        fun component(context: Context): ComponentName =
            ComponentName(context.applicationContext, SleepAdminReceiver::class.java)
    }
}
