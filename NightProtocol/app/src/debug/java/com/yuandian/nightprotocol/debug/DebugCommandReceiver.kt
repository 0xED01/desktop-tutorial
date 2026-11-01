package com.yuandian.nightprotocol.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yuandian.nightprotocol.core.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 仅 Debug 构建存在。供 scripts/emulator_e2e.sh 自动化验证使用（受 DUMP 权限保护，只有 adb shell 可发送）。
 *   adb shell am broadcast -a com.yuandian.nightprotocol.debug.START_TEST -n com.yuandian.nightprotocol/.debug.DebugCommandReceiver --ei minutes 2
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Graph.init(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    "com.yuandian.nightprotocol.debug.START_TEST" -> {
                        val minutes = intent.getIntExtra("minutes", 2).coerceIn(1, 10)
                        Graph.store.setTestSessionEnd(System.currentTimeMillis() + minutes * 60_000L)
                        Graph.logger.log("TEST_LOCK", true, "ADB(debug) 开始测试封锁 $minutes 分钟")
                        Graph.enforcer.reconcile("debug_test_start")
                    }
                    "com.yuandian.nightprotocol.debug.RECONCILE" -> Graph.enforcer.reconcile("debug_reconcile")
                }
            } finally {
                pending.finish()
            }
        }
    }
}
