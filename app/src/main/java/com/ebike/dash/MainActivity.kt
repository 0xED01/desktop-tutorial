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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.ebike.dash.databinding.ActivityMainBinding
import com.ebike.dash.databinding.DialogSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), LocationSink, NavListener {

    companion object {
        private const val SRC_ROUTE = "rd-route"
        private const val SRC_DEST = "rd-dest"
    }

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var voice: Voice
    private lateinit var gps: GpsSource
    private lateinit var nav: NavigationEngine
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
    private var destPoint: DoubleArray? = null

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
        voice = Voice(this)
        gps = GpsSource(this, this)
        heading = HeadingSensor(this) { onHeading(it) }
        nav = NavigationEngine(lifecycleScope, this, units)
        tracker.restore(prefs)

        b.mapView.onCreate(savedInstanceState)
        b.mapView.getMapAsync { m ->
            map = m
            setupMap(m)
        }

        b.btnRecenter.setOnClickListener { recenter() }
        b.btnSearch.setOnClickListener { showSearch() }
        b.btnSettings.setOnClickListener { showSettings() }
        b.btnInfo.setOnClickListener { setInfoVisible(!b.infoPanel.isVisible) }
        b.btnStopNav.setOnClickListener { stopNavigation(true) }
        b.statsRow.setOnLongClickListener {
            confirmResetTrip(); true
        }

        b.infoPanel.isVisible = prefs.showInfo
        applyPalette(if (isNight()) Palette.DARK else Palette.LIGHT)
        applyPrefs()
        b.bottomArea.post { updateTrackingPadding() }
        updateDashboard()
        updateStatus()

        if (gps.hasPermission()) startGps() else permLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    override fun onStart() { super.onStart(); b.mapView.onStart() }
    override fun onResume() {
        super.onResume()
        b.mapView.onResume()
        ui.post(statusTicker)
        heading.start()
        if (demo == null) startGps()
    }
    override fun onPause() {
        super.onPause()
        b.mapView.onPause()
        ui.removeCallbacks(statusTicker)
        heading.stop()
        tracker.save(prefs)
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
        voice.shutdown()
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
        if (!gps.gpsEnabled()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("GPS 未开启")
                .setMessage("码表需要 GPS 来测速和导航，请在系统设置中打开位置信息。")
                .setPositiveButton("去设置") { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton("取消", null)
                .show()
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
            onMapLongPress(p.latitude, p.longitude)
            true
        }
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
            addRouteLayers(s)
            enableLocationComponent(s)
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

    private fun addRouteLayers(s: Style) {
        s.addSource(GeoJsonSource(SRC_ROUTE))
        s.addSource(GeoJsonSource(SRC_DEST))
        val width = Expression.interpolate(
            Expression.exponential(1.5f), Expression.zoom(),
            Expression.stop(10, 3f), Expression.stop(14, 6f), Expression.stop(18, 16f),
        )
        val casingWidth = Expression.interpolate(
            Expression.exponential(1.5f), Expression.zoom(),
            Expression.stop(10, 5f), Expression.stop(14, 9f), Expression.stop(18, 22f),
        )
        val casing = LineLayer("rd-route-casing", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(pal.routeCasing),
            PropertyFactory.lineWidth(casingWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        val line = LineLayer("rd-route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(pal.routeLine),
            PropertyFactory.lineWidth(width),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        val dest = CircleLayer("rd-dest", SRC_DEST).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(pal.routeLine),
            PropertyFactory.circleStrokeColor(pal.routeCasing),
            PropertyFactory.circleStrokeWidth(4f),
        )
        // 路线画在所有道路之上、文字标注之下
        val lastShape = s.layers.lastOrNull { it !is SymbolLayer }?.id
        if (lastShape != null) {
            s.addLayerAbove(casing, lastShape)
            s.addLayerAbove(line, casing.id)
        } else {
            s.addLayer(casing)
            s.addLayer(line)
        }
        s.addLayer(dest)
    }

    private fun drawRoute(coords: List<DoubleArray>?) {
        val src = style?.getSourceAs<GeoJsonSource>(SRC_ROUTE) ?: return
        if (coords == null || coords.size < 2) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        src.setGeoJson(LineString.fromLngLats(coords.map { Point.fromLngLat(it[1], it[0]) }))
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
        lc.activateLocationComponent(
            LocationComponentActivationOptions.builder(this, s)
                .locationComponentOptions(opts)
                .useDefaultLocationEngine(false)
                .build()
        )
        lc.isLocationComponentEnabled = true
        lc.renderMode = RenderMode.GPS
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
        lastLoc?.let { lc.forceLocationUpdate(it) }
        if (following) follow()
        b.bottomArea.post { updateTrackingPadding() }
    }

    private fun follow() {
        val lc = map?.locationComponent ?: return
        if (!lc.isLocationComponentActivated) return
        following = true
        b.btnRecenter.alpha = 1f
        lc.cameraMode = CameraMode.TRACKING_GPS
        lc.zoomWhileTracking(if (nav.active) 17.3 else 16.5, 900)
        lc.tiltWhileTracking(if (nav.active) 55.0 else 40.0, 900)
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
        delegate.localNightMode =
            if (p === Palette.DARK) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        val surface = ColorStateList.valueOf(p.surface)
        b.infoPanel.backgroundTintList = surface
        // 不透明：半透明卡片会透出自身阴影，看起来深浅不均
        b.speed.root.backgroundTintList = surface
        b.navDivider.setBackgroundColor(p.chip)
        for (btn in listOf(b.btnSearch, b.btnRecenter, b.btnInfo, b.btnSettings)) {
            btn.backgroundTintList = surface
            btn.imageTintList = ColorStateList.valueOf(p.onSurface)
        }
        // 结束导航按钮：反色实心圆
        b.btnStopNav.backgroundTintList = ColorStateList.valueOf(p.onSurface)
        b.btnStopNav.imageTintList = surface
        tintTexts(b.overlay, p)
        map?.uiSettings?.setCompassImage(compassDrawable())
        updateInfoButton()
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
        map?.locationComponent?.let { if (it.isLocationComponentActivated) it.forceLocationUpdate(fixed) }
        nav.onLocation(fixed)
        updateDashboard()
        updateGpsIndicator(fixed)

        if (prev == null && following) follow()
        if (System.currentTimeMillis() - lastSavedPos > 30_000) {
            lastSavedPos = System.currentTimeMillis()
            prefs.lastLat = fixed.latitude
            prefs.lastLon = fixed.longitude
        }
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
        map?.locationComponent?.let { if (it.isLocationComponentActivated) it.forceLocationUpdate(l) }
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
            }
            start()
        }
    }


    /** 数据面板（骑行数据 + 导航剩余信息），默认隐藏 */
    private fun setInfoVisible(show: Boolean) {
        prefs.showInfo = show
        b.infoPanel.isVisible = show
        updateInfoButton()
    }

    /** 面板打开时按钮反色，表示“已开启” */
    private fun updateInfoButton() {
        val on = b.infoPanel.isVisible
        b.btnInfo.backgroundTintList = ColorStateList.valueOf(if (on) pal.onSurface else pal.surface)
        b.btnInfo.imageTintList = ColorStateList.valueOf(if (on) pal.surface else pal.onSurface)
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

    // ---------------------------------------------------------------- 导航

    private fun currentPos(): DoubleArray? = lastLoc?.let { doubleArrayOf(it.latitude, it.longitude) }

    /** 长按地图：先查出这个位置的地址，再确认是否导航 */
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
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(p.name)
                .setMessage(listOfNotNull(p.address.ifBlank { null }, dist?.let { "距离你 $it" }).joinToString("\n"))
                .setPositiveButton("去这里") { _, _ -> prefs.addHistory(p); startNavigation(p.lat, p.lon, p.name) }
                .setNegativeButton("取消", null)
                .setNeutralButton("收藏") { _, _ -> saveAsShortcut(p) }
                .setOnCancelListener { clearPendingDest() }
                .setOnDismissListener { if (!nav.active) clearPendingDest() }
                .show()
        }
    }

    private fun clearPendingDest() {
        if (nav.active) return
        destPoint = null
        drawDest(null, null)
    }

    private fun saveAsShortcut(p: Place) {
        MaterialAlertDialogBuilder(this)
            .setTitle("收藏为")
            .setItems(arrayOf("家", "公司")) { _, i ->
                prefs.putPlace(if (i == 0) Prefs.HOME else Prefs.WORK, p)
                toast(if (i == 0) "已设为家" else "已设为公司")
            }
            .show()
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
        toast("路线规划失败：$msg")
    }

    override fun onProgress(p: NavProgress) {
        nav.route?.let { drawRoute(it.remainingCoords(p.snap)) }
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

    // ---------------------------------------------------------------- 演示模式

    private fun toggleDemo() {
        val d = demo
        if (d != null) {
            d.stop()
            demo = null
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
        val sb = DialogSettingsBinding.inflate(LayoutInflater.from(this))
        val dlg = BottomSheetDialog(this)
        dlg.setContentView(sb.root)

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
        dlg.show()
    }

    private fun confirmResetTrip() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清零本次骑行？")
            .setMessage("里程、骑行时间、均速、最高速将归零（总里程保留）。")
            .setPositiveButton("清零") { _, _ ->
                tracker.resetTrip()
                tracker.save(prefs)
                updateDashboard()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

object BuildConfigCompat {
    fun versionName(a: AppCompatActivity): String =
        try { a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
}
