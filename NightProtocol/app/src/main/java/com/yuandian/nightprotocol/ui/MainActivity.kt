package com.yuandian.nightprotocol.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yuandian.nightprotocol.core.Graph
import com.yuandian.nightprotocol.kiosk.SleepKioskActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(this)
        val actions = MainActions(
            setSchedule = vm::setSchedule, setStrict = vm::setStrict, startTest = vm::startTest,
            generateCode = vm::generateRecoveryCode, markManual = vm::markManual, resetChecks = vm::resetChecks,
            toggleExtra = vm::toggleExtraAllowed, removeOwner = vm::removeDeviceOwner,
            openExactAlarm = { if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri())) },
            openNotifications = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) },
            openFullScreen = { if (Build.VERSION.SDK_INT >= 34) open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri())) },
            openBattery = { open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri())) },
        )
        setContent {
            NightTheme {
                val u by vm.ui.collectAsStateWithLifecycle()
                val msg by vm.message.collectAsStateWithLifecycle()
                val code by vm.newRecoveryCode.collectAsStateWithLifecycle()
                MainScreen(u, actions)
                msg?.let {
                    AlertDialog(onDismissRequest = vm::consumeMessage, containerColor = NightColors.Surface,
                        text = { Text(it) }, confirmButton = { TextButton(onClick = vm::consumeMessage) { Text("好") } })
                }
                code?.let {
                    // 恢复码显示期间禁止截屏/录屏
                    DisposableEffect(Unit) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                    }
                    RecoveryCodeDialog(it, vm::consumeRecoveryCode)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            // 锁定期间主界面不可用：转到 Kiosk。
            if (Graph.store.snapshot().enforcedLock) {
                startActivity(Intent(this@MainActivity, SleepKioskActivity::class.java))
                finish()
            } else vm.refresh()
        }
    }

    private fun pkgUri() = Uri.parse("package:$packageName")
    private fun open(i: Intent) = runCatching { startActivity(i) }.onFailure {
        Toast.makeText(this, "此设备不支持该设置页面", Toast.LENGTH_SHORT).show()
    }
}
