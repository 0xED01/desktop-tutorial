package com.yuandian.nightprotocol.recovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yuandian.nightprotocol.core.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ADB 恢复入口。Manifest 中以 android.permission.DUMP 保护：
 * 该权限只授予 shell/系统，普通应用无法获取，因此只有连接 ADB 的电脑可以触发。
 *
 *   adb shell am broadcast -a com.yuandian.nightprotocol.action.ADB_RECOVER \
 *       -n com.yuandian.nightprotocol/.recovery.AdbRecoveryReceiver --es reason "故障说明" [--ez disable_strict true]
 */
class AdbRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        Graph.init(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                Graph.recovery.recoverFromAdb(intent.getStringExtra("reason").orEmpty(), intent.getBooleanExtra("disable_strict", false))
            } finally {
                pending.finish()
            }
        }
    }

    companion object { const val ACTION = "com.yuandian.nightprotocol.action.ADB_RECOVER" }
}
