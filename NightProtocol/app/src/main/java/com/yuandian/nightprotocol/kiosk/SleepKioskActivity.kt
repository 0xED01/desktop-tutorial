package com.yuandian.nightprotocol.kiosk

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yuandian.nightprotocol.core.Enforcer
import com.yuandian.nightprotocol.core.Graph
import com.yuandian.nightprotocol.data.CheckKey
import com.yuandian.nightprotocol.data.CheckStatus
import com.yuandian.nightprotocol.policy.EssentialPackageResolver
import com.yuandian.nightprotocol.ui.NightTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 睡眠专用 Kiosk。
 *
 * - Manifest: lockTaskMode="if_whitelisted"——当本包在 setLockTaskPackages() 白名单中时，
 *   Activity 一启动系统即进入真正的 Lock Task Mode（LOCK_TASK_MODE_LOCKED），无需用户确认。
 * - 仅在 DevicePolicyManager.isLockTaskPermitted() 为 true 时才调用 startLockTask()；
 *   否则 startLockTask() 会退化为需要用户确认、可随时退出的“屏幕固定”(PINNED)，
 *   本应用绝不把屏幕固定当作锁定。
 * - 不调用 setShowWhenLocked：安全锁屏保持有效，锁屏上的系统紧急呼叫照常可用。
 */
class SleepKioskActivity : ComponentActivity() {

    private val releaseReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { release() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* 锁定期间忽略返回键 */ }
        })
        ContextCompat.registerReceiver(this, releaseReceiver, IntentFilter(ACTION_RELEASE), ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            NightTheme {
                KioskScreen(
                    stateFlow = Graph.store.state,
                    onDial = { open(Intent(Intent.ACTION_DIAL)) },
                    onEmergency = { openEmergencyDialer() },
                    onAlarms = { open(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                    onRecover = { code, reason -> Graph.recovery.recoverWithCode(code, reason) },
                )
            }
        }
        lifecycleScope.launch {
            Graph.store.state.collect { if (!it.enforcedLock) release() }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val st = Graph.store.snapshot()
            if (!st.enforcedLock) { release(); return@launch }
            ensureLockTask(st.lastLockWasTest)
        }
    }

    private suspend fun ensureLockTask(isTest: Boolean) {
        val am = getSystemService(ActivityManager::class.java)
        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            if (dpm.isLockTaskPermitted(packageName)) {
                runCatching { startLockTask() }
                    .onFailure { Graph.logger.log("LOCKTASK_START", false, "startLockTask 异常：$it") }
            } else {
                Graph.logger.log("LOCKTASK_START", false, "本包不在 Lock Task 白名单，拒绝退化为屏幕固定")
            }
        }
        delay(1500)
        val mode = am.lockTaskModeState
        val ok = mode == ActivityManager.LOCK_TASK_MODE_LOCKED
        val st = Graph.store.snapshot()
        if (isTest && st.enforcedLock && st.check(CheckKey.LOCKTASK_ENTER).status != CheckStatus.PASSED) {
            Graph.store.recordCheck(CheckKey.LOCKTASK_ENTER, if (ok) CheckStatus.PASSED else CheckStatus.FAILED,
                "lockTaskModeState=${Enforcer.lockModeName(mode)}")
        }
        if (!ok) Graph.logger.log("LOCKTASK_START", false, "当前 lockTaskModeState=${Enforcer.lockModeName(mode)}")
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { e -> lifecycleScope.launch { Graph.logger.log("KIOSK_OPEN", false, "${intent.action} 打开失败：$e") } }
    }

    private fun openEmergencyDialer() {
        val emergency = Intent(EssentialPackageResolver.ACTION_EMERGENCY_DIAL)
        if (emergency.resolveActivity(packageManager) != null) open(emergency)
        else open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
    }

    private var released = false
    private fun release() {
        if (released) return
        released = true
        runCatching {
            if (getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) stopLockTask()
        }
        finishAndRemoveTask()
        // 回到用户原桌面（HOME 首选项此时已由 Enforcer 清除）
        runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(releaseReceiver) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_RELEASE = "com.yuandian.nightprotocol.action.RELEASE_KIOSK"
    }
}
