package com.yuandian.nightprotocol.schedule

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yuandian.nightprotocol.core.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 在 goAsync() 期限内执行一次核对。 */
internal fun BroadcastReceiver.reconcileAsync(context: Context, trigger: String) {
    Graph.init(context)
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            withTimeoutOrNull(9_000) { Graph.enforcer.reconcile(trigger) }
        } finally {
            pending.finish()
        }
    }
}

/** 精确闹钟：睡眠开始 / 结束。 */
class TransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) reconcileAsync(context, "alarm")
    }

    companion object { const val ACTION = "com.yuandian.nightprotocol.action.TRANSITION" }
}

/** 系统事件：开机（含直接启动）、时间/时区变化、APK 更新、精确闹钟权限变化。 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> "locked_boot"
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            Intent.ACTION_TIME_CHANGED -> "time_set"
            Intent.ACTION_TIMEZONE_CHANGED -> "timezone"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "apk_updated"
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> "exact_alarm_perm"
            else -> return
        }
        reconcileAsync(context, trigger)
    }
}
