package com.ebike.dash

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.ebike.dash.databinding.ActivityMainBinding
import com.ebike.dash.databinding.DialogSettingsBinding
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.camera.CameraUpdateFactory
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.maplibre.geojson.Feature
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), LocationSink, NavListener {

    companion object {
        private const val SRC_TRACK = "rd-track"
        private const val SRC_TRACK_LIVE = "rd-track-live"
        private const val LAYER_TRACK = "rd-track"
        private const val LAYER_TRACK_LIVE = "rd-track-live"
        private const val SRC_ROUTE = "rd-route"
        private const val SRC_DEST = "rd-dest"
    }

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var gps: GpsSource
    private lateinit var track: TrackStore
    private lateinit var music: MusicController
    private lateinit var offline: OfflineMaps
    private lateinit var voice: Voice
    private lateinit var nav: NavigationEngine
    private var destPoint: DoubleArray? = null
    private var selecting = false
    private var selectBack: androidx.activity.OnBackPressedCallback? = null
    private var dl: com.ebike.dash.databinding.PanelDownloadBinding? = null
    private var dlPanel: FloatingPanel? = null
    private var musicSheet: MusicSheet? = null
    private val units = Units()
    private val tracker = RideTracker()
    private val ui = Handler(Looper.getMainLooper())

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var nightApplied: Boolean? = null
    private var pal = Palette.LIGHT
    private var demo: DemoSource? = null
    private var lastLoc: Location? = null
    private var lastBearing = 0f
    private lateinit var heading: HeadingSensor
    private var lastHeadingPush = 0L
    private var shownSpeed = 0f
    private var speedAnim: ValueAnimator? = null
    private var speedKmh = 0.0
    private var following = true
    private var trackingListenerAdded = false
    private var lastSavedPos = 0L
    private var demoTrack: TrackStore? = null
    private var lastLiveDraw = 0L
    private var lastPuckPush = 0L
    private var lastNotice = 0L
    private var resumed = false

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (gps.hasPermission()) startGps() else toast("未授予定位权限，可使用演示模式体验")
    }

    // ---------------------------------------------------------------- 生命周期

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupImmersive()

        prefs = Prefs(this)
        gps = GpsSource(this, this)
        heading = HeadingSensor(this) { onHeading(it) }
        tracker.restore(prefs)
        track = TrackStore(File(filesDir, "track.txt")).apply { load() }
        music = MusicController(this) { onMusicChanged(it) }
        voice = Voice(this, prefs).apply { onChanged = { units.taiwanese = taiwanese } }
        nav = NavigationEngine(lifecycleScope, this, units)
        offline = OfflineMaps(this).apply {
            onUpdate = { refreshDownloadPanel() }
            onFinished = { ok, msg ->
                dlPanel?.dismiss()
                toast(if (ok) "离线地图已就绪，没有网络也能看附近的地图" else "离线地图下载失败：${msg ?: "未知原因"}")
            }
        }

        b.mapView.onCreate(savedInstanceState)
        b.mapView.getMapAsync { m ->
            map = m
            setupMap(m)
        }

        b.btnSearch.setOnClickListener { showSearch() }
        b.btnStopNav.setOnClickListener { stopNavigation(true) }
        b.btnSelectCancel.setOnClickListener { endAreaSelection(download = false) }
        b.btnSelectOk.setOnClickListener { endAreaSelection(download = true) }
        b.btnRecenter.setOnClickListener { recenter() }
        b.btnSettings.setOnClickListener { showSettings() }
        b.btnInfo.setOnClickListener { setInfoVisible(!b.infoPanel.isVisible) }
        b.btnMusic.setOnClickListener { setMusicVisible(!b.music.root.isVisible) }
        setupMusicCard()
        b.statsRow.setOnLongClickListener {
            confirmResetTrip(); true
        }

        b.infoPanel.isVisible = prefs.showInfo
        b.music.root.isVisible = prefs.showMusic
        // 所有可点的按钮和卡片使用统一的按压动效
        Motion.pressable(
            b.btnSearch, b.btnRecenter, b.btnInfo, b.btnMusic, b.btnSettings, b.btnStopNav,
            b.music.btnPrev, b.music.btnPlay, b.music.btnNext, b.btnSelectCancel, b.btnSelectOk,
        )
        Motion.pressable(b.music.root, scale = 0.98f)
        applyPalette(if (isNight()) Palette.DARK else Palette.LIGHT)
        applyPrefs()
        b.bottomArea.post { updateTrackingPadding() }
        updateDashboard()
        updateStatus()
        // 启动时状态栏、按钮、底部卡片依次浮现
        Motion.enter(
            listOf(b.statusRow, b.btnSearch, b.btnRecenter, b.btnInfo, b.btnMusic, b.btnSettings) +
                listOf(b.infoPanel, b.music.root, b.speed.root).filter { it.isVisible }
        )

        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        // Android 13+ 需要通知权限，才能显示“正在记录足迹”的通知
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        if (gps.hasPermission()) startGps() else permLauncher.launch(perms.toTypedArray())
    }

    override fun onStart() { super.onStart(); b.mapView.onStart() }
    override fun onResume() {
        super.onResume()
        resumed = true
        b.mapView.onResume()
        // 回到前台：把后台期间的速度、数据和足迹一次补画出来
        updateDashboard()
        drawTrackHistory()
        drawTrackLive(force = true)
        ui.post(statusTicker)
        ui.post(puckKeeper)
        heading.start()
        music.start()
        if (demo == null) startGps()
    }
    override fun onPause() {
        super.onPause()
        resumed = false
        b.mapView.onPause()
        ui.removeCallbacks(statusTicker)
        ui.removeCallbacks(puckKeeper)
        heading.stop()
        music.stop()
        tracker.save(prefs)
        track.flush()
    }
    override fun onStop() { super.onStop(); b.mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); b.mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        b.mapView.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        demo?.stop()
        gps.stop()
        RecordingService.stop(this)
        voice.shutdown()
        track.endSegment()
        track.close()
        b.mapView.onDestroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersive()
    }

    private fun setupImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        ViewCompat.setOnApplyWindowInsetsListener(b.overlay) { v, insets ->
            val c = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(c.left, 0, c.right, c.bottom)
            // 状态行和系统状态栏差不多高：在刘海区域内略靠下，不贴着屏幕顶边
            val d = resources.displayMetrics.density
            b.topArea.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = (c.top / 2 - 10 * d).toInt().coerceAtLeast(0) + (14 * d).toInt()
            }
            insets
        }
    }

    private fun startGps() {
        if (demo != null || !gps.hasPermission()) return
        gps.start()
        updateRecordingService()
        if (!gps.gpsEnabled()) {
            FloatingPanel.confirm(
                this, "GPS 未开启", "Driver 需要 GPS 来测速和记录足迹，请在系统设置中打开位置信息。", "去设置",
            ) { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        }
    }

    // ---------------------------------------------------------------- 布局

    /** 让定位箭头落在码表上方的空白区域 */
    private fun updateTrackingPadding() {
        val lc = map?.locationComponent ?: return
        if (!lc.isLocationComponentActivated) return
        // 只按速度卡片的高度留白，展开数据面板时地图不会跳动
        val d = resources.displayMetrics.density
        lc.paddingWhileTracking(doubleArrayOf(0.0, b.root.height * 0.12, 0.0, b.speed.root.height + 60.0 * d))
    }

    // ---------------------------------------------------------------- 地图

    private fun setupMap(m: MapLibreMap) {
        val d = resources.displayMetrics.density
        m.uiSettings.apply {
            isLogoEnabled = false
            // 指南针放在右侧按钮列的最上方，与圆形按钮同尺寸、同配色
            isCompassEnabled = true
            compassGravity = Gravity.TOP or Gravity.END
            // 图标 50dp（46dp 圆 + 2dp 投影），中心与下方 46dp 按钮对齐
            setCompassMargins(0, (106 * d).toInt(), (16 * d).toInt(), 0)
            setCompassImage(compassDrawable())
            // 地图版权信息放在菜单底部，不在地图上显示 (i) 按钮
            isAttributionEnabled = false
        }
        m.cameraPosition = CameraPosition.Builder()
            .target(LatLng(prefs.lastLat, prefs.lastLon))
            .zoom(15.5)
            .build()
        m.addOnMapLongClickListener { p ->
            if (!selecting) onMapLongPress(p.latitude, p.longitude)
            !selecting
        }
        m.addOnCameraMoveListener { if (selecting) updateSelectionInfo() }
        loadStyle(force = true)
    }

    private fun compassDrawable() = ContextCompat.getDrawable(
        this, if (pal === Palette.DARK) R.drawable.ic_compass_dark else R.drawable.ic_compass_light,
    )!!

    private fun isNight(): Boolean = when (prefs.theme) {
        1 -> false
        2 -> true
        else -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 19 || it < 6 }
    }

    private fun loadStyle(force: Boolean = false) {
        val m = map ?: return
        val night = isNight()
        if (!force && night == nightApplied) return
        nightApplied = night
        applyPalette(if (night) Palette.DARK else Palette.LIGHT)
        style = null
        m.setStyle(Style.Builder().fromUri(pal.mapStyle)) { s ->
            style = s
            localizeLabels(s)
            hideBoundaries(s)
            applyLabelVisibility(s)
            addTrackLayers(s)
            addRouteLayers(s)
            enableLocationComponent(s)
            drawTrackHistory()
            drawTrackLive(force = true)
            nav.route?.let { drawRoute(it.coords) }
            destPoint?.let { drawDest(it[0], it[1]) }
        }
    }

    /** 地名优先显示中文 */
    private fun localizeLabels(s: Style) {
        val zh = Expression.coalesce(
            Expression.get("name:zh-Hans"),
            Expression.get("name:zh"),
            Expression.get("name"),
        )
        for (layer in s.layers) {
            if (layer !is SymbolLayer) continue
            val tf = layer.textField
            if (tf.isNull) continue
            if (tf.toString().contains("name")) layer.setProperties(PropertyFactory.textField(zh))
        }
    }

    /** 去掉行政区划边界（地图上的虚线） */
    private fun hideBoundaries(s: Style) {
        for (layer in s.layers) {
            if (layer is LineLayer && layer.sourceLayer == "boundary") {
                layer.setProperties(PropertyFactory.visibility(Property.NONE))
            }
        }
    }

    /** 菜单里的“显示地名”：隐藏或显示所有文字和图标标注 */
    private fun applyLabelVisibility(s: Style) {
        val v = if (prefs.showLabels) Property.VISIBLE else Property.NONE
        for (layer in s.layers) if (layer is SymbolLayer) layer.setProperties(PropertyFactory.visibility(v))
    }

    // ---------------------------------------------------------------- 足迹

    /** 足迹线：历史足迹淡一些，本次骑行的更实；都在道路之上、文字之下 */
    private fun addTrackLayers(s: Style) {
        s.addSource(GeoJsonSource(SRC_TRACK))
        s.addSource(GeoJsonSource(SRC_TRACK_LIVE))
        val width = Expression.interpolate(
            Expression.exponential(1.4f), Expression.zoom(),
            Expression.stop(10, 1.5f), Expression.stop(14, 3f), Expression.stop(18, 8f),
        )
        fun layer(id: String, src: String, opacity: Float) = LineLayer(id, src).withProperties(
            PropertyFactory.lineColor(pal.trackColor),
            PropertyFactory.lineOpacity(opacity),
            PropertyFactory.lineWidth(width),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.visibility(if (prefs.showTrack) Property.VISIBLE else Property.NONE),
        )
        val history = layer(LAYER_TRACK, SRC_TRACK, 0.45f)
        val live = layer(LAYER_TRACK_LIVE, SRC_TRACK_LIVE, 0.9f)
        val lastShape = s.layers.lastOrNull { it !is SymbolLayer }?.id
        if (lastShape != null) {
            s.addLayerAbove(history, lastShape)
            s.addLayerAbove(live, history.id)
        } else {
            s.addLayer(history)
            s.addLayer(live)
        }
    }

    private fun lineOf(seg: List<DoubleArray>) = LineString.fromLngLats(seg.map { Point.fromLngLat(it[1], it[0]) })

    /** 历史足迹（不含正在记录的这一段） */
    private fun drawTrackHistory() {
        val src = style?.getSourceAs<GeoJsonSource>(SRC_TRACK) ?: return
        val cur = track.currentSegment
        val lines = track.segments.filter { it !== cur && it.size >= 2 }.map { lineOf(it) }
        if (lines.isEmpty()) src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        else src.setGeoJson(MultiLineString.fromLineStrings(lines))
    }

    /** 本次骑行（演示模式下画演示轨迹），最多每秒刷新一次 */
    private fun drawTrackLive(force: Boolean = false) {
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && now - lastLiveDraw < 1000) return
        lastLiveDraw = now
        val src = style?.getSourceAs<GeoJsonSource>(SRC_TRACK_LIVE) ?: return
        val seg = (demoTrack ?: track).currentSegment
        if (seg == null || seg.size < 2) src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        else src.setGeoJson(lineOf(seg))
    }

    private fun setTrackVisible(show: Boolean) {
        prefs.showTrack = show
        val v = PropertyFactory.visibility(if (show) Property.VISIBLE else Property.NONE)
        style?.getLayer(LAYER_TRACK)?.setProperties(v)
        style?.getLayer(LAYER_TRACK_LIVE)?.setProperties(v)
    }

    /** 记录足迹：有速度、定位精度够好时才记，停车抖动不会画出乱线 */
    private fun recordTrack(loc: Location) {
        val store = demoTrack ?: track
        if (speedKmh < 3 || loc.accuracy > 25) return
        if (store.add(loc.latitude, loc.longitude, System.currentTimeMillis())) {
            if (store === track) drawTrackHistory()
        }
        drawTrackLive()
    }

    // ---------------------------------------------------------------- 导航路线

    private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    /** 导航路线：前景色实线 + 底色描边，画在足迹之上、文字之下 */
    private fun addRouteLayers(s: Style) {
        s.addSource(GeoJsonSource(SRC_ROUTE))
        s.addSource(GeoJsonSource(SRC_DEST))
        val width = Expression.interpolate(
            Expression.exponential(1.5f), Expression.zoom(),
            Expression.stop(10, 3f), Expression.stop(14, 6f), Expression.stop(18, 15f),
        )
        val casingWidth = Expression.interpolate(
            Expression.exponential(1.5f), Expression.zoom(),
            Expression.stop(10, 5f), Expression.stop(14, 9f), Expression.stop(18, 21f),
        )
        val casing = LineLayer("rd-route-casing", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(hex(pal.surface)),
            PropertyFactory.lineWidth(casingWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        val line = LineLayer("rd-route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(pal.trackColor),
            PropertyFactory.lineWidth(width),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        val dest = CircleLayer("rd-dest", SRC_DEST).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(pal.trackColor),
            PropertyFactory.circleStrokeColor(hex(pal.surface)),
            PropertyFactory.circleStrokeWidth(4f),
        )
        s.addLayerAbove(casing, LAYER_TRACK_LIVE)
        s.addLayerAbove(line, casing.id)
        s.addLayer(dest)
    }

    private fun drawRoute(coords: List<DoubleArray>?) {
        val src = style?.getSourceAs<GeoJsonSource>(SRC_ROUTE) ?: return
        if (coords == null || coords.size < 2) src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        else src.setGeoJson(LineString.fromLngLats(coords.map { Point.fromLngLat(it[1], it[0]) }))
    }

    private fun drawDest(lat: Double?, lon: Double?) {
        val src = style?.getSourceAs<GeoJsonSource>(SRC_DEST) ?: return
        if (lat == null || lon == null) src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        else src.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(s: Style) {
        val m = map ?: return
        // 简约箭头，尖端指向手机前方 / 行进方向
        val opts = LocationComponentOptions.builder(this)
            .gpsDrawable(if (pal === Palette.DARK) R.drawable.ic_puck_dark else R.drawable.ic_puck_light)
            .elevation(0f)
            .enableStaleState(false)
            .accuracyColor(pal.dim)
            .accuracyAlpha(0.08f)
            .trackingGesturesManagement(true)
            .build()
        val lc = m.locationComponent
        if (!lc.isLocationComponentActivated) {
            lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, s)
                    .locationComponentOptions(opts)
                    .useDefaultLocationEngine(false)
                    .build()
            )
            lc.isLocationComponentEnabled = true
            lc.renderMode = RenderMode.GPS
        } else {
            // 换地图样式时组件会自己重建图层，这里只换箭头配色，避免重复初始化把箭头弄丢
            lc.applyStyle(opts)
        }
        if (!trackingListenerAdded) {
            trackingListenerAdded = true
            lc.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() {
                    following = false
                    b.btnRecenter.alpha = 0.55f
                }
                override fun onCameraTrackingChanged(currentMode: Int) {}
            })
        }
        pushPuck()
        // 组件的图层重建可能晚于这里完成，下一帧和稍后再各放一次，确保箭头一定显示
        b.mapView.post { pushPuck() }
        b.mapView.postDelayed({ pushPuck() }, 400)
        if (following) follow()
        b.bottomArea.post { updateTrackingPadding() }
    }

    /** 把箭头放到最新位置（同一位置重复放不会让地图移动） */
    private fun pushPuck(loc: Location? = lastLoc) {
        val l = loc ?: return
        val lc = map?.locationComponent ?: return
        if (!lc.isLocationComponentActivated || style == null) return
        lc.forceLocationUpdate(l)
        lastPuckPush = android.os.SystemClock.uptimeMillis()
    }

    /** 保险：GPS 信号弱、很久没有新定位时，每隔一会儿重新放一次箭头 */
    private val puckKeeper = object : Runnable {
        override fun run() {
            if (android.os.SystemClock.uptimeMillis() - lastPuckPush > 1500) pushPuck()
            ui.postDelayed(this, 1000)
        }
    }

    private fun follow() {
        val lc = map?.locationComponent ?: return
        if (!lc.isLocationComponentActivated) return
        following = true
        b.btnRecenter.alpha = 1f
        lc.cameraMode = CameraMode.TRACKING_GPS
        lc.zoomWhileTracking(if (nav.active) 17.0 else 16.5, 900)
        lc.tiltWhileTracking(if (nav.active) 50.0 else 40.0, 900)
        updateTrackingPadding()
    }

    private fun recenter() {
        if (lastLoc == null) {
            toast("正在等待 GPS 定位…")
            return
        }
        follow()
    }

    /** 把浅色/深色配色应用到所有浮层控件，并让菜单、对话框跟随 */
    private fun applyPalette(p: Palette) {
        pal = p
        // 已打开的菜单 / 播放器 / 提示跟着一起换配色（放到下一帧，避免在点击回调里替换正在点击的控件）
        b.root.post { FloatingPanel.palette = p }
        val surface = ColorStateList.valueOf(p.surface)
        b.infoPanel.backgroundTintList = surface
        // 不透明：半透明卡片会透出自身阴影，看起来深浅不均
        b.speed.root.backgroundTintList = surface
        b.music.root.backgroundTintList = surface
        // 结束导航：反色实心圆；分隔线用浅灰
        b.btnStopNav.backgroundTintList = ColorStateList.valueOf(p.onSurface)
        b.btnStopNav.imageTintList = surface
        b.navDivider.setBackgroundColor(p.chip)
        // 框选离线地图：框外压暗，卡片与按钮同其它悬浮卡片
        b.selectFrame.setColors(if (p.dark) Color.argb(140, 0, 0, 0) else Color.argb(90, 0, 0, 0), p.onSurface)
        b.selectCard.backgroundTintList = surface
        b.btnSelectCancel.backgroundTintList = ColorStateList.valueOf(p.chip)
        b.btnSelectCancel.setTextColor(p.onSurface)
        b.btnSelectOk.backgroundTintList = ColorStateList.valueOf(p.onSurface)
        b.btnSelectOk.setTextColor(p.surface)
        for (btn in listOf(b.btnSearch, b.btnRecenter, b.btnInfo, b.btnMusic, b.btnSettings)) {
            btn.backgroundTintList = surface
            btn.imageTintList = ColorStateList.valueOf(p.onSurface)
        }
        // 迷你播放器：切歌按钮透明底，播放按钮反色实心圆
        for (btn in listOf(b.music.btnPrev, b.music.btnNext)) {
            btn.backgroundTintList = surface
            btn.imageTintList = ColorStateList.valueOf(p.onSurface)
        }
        b.music.btnPlay.backgroundTintList = ColorStateList.valueOf(p.onSurface)
        b.music.btnPlay.imageTintList = surface
        b.music.eq.setColor(p.onSurface)
        b.speed.energy.setColors(p.onSurface, p.chip)
        tintTexts(b.overlay, p)
        tintTexts(b.selectCard, p)
        b.btnSelectCancel.setTextColor(p.onSurface)
        b.btnSelectOk.setTextColor(p.surface)
        onMusicChanged(if (::music.isInitialized) music.now else null)
        map?.uiSettings?.setCompassImage(compassDrawable())
        updateInfoButton(b.infoPanel.isVisible, animate = false)
        updateMusicButton(b.music.root.isVisible, animate = false)
        b.topScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(ColorUtils.setAlphaComponent(p.surface, 200), ColorUtils.setAlphaComponent(p.surface, 0)),
        )
        b.gpsDots.setColors(p.onSurface, ColorUtils.setAlphaComponent(p.onSurface, 50))
        lastLoc?.let { updateGpsIndicator(it) }
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = p === Palette.LIGHT
    }

    private fun tintTexts(v: View, p: Palette) {
        if (v is TextView) v.setTextColor(if (v.tag == "label") p.dim else p.onSurface)
        if (v is ViewGroup) for (i in 0 until v.childCount) tintTexts(v.getChildAt(i), p)
    }

    // ---------------------------------------------------------------- 定位与码表

    override fun onLocation(loc: Location) {
        if (demo != null && loc.provider != "demo") return

        val prev = lastLoc
        val raw = when {
            loc.hasSpeed() -> loc.speed * 3.6
            prev != null && loc.time > prev.time ->
                Geo.distance(prev.latitude, prev.longitude, loc.latitude, loc.longitude) /
                    ((loc.time - prev.time) / 1000.0) * 3.6
            else -> 0.0
        }
        // 平滑 + 静止去抖
        speedKmh = if (raw < 1.5) 0.0 else speedKmh * 0.3 + raw * 0.7

        // 慢速时 GPS 方向不可靠，保持上一个方向
        val fixed = Location(loc)
        // 骑行时用 GPS 行进方向；停车或慢速时用指南针（手机朝向）
        lastBearing = if (speedKmh > 4 && loc.hasBearing()) loc.bearing else heading.heading ?: lastBearing
        fixed.bearing = lastBearing
        if (prev == null) heading.updateDeclination(loc.latitude, loc.longitude, loc.altitude)
        lastLoc = fixed

        tracker.update(fixed, speedKmh)
        recordTrack(fixed)
        nav.onLocation(fixed)
        updateRecordingNotice()
        if (System.currentTimeMillis() - lastSavedPos > 30_000) {
            lastSavedPos = System.currentTimeMillis()
            prefs.lastLat = fixed.latitude
            prefs.lastLon = fixed.longitude
            tracker.save(prefs)
        }
        // 在后台时只记录，不刷新界面（省电）；回到前台时一次补上
        if (!resumed) return
        pushPuck(fixed)
        updateDashboard()
        updateGpsIndicator(fixed)

        if (prev == null && following) follow()
    }

    // ---------------------------------------------------------------- 后台记录

    /** 有定位权限、开启了“锁屏后继续记录”、不在演示模式时，常驻通知保持记录 */
    private fun updateRecordingService() {
        if (prefs.backgroundRecord && demo == null && gps.hasPermission()) {
            RecordingService.start(this, recordingText())
        } else {
            RecordingService.stop(this)
        }
    }

    private fun recordingText() =
        String.format(Locale.US, "本次 %.1f %s · 点击返回 Driver", units.bigDist(tracker.tripMeters), units.distUnit)

    /** 通知上的里程，最多每 10 秒更新一次 */
    private fun updateRecordingNotice() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastNotice < 10_000) return
        lastNotice = now
        RecordingService.update(this, recordingText())
    }

    /** 指南针更新：慢速时转动箭头（和跟随模式下的地图） */
    private fun onHeading(h: Float) {
        val loc = lastLoc ?: return
        if (speedKmh > 4) return
        val now = android.os.SystemClock.uptimeMillis()
        val diff = Math.abs(((h - lastBearing + 540) % 360) - 180)
        if (diff < 2f || now - lastHeadingPush < 120) return
        lastHeadingPush = now
        lastBearing = h
        val l = Location(loc).apply { bearing = h }
        lastLoc = l
        pushPuck(l)
    }

    private fun updateDashboard() {
        showSpeed(units.speed(speedKmh).toFloat())
        b.tvTrip.text = String.format(Locale.US, "%.2f", units.bigDist(tracker.tripMeters))
        b.lblTrip.text = "里程 ${units.distUnit}"
        val s = tracker.movingMs / 1000
        b.tvTime.text = if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
        b.tvAvg.text = String.format(Locale.US, "%.1f", units.speed(tracker.avgKmh))
        b.tvMax.text = String.format(Locale.US, "%.1f", units.speed(tracker.maxKmh))
    }

    /** 数字平滑滚动到新速度 */
    private fun showSpeed(v: Float) {
        speedAnim?.cancel()
        speedAnim = ValueAnimator.ofFloat(shownSpeed, v).apply {
            duration = 450
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shownSpeed = it.animatedValue as Float
                b.speed.tvSpeed.text = shownSpeed.roundToInt().toString()
                b.speed.energy.level = shownSpeed / energyFullScale()
            }
            start()
        }
    }


    /** 能量条满格对应的速度：40 km/h（约 25 mph） */
    private fun energyFullScale(): Float = if (units.imperial) 25f else 40f

    /** 骑行数据面板，默认隐藏 */
    private fun setInfoVisible(show: Boolean) {
        prefs.showInfo = show
        Motion.setVisible(b.infoPanel, show)
        updateInfoButton(show)
    }

    /** 面板打开时按钮反色，表示“已开启” */
    private fun updateInfoButton(on: Boolean, animate: Boolean = true) {
        Motion.tint(b.btnInfo, if (on) pal.onSurface else pal.surface, if (on) pal.surface else pal.onSurface, animate)
    }

    private fun setNavSectionVisible(show: Boolean) {
        b.navSection.isVisible = show
        b.navDivider.isVisible = show
    }

    private fun updateGpsIndicator(loc: Location) {
        val demoMode = loc.provider == "demo"
        val level = if (demoMode) SignalDotsView.COUNT else SignalDotsView.levelFor(loc.accuracy)
        b.gpsDots.level = level
        // 只剩 1 格时亮点变红提醒
        b.gpsDots.setColors(if (level <= 1) pal.danger else pal.onSurface, ColorUtils.setAlphaComponent(pal.onSurface, 50))
        b.tvGps.text = if (demoMode) "演示" else "GPS"
    }

    private val statusTicker = object : Runnable {
        override fun run() {
            updateStatus()
            if (prefs.theme == 0) loadStyle() // 自动模式下到点切换日/夜
            ui.postDelayed(this, 15_000)
        }
    }

    private fun updateStatus() {
        b.tvClock.text = clockFmt.format(Date())
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bat != null) {
            val level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val charging = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (level >= 0) b.tvBattery.text = "${if (charging) "充电 " else ""}${level * 100 / scale}%"
        }
    }

    private fun currentPos(): DoubleArray? = lastLoc?.let { doubleArrayOf(it.latitude, it.longitude) }

    // ---------------------------------------------------------------- 导航

    private fun showSearch() {
        val center = currentPos() ?: map?.cameraPosition?.target?.let { doubleArrayOf(it.latitude, it.longitude) }
            ?: doubleArrayOf(prefs.lastLat, prefs.lastLon)
        SearchSheet(
            this, prefs, units, center,
            navDestName = if (nav.active) nav.destName else null,
            onPick = { startNavigation(it.lat, it.lon, it.name) },
            onEndNav = { stopNavigation(true) },
        ).show()
    }

    /** 长按地图：先查出这个位置的地址，再选择“去这里”或收藏 */
    private fun onMapLongPress(lat: Double, lon: Double) {
        destPoint = doubleArrayOf(lat, lon)
        drawDest(lat, lon)
        lifecycleScope.launch {
            val place = try {
                withContext(Dispatchers.IO) { Net.reverse(lat, lon) }
            } catch (_: Exception) {
                null
            }
            val p = Place(place?.name ?: "地图上的位置", place?.address ?: "", lat, lon)
            val dist = currentPos()?.let { units.navDist(Geo.distance(it[0], it[1], lat, lon)) }
            val title = listOfNotNull(p.name, dist).joinToString("  ·  ")
            var picked = false
            FloatingPanel.actions(this@MainActivity, title, listOf("去这里", "收藏为家", "收藏为公司")) { i ->
                picked = true
                when (i) {
                    0 -> { prefs.addHistory(p); startNavigation(p.lat, p.lon, p.name) }
                    1 -> { prefs.putPlace(Prefs.HOME, p); toast("已设为家"); clearPendingDest() }
                    2 -> { prefs.putPlace(Prefs.WORK, p); toast("已设为公司"); clearPendingDest() }
                }
            }
            // 没选“去这里”就关掉时，把临时标记去掉
            ui.postDelayed({ if (!picked && !nav.active) clearPendingDest() }, 60_000)
        }
    }

    private fun clearPendingDest() {
        if (nav.active) return
        destPoint = null
        drawDest(null, null)
    }

    private fun startNavigation(lat: Double, lon: Double, name: String) {
        val from = currentPos()
        if (from == null) {
            toast("还没有定位，请稍候或开启演示模式")
            return
        }
        destPoint = doubleArrayOf(lat, lon)
        drawDest(lat, lon)
        toast("正在规划骑行路线…")
        nav.start(from[0], from[1], lat, lon, name)
    }

    private fun stopNavigation(byUser: Boolean) {
        nav.stop()
        destPoint = null
        drawRoute(null)
        drawDest(null, null)
        setNavSectionVisible(false)
        if (byUser) voice.speak("导航已结束")
        if (following) follow()
    }

    override fun onRouteReady(route: Route, reroute: Boolean) {
        drawRoute(route.coords)
        setNavSectionVisible(true)
        if (!reroute) {
            voice.speak("开始导航，全程${units.spokenDist(route.length)}")
            follow()
        }
        updateNavInfo(route.length)
    }

    override fun onRouteError(msg: String) {
        if (!nav.active) {
            destPoint = null
            drawDest(null, null)
        }
        toast("路线规划失败：$msg（导航需要网络）")
    }

    override fun onProgress(p: NavProgress) {
        if (resumed) nav.route?.let { drawRoute(it.remainingCoords(p.snap)) }
        updateNavInfo(p.remaining)
    }

    private fun updateNavInfo(remaining: Double) {
        // 用当前均速估算到达时间，最低按 15 km/h
        val v = maxOf(tracker.avgKmh, 15.0) / 3.6
        val sec = (remaining / v).toLong()
        b.tvRemain.text = units.navDist(remaining).replace(" ", "")
        b.tvEtaMin.text = if (sec >= 3600) "${sec / 3600}时${sec / 60 % 60}分" else "${(sec / 60).coerceAtLeast(1)}分钟"
        b.tvEta.text = clockFmt.format(Date(System.currentTimeMillis() + sec * 1000))
    }

    override fun onSpeak(text: String) = voice.speak(text)

    override fun onArrived() {
        toast("已到达目的地")
        stopNavigation(false)
    }

    /** 选择播报声音：列出手机上的中文语音（台湾在前），选中后试听 */
    private fun pickVoice() {
        val opts = voice.options()
        val hasTaiwan = opts.any { it.taiwan }
        val items = mutableListOf("自动（优先台湾口音）")
        items += opts.map { it.label }
        if (!hasTaiwan) items += "安装台湾口音语音…"
        val title = if (hasTaiwan) "选择播报声音（选后试听）" else "手机里还没有台湾口音的语音"
        FloatingPanel.actions(this, title, items) { i ->
            when {
                i == 0 -> { prefs.voiceName = null; voice.apply(null); voice.preview() }
                i <= opts.size -> {
                    val o = opts[i - 1]
                    prefs.voiceName = o.name
                    voice.apply(o.name)
                    voice.preview()
                }
                else -> FloatingPanel.confirm(
                    this,
                    "安装台湾口音语音",
                    "在系统设置的“文字转语音”里，把首选引擎设为“Google 语音服务”，再在其中下载“中文（台湾）”语音包，回到 Driver 选择即可。",
                    positive = "去设置",
                ) {
                    try {
                        startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                    } catch (_: Exception) {
                        toast("请在系统设置中搜索“文字转语音”")
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 音乐

    private fun setupMusicCard() {
        val m = b.music
        m.btnPlay.setOnClickListener { music.playPause() }
        m.btnPrev.setOnClickListener { music.previous() }
        m.btnNext.setOnClickListener { music.next() }
        m.root.setOnClickListener { openMusicSheet() }
    }

    private fun setMusicVisible(show: Boolean) {
        prefs.showMusic = show
        Motion.setVisible(b.music.root, show)
        updateMusicButton(show)
        // 第一次打开时，引导开启“通知使用权”以显示歌名
        if (show && !music.hasAccess() && !prefs.askedMusicAccess) {
            prefs.askedMusicAccess = true
            askMusicAccess()
        }
    }

    private fun updateMusicButton(on: Boolean, animate: Boolean = true) {
        Motion.tint(b.btnMusic, if (on) pal.onSurface else pal.surface, if (on) pal.surface else pal.onSurface, animate)
    }

    private fun openMusicSheet() {
        musicSheet = MusicSheet(this, music) { askMusicAccess() }.also { it.show() }
    }

    private fun onMusicChanged(np: NowPlaying?) {
        val m = b.music
        val access = music.hasAccess()
        Motion.setText(m.tvTitle, when {
            np != null -> np.title.ifBlank { "未知歌曲" }
            !access -> "Apple Music"
            else -> "未在播放"
        })
        Motion.setText(m.tvArtist, when {
            np != null -> np.artist.ifBlank { "Apple Music" }
            !access -> "点击开启歌曲信息显示"
            else -> "点 ▶ 继续播放"
        })
        Motion.swapIcon(m.btnPlay, if (np?.playing == true) R.drawable.ic_pause else R.drawable.ic_play)
        m.eq.setPlaying(np?.playing == true)
        musicSheet?.takeIf { it.isShowing }?.update(np)
    }

    private fun askMusicAccess() {
        FloatingPanel.confirm(
            this,
            "显示正在播放的歌曲",
            "开启「通知使用权」后，Driver 才能读取 Apple Music 正在播放的歌名和进度。\n\n" +
                "Driver 不会读取或保存你的任何通知内容。不开启也可以用播放、暂停和切歌。",
            positive = "去开启",
            negative = "暂不",
        ) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                toast("请在系统设置中搜索“通知使用权”")
            }
        }
    }

    // ---------------------------------------------------------------- 演示模式

    private fun toggleDemo() {
        val d = demo
        if (d != null) {
            d.stop()
            demo = null
            demoTrack = null
            drawTrackLive(force = true)
            speedKmh = 0.0
            lastLoc = null
            toast("演示模式已关闭")
            startGps()
            updateDashboard()
            return
        }
        val start = currentPos() ?: map?.cameraPosition?.target?.let { doubleArrayOf(it.latitude, it.longitude) }
            ?: doubleArrayOf(prefs.lastLat, prefs.lastLon)
        gps.stop()
        RecordingService.stop(this)
        // 演示轨迹只画在屏幕上，不写进真实足迹
        demoTrack = TrackStore(null)
        demo = DemoSource(start[0], start[1], this) { nav.route }.also { it.start() }
        toast("演示模式：模拟骑行中（长按地图可模拟导航）")
        follow()
    }

    // ---------------------------------------------------------------- 设置

    private fun applyPrefs() {
        units.imperial = prefs.imperial
        voice.enabled = prefs.voice
        b.speed.tvUnit.text = units.speedUnit
        updateDashboard()
    }

    private fun showSettings() {
        FloatingPanel(this) { inf, panel -> bindSettings(inf, panel) }.show()
    }

    /** 菜单内容；切换浅色 / 深色时会用新配色重新生成 */
    private fun bindSettings(inf: LayoutInflater, dlg: FloatingPanel): View {
        val sb = DialogSettingsBinding.inflate(inf)
        Motion.pressable(sb.btnClearTrack, sb.btnMusicAccess, sb.btnDemo, sb.btnResetTrip, sb.btnVoicePick)

        sb.swLabels.isChecked = prefs.showLabels
        sb.swLabels.setOnCheckedChangeListener { _, c ->
            prefs.showLabels = c
            style?.let { applyLabelVisibility(it) }
        }

        val themeIds = listOf(sb.tAuto.id, sb.tDay.id, sb.tNight.id)
        sb.groupTheme.check(themeIds[prefs.theme])
        sb.groupTheme.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            prefs.theme = themeIds.indexOf(id)
            loadStyle()
        }

        sb.swVoice.isChecked = prefs.voice
        sb.swVoice.setOnCheckedChangeListener { _, c -> prefs.voice = c; applyPrefs() }
        sb.btnVoicePick.text = "播报声音：${voice.currentLabel()}"
        sb.btnVoicePick.setOnClickListener {
            dlg.dismiss()
            pickVoice()
        }
        sb.swBackground.isChecked = prefs.backgroundRecord
        sb.swBackground.setOnCheckedChangeListener { _, c ->
            prefs.backgroundRecord = c
            updateRecordingService()
        }
        sb.swTrack.isChecked = prefs.showTrack
        sb.swTrack.setOnCheckedChangeListener { _, c -> setTrackVisible(c) }
        sb.tvTrack.text = String.format(
            Locale.US, "已记录 %.1f %s，共 %d 段",
            units.bigDist(track.totalMeters), units.distUnit, track.segments.count { it.size >= 2 },
        )
        sb.btnClearTrack.setOnClickListener {
            dlg.dismiss()
            confirmClearTrack()
        }
        // 离线地图
        if (offline.downloading) {
            sb.tvOffline.text = "正在下载离线地图 ${(offline.progress * 100).toInt()}%"
        } else {
            sb.tvOffline.text = "下载的是最精细的一级（全部道路、建筑、地名），没有网络时也能显示。浏览过的地方也会自动缓存。"
            offline.summary { areas, bytes ->
                if (areas > 0) {
                    sb.tvOffline.text = String.format(Locale.US, "已下载 %d 个区域，约 %.0f MB。没有网络时也能显示。", areas, bytes / 1048576.0)
                    sb.btnOfflineDelete.isVisible = true
                }
            }
        }
        Motion.pressable(sb.btnOfflineDownload, sb.btnOfflineDelete)
        sb.btnOfflineDownload.setOnClickListener {
            dlg.dismiss()
            if (offline.downloading) showDownloadPanel() else startAreaSelection()
        }
        sb.btnOfflineDelete.setOnClickListener {
            dlg.dismiss()
            FloatingPanel.confirm(this, "删除离线地图？", "删除后，没有网络时将无法显示这些地方的地图。", "删除") {
                offline.deleteAll { toast("离线地图已删除") }
            }
        }

        sb.btnMusicAccess.text = if (music.hasAccess()) "歌名显示：已开启" else "开启歌名显示"
        sb.btnMusicAccess.setOnClickListener {
            dlg.dismiss()
            if (music.hasAccess()) {
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
            } else {
                askMusicAccess()
            }
        }
        sb.swImperial.isChecked = prefs.imperial
        sb.swImperial.setOnCheckedChangeListener { _, c -> prefs.imperial = c; applyPrefs() }

        sb.tvOdo.text = String.format(
            Locale.US, "总里程 %.1f %s  ·  版本 %s\n地图数据 © OpenStreetMap 贡献者 · OpenFreeMap",
            units.bigDist(tracker.odoMeters), units.distUnit, BuildConfigCompat.versionName(this),
        )
        sb.btnDemo.text = if (demo == null) "演示模式" else "关闭演示模式"
        sb.btnDemo.setOnClickListener {
            dlg.dismiss()
            toggleDemo()
        }
        sb.btnResetTrip.setOnClickListener {
            dlg.dismiss()
            confirmResetTrip()
        }
        return sb.root
    }

    // ---------------------------------------------------------------- 离线地图

    /** 进入框选：自动框住常骑的范围（足迹 + 当前位置），可拖动缩放地图调整 */
    private fun startAreaSelection() {
        val m = map ?: return
        selecting = true
        following = false
        m.locationComponent.takeIf { it.isLocationComponentActivated }?.cameraMode = CameraMode.NONE
        m.uiSettings.isCompassEnabled = false
        for (v in listOf(b.topArea, b.fabColumn, b.bottomArea)) {
            v.animate().alpha(0f).setDuration(Motion.SHORT).setInterpolator(Motion.EASE)
                .withEndAction { v.visibility = View.INVISIBLE }.start()
        }
        b.selectOverlay.alpha = 0f
        b.selectOverlay.isVisible = true
        b.selectOverlay.animate().alpha(1f).setDuration(Motion.STANDARD).setInterpolator(Motion.EASE).start()
        Motion.panelIn(View(this), b.selectCard)
        selectBack = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = endAreaSelection(download = false)
        }.also { onBackPressedDispatcher.addCallback(it) }

        b.selectOverlay.post {
            val d = resources.displayMetrics.density
            val f = b.selectFrame
            f.setInsets(20 * d, 64 * d, 20 * d, b.selectCard.height + 28 * d + 20 * d)
            val pad = intArrayOf(
                (f.insetLeft + 8 * d).toInt(), (f.insetTop + 8 * d).toInt(),
                (f.insetRight + 8 * d).toInt(), (f.insetBottom + 8 * d).toInt(),
            )
            val cam = m.getCameraForLatLngBounds(autoSelectionBounds(), pad, 0.0, 0.0)
            if (cam != null) m.easeCamera(CameraUpdateFactory.newCameraPosition(cam), 700)
            updateSelectionInfo()
        }
    }

    /** 自动范围：所有足迹 + 当前位置；没有足迹时取附近 10 公里 */
    private fun autoSelectionBounds(): LatLngBounds {
        val here = currentPos() ?: map?.cameraPosition?.target?.let { doubleArrayOf(it.latitude, it.longitude) }
            ?: doubleArrayOf(prefs.lastLat, prefs.lastLon)
        val pts = track.segments.flatMap { seg -> seg.filterIndexed { i, _ -> i % 10 == 0 } }
        if (pts.isEmpty()) return OfflineMaps.around(here[0], here[1], 10.0)
        val bld = LatLngBounds.Builder().include(LatLng(here[0], here[1]))
        pts.forEach { bld.include(LatLng(it[0], it[1])) }
        // 再往外留 2 公里，太小时至少 5 公里半径
        val small = OfflineMaps.around(here[0], here[1], 5.0)
        bld.include(small.northEast).include(small.southWest)
        val bounds = bld.build()
        val ne = Geo.offset(bounds.latitudeNorth, bounds.longitudeEast, 45.0, 2000.0)
        val sw = Geo.offset(bounds.latitudeSouth, bounds.longitudeWest, 225.0, 2000.0)
        val padded = LatLngBounds.from(ne[0], ne[1], sw[0], sw[1])
        return if (OfflineMaps.tileCount(padded) > OfflineMaps.TILE_LIMIT) OfflineMaps.around(here[0], here[1], 20.0) else padded
    }

    /** 当前框内的地理范围 */
    private fun selectionBounds(): LatLngBounds? {
        val m = map ?: return null
        val f = b.selectFrame.frame
        if (f.width() <= 0) return null
        val tl = m.projection.fromScreenLocation(android.graphics.PointF(f.left, f.top))
        val br = m.projection.fromScreenLocation(android.graphics.PointF(f.right, f.bottom))
        return LatLngBounds.from(tl.latitude, br.longitude, br.latitude, tl.longitude)
    }

    private fun updateSelectionInfo() {
        val bnd = selectionBounds() ?: return
        val midLat = (bnd.latitudeNorth + bnd.latitudeSouth) / 2
        val wKm = Geo.distance(midLat, bnd.longitudeWest, midLat, bnd.longitudeEast) / 1000
        val hKm = Geo.distance(bnd.latitudeNorth, bnd.longitudeWest, bnd.latitudeSouth, bnd.longitudeWest) / 1000
        val tiles = OfflineMaps.tileCount(bnd)
        val mb = OfflineMaps.estimateBytes(bnd) / 1048576.0
        val tooBig = tiles > OfflineMaps.TILE_LIMIT
        b.tvSelectInfo.text = if (tooBig) {
            String.format(Locale.US, "范围太大（约 %.0f MB），请缩小一些", mb)
        } else {
            String.format(Locale.US, "约 %.0f × %.0f 公里  ·  约 %.0f MB", wKm, hKm, mb.coerceAtLeast(1.0))
        }
        b.btnSelectOk.isEnabled = !tooBig
        b.btnSelectOk.alpha = if (tooBig) 0.4f else 1f
    }

    private fun endAreaSelection(download: Boolean) {
        if (!selecting) return
        val bounds = if (download) selectionBounds() else null
        selecting = false
        selectBack?.remove()
        selectBack = null
        b.selectOverlay.animate().alpha(0f).setDuration(Motion.SHORT + 60).setInterpolator(Motion.EASE)
            .withEndAction { b.selectOverlay.isVisible = false }.start()
        for (v in listOf(b.topArea, b.fabColumn, b.bottomArea)) {
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(Motion.STANDARD).setInterpolator(Motion.EASE).start()
        }
        map?.uiSettings?.isCompassEnabled = true
        if (bounds != null) {
            offline.download(bounds)
            showDownloadPanel()
        }
        if (lastLoc != null) follow()
    }

    private fun showDownloadPanel() {
        dlPanel = FloatingPanel(this, maxWidthDp = 360) { inf, panel ->
            val v = com.ebike.dash.databinding.PanelDownloadBinding.inflate(inf)
            dl = v
            v.bar.setColors(FloatingPanel.palette.onSurface, FloatingPanel.palette.chip)
            Motion.pressable(v.btnPositive, v.btnNegative)
            v.btnPositive.setOnClickListener {
                panel.dismiss()
                if (offline.downloading) toast("下载会在后台继续，完成后提醒你")
            }
            v.btnNegative.setOnClickListener {
                offline.cancel()
                panel.dismiss()
                toast("已取消下载")
            }
            refreshDownloadPanel()
            v.root
        }.apply {
            onDismiss = { dl = null; dlPanel = null }
            show()
        }
    }

    private fun refreshDownloadPanel() {
        val v = dl ?: return
        v.tvMessage.text = offline.notice ?: "下载完成后，没有网络也能显示这片区域的地图。"
        v.bar.level = offline.progress
        v.tvPercent.text = String.format(
            Locale.US, "%d%%  ·  %.1f MB", (offline.progress * 100).toInt(), offline.bytes / 1048576.0,
        )
    }

    private fun confirmClearTrack() {
        FloatingPanel.confirm(this, "清除所有足迹？", "地图上记录的所有骑行路线都会被删除，无法恢复。", "清除") {
            track.clear()
            drawTrackHistory()
            drawTrackLive(force = true)
        }
    }

    private fun confirmResetTrip() {
        FloatingPanel.confirm(this, "清零本次骑行？", "里程、骑行时间、均速、最高速将归零（总里程保留）。", "清零") {
            tracker.resetTrip()
            tracker.save(prefs)
            updateDashboard()
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

object BuildConfigCompat {
    fun versionName(a: AppCompatActivity): String =
        try { a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
}
