package com.islandpet.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.islandpet.island.IslandAccessibilityService
import com.islandpet.island.IslandPrefs
import com.islandpet.island.IslandService
import com.islandpet.island.OverlayHost

private fun Context.hasNotificationPermission() =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.ignoringBattery() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

@Composable
fun IslandSetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                refresh++
                if (IslandPrefs.enabled(ctx)) IslandService.start(ctx)
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // 读取 refresh 让权限状态在回到页面时刷新
    refresh.let { }
    val notif = ctx.hasNotificationPermission()
    val overlay = Settings.canDrawOverlays(ctx)
    val enhanced = IslandAccessibilityService.instance != null
    val battery = ctx.ignoringBattery()
    var enabled by remember { mutableStateOf(IslandPrefs.enabled(ctx)) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = Orange) }
            Text("灵动岛设置", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("开启灵动岛", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        when {
                            !enabled -> "关闭中"
                            OverlayHost.isShowing && OverlayHost.isEnhanced -> "运行中 · 增强模式"
                            OverlayHost.isShowing -> "运行中 · 普通悬浮窗"
                            else -> "还差权限，按下面的步骤开启"
                        },
                        fontSize = 12.sp, color = Dim,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        IslandPrefs.setEnabled(ctx, it)
                        if (it) IslandService.start(ctx) else IslandService.stop(ctx)
                        refresh++
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Orange),
                )
            }
        }

        Text("按顺序完成这几步", fontWeight = FontWeight.Bold)

        Step(
            "1", "通知权限", "用来显示常驻的宠物状态通知，可以在通知里直接喂食；Android 16 上还会尝试显示为「实时活动」。", notif,
        ) { if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

        Step(
            "2", "悬浮窗权限", "让宠物显示在其他 App 上面。打开后在列表里找到「灵动岛宠物」，允许显示在其他应用上层。", overlay,
        ) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }

        Step(
            "3", "增强模式（推荐）",
            "普通悬浮窗在状态栏下面，点摄像头位置可能会拉下通知栏。开启后灵动岛能盖在状态栏上、点哪都灵。\n路径：设置 › 快捷与辅助 › 无障碍 › 已安装的服务 › 灵动岛宠物 · 增强模式 › 开启。不读取任何屏幕内容。",
            enhanced,
        ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        Step(
            "4", "允许后台运行",
            "防止系统把灵动岛清理掉。另外建议：设置 › 电池 › 后台耗电管理 › 灵动岛宠物 › 允许后台高耗电；在最近任务里下拉卡片锁定。",
            battery,
        ) {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }.onFailure { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }

        Text("位置和大小", fontWeight = FontWeight.Bold)
        CardBox {
            Text("会自动对准前置摄像头。如果没对齐，在这里微调（改完立刻生效）。", fontSize = 12.sp, color = Dim)
            PrefSlider("上下位置", IslandPrefs.offsetY(ctx), -30, 80, "dp") { IslandPrefs.setOffsetY(ctx, it) }
            PrefSlider("胶囊宽度", IslandPrefs.width(ctx), 100, 280, "dp") { IslandPrefs.setWidth(ctx, it) }
            PrefSlider("胶囊高度", IslandPrefs.height(ctx), 0, 60, "dp", zeroLabel = "自动") { IslandPrefs.setHeight(ctx, it) }
        }

        CardBox {
            Text("使用方法", fontWeight = FontWeight.Bold)
            Text(
                "• 点一下胶囊：展开，可以喂食、玩耍、摸摸、清洁 / 哄睡\n" +
                    "• 展开后点宠物：摸摸它\n" +
                    "• 展开后长按：打开 App\n" +
                    "• 点胶囊外面：收起\n" +
                    "• 宠物饿了、生病了，胶囊会自己弹开提醒你",
                fontSize = 13.sp, color = Dim,
            )
        }
    }
}

@Composable
private fun Step(num: String, title: String, detail: String, done: Boolean, onClick: () -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (done) "✅" else num, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, fontSize = 12.sp, color = Dim)
            }
            if (!done) {
                Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Orange), modifier = Modifier.padding(start = 8.dp)) {
                    Text("去开启")
                }
            }
        }
    }
}

@Composable
private fun PrefSlider(label: String, initial: Int, min: Int, max: Int, unit: String, zeroLabel: String? = null, onChange: (Int) -> Unit) {
    val ctx = LocalContext.current
    var v by remember { mutableFloatStateOf(initial.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text(if (zeroLabel != null && v.toInt() == 0) zeroLabel else "${v.toInt()} $unit", color = Dim)
        }
        Slider(
            value = v, onValueChange = { v = it }, valueRange = min.toFloat()..max.toFloat(),
            onValueChangeFinished = {
                onChange(v.toInt())
                if (IslandPrefs.enabled(ctx)) IslandService.start(ctx, relayout = true)
            },
            colors = SliderDefaults.colors(thumbColor = Orange, activeTrackColor = Orange),
        )
    }
}
