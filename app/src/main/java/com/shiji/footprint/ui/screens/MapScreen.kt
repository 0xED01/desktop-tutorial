package com.shiji.footprint.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiji.footprint.data.Prefs
import com.shiji.footprint.data.Repository
import com.shiji.footprint.data.TrackPoint
import com.shiji.footprint.service.TrackingService
import com.shiji.footprint.ui.MiniStat
import com.shiji.footprint.ui.RecordController
import com.shiji.footprint.ui.map.FootprintMap
import com.shiji.footprint.ui.map.MapStyles
import com.shiji.footprint.ui.theme.Coral
import com.shiji.footprint.ui.theme.Rose
import com.shiji.footprint.util.Fmt
import com.shiji.footprint.util.LocationHelper
import kotlinx.coroutines.delay

@Composable
fun MapScreen(recorder: RecordController) {
    val ctx = LocalContext.current
    val styleId by Prefs.mapStyle.collectAsStateWithLifecycle()
    val fogEnabled by Prefs.fogEnabled.collectAsStateWithLifecycle()
    val fogAlpha by Prefs.fogAlpha.collectAsStateWithLifecycle()
    val today by Repository.today.collectAsStateWithLifecycle()
    val last by Repository.lastLocation.collectAsStateWithLifecycle()
    val version by Repository.version.collectAsStateWithLifecycle()
    val stats by Repository.stats.collectAsStateWithLifecycle()
    val running by TrackingService.running.collectAsStateWithLifecycle()
    val startedAt by TrackingService.startedAt.collectAsStateWithLifecycle()

    var follow by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    val map = remember {
        FootprintMap(ctx, withFog = true).also {
            it.update(MapStyles.byId(Prefs.mapStyle.value), Prefs.fogEnabled.value, Prefs.fogAlpha.value,
                emptyList(), null, false)
            it.restoreCamera()
        }
    }
    map.onUserGesture = { follow = false }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.view.onResume()
                Lifecycle.Event.ON_PAUSE -> { map.saveCamera(); map.view.onPause() }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            map.saveCamera()
            map.view.onDetach()
        }
    }

    LaunchedEffect(last, follow) {
        val p = last
        if (follow && p != null) map.animateTo(p)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { map.view },
            modifier = Modifier.fillMaxSize(),
            update = {
                @Suppress("UNUSED_EXPRESSION") version
                map.update(MapStyles.byId(styleId), fogEnabled, fogAlpha, today, last, running)
            },
        )

        // 顶部数据卡片
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x33000000)),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
        ) {
            Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("${Fmt.km(stats.todayKm)} km", "今日里程", Modifier.weight(1f))
                MiniStat("${Fmt.area(stats.areaKm2)} km²", "探索面积", Modifier.weight(1f))
                MiniStat("${stats.cities}", "点亮城市", Modifier.weight(1f))
            }
        }

        // 右侧按钮
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundFab(Icons.Rounded.Layers, "图层") { showSheet = true }
            RoundFab(
                if (follow) Icons.Rounded.MyLocation else Icons.Rounded.LocationSearching, "定位",
                highlighted = follow,
            ) {
                follow = true
                last?.let { map.animateTo(it) }
                if (!running) {
                    val ok = LocationHelper.oneShot(ctx) { loc ->
                        Repository.lastLocation.value = TrackPoint(
                            System.currentTimeMillis(), loc.latitude, loc.longitude, loc.accuracy
                        )
                    }
                    if (!ok) Toast.makeText(ctx, "请先授予定位权限并开启定位服务", Toast.LENGTH_SHORT).show()
                }
            }
        }

        RecordButton(
            running = running,
            startedAt = startedAt,
            onClick = {
                if (!running) follow = true
                recorder.toggle()
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
        )
    }

    if (showSheet) MapStyleSheet(onDismiss = { showSheet = false })
}

@Composable
private fun RoundFab(icon: ImageVector, desc: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 6.dp,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, desc,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RecordButton(running: Boolean, startedAt: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha",
    )
    val brush = if (running) Brush.horizontalGradient(listOf(Rose, Coral))
    else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF6E8BFF)))

    Box(
        modifier
            .shadow(14.dp, RoundedCornerShape(32.dp), spotColor = if (running) Rose else MaterialTheme.colorScheme.primary)
            .clip(RoundedCornerShape(32.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 15.dp),
    ) {
        AnimatedContent(running, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "rec") { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White.copy(alpha = pulse.value)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (startedAt > 0) Fmt.clock(now - startedAt) else "00:00:00",
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Rounded.Stop, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("停止", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                } else {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("开始记录", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapStyleSheet(onDismiss: () -> Unit) {
    val styleId by Prefs.mapStyle.collectAsStateWithLifecycle()
    val fogEnabled by Prefs.fogEnabled.collectAsStateWithLifecycle()
    val fogAlpha by Prefs.fogAlpha.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("地图样式", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(230.dp),
                userScrollEnabled = false,
            ) {
                items(MapStyles.all, key = { it.id }) { s ->
                    val selected = s.id == styleId
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { Prefs.setMapStyle(s.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.25f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(s.preview))
                                .border(
                                    if (selected) 2.5.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp),
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.title, fontSize = 13.sp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("探索迷雾", style = MaterialTheme.typography.titleMedium)
                    Text("未去过的地方笼罩在迷雾中", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = fogEnabled, onCheckedChange = Prefs::setFogEnabled)
            }
            if (fogEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("迷雾浓度 ${(fogAlpha * 100).toInt()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = fogAlpha, onValueChange = Prefs::setFogAlpha, valueRange = 0.4f..0.95f)
            }
        }
    }
}
