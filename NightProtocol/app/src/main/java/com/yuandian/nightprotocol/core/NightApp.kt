package com.yuandian.nightprotocol.core

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NightApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // 进程（重新）启动即核对一次：覆盖崩溃、被杀后重启、APK 更新等情况。
        appScope.launch { Graph.enforcer.reconcile("app_start", launchKiosk = true) }
        // 用户解锁屏幕：ACTION_USER_PRESENT 不能在 Manifest 中静态注册，只能在进程存活时动态监听。
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                appScope.launch { Graph.enforcer.reconcile("user_present") }
            }
        }, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
