package com.shiji.footprint.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiji.footprint.R
import com.shiji.footprint.data.Prefs
import com.shiji.footprint.data.RecordMode
import com.shiji.footprint.data.Repository
import com.shiji.footprint.service.TrackingService
import com.shiji.footprint.ui.IconBadge
import com.shiji.footprint.ui.RecordController
import com.shiji.footprint.ui.SectionCard
import com.shiji.footprint.ui.map.MapStyles
import com.shiji.footprint.ui.theme.Amber
import com.shiji.footprint.ui.theme.Coral
import com.shiji.footprint.ui.theme.Indigo
import com.shiji.footprint.ui.theme.Mint
import com.shiji.footprint.ui.theme.Rose
import com.shiji.footprint.util.Fmt
import com.shiji.footprint.util.TrackImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(recorder: RecordController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats by Repository.stats.collectAsStateWithLifecycle()
    val mode by Prefs.recordMode.collectAsStateWithLifecycle()
    val styleId by Prefs.mapStyle.collectAsStateWithLifecycle()
    val running by TrackingService.running.collectAsStateWithLifecycle()
    var showStyles by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val ignoringBattery = remember(refresh) { isIgnoringBattery(ctx) }
    val bgLocation = remember(refresh) { hasBackgroundLocation(ctx) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { Repository.exportGpx(it) } ?: 0 }
                    .getOrElse { -1 }
            }
            Toast.makeText(ctx, if (n >= 0) "已导出 $n 个足迹点" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        Toast.makeText(ctx, "正在导入…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val segs = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { TrackImport.parse(it) } }.getOrNull()
            }
            if (segs.isNullOrEmpty()) {
                Toast.makeText(ctx, "未能读取轨迹，请选择 GPX 或 CSV 文件（CSV 需包含经纬度列）", Toast.LENGTH_LONG).show()
            } else {
                Repository.importSegments(segs) { n ->
                    Toast.makeText(ctx, "导入完成，新增 $n 个足迹点", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp),
    ) {
        // 头部
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Indigo, Mint))),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(92.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("拾迹", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "已陪你走过 ${stats.days} 天 · ${Fmt.km(stats.totalKm)} km",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionCard(title = "记录模式") {
            RecordMode.entries.forEach { m ->
                val sel = m == mode
                Surface(
                    onClick = {
                        Prefs.setRecordMode(m)
                        TrackingService.reload(ctx)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    border = BorderStroke(
                        1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.title, fontWeight = FontWeight.SemiBold)
                            Text(m.desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = recorder.toggle,
                shape = RoundedCornerShape(16.dp),
                color = if (running) Rose.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (running) "停止记录" else "开始记录",
                    color = if (running) Rose else MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "后台保活") {
            SettingRow(
                Icons.Rounded.BatteryChargingFull, Mint, "忽略电池优化",
                "防止息屏后被系统停止",
                trailing = if (ignoringBattery) "已开启" else "去开启",
            ) { requestIgnoreBattery(ctx) }
            if (Build.VERSION.SDK_INT >= 29) {
                SettingRow(
                    Icons.Rounded.LocationOn, Indigo, "后台定位权限",
                    "选择「始终允许」更稳定",
                    trailing = if (bgLocation) "已开启" else "去设置",
                ) { openAppSettings(ctx) }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Amber.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Row(Modifier.padding(12.dp)) {
                    Icon(Icons.Rounded.Lock, null, tint = Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "小米 / 华为 / OPPO / vivo 等手机，请在系统设置中为「拾迹」开启「自启动」、" +
                            "「后台运行：无限制」，并在多任务界面下拉锁定本应用，以保证息屏后持续记录。",
                        fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "地图") {
            SettingRow(Icons.Rounded.Layers, Coral, "地图样式与迷雾", MapStyles.byId(styleId).title) {
                showStyles = true
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "数据") {
            SettingRow(Icons.Rounded.FileUpload, Indigo, "导出 GPX", "备份全部足迹，可导入其他应用") {
                exportLauncher.launch("拾迹足迹_${LocalDate.now()}.gpx")
            }
            SettingRow(Icons.Rounded.FileDownload, Mint, "导入轨迹", "支持 GPX、CSV（含一生足迹等导出格式）") {
                importLauncher.launch(arrayOf("*/*"))
            }
            SettingRow(Icons.Rounded.DeleteOutline, Rose, "清除所有数据", "删除全部足迹，不可恢复") {
                confirmClear = true
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "关于") {
            SettingRow(Icons.Rounded.Cloud, Indigo, "数据仅保存在本机", "足迹不会上传到任何服务器，地名解析除外", trailing = "")
            SettingRow(Icons.Rounded.Info, Mint, "版本", "拾迹 1.1.0 · 地图数据 © 高德 / OpenStreetMap / CARTO", trailing = "")
        }
    }

    if (showStyles) MapStyleSheet(onDismiss = { showStyles = false })
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除所有数据？") },
            text = { Text("将删除全部 ${Fmt.count(stats.points)} 个足迹点、点亮的城市和统计数据。此操作不可恢复，建议先导出 GPX 备份。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    if (TrackingService.running.value) TrackingService.stop(ctx)
                    Repository.clearAll { Toast.makeText(ctx, "已清除", Toast.LENGTH_SHORT).show() }
                }) { Text("清除", color = Rose) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, tint, 38)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!trailing.isNullOrEmpty()) {
            Text(trailing, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null && trailing != "") {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun isIgnoringBattery(ctx: Context): Boolean =
    (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

private fun hasBackgroundLocation(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("BatteryLife")
private fun requestIgnoreBattery(ctx: Context) {
    val intent = if (!isIgnoringBattery(ctx)) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
    } else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    runCatching { ctx.startActivity(intent) }.onFailure { openAppSettings(ctx) }
}

private fun openAppSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
