package com.shiji.footprint.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shiji.footprint.data.Prefs
import com.shiji.footprint.service.TrackingService
import com.shiji.footprint.ui.screens.MapScreen
import com.shiji.footprint.ui.screens.SettingsScreen
import com.shiji.footprint.ui.screens.StatsScreen
import com.shiji.footprint.ui.screens.TimelineScreen

enum class Tab(val label: String, val icon: ImageVector) {
    MAP("地图", Icons.Rounded.Map),
    TIMELINE("足迹", Icons.Rounded.CalendarMonth),
    STATS("统计", Icons.Rounded.BarChart),
    ME("我的", Icons.Rounded.Person),
}

/** 开始/停止记录，并处理权限申请。 */
class RecordController(val toggle: () -> Unit)

private fun granted(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun rememberRecordController(): RecordController {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        TrackingService.start(ctx)
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            if (Build.VERSION.SDK_INT >= 33 && !granted(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else TrackingService.start(ctx)
        } else {
            Toast.makeText(ctx, "需要定位权限才能记录足迹", Toast.LENGTH_LONG).show()
        }
    }
    return remember {
        RecordController {
            when {
                TrackingService.running.value -> TrackingService.stop(ctx)
                !TrackingService.hasLocationPermission(ctx) -> locLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
                Build.VERSION.SDK_INT >= 33 && !granted(ctx, Manifest.permission.POST_NOTIFICATIONS) ->
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> TrackingService.start(ctx)
            }
        }
    }
}

@Composable
fun FootprintRoot() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.MAP) }
    val recorder = rememberRecordController()

    // 进程被系统杀掉后，重新打开应用时自动恢复记录
    LaunchedEffect(Unit) {
        if (Prefs.recording.value && !TrackingService.running.value && TrackingService.hasLocationPermission(ctx)) {
            TrackingService.start(ctx)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            Crossfade(targetState = tab, label = "tab") { t ->
                when (t) {
                    Tab.MAP -> MapScreen(recorder)
                    Tab.TIMELINE -> TimelineScreen()
                    Tab.STATS -> StatsScreen()
                    Tab.ME -> SettingsScreen(recorder)
                }
            }
        }
    }
}

